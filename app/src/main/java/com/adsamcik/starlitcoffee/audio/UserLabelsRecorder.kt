package com.adsamcik.starlitcoffee.audio

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Records user-marked brew events as Audacity-compatible label files (TSV).
 *
 * During a brew experiment, the user taps buttons like "Pour Start", "First Drip",
 * "Problem" etc. Each tap is recorded with its timestamp relative to recording start.
 * The resulting file can be loaded directly into Audacity as a label track alongside
 * the WAV recording, and fed into the regression test pipeline via [LabelReader].
 *
 * Labels are **approximate** (Tier 1, ±2-3s accuracy) — the user may tap slightly
 * before or after the actual acoustic event. The [EventMatcher] tolerance windows
 * account for this imprecision.
 *
 * Output format (Audacity label track TSV):
 * ```
 * 0.000000	0.000000	recording_start
 * 5.120000	5.120000	pour_start
 * 18.450000	18.450000	pour_stop
 * 22.300000	22.300000	drip_start
 * 65.800000	65.800000	drawdown_complete
 * 42.100000	42.100000	problem:drip_detected_too_early
 * ```
 *
 * File naming: `brew_{timestamp}_user_labels.txt`
 */
class UserLabelsRecorder {

    var isOpen: Boolean = false
        private set

    var outputFile: File? = null
        private set

    var labelCount: Int = 0
        private set

    private var writer: BufferedWriter? = null
    private var sessionStartMs: Long = 0L

    /**
     * Opens a new label file for writing.
     * Automatically writes a "recording_start" label at time 0.
     *
     * @param file output file path
     * @param startTimeMs wall-clock time when recording/monitoring started
     */
    fun open(file: File, startTimeMs: Long = System.currentTimeMillis()) {
        close()
        file.parentFile?.mkdirs()
        writer = BufferedWriter(FileWriter(file))
        outputFile = file
        sessionStartMs = startTimeMs
        labelCount = 0
        isOpen = true

        // Write header comment (Audacity ignores lines starting with #,
        // but our LabelReader also skips them)
        writeLine("# Starlit Coffee user labels (Tier 1: approximate ±2-3s)")
        writeLine("# Source: on-device button taps during brew")
        markEvent("recording_start")
    }

    /**
     * Records a user-marked event at the current time.
     *
     * @param label event name (e.g. "pour_start", "drip_start", "problem:too_early")
     * @return elapsed seconds from recording start (for UI confirmation)
     */
    fun markEvent(label: String): Double {
        if (!isOpen) return 0.0
        val now = System.currentTimeMillis()
        val elapsedMs = now - sessionStartMs
        val elapsedS = elapsedMs / 1000.0

        // Audacity format: start_time\tend_time\tlabel (point labels: start == end)
        val timeStr = "%.6f".format(elapsedS)
        writeLine("$timeStr\t$timeStr\t$label")
        labelCount++
        return elapsedS
    }

    /**
     * Records a problem/feedback marker with a description.
     * These help identify what went wrong during a brew for later analysis.
     *
     * @param description brief description (e.g. "detected_pour_too_early", "missed_drip")
     */
    fun markProblem(description: String): Double {
        return markEvent("problem:${description.replace('\t', '_').replace('\n', '_')}")
    }

    /**
     * Closes the label file. Writes a final "recording_end" label.
     * Safe to call multiple times.
     */
    fun close() {
        if (isOpen) {
            markEvent("recording_end")
        }
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {}
        writer = null
        isOpen = false
    }

    private fun writeLine(line: String) {
        try {
            writer?.apply {
                write(line)
                newLine()
                flush() // Flush every write for crash safety
            }
        } catch (_: Exception) {}
    }
}
