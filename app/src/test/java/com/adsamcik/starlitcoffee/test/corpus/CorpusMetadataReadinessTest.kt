package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
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
    private lateinit var automationCorpus: CoffeeBagCorpus

    @Before
    fun loadCorpus() {
        val dir = resolveCorpusDir()
        assumeTrue(
            "Synthetic coffee-bag corpus not found relative to the repo root.",
            dir != null,
        )
        corpusDir = dir!!
        corpus = CoffeeBagCorpusLoader.loadAll(corpusDir)
        automationCorpus = CoffeeBagCorpusLoader.loadAutomationReady(corpusDir)
        assertTrue("Corpus must contain at least 13 automation-ready bags", automationCorpus.bags.size >= 13)
    }

    @Test
    fun `automation ready fixtures declare a front photo`() {
        assertTrue("Automation-ready corpus must not be empty", automationCorpus.bags.isNotEmpty())
        for (bag in automationCorpus.bags) {
            assertTrue(
                "Automation-ready bag '${bag.id}' must declare a front photo",
                bag.hasFrontPhoto,
            )
        }
    }

    @Test
    fun `declared barcode values are digit only`() {
        val barcodeBags = corpus.bags.filter { !it.extraString("barcode").isNullOrBlank() }
        assertTrue("Corpus must keep at least one declared barcode fixture", barcodeBags.isNotEmpty())
        for (bag in barcodeBags) {
            val barcode = bag.extraString("barcode")
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

        val splitBags = automationCorpus.bags.filter { "split_front_back" in it.extraStringList("riskTags") }
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
            if (candidate != null && CoffeeBagCorpusLoader.looksLikeCorpusDir(candidate)) return candidate
            current = current?.parentFile
        }
        return null
    }

    private companion object {
        const val MAX_UPWARD_SEARCH = 6
    }
}
