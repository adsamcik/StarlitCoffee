package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Shadow mode comparison logger for A/B testing audio detection accuracy.
 *
 * During brew sessions, logs both:
 * 1. User-initiated phase transitions (manual taps on "next phase")
 * 2. Audio detector events (what the detector would have triggered)
 *
 * The resulting log enables offline comparison: how closely do detector events
 * match user taps? This feeds the PASSIVE → ASSISTIVE → AUTOMATIC rollout
 * strategy from the validation plan.
 *
 * Output format: JSONL (one comparison record per event)
 *
 * Usage:
 * ```
 * val log = ShadowComparisonLog()
 * log.open(outputFile)
 * // When user taps "next phase":
 * log.recordUserTap(phaseIndex, phaseLabel)
 * // When detector fires an event:
 * log.recordDetectorEvent(event)
 * // At brew end:
 * val summary = log.close()
 * ```
 */
class ShadowComparisonLog {

    var isOpen: Boolean = false
        private set

    private var writer: BufferedWriter? = null
    private var sessionStartMs: Long = 0L

    // Accumulate events for summary computation
    private val userTaps = mutableListOf<UserTapRecord>()
    private val detectorEvents = mutableListOf<DetectorEventRecord>()

    data class UserTapRecord(
        val timeMs: Long,
        val elapsedMs: Long,
        val phaseIndex: Int,
        val phaseLabel: String,
    )

    data class DetectorEventRecord(
        val timeMs: Long,
        val elapsedMs: Long,
        val eventType: String,
        val detail: String,
    )

    /**
     * Summary of a shadow comparison session.
     */
    data class SessionSummary(
        val userTapCount: Int,
        val detectorEventCount: Int,
        val matchedCount: Int,
        val unmatchedTaps: Int,
        val falsePositives: Int,
        val meanDeltaMs: Long?,
        val medianDeltaMs: Long?,
    ) {
        override fun toString(): String = buildString {
            append("Shadow Summary: ")
            append("taps=$userTapCount events=$detectorEventCount ")
            append("matched=$matchedCount unmatched=$unmatchedTaps FP=$falsePositives ")
            if (meanDeltaMs != null) append("meanΔ=${meanDeltaMs}ms ")
            if (medianDeltaMs != null) append("medΔ=${medianDeltaMs}ms")
        }
    }

    /**
     * Opens a new shadow comparison log file.
     */
    fun open(file: File) {
        close()
        file.parentFile?.mkdirs()
        writer = BufferedWriter(FileWriter(file))
        sessionStartMs = System.currentTimeMillis()
        userTaps.clear()
        detectorEvents.clear()
        isOpen = true
    }

    /**
     * Records a user-initiated phase transition (manual tap).
     */
    fun recordUserTap(phaseIndex: Int, phaseLabel: String) {
        if (!isOpen) return
        val now = System.currentTimeMillis()
        val elapsed = now - sessionStartMs
        val record = UserTapRecord(now, elapsed, phaseIndex, phaseLabel)
        userTaps.add(record)

        writeLine("""{"type":"user_tap","t":$now,"elapsed":$elapsed,"phase":$phaseIndex,"label":"${escapeJson(phaseLabel)}"}""")
    }

    /**
     * Records a detector event (what audio detection would have triggered).
     */
    fun recordDetectorEvent(event: BrewAudioEvent) {
        if (!isOpen) return
        val now = System.currentTimeMillis()
        val elapsed = now - sessionStartMs
        val (eventType, detail) = formatEvent(event)
        val record = DetectorEventRecord(now, elapsed, eventType, detail)
        detectorEvents.add(record)

        writeLine("""{"type":"detector","t":$now,"elapsed":$elapsed,"event":"$eventType","detail":"${escapeJson(detail)}"}""")
    }

    /**
     * Closes the log and returns a comparison summary.
     * Matches detector events to user taps within a ±5s window.
     */
    fun close(): SessionSummary? {
        if (!isOpen) return null

        val summary = computeSummary()

        // Write summary as final line. The wire format is one JSON object
        // per line (newline-delimited JSON); we build it via interpolation
        // because the call site has the full set of fields and adding a
        // serializer for a single line would obscure the format.
        val summaryJson = buildString {
            append("""{"type":"summary"""")
            append(""","taps":${summary.userTapCount}""")
            append(""","events":${summary.detectorEventCount}""")
            append(""","matched":${summary.matchedCount}""")
            append(""","unmatched":${summary.unmatchedTaps}""")
            append(""","fp":${summary.falsePositives}""")
            append(""","meanDeltaMs":${summary.meanDeltaMs ?: "null"}""")
            append(""","medianDeltaMs":${summary.medianDeltaMs ?: "null"}""")
            append("}")
        }
        writeLine(summaryJson)

        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {}
        writer = null
        isOpen = false

        return summary
    }

    private fun computeSummary(): SessionSummary {
        if (userTaps.isEmpty() && detectorEvents.isEmpty()) {
            return SessionSummary(0, 0, 0, 0, 0, null, null)
        }

        // Match: for each user tap, find nearest detector event within ±5s
        val matchedDetectorIndices = mutableSetOf<Int>()
        val deltas = mutableListOf<Long>()

        for (tap in userTaps) {
            var bestIdx = -1
            var bestDist = Long.MAX_VALUE
            for ((i, det) in detectorEvents.withIndex()) {
                if (i in matchedDetectorIndices) continue
                val dist = kotlin.math.abs(det.timeMs - tap.timeMs)
                if (dist <= MATCH_TOLERANCE_MS && dist < bestDist) {
                    bestDist = dist
                    bestIdx = i
                }
            }
            if (bestIdx >= 0) {
                matchedDetectorIndices.add(bestIdx)
                deltas.add(detectorEvents[bestIdx].timeMs - tap.timeMs)
            }
        }

        val matched = deltas.size
        val meanDelta = if (deltas.isNotEmpty()) deltas.average().toLong() else null
        val medianDelta = if (deltas.isNotEmpty()) {
            val sorted = deltas.sorted()
            sorted[sorted.size / 2]
        } else null

        return SessionSummary(
            userTapCount = userTaps.size,
            detectorEventCount = detectorEvents.size,
            matchedCount = matched,
            unmatchedTaps = userTaps.size - matched,
            falsePositives = detectorEvents.size - matched,
            meanDeltaMs = meanDelta,
            medianDeltaMs = medianDelta,
        )
    }

    private fun writeLine(json: String) {
        try {
            writer?.apply {
                write(json)
                newLine()
            }
        } catch (_: Exception) {}
    }

    private fun formatEvent(event: BrewAudioEvent): Pair<String, String> = when (event) {
        is BrewAudioEvent.PourStarted -> "PourStarted" to "confidence=${event.confidenceDb}"
        is BrewAudioEvent.PourStopped -> "PourStopped" to "duration=${event.durationMs}ms"
        is BrewAudioEvent.DripDetected -> "DripDetected" to "energy=${event.energyDb}dB"
        is BrewAudioEvent.DripRateUpdated -> "DripRateUpdated" to "rate=${event.dripsPerSecond}/s"
        is BrewAudioEvent.DrawdownComplete -> "DrawdownComplete" to "drainTime=${event.totalDrainTimeMs}ms"
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    companion object {
        /** Tolerance for matching detector events to user taps */
        private const val MATCH_TOLERANCE_MS = 5000L
    }
}
