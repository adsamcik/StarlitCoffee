package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagCorpus
import com.adsamcik.starlitcoffee.test.corpus.CoffeeBagFixture
import com.adsamcik.starlitcoffee.test.corpus.CorpusFields
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagOcrTextMerger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Corpus-driven validation of the bag-extraction parser + prompt contract,
 * plus structural validation of the committed synthetic corpus metadata.
 *
 * The synthetic corpus now ships in the repo at
 * `testdata/synthetic-coffee-bag-corpus/` (images as WebP + `corpus_metadata.json`),
 * so this test resolves it automatically and no longer depends on a local
 * real-photo directory. An override is still honoured via the
 * `mindlayer.coffee.bag.corpus` system property / `MINDLAYER_COFFEE_BAG_CORPUS`
 * env var for pointing at an alternate corpus.
 *
 * This is a pure-JVM test: the bag PHOTOS are not consumed and the on-device
 * model never runs. It pins, before anything reaches an emulator:
 *  1. The metadata is structurally valid (schema, all 14 fields per bag,
 *     valid tiers, referenced image files exist, multilingual + decaf coverage).
 *  2. Synthesised LLM JSON mirroring the ground truth round-trips through
 *     [MindlayerLlmInferenceProvider.parseResponse] into the right slots.
 *  3. The [BagOcrTextMerger] front+back fusion contract holds.
 */
class CoffeeBagCorpusExtractionTest {

    private lateinit var corpusDir: File
    private lateinit var corpus: CoffeeBagCorpus

    @Before
    fun loadCorpus() {
        val dir = resolveCorpusDir()
        assumeTrue(
            "Synthetic coffee-bag corpus not found. Expected testdata/synthetic-coffee-bag-corpus/" +
                "corpus_metadata.json relative to the repo root, or an override via " +
                "MINDLAYER_COFFEE_BAG_CORPUS / mindlayer.coffee.bag.corpus.",
            dir != null,
        )
        corpusDir = dir!!
        val metadataFile = File(corpusDir, "corpus_metadata.json")
        corpus = parser.decodeFromString(CoffeeBagCorpus.serializer(), metadataFile.readText())
        assertEquals(
            "Test code only knows schema v1 — metadata declares v${corpus.schemaVersion}.",
            1,
            corpus.schemaVersion,
        )
        assertTrue("Corpus must contain at least 13 bags", corpus.bags.size >= 13)
    }

    @Test
    fun `every bag declares all 14 fields with keys the parser recognises`() {
        val parserKeys = CorpusFields.metadataKeys.toSet()
        for (bag in corpus.bags) {
            val unknown = bag.fields.keys - parserKeys
            assertTrue("Bag '${bag.id}' declares unknown field keys: $unknown", unknown.isEmpty())
            val missing = parserKeys - bag.fields.keys
            assertTrue(
                "Bag '${bag.id}' is missing required field keys: $missing. Every bag must " +
                    "declare all 14 fields explicitly (use JSON null for not_visible) so " +
                    "absence is never confused with abstention.",
                missing.isEmpty(),
            )
        }
    }

    @Test
    fun `every bag has a valid tier and existing image files`() {
        val validTiers = setOf("Q0", "Q1", "Q2", "Q3", "Q4")
        for (bag in corpus.bags) {
            assertTrue(
                "Bag '${bag.id}' has invalid/absent captureTier '${bag.captureTier}'",
                bag.captureTier in validTiers,
            )
            assertTrue("Bag '${bag.id}' must declare a language", bag.language.isNotEmpty())
            val front = File(corpusDir, bag.photos.front)
            assertTrue("Bag '${bag.id}' front image missing: ${front.path}", front.isFile)
            bag.photos.back?.let { back ->
                val backFile = File(corpusDir, back)
                assertTrue("Bag '${bag.id}' back image missing: ${backFile.path}", backFile.isFile)
            }
        }
    }

    @Test
    fun `corpus covers Q0 best case, multiple languages, two-side fusion and decaf`() {
        val tiers = corpus.bags.mapNotNull { it.captureTier }.toSet()
        assertTrue("Corpus must include a Q0 best-case tier (currently $tiers)", "Q0" in tiers)

        val languages = corpus.bags.flatMap { it.language }.toSet()
        assertTrue("Corpus must cover >= 3 languages (currently $languages)", languages.size >= 3)

        val twoSide = corpus.bags.count { it.isTwoSided }
        assertTrue("Corpus must contain >= 1 two-side bag (currently $twoSide)", twoSide >= 1)

        val decafBags = corpus.bags.count { bag ->
            val element = bag.fields["isDecaf"]
            element is JsonPrimitive && element.booleanOrNull == true
        }
        assertTrue("Corpus must contain >= 1 decaf bag (currently $decafBags)", decafBags >= 1)
    }

