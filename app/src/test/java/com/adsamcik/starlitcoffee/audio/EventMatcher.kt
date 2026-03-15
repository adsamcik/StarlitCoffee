package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import kotlin.math.abs

/**
 * Matches detected brew audio events against ground truth labels
 * with configurable time tolerance windows.
 *
 * Used in regression tests to compute precision, recall, and latency.
 */
object EventMatcher {

    /** Mapping from ground truth label names to brew event types. */
    enum class EventType(val labelNames: Set<String>) {
        POUR_START(setOf("pour_start", "pour_started")),
        POUR_STOP(setOf("pour_stop", "pour_stopped")),
        DRIP_START(setOf("drip_start", "drip_started", "drip_detected")),
        DRAWDOWN_COMPLETE(setOf("drawdown_complete", "drawdown_done", "complete")),
    }

    /** Default tolerance windows in milliseconds. */
    val DEFAULT_TOLERANCES = mapOf(
        EventType.POUR_START to 1500L,
        EventType.POUR_STOP to 2000L,
        EventType.DRIP_START to 3000L,
        EventType.DRAWDOWN_COMPLETE to 5000L,
    )

    data class MatchResult(
        val eventType: EventType,
        val truePositives: Int,
        val falsePositives: Int,
        val falseNegatives: Int,
        val matchedPairs: List<MatchedPair>,
    ) {
        val precision: Float
            get() = if (truePositives + falsePositives > 0)
                truePositives.toFloat() / (truePositives + falsePositives) else 0f

        val recall: Float
            get() = if (truePositives + falseNegatives > 0)
                truePositives.toFloat() / (truePositives + falseNegatives) else 0f

        val meanLatencyMs: Float
            get() = if (matchedPairs.isEmpty()) 0f
            else matchedPairs.map { it.latencyMs.toFloat() }.average().toFloat()
    }

    data class MatchedPair(
        val groundTruthMs: Long,
        val detectedMs: Long,
    ) {
        val latencyMs: Long get() = detectedMs - groundTruthMs
    }

    /** Converts a [BrewAudioEvent] to a timestamped detection for matching. */
    data class TimestampedDetection(
        val timeMs: Long,
        val event: BrewAudioEvent,
    )

    /**
     * Matches detected events against ground truth labels for a specific event type.
     * Uses greedy nearest-neighbor matching within the tolerance window.
     *
     * @param detections timestamped detected events
     * @param groundTruth labels from Audacity label file
     * @param eventType which event type to match
     * @param toleranceMs tolerance window in milliseconds
     */
    fun matchEvents(
        detections: List<TimestampedDetection>,
        groundTruth: List<LabelReader.Label>,
        eventType: EventType,
        toleranceMs: Long = DEFAULT_TOLERANCES[eventType] ?: 2000L,
    ): MatchResult {
        val gtEvents = groundTruth
            .filter { it.name in eventType.labelNames }
            .sortedBy { it.startTimeMs }

        val detEvents = detections
            .filter { matchesEventType(it.event, eventType) }
            .sortedBy { it.timeMs }

        // Greedy matching: for each ground truth, find nearest unmatched detection
        val matchedDetections = mutableSetOf<Int>()
        val pairs = mutableListOf<MatchedPair>()

        for (gt in gtEvents) {
            var bestIdx = -1
            var bestDist = Long.MAX_VALUE
            for ((i, det) in detEvents.withIndex()) {
                if (i in matchedDetections) continue
                val dist = abs(det.timeMs - gt.startTimeMs)
                if (dist <= toleranceMs && dist < bestDist) {
                    bestDist = dist
                    bestIdx = i
                }
            }
            if (bestIdx >= 0) {
                matchedDetections.add(bestIdx)
                pairs.add(MatchedPair(gt.startTimeMs, detEvents[bestIdx].timeMs))
            }
        }

        return MatchResult(
            eventType = eventType,
            truePositives = pairs.size,
            falsePositives = detEvents.size - pairs.size,
            falseNegatives = gtEvents.size - pairs.size,
            matchedPairs = pairs,
        )
    }

    private fun matchesEventType(event: BrewAudioEvent, type: EventType): Boolean = when (type) {
        EventType.POUR_START -> event is BrewAudioEvent.PourStarted
        EventType.POUR_STOP -> event is BrewAudioEvent.PourStopped
        EventType.DRIP_START -> event is BrewAudioEvent.DripDetected
        EventType.DRAWDOWN_COMPLETE -> event is BrewAudioEvent.DrawdownComplete
    }
}
