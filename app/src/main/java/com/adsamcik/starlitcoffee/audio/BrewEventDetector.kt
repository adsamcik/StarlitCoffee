package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.DetectorConfig
import com.adsamcik.starlitcoffee.data.model.DetectorState
import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import com.adsamcik.starlitcoffee.data.model.SpectralFeatures
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Brew event state machine with continuous adaptive calibration.
 *
 * Detects pour start/stop, dripping, and drawdown completion using:
 * - Per-band adaptive noise floor (running 20th percentile, rate-limited)
 * - Water spectral signature detection (spectral tilt) to freeze contaminated bands
 * - Adaptive thresholds (μ + λσ over trailing window)
 * - Drip impulse counter with IOI constraint
 *
 * Pure computation — inject timestamps for testability. No Android dependencies.
 *
 * @param config detection parameters (thresholds, window sizes, timing)
 * @param timeProvider returns current time in ms (injectable for testing)
 */
class BrewEventDetector(
    private val config: DetectorConfig = DetectorConfig(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    var state: DetectorState = DetectorState.IDLE
        private set

    /** Current frame number since start */
    var frameCount: Long = 0L
        private set

    /** Per-band adaptive noise floor in dB */
    val noiseFloorDb: Map<FrequencyBand, Float>
        get() = _noiseFloorDb.toMap()

    /** Current drip rate (drips/second, rolling window) */
    var dripRate: Float = 0f
        private set

    // Internal noise floor tracking
    private val _noiseFloorDb = mutableMapOf<FrequencyBand, Float>().apply {
        FrequencyBand.entries.forEach { put(it, INITIAL_NOISE_FLOOR) }
    }

    // Per-band sorted ring buffers for percentile estimation
    private val noiseHistories = FrequencyBand.entries.associateWith {
        RingBuffer(NOISE_HISTORY_SIZE)
    }

    // Trailing window for adaptive threshold (per-band ODF values)
    private val odfHistories = FrequencyBand.entries.associateWith {
        RingBuffer(config.trailingWindowSize)
    }

    // State tracking
    private var stateEntryFrame: Long = 0L
    private var stateEntryTimeMs: Long = 0L
    private var pourOnsetCount = 0
    private var pourOffsetCount = 0
    private var lastDripFrame: Long = -1000L
    private val dripTimestamps = mutableListOf<Long>() // frame numbers of drips

    // Water signature detection
    private var waterSignatureDetected = false

    /**
     * Processes one spectral frame and returns any detected events.
     *
     * @param features spectral features from [SpectralAnalyzer]
     * @return list of brew events detected this frame (usually 0 or 1)
     */
    fun processFrame(features: SpectralFeatures): List<BrewAudioEvent> {
        val events = mutableListOf<BrewAudioEvent>()
        val now = timeProvider()
        frameCount++

        // Update water signature detection from spectral tilt.
        // Real pour data: tilt 1-10 (broadband, flat spectrum).
        // Ambient/drip: tilt 15-30 (low frequencies dominate).
        // LOW tilt = water present (broadband energy fills both sub-bands).
        waterSignatureDetected = features.spectralTilt < WATER_TILT_THRESHOLD
            && features.spectralTilt > 0.1f // guard against silence (tilt ≈ 0)

        // Update per-band noise floors (continuous calibration)
        updateNoiseFloors(features)

        // Compute onset detection function (ODF) per band
        val pourOdf = computePourOdf(features)
        val dripOdf = computeDripOdf(features)

        // Compute adaptive thresholds BEFORE updating history (use prior distribution)
        val pourThreshold = computeAdaptiveThreshold(
            FrequencyBand.POUR, config.pourThresholdLambda,
        )
        val dripThreshold = computeAdaptiveThreshold(
            FrequencyBand.DRIP, config.dripThresholdLambda,
        )

        // Only update ODF histories with sub-threshold values to keep
        // the baseline calibrated to ambient level. Above-threshold values
        // (actual onsets) would contaminate the baseline and inflate thresholds.
        if (state == DetectorState.IDLE) {
            if (pourOdf <= pourThreshold) {
                odfHistories[FrequencyBand.POUR]?.add(pourOdf)
            }
            if (dripOdf <= dripThreshold) {
                odfHistories[FrequencyBand.DRIP]?.add(dripOdf)
            }
        } else if (state == DetectorState.DRIPPING) {
            if (dripOdf <= dripThreshold) {
                odfHistories[FrequencyBand.DRIP]?.add(dripOdf)
            }
        }

        // State machine transitions
        val framesInState = frameCount - stateEntryFrame
        val minPhaseElapsed = framesInState >= config.minPhaseDurationFrames

        when (state) {
            DetectorState.IDLE -> {
                if (pourOdf > pourThreshold) {
                    pourOnsetCount++
                    if (pourOnsetCount >= config.pourOnsetConfirmFrames) {
                        transitionTo(DetectorState.POURING, now)
                        val confidenceDb = pourOdf - pourThreshold
                        events.add(BrewAudioEvent.PourStarted(confidenceDb))
                        pourOnsetCount = 0
                    }
                } else {
                    pourOnsetCount = 0
                }
            }

            DetectorState.POURING -> {
                // Pour offset: energy drops below noise floor + margin
                val pourEnergy = features.bandEnergyDb[FrequencyBand.POUR] ?: -96f
                val pourFloor = _noiseFloorDb[FrequencyBand.POUR] ?: INITIAL_NOISE_FLOOR
                val energyAboveFloor = pourEnergy - pourFloor

                if (energyAboveFloor < config.pourOffEnergyMarginDb) {
                    pourOffsetCount++
                    if (pourOffsetCount >= config.pourOffsetConfirmFrames && minPhaseElapsed) {
                        val durationMs = now - stateEntryTimeMs
                        transitionTo(DetectorState.DRIPPING, now)
                        events.add(BrewAudioEvent.PourStopped(durationMs))
                        pourOffsetCount = 0
                    }
                } else {
                    pourOffsetCount = 0
                }
            }

            DetectorState.DRIPPING -> {
                // Check for pour restart (refill pour)
                if (pourOdf > pourThreshold) {
                    pourOnsetCount++
                    if (pourOnsetCount >= config.pourOnsetConfirmFrames && minPhaseElapsed) {
                        transitionTo(DetectorState.POURING, now)
                        val confidenceDb = pourOdf - pourThreshold
                        events.add(BrewAudioEvent.PourStarted(confidenceDb))
                        pourOnsetCount = 0
                    }
                } else {
                    pourOnsetCount = 0
                }

                // Drip detection: peak picking with IOI constraint
                val sinceLastDrip = frameCount - lastDripFrame
                if (dripOdf > dripThreshold && sinceLastDrip >= config.dripMinIoiFrames) {
                    lastDripFrame = frameCount
                    dripTimestamps.add(frameCount)

                    val energyDb = features.bandEnergyDb[FrequencyBand.DRIP] ?: -96f
                    events.add(BrewAudioEvent.DripDetected(energyDb))
                }

                // Update drip rate (rolling window)
                pruneOldDrips()
                val newRate = computeDripRate()
                if (abs(newRate - dripRate) > 0.1f) {
                    dripRate = newRate
                    events.add(BrewAudioEvent.DripRateUpdated(dripRate))
                }

                // Drawdown complete: no drips/activity for threshold period
                if (sinceLastDrip >= config.drawdownCompleteFrames && minPhaseElapsed) {
                    val totalDrainMs = now - stateEntryTimeMs
                    transitionTo(DetectorState.COMPLETE, now)
                    events.add(BrewAudioEvent.DrawdownComplete(totalDrainMs))
                }
            }

            DetectorState.COMPLETE -> {
                // Terminal state — no further transitions until reset
            }
        }

        return events
    }

    /** Resets the detector to initial state. Call when starting a new brew session. */
    fun reset() {
        state = DetectorState.IDLE
        frameCount = 0L
        stateEntryFrame = 0L
        stateEntryTimeMs = timeProvider()
        pourOnsetCount = 0
        pourOffsetCount = 0
        lastDripFrame = -1000L
        dripTimestamps.clear()
        dripRate = 0f
        waterSignatureDetected = false

        _noiseFloorDb.entries.forEach { it.setValue(INITIAL_NOISE_FLOOR) }
        noiseHistories.values.forEach { it.clear() }
        odfHistories.values.forEach { it.clear() }
    }

    // --- Continuous Adaptive Noise Floor ---

    private fun updateNoiseFloors(features: SpectralFeatures) {
        for (band in FrequencyBand.entries) {
            val currentEnergy = features.bandEnergyDb[band] ?: continue

            // Water signature: freeze bands contaminated by pour noise
            // POUR and DRIP overlap with water's spectral profile
            if (waterSignatureDetected && (band == FrequencyBand.POUR || band == FrequencyBand.DRIP)) {
                continue // Skip noise floor update for water-contaminated bands
            }

            // Add to history for percentile estimation
            noiseHistories[band]?.add(currentEnergy)

            // Compute 20th percentile from sorted ring buffer
            val history = noiseHistories[band] ?: continue
            val targetFloor = history.percentile(0.2f)

            // Rate-limit the change
            val currentFloor = _noiseFloorDb[band] ?: INITIAL_NOISE_FLOOR
            val maxChange = config.maxNoiseFloorChangePerFrame
            val newFloor = when {
                targetFloor > currentFloor + maxChange -> currentFloor + maxChange
                targetFloor < currentFloor - maxChange -> currentFloor - maxChange
                else -> targetFloor
            }

            _noiseFloorDb[band] = newFloor
        }
    }

    // --- Onset Detection Functions ---

    private fun computePourOdf(features: SpectralFeatures): Float {
        val flux = features.spectralFlux[FrequencyBand.POUR] ?: 0f
        val energy = features.bandEnergyDb[FrequencyBand.POUR] ?: -96f
        val floor = _noiseFloorDb[FrequencyBand.POUR] ?: INITIAL_NOISE_FLOOR
        val energyAboveFloor = max(0f, energy - floor)

        // Spectral flatness bonus: water is noise-like (flatness ~0.1-0.5 for real pours)
        // Scale: flatness 0→0x, 0.08→0.5x, 0.15→1x, 0.3+→1.5x
        val flatnessBonus = (features.spectralFlatness / 0.15f).coerceIn(0f, 1.5f)

        // Band coincidence bonus: water lights ≥4/5 bands
        val coincidenceBonus = when {
            features.bandCoincidenceCount >= 4 -> 1.0f
            features.bandCoincidenceCount == 3 -> 0.5f
            else -> 0.2f
        }

        // Cepstral veto: strong pitch (speech/music) → suppress
        // CPP > 4 dB = almost certainly speech/music → multiply by 0
        val cepstralGate = when {
            features.cepstralPeakProminence > CPP_HARD_VETO -> 0f
            features.cepstralPeakProminence > CPP_SOFT_VETO -> 0.3f
            else -> 1f
        }

        // Composite ODF: weighted sum of energy + flux, scaled by flatness,
        // coincidence, and cepstral gate
        val rawOdf = ODF_FLUX_WEIGHT * flux + ODF_ENERGY_WEIGHT * energyAboveFloor
        return rawOdf * flatnessBonus * coincidenceBonus * cepstralGate
    }

    private fun computeDripOdf(features: SpectralFeatures): Float {
        val flux = features.spectralFlux[FrequencyBand.DRIP] ?: 0f
        val energy = features.bandEnergyDb[FrequencyBand.DRIP] ?: -96f
        val floor = _noiseFloorDb[FrequencyBand.DRIP] ?: INITIAL_NOISE_FLOOR
        val energyAboveFloor = max(0f, energy - floor)

        return 0.5f * flux + 0.5f * energyAboveFloor
    }

    // --- Adaptive Threshold ---

    private fun computeAdaptiveThreshold(band: FrequencyBand, lambda: Float): Float {
        val history = odfHistories[band] ?: return INITIAL_THRESHOLD
        if (history.count < 3) return INITIAL_THRESHOLD

        val mean = history.mean()
        val stdDev = history.stdDev()

        return max(MIN_THRESHOLD, mean + lambda * stdDev)
    }

    // --- Drip Rate ---

    private fun pruneOldDrips() {
        val cutoff = frameCount - config.dripRateWindowFrames
        dripTimestamps.removeAll { it < cutoff }
    }

    private fun computeDripRate(): Float {
        val windowSeconds = config.dripRateWindowFrames / DetectorConfig.FRAMES_PER_SECOND
        return dripTimestamps.size / windowSeconds
    }

    private fun transitionTo(newState: DetectorState, timeMs: Long) {
        state = newState
        stateEntryFrame = frameCount
        stateEntryTimeMs = timeMs

        when (newState) {
            DetectorState.POURING -> {
                // Clear POUR ODF so threshold resets for future offset/restart detection
                odfHistories[FrequencyBand.POUR]?.clear()
            }
            DetectorState.DRIPPING -> {
                // Reset drip tracking when entering dripping state
                lastDripFrame = frameCount
                dripTimestamps.clear()
                dripRate = 0f
                // Clear DRIP ODF so threshold calibrates to dripping ambient
                odfHistories[FrequencyBand.DRIP]?.clear()
            }
            else -> {}
        }
    }

    /**
     * Simple ring buffer with sorted access for percentile estimation.
     * Not a general-purpose collection — optimized for noise floor tracking.
     */
    internal class RingBuffer(private val capacity: Int) {
        private val data = FloatArray(capacity)
        private var writePos = 0
        var count = 0
            private set

        fun add(value: Float) {
            data[writePos] = value
            writePos = (writePos + 1) % capacity
            if (count < capacity) count++
        }

        fun clear() {
            data.fill(0f)
            writePos = 0
            count = 0
        }

        fun percentile(p: Float): Float {
            if (count == 0) return 0f
            val sorted = data.copyOf(count).also { it.sort() }
            val index = ((count - 1) * p).toInt().coerceIn(0, count - 1)
            return sorted[index]
        }

        fun mean(): Float {
            if (count == 0) return 0f
            var sum = 0.0
            for (i in 0 until count) sum += data[i]
            return (sum / count).toFloat()
        }

        fun stdDev(): Float {
            if (count < 2) return 0f
            val m = mean().toDouble()
            var sumSq = 0.0
            for (i in 0 until count) {
                val diff = data[i] - m
                sumSq += diff * diff
            }
            return sqrt(sumSq / count).toFloat()
        }
    }

    companion object {
        // Real-world ambient is ~-30 dB in POUR band (from Pulsar recordings).
        // Starting at -40 gives a conservative buffer that calibrates upward quickly.
        private const val INITIAL_NOISE_FLOOR = -40f
        private const val INITIAL_THRESHOLD = 5f
        private const val MIN_THRESHOLD = 1f

        // Noise history: ~5 seconds at 86fps
        private const val NOISE_HISTORY_SIZE = 430

        // Water spectral tilt threshold — real pour data shows tilt 1-10 (broadband),
        // while ambient/drip shows tilt 15-30 (low-freq dominant).
        // Low tilt = broadband water noise present → freeze noise floor.
        private const val WATER_TILT_THRESHOLD = 8.0f

        // ODF weights for pour detection
        private const val ODF_FLUX_WEIGHT = 0.7f
        private const val ODF_ENERGY_WEIGHT = 0.3f

        // Cepstral Peak Prominence veto thresholds
        // CPP > 4 dB = strong pitch → hard veto (speech/music)
        // CPP > 2.5 dB = moderate pitch → soft suppression
        private const val CPP_HARD_VETO = 4.0f
        private const val CPP_SOFT_VETO = 2.5f
    }
}
