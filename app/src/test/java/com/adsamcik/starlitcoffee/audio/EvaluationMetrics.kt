package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent

/**
 * Evaluation metrics for audio brew detection regression testing.
 *
 * Computes per-event-type precision, recall, and detection latency,
 * plus aggregate session-level metrics. Used to verify detector changes
 * don't regress on labeled recordings.
 *
 * Tolerance windows from the validation strategy:
 * - pour_start: ±1.5s
 * - pour_stop: ±2.0s
 * - drip_start: ±3.0s
 * - drawdown_complete: ±5.0s
 *
 * Targets (MVP):
 * - Precision ≥ 0.85
 * - Recall ≥ 0.80
 * - Detection latency ≤ 2.0s for pour, ≤ 5.0s for drawdown
 * - False positives ≤ 1 per session
 */
object EvaluationMetrics {

    /**
     * Full session evaluation result combining all event types.
     */
    data class SessionReport(
        val sessionId: String,
        val perEvent: Map<EventMatcher.EventType, EventMatcher.MatchResult>,
        val totalDetections: Int,
        val totalGroundTruth: Int,
    ) {
        /** Session accuracy: all events detected correctly */
        val allEventsCorrect: Boolean
            get() = perEvent.values.all { it.falseNegatives == 0 && it.falsePositives == 0 }

        /** Overall precision across all event types */
        val overallPrecision: Float
            get() {
                val tp = perEvent.values.sumOf { it.truePositives }
                val fp = perEvent.values.sumOf { it.falsePositives }
                return if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
            }

        /** Overall recall across all event types */
        val overallRecall: Float
            get() {
                val tp = perEvent.values.sumOf { it.truePositives }
                val fn = perEvent.values.sumOf { it.falseNegatives }
                return if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
            }

        /** Total false positives across all event types */
        val totalFalsePositives: Int
            get() = perEvent.values.sumOf { it.falsePositives }

        /** Mean absolute phase timing error in ms (across all matched pairs) */
        val meanPhaseTimingErrorMs: Float
            get() {
                val allPairs = perEvent.values.flatMap { it.matchedPairs }
                if (allPairs.isEmpty()) return 0f
                return allPairs.map { kotlin.math.abs(it.latencyMs).toFloat() }.average().toFloat()
            }

        /** Format as human-readable report */
        fun format(): String {
            val sb = StringBuilder()
            sb.appendLine("=== Session Report: $sessionId ===")
            sb.appendLine("Overall: precision=%.2f recall=%.2f FP=%d timing_err=%.0fms"
                .format(overallPrecision, overallRecall, totalFalsePositives, meanPhaseTimingErrorMs))
            sb.appendLine()
            for ((type, result) in perEvent) {
                sb.appendLine("  ${type.name}:")
                sb.appendLine("    precision=%.2f recall=%.2f latency=%.0fms TP=%d FP=%d FN=%d"
                    .format(result.precision, result.recall, result.meanLatencyMs,
                        result.truePositives, result.falsePositives, result.falseNegatives))
            }
            return sb.toString()
        }
    }

    /**
     * Aggregate report across multiple sessions.
     */
    data class AggregateReport(
        val sessions: List<SessionReport>,
    ) {
        val sessionCount: Int get() = sessions.size

        /** % of sessions where ALL events detected correctly */
        val sessionAccuracy: Float
            get() = if (sessions.isEmpty()) 0f
            else sessions.count { it.allEventsCorrect }.toFloat() / sessions.size

        /** Mean precision across sessions */
        val meanPrecision: Float
            get() = if (sessions.isEmpty()) 0f
            else sessions.map { it.overallPrecision }.average().toFloat()

        /** Mean recall across sessions */
        val meanRecall: Float
            get() = if (sessions.isEmpty()) 0f
            else sessions.map { it.overallRecall }.average().toFloat()

        /** Mean false positives per session (nuisance rate) */
        val meanFalsePositivesPerSession: Float
            get() = if (sessions.isEmpty()) 0f
            else sessions.map { it.totalFalsePositives.toFloat() }.average().toFloat()

        /** Mean phase timing error across all sessions */
        val meanPhaseTimingErrorMs: Float
            get() {
                val allErrors = sessions.map { it.meanPhaseTimingErrorMs }.filter { it > 0 }
                return if (allErrors.isEmpty()) 0f else allErrors.average().toFloat()
            }

        /** Per-event-type aggregated metrics */
        fun perEventAggregate(): Map<EventMatcher.EventType, AggregatedEventMetrics> {
            return EventMatcher.EventType.entries.associateWith { type ->
                val results = sessions.mapNotNull { it.perEvent[type] }
                AggregatedEventMetrics(
                    eventType = type,
                    meanPrecision = results.map { it.precision }.average().toFloat(),
                    meanRecall = results.map { it.recall }.average().toFloat(),
                    meanLatencyMs = results.map { it.meanLatencyMs }.filter { it > 0 }.let {
                        if (it.isEmpty()) 0f else it.average().toFloat()
                    },
                    totalTp = results.sumOf { it.truePositives },
                    totalFp = results.sumOf { it.falsePositives },
                    totalFn = results.sumOf { it.falseNegatives },
                )
            }
        }

        fun format(): String {
            val sb = StringBuilder()
            sb.appendLine("=== Aggregate Report ($sessionCount sessions) ===")
            sb.appendLine("Session accuracy: %.1f%%".format(sessionAccuracy * 100))
            sb.appendLine("Mean precision: %.2f  recall: %.2f".format(meanPrecision, meanRecall))
            sb.appendLine("Mean FP/session: %.1f  timing err: %.0fms"
                .format(meanFalsePositivesPerSession, meanPhaseTimingErrorMs))
            sb.appendLine()
            for ((type, agg) in perEventAggregate()) {
                sb.appendLine("  ${type.name}: prec=%.2f rec=%.2f lat=%.0fms (TP=%d FP=%d FN=%d)"
                    .format(agg.meanPrecision, agg.meanRecall, agg.meanLatencyMs,
                        agg.totalTp, agg.totalFp, agg.totalFn))
            }
            return sb.toString()
        }
    }

