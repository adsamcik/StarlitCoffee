package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic JVM coverage of the scoring rules in [BagFieldScorer] +
 * [FieldComparators]. The on-device model never runs here — these pin the
 * classification logic so the instrumented quality numbers and the Q0 gate
 * mean what they claim.
 */
class BagFieldScorerTest {

    private fun spec(metadataKey: String): FieldSpec = CorpusFields.byMetadataKey.getValue(metadataKey)

    @Test
    fun `exact value is EXACT`() {
        assertEquals(
            FieldOutcome.EXACT,
            BagFieldScorer.scoreField(spec("roaster"), "North Axis Roastery", "north axis roastery"),
        )
    }

    @Test
    fun `not_visible plus blank extraction is ABSTAINED`() {
        assertEquals(FieldOutcome.ABSTAINED, BagFieldScorer.scoreField(spec("farm"), null, null))
    }

    @Test
    fun `not_visible plus a produced value is HALLUCINATED`() {
        assertEquals(FieldOutcome.HALLUCINATED, BagFieldScorer.scoreField(spec("farm"), null, "Some Farm"))
    }

    @Test
    fun `visible value plus blank extraction is MISSING`() {
        assertEquals(FieldOutcome.MISSING, BagFieldScorer.scoreField(spec("origin"), "Ethiopia", null))
    }

    @Test
    fun `disagreeing value is WRONG`() {
        assertEquals(FieldOutcome.WRONG, BagFieldScorer.scoreField(spec("origin"), "Ethiopia", "Brazil"))
    }

    @Test
    fun `origin matches multilingual aliases`() {
        assertEquals(FieldOutcome.EXACT, BagFieldScorer.scoreField(spec("origin"), "Ethiopia", "Aethiopien"))
        assertEquals(FieldOutcome.EXACT, BagFieldScorer.scoreField(spec("origin"), "Colombia", "Kolumbie"))
    }

    @Test
    fun `process matches localized variants`() {
        assertEquals(FieldOutcome.EXACT, BagFieldScorer.scoreField(spec("process"), "Washed", "Lavé"))
        assertEquals(FieldOutcome.EXACT, BagFieldScorer.scoreField(spec("process"), "Natural", "Naturale"))
    }

    @Test
    fun `weight compares grams not formatting`() {
        assertEquals(FieldOutcome.EXACT, BagFieldScorer.scoreField(spec("weight"), "250 g", "250g"))
        assertEquals(FieldOutcome.WRONG, BagFieldScorer.scoreField(spec("weight"), "250 g", "200 g"))
    }

    @Test
    fun `dates compare digit signature across formats`() {
        assertEquals(FieldOutcome.EXACT, BagFieldScorer.scoreField(spec("roastDate"), "2026-05-28", "28.05.2026"))
    }

    @Test
    fun `altitude range matches regardless of unit text`() {
        assertEquals(FieldOutcome.EXACT, BagFieldScorer.scoreField(spec("altitude"), "1900-2200", "1900-2200 masl"))
        assertEquals(FieldOutcome.PARTIAL, BagFieldScorer.scoreField(spec("altitude"), "1900-2200", "1900-2300"))
    }

    @Test
    fun `tasting notes are order-insensitive token sets`() {
        assertEquals(
            FieldOutcome.EXACT,
            BagFieldScorer.scoreField(spec("tastingNotes"), "blueberry, jasmine, citrus", "citrus, blueberry, jasmine"),
        )
        assertEquals(
            FieldOutcome.PARTIAL,
            BagFieldScorer.scoreField(spec("tastingNotes"), "blueberry, jasmine, citrus", "blueberry, jasmine, lime"),
        )
    }

    @Test
    fun `isDecaf false does not match true`() {
        // Regression guard: a naive substring matcher would let "decaf" pass
        // against "not decaf". Boolean comparator must treat them as distinct.
        assertEquals(FieldOutcome.WRONG, BagFieldScorer.scoreField(spec("isDecaf"), "false", "true"))
        assertEquals(FieldOutcome.EXACT, BagFieldScorer.scoreField(spec("isDecaf"), "true", "true"))
    }

    @Test
    fun `scoreBag maps process to processType via FieldSpec`() {
        val bag = CoffeeBagFixture(
            id = "t",
            photos = CoffeeBagPhotos("f.webp", "b.webp"),
            fields = mapOf("process" to JsonPrimitive("Washed")),
        )
        // Ground truth key is "process"; extraction key is the app name "processType".
        val score = BagFieldScorer.scoreBag(bag, mapOf("processType" to "Washed"))
        val processScore = score.fields.first { it.metadataKey == "process" }
        assertEquals(FieldOutcome.EXACT, processScore.outcome)
    }

    @Test
    fun `gate passes when all visible gate fields are exact`() {
        val score = scoreFrom(
            gt = gateGroundTruth(),
            extracted = mapOf(
                "name" to "Perihelion",
                "roaster" to "Astral Common Roasters",
                "origin" to "Kenya",
                "processType" to "Washed",
                "roastLevel" to "Filter",
                "weight" to "250 g",
            ),
        )
        val gate = BagFieldScorer.evaluateGate(score)
        assertTrue("Gate should pass; failures=${gate.failures}", gate.passed)
    }

    @Test
    fun `gate fails on a wrong gate field but ignores report-only fields`() {
        val score = scoreFrom(
            gt = gateGroundTruth() + ("region" to JsonPrimitive("Nyeri")),
            extracted = mapOf(
                "name" to "Perihelion",
                "roaster" to "Astral Common Roasters",
                "origin" to "Brazil", // wrong gate field
                "processType" to "Washed",
                "roastLevel" to "Filter",
                "weight" to "250 g",
                "region" to "Totally Wrong Region", // report-only, must NOT fail the gate
            ),
        )
        val gate = BagFieldScorer.evaluateGate(score)
        assertEquals(false, gate.passed)
        assertEquals(listOf("origin"), gate.failures.map { it.metadataKey })
    }

    private fun gateGroundTruth(): Map<String, JsonPrimitive> = mapOf(
        "name" to JsonPrimitive("Perihelion"),
        "roaster" to JsonPrimitive("Astral Common Roasters"),
        "origin" to JsonPrimitive("Kenya"),
        "process" to JsonPrimitive("Washed"),
        "roastLevel" to JsonPrimitive("Filter"),
        "weight" to JsonPrimitive("250 g"),
    )

    private fun scoreFrom(gt: Map<String, JsonPrimitive>, extracted: Map<String, String?>): BagScore {
        val bag = CoffeeBagFixture(
            id = "gate-test",
            photos = CoffeeBagPhotos("f.webp", "b.webp"),
            extras = buildJsonObject { put("captureTier", "Q0") },
            fields = gt,
        )
        return BagFieldScorer.scoreBag(bag, extracted)
    }
}
