package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.DetectorState
import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import com.adsamcik.starlitcoffee.data.model.SpectralFeatures
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * Flight recorder that writes detection metadata alongside WAV audio.
 *
 * Produces a `.jsonl` (JSON Lines) sidecar file with one snapshot per interval
 * (default 250ms). Each line is a self-contained JSON object capturing the full
 * pipeline state at that moment — detector state, spectral features, noise floors,
 * trajectory phase, events, etc.
 *
 * Designed for offline replay and debugging: load the JSONL into Python/Excel
 * to visualize exactly how the detector performed during a real brew.
 *
 * Usage:
 * 1. [open] with a file path (typically same directory as WAV, same base name)
 * 2. [recordSnapshot] every frame — internally throttles to [intervalMs]
 * 3. [close] when recording ends
 *
 * File format (one JSON object per line):
 * ```json
 * {"t":1234,"elapsed":5600,"state":"POURING","phase":"Pour 1/3","rmsDb":-12.3,...}
 * ```
 */
class FlightRecorder(
    /** Minimum interval between snapshots in milliseconds */
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    var isOpen: Boolean = false
        private set

    var outputFile: File? = null
        private set

    private var writer: BufferedWriter? = null
    private var startTimeMs: Long = 0L
    private var lastSnapshotMs: Long = 0L
    private var snapshotCount: Int = 0

    /**
     * Opens a new JSONL file for writing metadata snapshots.
     */
    fun open(file: File) {
        close()
        file.parentFile?.mkdirs()
        writer = BufferedWriter(FileWriter(file))
        outputFile = file
        startTimeMs = System.currentTimeMillis()
        lastSnapshotMs = 0L
        snapshotCount = 0
        isOpen = true
    }

    /**
     * Records a pipeline state snapshot if enough time has elapsed since the last one.
     * Call this every frame (~86fps) — the recorder internally throttles.
     *
     * @return true if a snapshot was written this call
     */
    fun recordSnapshot(
        spectralFeatures: SpectralFeatures,
        detectorState: DetectorState,
        noiseFloorDb: Map<FrequencyBand, Float>,
        dripRate: Float,
        rmsDb: Float,
        brewPhaseLabel: String,
        trajectoryPhase: String,
        brewConfidence: Float,
        waterLikeness: Float,
        baselineCalibrated: Boolean,
        probeTurbulence: Float,
        events: List<BrewAudioEvent>,
    ): Boolean {
        if (!isOpen) return false

        val now = System.currentTimeMillis()
        if (now - lastSnapshotMs < intervalMs) return false

        val elapsed = now - startTimeMs
        lastSnapshotMs = now
        snapshotCount++

        val line = buildJsonLine(
            timestamp = now,
            elapsedMs = elapsed,
            spectralFeatures = spectralFeatures,
            detectorState = detectorState,
            noiseFloorDb = noiseFloorDb,
            dripRate = dripRate,
            rmsDb = rmsDb,
            brewPhaseLabel = brewPhaseLabel,
            trajectoryPhase = trajectoryPhase,
            brewConfidence = brewConfidence,
            waterLikeness = waterLikeness,
            baselineCalibrated = baselineCalibrated,
            probeTurbulence = probeTurbulence,
            events = events,
        )

        try {
            writer?.apply {
                write(line)
                newLine()
                // Flush periodically (every 10 snapshots) for crash safety
                if (snapshotCount % 10 == 0) flush()
            }
        } catch (_: Exception) {
            // Don't crash the pipeline if file I/O fails
        }

        return true
    }

    /** Closes the file. Safe to call multiple times. */
    fun close() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
        writer = null
        isOpen = false
    }

    private fun buildJsonLine(
        timestamp: Long,
        elapsedMs: Long,
        spectralFeatures: SpectralFeatures,
        detectorState: DetectorState,
        noiseFloorDb: Map<FrequencyBand, Float>,
        dripRate: Float,
        rmsDb: Float,
        brewPhaseLabel: String,
        trajectoryPhase: String,
        brewConfidence: Float,
        waterLikeness: Float,
        baselineCalibrated: Boolean,
        probeTurbulence: Float,
        events: List<BrewAudioEvent>,
    ): String {
        // Manual JSON construction to avoid dependency on serialization library
        val sb = StringBuilder(512)
        sb.append('{')
        sb.appendField("t", timestamp)
        sb.appendField("elapsed", elapsedMs)
        sb.appendField("state", detectorState.name)
        sb.appendField("phase", escapeJson(brewPhaseLabel))
        sb.appendField("trajectory", trajectoryPhase)
        sb.appendField("confidence", brewConfidence, 3)
        sb.appendField("rmsDb", rmsDb, 1)

        // Band energies
        for (band in FrequencyBand.entries) {
            val energy = spectralFeatures.bandEnergyDb[band] ?: -96f
            sb.appendField("${band.name.lowercase()}Db", energy, 1)
        }

        // Noise floors
        for (band in FrequencyBand.entries) {
            val floor = noiseFloorDb[band] ?: -40f
            sb.appendField("${band.name.lowercase()}Floor", floor, 1)
        }

        // Spectral features
        sb.appendField("flatness", spectralFeatures.spectralFlatness, 4)
        sb.appendField("cpp", spectralFeatures.cepstralPeakProminence, 2)
        sb.appendField("coincidence", spectralFeatures.bandCoincidenceCount)
        sb.appendField("tilt", spectralFeatures.spectralTilt, 2)
        sb.appendField("waterLikeness", waterLikeness, 3)
        sb.appendField("dripRate", dripRate, 2)
        sb.appendField("calibrated", baselineCalibrated)
        sb.appendField("probe", probeTurbulence, 3)

        // Spectral flux
        for (band in FrequencyBand.entries) {
            val flux = spectralFeatures.spectralFlux[band] ?: 0f
            sb.appendField("${band.name.lowercase()}Flux", flux, 2)
        }

        // Events (if any)
        if (events.isNotEmpty()) {
            sb.append("\"events\":[")
            events.forEachIndexed { i, event ->
                if (i > 0) sb.append(',')
                sb.append('"').append(formatEvent(event)).append('"')
            }
            sb.append("],")
        }

        // Remove trailing comma and close
        if (sb.last() == ',') sb.setLength(sb.length - 1)
        sb.append('}')
        return sb.toString()
    }

    private fun formatEvent(event: BrewAudioEvent): String = when (event) {
        is BrewAudioEvent.PourStarted -> "PourStarted(${event.confidenceDb})"
        is BrewAudioEvent.PourStopped -> "PourStopped(${event.durationMs}ms)"
        is BrewAudioEvent.DripDetected -> "DripDetected(${event.energyDb}dB)"
        is BrewAudioEvent.DripRateUpdated -> "DripRate(${event.dripsPerSecond}/s)"
        is BrewAudioEvent.DrawdownComplete -> "DrawdownComplete(${event.totalDrainTimeMs}ms)"
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun StringBuilder.appendField(key: String, value: Long) {
        append('"').append(key).append("\":").append(value).append(',')
    }

    private fun StringBuilder.appendField(key: String, value: Int) {
        append('"').append(key).append("\":").append(value).append(',')
    }

    private fun StringBuilder.appendField(key: String, value: Float, decimals: Int) {
        append('"').append(key).append("\":").append("%.${decimals}f".format(value)).append(',')
    }

    private fun StringBuilder.appendField(key: String, value: String) {
        append('"').append(key).append("\":\"").append(value).append("\",")
    }

    private fun StringBuilder.appendField(key: String, value: Boolean) {
        append('"').append(key).append("\":").append(value).append(',')
    }

    companion object {
        /** Default snapshot interval: 250ms = 4 snapshots/second */
        const val DEFAULT_INTERVAL_MS = 250L
    }
}