    @Test
    fun `synthesised LLM JSON for every bag round-trips through parseResponse`() {
        for (bag in corpus.bags) {
            val response = synthesiseLlmResponse(bag)
            val result = MindlayerLlmInferenceProvider.parseResponse(response, emptySet())
            assertTrue(
                "parseResponse must return Success for bag '${bag.id}'; got $result",
                result is LlmExtractionResult.Success,
            )
            val candidateByField = (result as LlmExtractionResult.Success)
                .fieldCandidates.associateBy { it.fieldName }

            for ((llmKey, element) in bag.fields) {
                val starlitFieldName = MindlayerLlmInferenceProvider.fieldMapping.getValue(llmKey)
                val candidate = candidateByField[starlitFieldName]
                if (element is JsonNull) {
                    assertNull(
                        "Bag '${bag.id}' field '$starlitFieldName' is not_visible — candidate must be absent",
                        candidate,
                    )
                } else {
                    val expectedValue = jsonElementToString(element)
                    assertNotNull(
                        "Bag '${bag.id}' field '$starlitFieldName' (expected '$expectedValue') was dropped",
                        candidate,
                    )
                    assertEquals(
                        "Bag '${bag.id}' field '$starlitFieldName' value mismatch",
                        expectedValue,
                        candidate!!.value,
                    )
                }
            }
        }
    }

    @Test
    fun `two-side bags produce labeled merger output`() {
        val twoSideBags = corpus.bags.filter { it.isTwoSided }
        assertTrue("Corpus must contain at least one two-side bag", twoSideBags.isNotEmpty())
        for (bag in twoSideBags) {
            val merged = BagOcrTextMerger.combineBySide(
                listOf(
                    BagCaptureSide.FRONT to "front-of-${bag.id}",
                    BagCaptureSide.BACK to "back-of-${bag.id}",
                ),
            )
            assertTrue("Bag '${bag.id}' merged output missing --- FRONT ---", merged.contains("--- FRONT ---"))
            assertTrue("Bag '${bag.id}' merged output missing --- BACK ---", merged.contains("--- BACK ---"))
        }
    }

    private fun synthesiseLlmResponse(bag: CoffeeBagFixture): String {
        val fieldsObj = buildString {
            append("{\n")
            val entries = MindlayerLlmInferenceProvider.fieldMapping.keys.toList()
            entries.forEachIndexed { index, key ->
                val element = bag.fields[key]
                val (jsonValue, status) = when {
                    element == null || element is JsonNull -> "null" to "not_visible"
                    element is JsonPrimitive && element.booleanOrNull != null ->
                        element.booleanOrNull.toString() to "found"
                    else -> "\"${jsonEscape(jsonElementToString(element))}\"" to "found"
                }
                append("    \"$key\": {\"value\": $jsonValue, \"status\": \"$status\"}")
                if (index != entries.size - 1) append(",")
                append("\n")
            }
            append("  }")
        }
        return "{\n  \"fields\": $fieldsObj\n}"
    }

    private fun jsonElementToString(element: JsonElement): String = when {
        element is JsonNull -> ""
        element is JsonPrimitive && element.booleanOrNull != null -> element.booleanOrNull.toString()
        element is JsonPrimitive -> element.contentOrNull ?: ""
        else -> element.toString()
    }

    private fun jsonEscape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    /**
     * Resolve the corpus dir: explicit override first, else search upward from
     * the working directory for the committed `testdata/synthetic-coffee-bag-corpus`.
     */
    private fun resolveCorpusDir(): File? {
        val override = listOf(
            System.getenv("MINDLAYER_COFFEE_BAG_CORPUS"),
            System.getProperty("mindlayer.coffee.bag.corpus"),
        ).firstOrNull { !it.isNullOrBlank() }
        if (override != null) {
            val dir = File(override)
            return dir.takeIf { it.isDirectory && File(it, "corpus_metadata.json").isFile }
        }

        var current: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(MAX_UPWARD_SEARCH) {
            val candidate = current?.let { File(it, "testdata/synthetic-coffee-bag-corpus") }
            if (candidate != null && File(candidate, "corpus_metadata.json").isFile) return candidate
            current = current?.parentFile
        }
        return null
    }

    private val parser: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }

    private companion object {
        const val MAX_UPWARD_SEARCH = 6
    }
}
