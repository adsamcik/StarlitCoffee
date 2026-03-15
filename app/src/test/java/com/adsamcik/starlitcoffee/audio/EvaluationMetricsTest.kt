package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class EvaluationMetricsTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Helper factories ---

    private fun detection(timeMs: Long, event: BrewAudioEvent) =
        EventMatcher.TimestampedDetection(timeMs = timeMs, event = event)

    private fun label(timeS: Double, name: String) =
        LabelReader.Label(startTimeS = timeS, endTimeS = timeS, name = name)

    private fun pourStartDetection(timeMs: Long) =
        detection(timeMs, BrewAudioEvent.PourStarted(confidenceDb = -30f))

    private fun pourStopDetection(timeMs: Long) =
        detection(timeMs, BrewAudioEvent.PourStopped(durationMs = 5000L))

    private fun dripDetection(timeMs: Long) =
        detection(timeMs, BrewAudioEvent.DripDetected(energyDb = -40f))

    private fun drawdownDetection(timeMs: Long) =
        detection(timeMs, BrewAudioEvent.DrawdownComplete(totalDrainTimeMs = 60000L))

    /**
     * Builds a complete "perfect" session: one detection exactly matching each ground truth label.
     */
    private fun perfectSession(sessionId: String = "perfect"): EvaluationMetrics.SessionReport {
        val detections = listOf(
            pourStartDetection(5000L),
            pourStopDetection(18000L),
            dripDetection(25000L),
            drawdownDetection(90000L),
        )
        val groundTruth = listOf(
            label(5.0, "pour_start"),
            label(18.0, "pour_stop"),
            label(25.0, "drip_start"),
            label(90.0, "drawdown_complete"),
        )
        return EvaluationMetrics.evaluateSession(sessionId, detections, groundTruth)
    }

    // --- Precision & Recall ---

    @Test
    fun `perfect detection produces precision 1 and recall 1`() {
        val report = perfectSession()

        assertEquals(1.0f, report.overallPrecision, 0.01f)
        assertEquals(1.0f, report.overallRecall, 0.01f)
        assertEquals(0, report.totalFalsePositives)
        assertTrue(report.allEventsCorrect)

        // Each event type should also be perfect
        for ((_, result) in report.perEvent) {
            if (result.truePositives + result.falsePositives + result.falseNegatives > 0) {
                assertEquals(1.0f, result.precision, 0.01f)
                assertEquals(1.0f, result.recall, 0.01f)
            }
        }
    }

    @Test
    fun `missed events produce low recall`() {
        // Detect pour_start but miss pour_stop, drip_start, drawdown_complete
        val detections = listOf(
            pourStartDetection(5000L),
        )
        val groundTruth = listOf(
            label(5.0, "pour_start"),
            label(18.0, "pour_stop"),
            label(25.0, "drip_start"),
            label(90.0, "drawdown_complete"),
        )

        val report = EvaluationMetrics.evaluateSession("missed", detections, groundTruth)

        // 1 TP, 0 FP, 3 FN → recall = 1/4 = 0.25
        assertEquals(1.0f, report.overallPrecision, 0.01f)
        assertEquals(0.25f, report.overallRecall, 0.01f)
        assertFalse(report.allEventsCorrect)
    }

    @Test
    fun `extra detections produce low precision`() {
        // Ground truth has 1 pour_start, but we detect 3 pour_starts (2 false positives)
        val detections = listOf(
            pourStartDetection(5000L),   // matches GT
            pourStartDetection(8000L),   // FP (outside 1500ms tolerance)
            pourStartDetection(40000L),  // FP
        )
        val groundTruth = listOf(
            label(5.0, "pour_start"),
        )

        val report = EvaluationMetrics.evaluateSession("extra", detections, groundTruth)

        val pourResult = report.perEvent[EventMatcher.EventType.POUR_START]!!
        assertEquals(1, pourResult.truePositives)
        assertEquals(2, pourResult.falsePositives)
        assertEquals(0, pourResult.falseNegatives)
        // Precision = 1/3 ≈ 0.33
        assertEquals(0.33f, pourResult.precision, 0.01f)
        assertEquals(1.0f, pourResult.recall, 0.01f)
    }

    @Test
    fun `latency calculated correctly`() {
        // Detection arrives 500ms after ground truth for pour, 1000ms for stop
        val detections = listOf(
            pourStartDetection(5500L),  // 500ms late
            pourStopDetection(19000L),  // 1000ms late
        )
        val groundTruth = listOf(
            label(5.0, "pour_start"),
            label(18.0, "pour_stop"),
        )

        val report = EvaluationMetrics.evaluateSession("latency", detections, groundTruth)

        val pourStartResult = report.perEvent[EventMatcher.EventType.POUR_START]!!
        assertEquals(500f, pourStartResult.meanLatencyMs, 0.01f)

        val pourStopResult = report.perEvent[EventMatcher.EventType.POUR_STOP]!!
        assertEquals(1000f, pourStopResult.meanLatencyMs, 0.01f)

        // Mean phase timing error: (|500| + |1000|) / 2 = 750
        assertEquals(750f, report.meanPhaseTimingErrorMs, 0.01f)
    }

    @Test
    fun `session with no detections has zero recall`() {
        val detections = emptyList<EventMatcher.TimestampedDetection>()
        val groundTruth = listOf(
            label(5.0, "pour_start"),
            label(18.0, "pour_stop"),
        )

        val report = EvaluationMetrics.evaluateSession("empty-det", detections, groundTruth)

        assertEquals(0f, report.overallRecall, 0.01f)
        // Precision is 0/0 → 0f by convention
        assertEquals(0f, report.overallPrecision, 0.01f)
        assertEquals(0, report.totalFalsePositives)
        assertFalse(report.allEventsCorrect)
    }

    @Test
    fun `session with no ground truth has undefined precision`() {
        // No ground truth labels, but 2 spurious detections
        val detections = listOf(
            pourStartDetection(5000L),
            pourStopDetection(18000L),
        )
        val groundTruth = emptyList<LabelReader.Label>()

        val report = EvaluationMetrics.evaluateSession("no-gt", detections, groundTruth)

        // 0 TP, 2 FP → precision = 0/2 = 0.0
        assertEquals(0f, report.overallPrecision, 0.01f)
        // 0 TP, 0 FN → recall = 0/0 → 0f
        assertEquals(0f, report.overallRecall, 0.01f)
        assertEquals(2, report.totalFalsePositives)
    }

    // --- Aggregate report ---

    @Test
    fun `aggregate report computes session accuracy`() {
        val perfect1 = perfectSession("session-1")
        val perfect2 = perfectSession("session-2")

        // Imperfect session: misses drawdown
        val imperfectDetections = listOf(
            pourStartDetection(5000L),
            pourStopDetection(18000L),
            dripDetection(25000L),
            // drawdown missing
        )
        val imperfectGt = listOf(
            label(5.0, "pour_start"),
            label(18.0, "pour_stop"),
            label(25.0, "drip_start"),
            label(90.0, "drawdown_complete"),
        )
        val imperfect = EvaluationMetrics.evaluateSession("session-3", imperfectDetections, imperfectGt)

        val aggregate = EvaluationMetrics.evaluateMultiple(listOf(perfect1, perfect2, imperfect))

        assertEquals(3, aggregate.sessionCount)
        // 2 out of 3 sessions correct → 66.7%
        assertEquals(0.667f, aggregate.sessionAccuracy, 0.01f)
        // Mean precision: (1.0 + 1.0 + 1.0) / 3 = 1.0 (no FPs in imperfect session)
        assertEquals(1.0f, aggregate.meanPrecision, 0.01f)
        // Mean recall: session-3 has 3 TP out of 4 GT → recall=0.75; others 1.0
        // (1.0 + 1.0 + 0.75) / 3 ≈ 0.917
        assertEquals(0.917f, aggregate.meanRecall, 0.02f)
    }

    // --- MVP target assertions ---

    @Test
    fun `MVP target check identifies failures`() {
        // Build a session with poor performance:
        // 1 TP pour_start, 3 FP pour_starts, missed pour_stop → low precision, low recall
        val detections = listOf(
            pourStartDetection(5500L),
            pourStartDetection(10000L),  // FP
            pourStartDetection(20000L),  // FP
            pourStartDetection(30000L),  // FP
        )
        val groundTruth = listOf(
            label(5.0, "pour_start"),
            label(18.0, "pour_stop"),    // missed
        )

        val report = EvaluationMetrics.evaluateSession("bad", detections, groundTruth)
        val failures = EvaluationMetrics.checkMvpTargets(report)

        assertTrue("Expected precision failure", failures.any { it.contains("Precision") })
        assertTrue("Expected recall failure", failures.any { it.contains("Recall") })
        assertTrue("Expected FP failure", failures.any { it.contains("FP") })
    }

    @Test
    fun `MVP target check passes for good report`() {
        val report = perfectSession()
        val failures = EvaluationMetrics.checkMvpTargets(report)

        assertTrue("Expected no failures but got: $failures", failures.isEmpty())
    }

    // --- Format ---

    @Test
    fun `format produces readable output`() {
        val report = perfectSession("test-brew-001")
        val formatted = report.format()

        assertTrue(formatted.contains("Session Report: test-brew-001"))
        assertTrue(formatted.contains("precision="))
        assertTrue(formatted.contains("recall="))
        assertTrue(formatted.contains("POUR_START"))
    }

    @Test
    fun `aggregate format produces readable output`() {
        val aggregate = EvaluationMetrics.evaluateMultiple(
            listOf(perfectSession("s1"), perfectSession("s2"))
        )
        val formatted = aggregate.format()

        assertTrue(formatted.contains("Aggregate Report"))
        assertTrue(formatted.contains("2 sessions"))
        assertTrue(formatted.contains("Session accuracy"))
        assertTrue(formatted.contains("POUR_START"))
    }
}
