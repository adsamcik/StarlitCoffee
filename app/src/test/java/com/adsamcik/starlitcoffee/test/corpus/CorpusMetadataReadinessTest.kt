package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Metadata-contract checks for automation wiring that go beyond the basic
 * schema test. These keep the committed corpus ready for barcode fixtures and
 * side-fusion assertions without forcing instrumented runs.
 */
class CorpusMetadataReadinessTest {

    private lateinit var corpusDir: File
    private lateinit var corpus: CoffeeBagCorpus

    @Before
    fun loadCorpus() {
        val dir = resolveCorpusDir()
        assumeTrue(
            "Synthetic coffee-bag corpus not found relative to the repo root.",
            dir != null,
        )
        corpusDir = dir!!
        corpus = parser.decodeFromString(
            CoffeeBagCorpus.serializer(),
            File(corpusDir, "corpus_metadata.json").readText(),
        )
        assertTrue("Corpus must contain at least 13 bags", corpus.bags.size >= 13)
    }

    @Test
    fun `every bag declares barcode digits for the back label fixture`() {
        for (bag in corpus.bags) {
            assertTrue("Bag '${bag.id}' must remain two-sided for fixture wiring", bag.photos.back != null)
            val barcode = bag.extraString("barcode")
            assertNotNull("Bag '${bag.id}' is missing extras.barcode", barcode)
            assertTrue(
                "Bag '${bag.id}' barcode must be digit-only; got '$barcode'",
                barcode!!.isNotBlank() && barcode.all(Char::isDigit),
            )
        }
    }

    @Test
    fun `duplicate barcode values are explicitly marked as collisions`() {
        val groups = corpus.bags
            .groupBy { it.extraString("barcode") }
            .filterKeys { !it.isNullOrBlank() }

        for ((barcode, bags) in groups) {
            if (bags.size < 2) continue
            val expectedGroup = "ean13-$barcode"
            for (bag in bags) {
                assertEquals(
                    "Bag '${bag.id}' shares barcode '$barcode' and must opt out of uniqueness.",
                    false,
                    bag.extraBoolean("barcodeUnique"),
                )
                assertEquals(
                    "Bag '${bag.id}' must name its barcode collision group.",
                    expectedGroup,
                    bag.extraString("barcodeCollisionGroup"),
                )
            }
        }
    }

    @Test
    fun `split front back bags declare machine-readable side metadata`() {
        val expectedFront = setOf("roaster", "name", "origin", "roastLevel", "weight")
        val expectedBack =
            setOf("region", "farm", "variety", "process", "tastingNotes", "altitude", "roastDate", "expiryDate")

        val splitBags = corpus.bags.filter { "split_front_back" in it.extraStringList("riskTags") }
        assertTrue("Corpus must contain at least one split front/back bag", splitBags.isNotEmpty())

        for (bag in splitBags) {
            assertEquals("split_front_back", bag.extraString("labelLayout"))
            assertEquals(true, bag.extraBoolean("requiresSideFusion"))

            val frontFields = bag.extraStringList("frontFields").toSet()
            val backFields = bag.extraStringList("backFields").toSet()

            assertTrue(
                "Bag '${bag.id}' frontFields must cover the shopping-side subset",
                frontFields.containsAll(expectedFront),
            )
            assertTrue(
                "Bag '${bag.id}' backFields must cover the detail-side subset",
                backFields.containsAll(expectedBack),
            )
            assertTrue(
                "Bag '${bag.id}' front/back side metadata must only reference known field keys",
                (frontFields + backFields).all { it in CorpusFields.metadataKeys },
            )
            assertTrue(
                "Bag '${bag.id}' frontFields and backFields should describe different responsibilities",
                frontFields.intersect(backFields).isEmpty(),
            )
        }
    }

    private fun CoffeeBagFixture.extraString(key: String): String? =
        (extras[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun CoffeeBagFixture.extraBoolean(key: String): Boolean? =
        (extras[key] as? JsonPrimitive)?.booleanOrNull

    private fun CoffeeBagFixture.extraStringList(key: String): List<String> =
        (extras[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
            .orEmpty()

    private fun resolveCorpusDir(): File? {
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