    data class AggregatedEventMetrics(
        val eventType: EventMatcher.EventType,
        val meanPrecision: Float,
        val meanRecall: Float,
        val meanLatencyMs: Float,
        val totalTp: Int,
        val totalFp: Int,
        val totalFn: Int,
    )

    /**
     * Evaluates a single session: matches all event types and builds a report.
     *
     * @param sessionId human-readable session identifier
     * @param detections timestamped detected events from the pipeline
     * @param groundTruth labels from Audacity label file
     * @param tolerances per-event-type tolerance windows (defaults to validation strategy values)
     */
    fun evaluateSession(
        sessionId: String,
        detections: List<EventMatcher.TimestampedDetection>,
        groundTruth: List<LabelReader.Label>,
        tolerances: Map<EventMatcher.EventType, Long> = EventMatcher.DEFAULT_TOLERANCES,
    ): SessionReport {
        val perEvent = EventMatcher.EventType.entries.associateWith { type ->
            EventMatcher.matchEvents(
                detections = detections,
                groundTruth = groundTruth,
                eventType = type,
                toleranceMs = tolerances[type] ?: 2000L,
            )
        }

        return SessionReport(
            sessionId = sessionId,
            perEvent = perEvent,
            totalDetections = detections.size,
            totalGroundTruth = groundTruth.size,
        )
    }

    /**
     * Evaluates multiple sessions and builds an aggregate report.
     */
    fun evaluateMultiple(sessions: List<SessionReport>): AggregateReport {
        return AggregateReport(sessions)
    }

    // --- MVP target assertions ---

    /** MVP targets from validation strategy */
    object MvpTargets {
        const val MIN_PRECISION = 0.85f
        const val MIN_RECALL = 0.80f
        const val MAX_POUR_LATENCY_MS = 2000f
        const val MAX_DRAWDOWN_LATENCY_MS = 5000f
        const val MAX_FP_PER_SESSION = 1.0f
        const val MIN_SESSION_ACCURACY = 0.70f
        const val MAX_PHASE_TIMING_ERROR_MS = 3000f
    }

    /**
     * Checks if a session report meets MVP targets.
     * Returns a list of failure descriptions (empty = all passed).
     */
    fun checkMvpTargets(report: SessionReport): List<String> {
        val failures = mutableListOf<String>()
        if (report.overallPrecision < MvpTargets.MIN_PRECISION) {
            failures.add("Precision %.2f < %.2f".format(report.overallPrecision, MvpTargets.MIN_PRECISION))
        }
        if (report.overallRecall < MvpTargets.MIN_RECALL) {
            failures.add("Recall %.2f < %.2f".format(report.overallRecall, MvpTargets.MIN_RECALL))
        }
        if (report.totalFalsePositives > MvpTargets.MAX_FP_PER_SESSION) {
            failures.add("FP %d > %.0f".format(report.totalFalsePositives, MvpTargets.MAX_FP_PER_SESSION))
        }
        report.perEvent[EventMatcher.EventType.POUR_START]?.let {
            if (it.meanLatencyMs > MvpTargets.MAX_POUR_LATENCY_MS) {
                failures.add("Pour latency %.0fms > %.0fms".format(it.meanLatencyMs, MvpTargets.MAX_POUR_LATENCY_MS))
            }
        }
        report.perEvent[EventMatcher.EventType.DRAWDOWN_COMPLETE]?.let {
            if (it.meanLatencyMs > MvpTargets.MAX_DRAWDOWN_LATENCY_MS) {
                failures.add("Drawdown latency %.0fms > %.0fms".format(it.meanLatencyMs, MvpTargets.MAX_DRAWDOWN_LATENCY_MS))
            }
        }
        return failures
    }
}
