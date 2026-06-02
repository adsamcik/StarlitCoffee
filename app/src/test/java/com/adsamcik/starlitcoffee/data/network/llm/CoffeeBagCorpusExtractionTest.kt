package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagOcrTextMerger
import kotlinx.serialization.Serializable
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
 * Corpus-driven validation of the bag-extraction parser + prompt contract.
 *
 * Reads ground-truth metadata for every coffee bag in the test corpus
 * (`corpus_metadata.json` in the directory pointed to by the
 * `MINDLAYER_COFFEE_BAG_CORPUS` env var or `mindlayer.coffee.bag.corpus`
 * Gradle property) and verifies that:
 *
 *  1. Synthesised LLM JSON that mirrors the ground truth round-trips
 *     through [MindlayerLlmInferenceProvider.parseResponse] and lands
 *     in the right field-mapping slots (`process` → `processType`,
 *     `isDecaf` → boolean, etc.).
 *  2. The metadata file declares fields the parser can actually handle —
 *     a regression to the JSON schema (e.g. someone adds a `country`
 *     field without wiring it through `fieldMapping`) trips here.
 *  3. The [BagOcrTextMerger] front+back fusion contract holds for the
 *     two-photo entries in the corpus (the metadata declares which
 *     photos exist; the merger should produce a labeled string only
 *     when both sides are present).
 *
 * The bag PHOTOS themselves are NOT consumed by this test — running
 * OCR against the real JPEGs requires the Mindlayer service on a
 * connected device, so that work lives in the manual on-device
 * validation. This test pins the parser/prompt contract end-to-end in
 * pure JVM so a regression to the LLM JSON shape, the field mapping,
 * or the merger is caught by `:app:testDebugUnitTest` before anyone
 * notices on-device.
 *
 * # Skip behavior
 * If the corpus env var is unset or the metadata file is missing, the
 * test class skips with a clear message instead of failing. This keeps
 * CI happy when the corpus isn't mounted while still surfacing
 * regressions for any developer who has the corpus present (the common
 * dev setup on the original author's machine).
 *
 * # Running locally
 * ```powershell
 * $env:MINDLAYER_COFFEE_BAG_CORPUS = "D:\Cloud\Proton\My files\Coffee bags"
 * ./gradlew :app:testDebugUnitTest --tests '*CoffeeBagCorpusExtractionTest*'
 * ```
 *
 * Or via Gradle property:
 * ```powershell
 * ./gradlew :app:testDebugUnitTest --tests '*CoffeeBagCorpusExtractionTest*' `
 *     "-Pmindlayer.coffee.bag.corpus=D:\Cloud\Proton\My files\Coffee bags"
 * ```
 */
class CoffeeBagCorpusExtractionTest {

    private lateinit var corpus: CoffeeBagCorpus

    @Before
    fun loadCorpus() {
        val dir = resolveCorpusDir()
        assumeTrue(
            "Coffee bag corpus not configured — set MINDLAYER_COFFEE_BAG_CORPUS env var or " +
                "mindlayer.coffee.bag.corpus system property to the directory containing " +
                "corpus_metadata.json. Skipping corpus-driven extraction tests.",
            dir != null,
        )
        val metadataFile = File(dir!!, "corpus_metadata.json")
        assumeTrue(
            "corpus_metadata.json not found in $dir — corpus directory is configured " +
                "but the metadata file is missing.",
            metadataFile.isFile,
        )
        corpus = parser.decodeFromString(
            CoffeeBagCorpus.serializer(),
            metadataFile.readText(),
        )
        assertEquals(
            "Test code only knows schema v1 — metadata file declares v${corpus.schemaVersion}. " +
                "Update CoffeeBagCorpusExtractionTest if the corpus schema has evolved.",
            1,
            corpus.schemaVersion,
        )
        assertTrue("Corpus must contain at least one bag", corpus.bags.isNotEmpty())
    }

    @Test
    fun `every bag's fields keys are recognised by the LLM parser`() {
        // Regression guard against silently dropping a field. If the
        // metadata file declares a field key that fieldMapping doesn't
        // know about, the corpus will still "pass" extraction tests
        // (because parseResponse just skips unknown keys) but the
        // ground truth would be unverifiable. Fail loudly instead.
        val parserKeys = MindlayerLlmInferenceProvider.fieldMapping.keys
        for (bag in corpus.bags) {
            val unknownFields = bag.fields.keys - parserKeys
            assertTrue(
                "Bag '${bag.id}' declares unknown field keys: $unknownFields. " +
                    "Either add them to MindlayerLlmInferenceProvider.fieldMapping or " +
                    "remove them from the metadata file.",
                unknownFields.isEmpty(),
            )
        }
    }

    @Test
    fun `synthesised LLM JSON for every bag round-trips through parseResponse`() {
        // Build an LLM response JSON for each bag that mirrors the
        // ground truth, parse it, and verify each non-null field lands
        // in the correct candidate slot with the right value. Null
        // ground-truth fields must drop out (not_visible → skipped).
        // This pins three contracts at once:
        //   1. The LLM JSON shape the parser expects.
        //   2. The fieldMapping (LLM key -> StarlitCoffee field name).
        //   3. The non-null-only candidate emission rule.
        for (bag in corpus.bags) {
            val response = synthesiseLlmResponse(bag)
            val result = MindlayerLlmInferenceProvider.parseResponse(
                response = response,
                fieldsNeeded = emptySet(),
            )
            assertTrue(
                "parseResponse must return Success for bag '${bag.id}'; got $result",
                result is LlmExtractionResult.Success,
            )
            val candidates = (result as LlmExtractionResult.Success).fieldCandidates
            val candidateByField = candidates.associateBy { it.fieldName }

            for ((llmKey, element) in bag.fields) {
                val starlitFieldName =
                    MindlayerLlmInferenceProvider.fieldMapping.getValue(llmKey)
                val candidate = candidateByField[starlitFieldName]
                val isGroundTruthNull = element is JsonNull
                if (isGroundTruthNull) {
                    assertNull(
                        "Bag '${bag.id}' field '$starlitFieldName' is null in ground " +
                            "truth (LLM emitted not_visible) — candidate must be absent " +
                            "from the parsed result, but found: $candidate",
                        candidate,
                    )
                } else {
                    val expectedValue = jsonElementToString(element)
                    assertNotNull(
                        "Bag '${bag.id}' field '$starlitFieldName' (expected '$expectedValue') " +
                            "was dropped by parseResponse. fieldMapping or extraction " +
                            "logic may be broken.",
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
        // Pin the merger contract for the corpus subset that has both
        // photos. Single-photo bags get covered by BagOcrTextMergerTest;
        // here we just verify the corpus declares ≥1 two-side bag so
        // the test corpus actually exercises the fusion path.
        val twoSideBags = corpus.bags.filter { it.photos.back != null }
        assertTrue(
            "Corpus must contain at least one two-side bag to exercise the " +
                "front+back fusion path. Add a {front, back} pair to corpus_metadata.json.",
            twoSideBags.isNotEmpty(),
        )

        // Verify the merger labels them with both --- FRONT --- and
        // --- BACK --- sections. Tokens fed in are placeholders — the
        // contract under test is the section layout, not the OCR text.
        for (bag in twoSideBags) {
            val merged = BagOcrTextMerger.combineBySide(
                listOf(
                    BagCaptureSide.FRONT to "front-of-${bag.id}",
                    BagCaptureSide.BACK to "back-of-${bag.id}",
                ),
            )
            assertTrue(
                "Bag '${bag.id}' merged output missing --- FRONT --- section: $merged",
                merged.contains("--- FRONT ---"),
            )
            assertTrue(
                "Bag '${bag.id}' merged output missing --- BACK --- section: $merged",
                merged.contains("--- BACK ---"),
            )
        }
    }

    @Test
    fun `corpus covers a representative mix of language and side counts`() {
        // Coverage smoke test — if someone trims the corpus down to one
        // English single-side bag, that's a real loss in test value the
        // CI signal should surface. Numbers are deliberately loose so
        // the corpus can grow without rewriting this test every time.
        val languages = corpus.bags.flatMap { it.language }.toSet()
        assertTrue(
            "Corpus must cover at least 2 distinct languages to exercise the " +
                "multilingual translation contract (currently: $languages).",
            languages.size >= 2,
        )
        val twoSide = corpus.bags.count { it.photos.back != null }
        val frontOnly = corpus.bags.count { it.photos.back == null }
        assertTrue(
            "Corpus must contain at least one two-side bag (currently $twoSide) " +
                "and at least one front-only bag (currently $frontOnly) to " +
                "exercise both the fusion and fallback paths.",
            twoSide >= 1 && frontOnly >= 1,
        )
        val decafBags = corpus.bags.count { bag ->
            val element = bag.fields["isDecaf"]
            element is JsonPrimitive && element.booleanOrNull == true
        }
        assertTrue(
            "Corpus must contain at least one decaf bag to exercise the isDecaf " +
                "extraction contract (currently $decafBags).",
            decafBags >= 1,
        )
    }

    /**
     * Build an LLM response JSON string for [bag] that mirrors its
     * ground truth, in the same shape the real `MindlayerLlmInferenceProvider`
     * expects (nested `{ "fields": { ... } }` with `{ "value": ..., "status": ... }`
     * entries). Null ground-truth values get `value: null, status: "not_visible"`
     * which `parseResponse` should drop.
     */
    private fun synthesiseLlmResponse(bag: CoffeeBagFixture): String {
        val fieldsObj = buildString {
            append("{\n")
            val entries = MindlayerLlmInferenceProvider.fieldMapping.keys.toList()
            entries.forEachIndexed { index, key ->
                val element = bag.fields[key]
                val (jsonValue, status) = when {
                    element == null || element is JsonNull -> "null" to "not_visible"
                    element is JsonPrimitive && element.booleanOrNull != null -> {
                        element.booleanOrNull.toString() to "found"
                    }
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

    /**
     * Render a JsonElement as the plain Kotlin string the candidate value
     * would hold after parsing. Booleans use Kotlin's true/false (matching
     * what `JsonPrimitive(true).toString()` would yield via the LLM
     * response — kept lowercased for the wire contract).
     */
    private fun jsonElementToString(element: JsonElement): String = when {
        element is JsonNull -> ""
        element is JsonPrimitive && element.booleanOrNull != null ->
            element.booleanOrNull.toString()
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
     * Resolve the corpus directory from env var or system property.
     * Returns null if neither is set, which triggers Assume.assumeTrue
     * to skip the test class.
     */
    private fun resolveCorpusDir(): File? {
        val envDir = System.getenv("MINDLAYER_COFFEE_BAG_CORPUS")
        val propDir = System.getProperty("mindlayer.coffee.bag.corpus")
        val raw = listOf(envDir, propDir).firstOrNull { !it.isNullOrBlank() } ?: return null
        val dir = File(raw)
        return if (dir.isDirectory) dir else null
    }

    private val parser: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
    }
}

@Serializable
internal data class CoffeeBagCorpus(
    @kotlinx.serialization.SerialName("schema_version")
    val schemaVersion: Int,
    val bags: List<CoffeeBagFixture>,
)

@Serializable
internal data class CoffeeBagFixture(
    val id: String,
    val photos: CoffeeBagPhotos,
    val language: List<String>,
    val fields: Map<String, JsonElement>,
    val extras: Map<String, JsonElement> = emptyMap(),
    val notes: String? = null,
)

@Serializable
internal data class CoffeeBagPhotos(
    val front: String,
    val back: String? = null,
)

