package com.adsamcik.starlitcoffee.test.corpus

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic coverage of the metric aggregation + serialization in [QualityReport]. */
class QualityReportTest {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    private fun score(bagId: String, tier: String, vararg outcomes: Pair<String, FieldOutcome>): BagScore =
        BagScore(
            bagId = bagId,
            tier = tier,
            fields = outcomes.map { (key, outcome) -> FieldScore(key, "x", "y", outcome) },
        )

    @Test
    fun `field metrics derive precision recall abstention hallucination`() {
        // visible = exact+partial+wrong+missing = 4 ; notVisible = halluc+abstain = 2
        val m = FieldMetrics(exact = 2, partial = 0, wrong = 1, missing = 1, hallucinated = 1, abstained = 1)
        assertEquals(6, m.total)
        assertEquals(4, m.visible)
        assertEquals(2, m.notVisible)
        assertEquals(4, m.produced) // exact+partial+wrong+halluc
        assertEquals(0.5, m.recall!!, 1e-9) // 2/4
        assertEquals(0.5, m.precision!!, 1e-9) // 2/4
        assertEquals(0.5, m.abstentionRate!!, 1e-9) // 1/2
        assertEquals(0.5, m.hallucinationRate!!, 1e-9) // 1/2
    }

    @Test
    fun `exact accuracy ceiling and decision accuracy are abstention-aware`() {
        // visible = 4, notVisible = 2, total = 6
        val m = FieldMetrics(exact = 2, partial = 0, wrong = 1, missing = 1, hallucinated = 1, abstained = 1)
        assertEquals(2.0 / 6.0, m.exactAccuracy!!, 1e-9) // 2/6
        // ceiling = visible/total = 4/6: a perfect extractor still cannot beat this
        assertEquals(4.0 / 6.0, m.exactAccuracyCeiling!!, 1e-9)
        // decisionAccuracy = (exact + abstained)/total = 3/6: right value OR right blank
        assertEquals(3.0 / 6.0, m.decisionAccuracy!!, 1e-9)
    }

    @Test
    fun `a perfect extractor reaches the ceiling but not 100 percent exact`() {
        // 3 visible all EXACT, 2 absent both correctly ABSTAINED -> flawless
        val m = FieldMetrics(exact = 3, abstained = 2)
        assertEquals(3.0 / 5.0, m.exactAccuracy!!, 1e-9) // 60%, not 100%
        assertEquals(3.0 / 5.0, m.exactAccuracyCeiling!!, 1e-9) // ceiling == exactAcc when flawless
        assertEquals(1.0, m.decisionAccuracy!!, 1e-9) // every decision correct
    }

    @Test
    fun `rates are null when the denominator is zero`() {
        val empty = FieldMetrics()
        assertNull(empty.exactAccuracy)
        assertNull(empty.recall)
        assertNull(empty.abstentionRate)
    }

    @Test
    fun `report aggregates per-field per-tier and overall`() {
        val scores = listOf(
            score("b1", "Q0", "name" to FieldOutcome.EXACT, "farm" to FieldOutcome.ABSTAINED),
            score("b2", "Q0", "name" to FieldOutcome.WRONG, "farm" to FieldOutcome.HALLUCINATED),
            score("b3", "Q2", "name" to FieldOutcome.EXACT, "farm" to FieldOutcome.MISSING),
        )
        val report = QualityReport.from("test", scores)

        assertEquals(3, report.bagsEvaluated)
        // name: 2 exact + 1 wrong = 3 scored across bags
        val nameField = report.perField.first { it.field == "name" }
        assertEquals(2, nameField.metrics.exact)
        assertEquals(1, nameField.metrics.wrong)
        assertTrue("name must be a gated field", nameField.gated)

        // farm is report-only
        assertEquals(false, report.perField.first { it.field == "farm" }.gated)

        // tiers grouped: Q0 has 2 bags, Q2 has 1
        assertEquals(2, report.perTier.first { it.tier == "Q0" }.bags)
        assertEquals(1, report.perTier.first { it.tier == "Q2" }.bags)

        // overall exact = 2 (names) ; total = 6 cells
        assertEquals(2, report.overall.exact)
        assertEquals(6, report.overall.total)
    }

    @Test
    fun `report round-trips through JSON`() {
        val scores = listOf(score("b1", "Q0", "name" to FieldOutcome.EXACT))
        val report = QualityReport.from("rt", scores)
        val json = report.toJson()
        val decoded = lenientJson.decodeFromString(QualityReport.serializer(), json)
        assertEquals(report.bagsEvaluated, decoded.bagsEvaluated)
        assertEquals(report.overall.exact, decoded.overall.exact)
    }

    @Test
    fun `text rendering includes headline metrics`() {
        val scores = listOf(score("b1", "Q0", "name" to FieldOutcome.EXACT))
        val text = QualityReport.from("hdr", scores).toText()
        assertTrue(text.contains("QUALITY REPORT: hdr"))
        assertTrue(text.contains("Per-field"))
        assertTrue(text.contains("Per-tier"))
        // honest-metric surfacing (Item 2)
        assertTrue("must show the exactAcc ceiling", text.contains("ceil"))
        assertTrue("must show decision accuracy", text.contains("decisionAcc"))
        assertTrue("must show hallucination on the headline", text.contains("halluc="))
    }
}
