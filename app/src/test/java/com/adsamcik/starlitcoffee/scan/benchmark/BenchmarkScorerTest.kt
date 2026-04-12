package com.adsamcik.starlitcoffee.scan.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkScorerTest {

    // ── Helpers ─────────────────────────────────────────────────────

    private fun gtField(value: String?, onLabel: Boolean = value != null, notes: String? = null) =
        GroundTruthField(groundTruth = value, isOnLabel = onLabel, notes = notes)

    private fun entry(
        bagId: String = "bag-test",
        fields: Map<String, GroundTruthField>,
    ) = GroundTruthEntry(
        bagId = bagId,
        photoPath = null,
        scannedAt = "2025-01-15T10:00:00Z",
        bagDescription = "test bag",
        material = null,
        language = null,
        fields = fields,
    )

    // ── Exact match ─────────────────────────────────────────────────

    @Test
    fun `exact match - identical strings`() {
        val score = BenchmarkScorer.scoreField("name", "Big Trouble", gtField("Big Trouble"))
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    @Test
    fun `exact match - case insensitive`() {
        val score = BenchmarkScorer.scoreField("roaster", "counter culture", gtField("Counter Culture"))
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    @Test
    fun `exact match - with whitespace trimming`() {
        val score = BenchmarkScorer.scoreField("origin", "  Ethiopia  ", gtField("Ethiopia"))
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    // ── Semantic match (Levenshtein) ────────────────────────────────

    @Test
    fun `semantic match - minor spelling difference`() {
        val score = BenchmarkScorer.scoreField("region", "Yirgachefe", gtField("Yirgacheffe"))
        assertEquals(FieldScoreType.SEMANTIC, score.scoreType)
    }

    @Test
    fun `semantic match - long string within threshold`() {
        val score = BenchmarkScorer.scoreField("roaster", "Counter Cultur Coffee", gtField("Counter Culture Coffee"))
        assertEquals(FieldScoreType.SEMANTIC, score.scoreType)
    }

    @Test
    fun `wrong - large spelling difference`() {
        val score = BenchmarkScorer.scoreField("origin", "Brazil", gtField("Ethiopia"))
        assertEquals(FieldScoreType.WRONG, score.scoreType)
    }

    // ── Tasting notes (Jaccard) ─────────────────────────────────────

    @Test
    fun `tasting notes - exact set match`() {
        val score = BenchmarkScorer.scoreField(
            "tastingNotes",
            "blueberry, chocolate, citrus",
            gtField("chocolate, blueberry, citrus"),
        )
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    @Test
    fun `tasting notes - partial overlap above threshold`() {
        // 2 out of 3 match → Jaccard = 2/3 ≈ 0.67 → SEMANTIC
        val score = BenchmarkScorer.scoreField(
            "tastingNotes",
            "blueberry, chocolate",
            gtField("blueberry, chocolate, citrus"),
        )
        assertEquals(FieldScoreType.SEMANTIC, score.scoreType)
    }

    @Test
    fun `tasting notes - low overlap`() {
        // 1 out of 4 match → Jaccard = 1/4 = 0.25 → WRONG
        val score = BenchmarkScorer.scoreField(
            "tastingNotes",
            "vanilla, caramel, toffee",
            gtField("blueberry, chocolate, citrus, vanilla"),
        )
        assertEquals(FieldScoreType.WRONG, score.scoreType)
    }

    @Test
    fun `tasting notes - semicolons parsed correctly`() {
        val score = BenchmarkScorer.scoreField(
            "tastingNotes",
            "blueberry; chocolate; citrus",
            gtField("citrus, chocolate, blueberry"),
        )
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    // ── Weight normalization ────────────────────────────────────────

    @Test
    fun `weight - same grams`() {
        val score = BenchmarkScorer.scoreField("weight", "340g", gtField("340g"))
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    @Test
    fun `weight - oz to grams conversion`() {
        // 12 oz ≈ 340.194g → ground truth is 340g → diff ≈ 0.19 → EXACT (within 1g)
        val score = BenchmarkScorer.scoreField("weight", "12 oz", gtField("340g"))
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    @Test
    fun `weight - pounds to grams`() {
        // 1 lb = 453.592g
        val score = BenchmarkScorer.scoreField("weight", "1 lb", gtField("454g"))
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    @Test
    fun `weight - wrong value`() {
        val score = BenchmarkScorer.scoreField("weight", "250g", gtField("340g"))
        assertEquals(FieldScoreType.WRONG, score.scoreType)
    }

    // ── Altitude range matching ─────────────────────────────────────

    @Test
    fun `altitude - exact match single value`() {
        val score = BenchmarkScorer.scoreField("altitude", "1800", gtField("1800"))
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    @Test
    fun `altitude - within tolerance`() {
        val score = BenchmarkScorer.scoreField("altitude", "1850", gtField("1800"))
        assertEquals(FieldScoreType.SEMANTIC, score.scoreType)
    }

    @Test
    fun `altitude - range vs range within tolerance`() {
        // midpoint "1800-2000" = 1900, midpoint "1850-2050" = 1950 → diff = 50 → SEMANTIC
        val score = BenchmarkScorer.scoreField("altitude", "1850-2050", gtField("1800-2000"))
        assertEquals(FieldScoreType.SEMANTIC, score.scoreType)
    }

    @Test
    fun `altitude - out of tolerance`() {
        val score = BenchmarkScorer.scoreField("altitude", "1200", gtField("1800"))
        assertEquals(FieldScoreType.WRONG, score.scoreType)
    }

    // ── Roast date ──────────────────────────────────────────────────

    @Test
    fun `roast date - exact`() {
        val score = BenchmarkScorer.scoreField("roastDate", "2025-01-15", gtField("2025-01-15"))
        assertEquals(FieldScoreType.EXACT, score.scoreType)
    }

    @Test
    fun `roast date - different day is wrong`() {
        val score = BenchmarkScorer.scoreField("roastDate", "2025-01-16", gtField("2025-01-15"))
        assertEquals(FieldScoreType.WRONG, score.scoreType)
    }

    // ── Hallucination detection ─────────────────────────────────────

    @Test
    fun `hallucinated - extracted non-null but not on label`() {
        val gt = GroundTruthField(groundTruth = null, isOnLabel = false)
        val score = BenchmarkScorer.scoreField("region", "Huila", gt)
        assertEquals(FieldScoreType.HALLUCINATED, score.scoreType)
    }

    @Test
    fun `hallucinated - extracted non-null, ground truth null, not on label`() {
        val gt = GroundTruthField(groundTruth = null, isOnLabel = false)
        val score = BenchmarkScorer.scoreField("variety", "SL28", gt)
        assertEquals(FieldScoreType.HALLUCINATED, score.scoreType)
    }

    // ── Correct null ────────────────────────────────────────────────

    @Test
    fun `correct null - both null and not on label`() {
        val gt = GroundTruthField(groundTruth = null, isOnLabel = false)
        val score = BenchmarkScorer.scoreField("altitude", null, gt)
        assertEquals(FieldScoreType.CORRECT_NULL, score.scoreType)
    }

    @Test
    fun `correct null - extracted empty string treated as null`() {
        val gt = GroundTruthField(groundTruth = null, isOnLabel = false)
        val score = BenchmarkScorer.scoreField("altitude", "", gt)
        assertEquals(FieldScoreType.CORRECT_NULL, score.scoreType)
    }

    // ── Missing ─────────────────────────────────────────────────────

    @Test
    fun `missing - null extraction but value on label`() {
        val gt = GroundTruthField(groundTruth = "Ethiopia", isOnLabel = true)
        val score = BenchmarkScorer.scoreField("origin", null, gt)
        assertEquals(FieldScoreType.MISSING, score.scoreType)
    }

    // ── Session scoring thresholds ──────────────────────────────────

    @Test
    fun `session - complete when 80 percent correct`() {
        // 4 out of 5 on-label fields correct → 80% → COMPLETE
        val fields = mapOf(
            "name" to gtField("Big Trouble"),
            "roaster" to gtField("Counter Culture"),
            "origin" to gtField("Ethiopia"),
            "region" to gtField("Yirgacheffe"),
            "roastLevel" to gtField("Light"),
        )
        val extracted = mapOf(
            "name" to "Big Trouble",
            "roaster" to "Counter Culture",
            "origin" to "Ethiopia",
            "region" to "Yirgacheffe",
            "roastLevel" to "Medium",        // wrong
        )
        val session = BenchmarkScorer.scoreSession(extracted, entry(fields = fields))
        assertEquals(SessionOutcome.COMPLETE, session.outcome)
        assertEquals(4, session.correctCount)
    }

    @Test
    fun `session - partial when between 40 and 80 percent`() {
        // 2 out of 5 correct → 40% → PARTIAL
        val fields = mapOf(
            "name" to gtField("Big Trouble"),
            "roaster" to gtField("Counter Culture"),
            "origin" to gtField("Ethiopia"),
            "region" to gtField("Yirgacheffe"),
            "roastLevel" to gtField("Light"),
        )
        val extracted = mapOf(
            "name" to "Big Trouble",
            "roaster" to "Counter Culture",
            "origin" to "Brazil",
            "region" to "Huila",
            "roastLevel" to "Dark",
        )
        val session = BenchmarkScorer.scoreSession(extracted, entry(fields = fields))
        assertEquals(SessionOutcome.PARTIAL, session.outcome)
    }

    @Test
    fun `session - failed when below 40 percent`() {
        // 1 out of 5 correct → 20% → FAILED
        val fields = mapOf(
            "name" to gtField("Big Trouble"),
            "roaster" to gtField("Counter Culture"),
            "origin" to gtField("Ethiopia"),
            "region" to gtField("Yirgacheffe"),
            "roastLevel" to gtField("Light"),
        )
        val extracted = mapOf(
            "name" to "Big Trouble",
            "roaster" to "Stumptown",
            "origin" to "Brazil",
            "region" to "Huila",
            "roastLevel" to "Dark",
        )
        val session = BenchmarkScorer.scoreSession(extracted, entry(fields = fields))
        assertEquals(SessionOutcome.FAILED, session.outcome)
    }

    @Test
    fun `session - failed when hallucination present`() {
        val fields = mapOf(
            "name" to gtField("Big Trouble"),
            "roaster" to gtField("Counter Culture"),
            "altitude" to GroundTruthField(groundTruth = null, isOnLabel = false),
        )
        val extracted = mapOf(
            "name" to "Big Trouble",
            "roaster" to "Counter Culture",
            "altitude" to "1800",  // hallucinated
        )
        val session = BenchmarkScorer.scoreSession(extracted, entry(fields = fields))
        assertEquals(SessionOutcome.FAILED, session.outcome)
        assertEquals(1, session.hallucinatedCount)
    }

    // ── Batch verdict logic ─────────────────────────────────────────

    @Test
    fun `batch - SHIP verdict`() {
        // All sessions complete, no hallucinations
        val fields = mapOf(
            "name" to gtField("Coffee A"),
            "roaster" to gtField("Roaster A"),
        )
        val entries = (1..10).map { i ->
            mapOf("name" to "Coffee A", "roaster" to "Roaster A") to
                entry(bagId = "bag-$i", fields = fields)
        }
        val batch = BenchmarkScorer.scoreBatch(entries)
        assertEquals(BenchmarkVerdict.SHIP, batch.verdict)
        assertTrue(batch.overallSuccessRate >= 0.7f)
        assertTrue(batch.overallHallucinationRate <= 0.2f)
    }

    @Test
    fun `batch - ITERATE verdict`() {
        val fields = mapOf(
            "name" to gtField("Coffee A"),
            "roaster" to gtField("Roaster A"),
            "origin" to gtField("Ethiopia"),
        )
        // 6 complete, 4 partial → 60% success → ITERATE
        val complete = (1..6).map { i ->
            mapOf("name" to "Coffee A", "roaster" to "Roaster A", "origin" to "Ethiopia") to
                entry(bagId = "bag-$i", fields = fields)
        }
        val partial = (7..10).map { i ->
            mapOf("name" to "Coffee A", "roaster" to "Wrong Roaster", "origin" to "Brazil") to
                entry(bagId = "bag-$i", fields = fields)
        }
        val batch = BenchmarkScorer.scoreBatch(complete + partial)
        assertEquals(BenchmarkVerdict.ITERATE, batch.verdict)
    }

    @Test
    fun `batch - RETHINK verdict on low success`() {
        val fields = mapOf(
            "name" to gtField("Coffee A"),
            "roaster" to gtField("Roaster A"),
            "origin" to gtField("Ethiopia"),
            "region" to gtField("Sidamo"),
            "roastLevel" to gtField("Light"),
        )
        // All sessions fail
        val entries = (1..10).map { i ->
            mapOf(
                "name" to "Wrong",
                "roaster" to "Wrong",
                "origin" to "Wrong",
                "region" to "Wrong",
                "roastLevel" to "Wrong",
            ) to entry(bagId = "bag-$i", fields = fields)
        }
        val batch = BenchmarkScorer.scoreBatch(entries)
        assertEquals(BenchmarkVerdict.RETHINK, batch.verdict)
    }

    @Test
    fun `batch - RETHINK verdict on high hallucination`() {
        val fields = mapOf(
            "name" to gtField("Coffee A"),
            "altitude" to GroundTruthField(groundTruth = null, isOnLabel = false),
        )
        // Every session has a hallucinated altitude
        val entries = (1..10).map { i ->
            mapOf("name" to "Coffee A", "altitude" to "1500") to
                entry(bagId = "bag-$i", fields = fields)
        }
        val batch = BenchmarkScorer.scoreBatch(entries)
        assertEquals(BenchmarkVerdict.RETHINK, batch.verdict)
        assertTrue(batch.overallHallucinationRate > 0.3f)
    }

    // ── Internal helpers ────────────────────────────────────────────

    @Test
    fun `levenshtein - identical strings`() {
        assertEquals(0, BenchmarkScorer.levenshtein("hello", "hello"))
    }

    @Test
    fun `levenshtein - one edit`() {
        assertEquals(1, BenchmarkScorer.levenshtein("hello", "hallo"))
    }

    @Test
    fun `levenshtein - completely different`() {
        assertEquals(3, BenchmarkScorer.levenshtein("abc", "xyz"))
    }

    @Test
    fun `weight parsing - grams`() {
        assertEquals(340.0, BenchmarkScorer.parseWeightToGrams("340g")!!, 0.01)
    }

    @Test
    fun `weight parsing - ounces`() {
        assertEquals(340.194, BenchmarkScorer.parseWeightToGrams("12 oz")!!, 0.01)
    }

    @Test
    fun `weight parsing - pounds`() {
        assertEquals(453.592, BenchmarkScorer.parseWeightToGrams("1 lb")!!, 0.01)
    }

    @Test
    fun `altitude midpoint - single value`() {
        assertEquals(1800.0, BenchmarkScorer.parseAltitudeMidpoint("1800m")!!, 0.01)
    }

    @Test
    fun `altitude midpoint - range`() {
        assertEquals(1950.0, BenchmarkScorer.parseAltitudeMidpoint("1800-2100")!!, 0.01)
    }

    @Test
    fun `tasting notes parsing`() {
        val notes = BenchmarkScorer.parseTastingNotes("Blueberry, Dark Chocolate; Citrus")
        assertEquals(setOf("blueberry", "dark chocolate", "citrus"), notes)
    }
}
