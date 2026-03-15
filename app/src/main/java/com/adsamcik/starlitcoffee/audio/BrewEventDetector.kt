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
 * - Relative CPP speech rejection (deviation from running baseline, not fixed clinical
 *   thresholds) combined with band coincidence as the primary broadband discriminator
 *
 * Event-specific feature emphasis:
 * - Pour onset: spectral flux + band coincidence (soft broadband onset detection)
 * - Drip detection: HFC-weighted combined-band ODF (bubble resonance emphasis)
 * - End-of-flow: dual criterion — drip silence + smoothed energy decay toward noise floor
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

    // Tilt baseline tracking for relative thresholds (research: absolute thresholds not portable)
    private var tiltBaseline = 15f  // Start with typical non-water tilt
    private var tiltBaselineCount = 0

    // Running CPP baseline for relative thresholds (research: clinical CPP values aren't portable)
    private var cppBaseline = 0f
    private var cppBaselineCount = 0
    private val CPP_BASELINE_ALPHA = 0.02f // slow EMA update rate

    // Smoothed energy tracking for end-of-flow detection
    private var smoothedPourEnergy = INITIAL_NOISE_FLOOR
    private val ENERGY_SMOOTHING_ALPHA = 0.05f  // ~1s time constant at 86fps
    private var peakPourEnergy = INITIAL_NOISE_FLOOR

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

        // Water signature: relative to ambient tilt baseline, not a fixed threshold.
        // During idle, track what "normal" tilt looks like. During brew, a significant
        // drop in tilt (toward broadband/flat) indicates water.
        if (state == DetectorState.IDLE || state == DetectorState.COMPLETE) {
            if (tiltBaselineCount < 100) {
                tiltBaseline += (features.spectralTilt - tiltBaseline) / (tiltBaselineCount + 1)
                tiltBaselineCount++
            } else {
                tiltBaseline += 0.02f * (features.spectralTilt - tiltBaseline)
            }
        }
        val tiltDrop = tiltBaseline - features.spectralTilt
        waterSignatureDetected = tiltDrop > WATER_TILT_DROP_THRESHOLD
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
            FrequencyBand.DRIP_LOW, config.dripThresholdLambda,
        )

        // Only update ODF histories with sub-threshold values to keep
        // the baseline calibrated to ambient level. Above-threshold values
        // (actual onsets) would contaminate the baseline and inflate thresholds.
        if (state == DetectorState.IDLE) {
            if (pourOdf <= pourThreshold) {
                odfHistories[FrequencyBand.POUR]?.add(pourOdf)
            }
            if (dripOdf <= dripThreshold) {
                odfHistories[FrequencyBand.DRIP_LOW]?.add(dripOdf)
            }
        } else if (state == DetectorState.DRIPPING) {
            if (dripOdf <= dripThreshold) {
                odfHistories[FrequencyBand.DRIP_LOW]?.add(dripOdf)
            }
        }

        // Track CPP baseline during non-active periods for relative thresholds.
        // Updated AFTER ODF computation so the current frame's gate uses the prior baseline.
        if (state == DetectorState.IDLE || state == DetectorState.COMPLETE) {
            if (cppBaselineCount < 100) {
                // Bootstrap: use simple average for first 100 frames
                cppBaseline += (features.cepstralPeakProminence - cppBaseline) / (cppBaselineCount + 1)
                cppBaselineCount++
            } else {
                // EMA tracking after bootstrap
                cppBaseline += CPP_BASELINE_ALPHA * (features.cepstralPeakProminence - cppBaseline)
            }
        }

        // Track smoothed pour energy for end-of-flow detection
        val currentPourEnergy = features.bandEnergyDb[FrequencyBand.POUR] ?: -96f
        smoothedPourEnergy += ENERGY_SMOOTHING_ALPHA * (currentPourEnergy - smoothedPourEnergy)
        if (state == DetectorState.POURING && currentPourEnergy > peakPourEnergy) {
            peakPourEnergy = currentPourEnergy
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
                // Pour offset: energy drops back toward noise floor.
                // Already relative to adaptive noise floor — research-supported approach.
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

                    val energyLow = features.bandEnergyDb[FrequencyBand.DRIP_LOW] ?: -96f
                    val energyHigh = features.bandEnergyDb[FrequencyBand.DRIP_HIGH] ?: -96f
                    val energyDb = max(energyLow, energyHigh)
                    events.add(BrewAudioEvent.DripDetected(energyDb))
                }

                // Update drip rate (rolling window)
                pruneOldDrips()
                val newRate = computeDripRate()
                if (abs(newRate - dripRate) > 0.1f) {
                    dripRate = newRate
                    events.add(BrewAudioEvent.DripRateUpdated(dripRate))
                }

                // Drawdown complete: dual criterion (research: use sustained energy decay
                // with hysteresis, not just absence of drip events).
                // 1. No drips for threshold period
                // 2. Smoothed energy has decayed close to noise floor
                val pourFloor = _noiseFloorDb[FrequencyBand.POUR] ?: INITIAL_NOISE_FLOOR
                val energyDecayed = smoothedPourEnergy < pourFloor + DRAWDOWN_ENERGY_MARGIN
                val dripsAbsent = sinceLastDrip >= config.drawdownCompleteFrames

                if (dripsAbsent && energyDecayed && minPhaseElapsed) {
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
        tiltBaseline = 15f
        tiltBaselineCount = 0
        cppBaseline = 0f
        cppBaselineCount = 0
        smoothedPourEnergy = INITIAL_NOISE_FLOOR
        peakPourEnergy = INITIAL_NOISE_FLOOR

        _noiseFloorDb.entries.forEach { it.setValue(INITIAL_NOISE_FLOOR) }
        noiseHistories.values.forEach { it.clear() }
        odfHistories.values.forEach { it.clear() }
    }

    // --- Continuous Adaptive Noise Floor ---

    private fun updateNoiseFloors(features: SpectralFeatures) {
        for (band in FrequencyBand.entries) {
            val currentEnergy = features.bandEnergyDb[band] ?: continue

            // Water signature: freeze bands contaminated by pour noise
            // POUR and both DRIP bands overlap with water's spectral profile
            if (waterSignatureDetected && (band == FrequencyBand.POUR || band == FrequencyBand.DRIP_LOW || band == FrequencyBand.DRIP_HIGH)) {
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

        // Spectral flatness: secondary noise-likeness cue (research: not reliable as primary gate).
        // Reduced influence — used as mild bonus, not a strong multiplier.
        val flatnessBonus = (features.spectralFlatness / 0.2f).coerceIn(0.3f, 1.2f)

        // Band coincidence: more robust broadband-vs-tonal discriminator (research-supported).
        // Water fills ≥5 bands (of 6); speech/fan typically 2-3. Increased weight as primary
        // broadband indicator, replacing flatness as the dominant spectral shape cue.
        val coincidenceBonus = when {
            features.bandCoincidenceCount >= 5 -> 1.2f   // strong broadband signal (5-6 of 6 bands)
            features.bandCoincidenceCount == 4 -> 0.9f
            features.bandCoincidenceCount == 3 -> 0.7f
            features.bandCoincidenceCount == 2 -> 0.3f
            else -> 0.15f                                 // very narrowband → strongly suppressed
        }

        // Cepstral gate: uses relative CPP (deviation above running baseline).
        // Research: fixed CPP thresholds (2.5/4.0 dB) from clinical voice analysis
        // aren't portable to far-field smartphone recordings. Relative deviation
        // is more robust across devices and environments.
        val cppDeviation = features.cepstralPeakProminence - cppBaseline
        val cepstralGate = when {
            cppDeviation > CPP_RELATIVE_HARD_VETO -> 0f      // strong pitch spike
            cppDeviation > CPP_RELATIVE_SOFT_VETO -> 0.3f    // moderate pitch
            else -> 1f
        }

        // Event-conditional ODF weights (research: Duxbury 2002, Tian 2014).
        // IDLE: emphasize spectral flux (better for detecting soft pour onsets)
        // POURING: emphasize energy (better for tracking sustained activity)
        // DRIPPING: emphasize flux (better for sharp transient detection)
        val (fluxW, energyW) = when (state) {
            DetectorState.IDLE -> 0.8f to 0.2f
            DetectorState.POURING -> 0.5f to 0.5f
            DetectorState.DRIPPING -> 0.7f to 0.3f
            DetectorState.COMPLETE -> 0.7f to 0.3f
        }
        val rawOdf = fluxW * flux + energyW * energyAboveFloor
        return rawOdf * flatnessBonus * coincidenceBonus * cepstralGate
    }

    private fun computeDripOdf(features: SpectralFeatures): Float {
        // DRIP_LOW: vessel resonance, structure-borne impact
        val fluxLow = features.spectralFlux[FrequencyBand.DRIP_LOW] ?: 0f
        val energyLow = features.bandEnergyDb[FrequencyBand.DRIP_LOW] ?: -96f
        val floorLow = _noiseFloorDb[FrequencyBand.DRIP_LOW] ?: INITIAL_NOISE_FLOOR

        // DRIP_HIGH: bubble resonance at ~8.66 kHz
        val fluxHigh = features.spectralFlux[FrequencyBand.DRIP_HIGH] ?: 0f
        val energyHigh = features.bandEnergyDb[FrequencyBand.DRIP_HIGH] ?: -96f
        val floorHigh = _noiseFloorDb[FrequencyBand.DRIP_HIGH] ?: INITIAL_NOISE_FLOOR

        // Take max contribution from either drip band
        // HFC-weighted drip ODF (research: Duxbury 2002).
        // High-frequency content is more discriminative for sharp transients (drip impacts).
        // Weight DRIP_HIGH more heavily since bubble resonance (~8.66 kHz) is the
        // primary drip acoustic signature (Phillips et al. 2018).
        val weightedFlux = 0.4f * fluxLow + 0.6f * fluxHigh
        val weightedEnergy = 0.4f * max(0f, energyLow - floorLow) +
            0.6f * max(0f, energyHigh - floorHigh)

        return 0.5f * weightedFlux + 0.5f * weightedEnergy
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
                // Clear drip ODF so threshold calibrates to dripping ambient
                odfHistories[FrequencyBand.DRIP_LOW]?.clear()
                // Don't reset peakPourEnergy — we need it for decay ratio
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

        // Relative tilt drop needed to detect water (from ambient baseline).
        // Research: absolute tilt thresholds not portable across devices (±9 dB variation).
        private const val WATER_TILT_DROP_THRESHOLD = 5.0f

        // Relative CPP thresholds: deviation above running baseline.
        // More robust than absolute thresholds across devices and environments.
        // A 3 dB spike above the ambient CPP baseline indicates strong periodicity.
        private const val CPP_RELATIVE_HARD_VETO = 3.0f
        private const val CPP_RELATIVE_SOFT_VETO = 1.5f

        // Energy must decay to within this margin of noise floor for drawdown complete
        private const val DRAWDOWN_ENERGY_MARGIN = 5f  // dB above floor
    }
}
