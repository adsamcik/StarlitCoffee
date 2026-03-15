package com.adsamcik.starlitcoffee.audio

import kotlin.math.max

/**
 * Adaptive ambient baseline capture and spectral subtraction for brew detection.
 *
 * Captures per-bin median power spectrum during a calibration window (first N frames).
 * Static noise sources (fan, fridge, music) are learned and subtracted from subsequent
 * frames. Only the *residual* — what changed since calibration — reaches the detector.
 *
 * After calibration, a slow continuous adaptation tracks environmental drift
 * (HVAC cycling, door opening) without adapting to the brew event itself.
 *
 * Usage:
 * ```
 * val baseline = AmbientBaseline()
 * // During calibration:
 * baseline.feedCalibrationFrame(powerSpectrum)
 * // After calibration:
 * baseline.finalizeCalibration()
 * // During brew:
 * val residual = baseline.subtract(powerSpectrum)
 * ```
 */
class AmbientBaseline(
    /** Number of bins in the power spectrum (FFT_SIZE / 2 + 1) */
    private val spectrumSize: Int = DEFAULT_SPECTRUM_SIZE,
    /** Number of frames to collect during calibration */
    private val calibrationFrames: Int = DEFAULT_CALIBRATION_FRAMES,
) {
    /** Whether calibration is complete */
    var isCalibrated: Boolean = false
        private set

    /** Number of calibration frames collected so far */
    var calibrationProgress: Int = 0
        private set

    /** The ambient baseline spectrum (per-bin median power) */
    val baselineSpectrum: FloatArray
        get() = _baseline.copyOf()

    // Internal state
    private val _baseline = FloatArray(spectrumSize)
    private val calibrationBuffers = Array(spectrumSize) { mutableListOf<Float>() }
    private var continuousAdaptRate = 0f

    /**
     * Feeds one power spectrum frame during the calibration window.
     * Call this for the first [calibrationFrames] frames of a brew session.
     *
     * @return true if calibration is now complete
     */
    fun feedCalibrationFrame(powerSpectrum: FloatArray): Boolean {
        if (isCalibrated) return true
        if (powerSpectrum.size < spectrumSize) return false

        for (k in 0 until spectrumSize) {
            calibrationBuffers[k].add(powerSpectrum[k])
        }
        calibrationProgress++

        if (calibrationProgress >= calibrationFrames) {
            finalizeCalibration()
            return true
        }
        return false
    }

    /**
     * Finalizes calibration by computing per-bin median from collected frames.
     * Can be called early if the user wants to cut calibration short.
     */
    fun finalizeCalibration() {
        if (calibrationProgress == 0) {
            // No frames collected — use a conservative default
            _baseline.fill(DEFAULT_NOISE_POWER)
            isCalibrated = true
            return
        }

        for (k in 0 until spectrumSize) {
            val values = calibrationBuffers[k]
            if (values.isEmpty()) {
                _baseline[k] = DEFAULT_NOISE_POWER
            } else {
                values.sort()
                _baseline[k] = values[values.size / 2] // Median
            }
        }

        // Free calibration memory
        calibrationBuffers.forEach { it.clear() }
        isCalibrated = true

        // Enable slow continuous adaptation after calibration
        continuousAdaptRate = CONTINUOUS_ADAPT_RATE
    }

    /**
     * Subtracts the ambient baseline from a power spectrum, returning the residual.
     * Applies magnitude flooring to prevent negative artifacts ("musical noise").
     *
     * If not yet calibrated, returns the raw spectrum scaled by a conservative factor.
     *
     * @param powerSpectrum raw power spectrum from FFT
     * @return residual power spectrum (what changed since calibration)
     */
    fun subtract(powerSpectrum: FloatArray): FloatArray {
        val residual = FloatArray(spectrumSize)

        if (!isCalibrated) {
            // Before calibration: pass through with mild suppression
            for (k in 0 until spectrumSize.coerceAtMost(powerSpectrum.size)) {
                residual[k] = powerSpectrum[k] * UNCALIBRATED_PASSTHROUGH
            }
            return residual
        }

        for (k in 0 until spectrumSize.coerceAtMost(powerSpectrum.size)) {
            val raw = powerSpectrum[k]
            val baseline = _baseline[k]

            // Adaptive over-subtraction: stronger at low SNR, milder at high SNR.
            // Literature (Berouti 1979): α ≈ 3-6 at 0dB SNR, ~1 at 20dB SNR.
            // We use a conservative linear schedule: α = max(ALPHA_MIN, ALPHA_MAX - ALPHA_SLOPE * localSnr)
            val localSnr = if (baseline > 1e-12f) raw / baseline else 1f
            val localSnrDb = (10f * kotlin.math.log10(localSnr.coerceAtLeast(1e-6f)))
            val alpha = (ALPHA_MAX - ALPHA_SLOPE * localSnrDb).coerceIn(ALPHA_MIN, ALPHA_MAX)
            val subtracted = raw - alpha * baseline

            // Magnitude flooring: prevent negative power (musical noise artifact)
            residual[k] = max(subtracted, SPECTRAL_FLOOR * baseline)

            // Slow continuous adaptation of baseline (only for bins below threshold)
            // This tracks slow environmental drift (HVAC cycling, door opening)
            // without adapting to the brew event itself
            if (continuousAdaptRate > 0 && raw < baseline * ADAPT_CEILING_FACTOR) {
                _baseline[k] += continuousAdaptRate * (raw - baseline)
            }
        }

        return residual
    }

    /** Resets all state for a new session. */
    fun reset() {
        isCalibrated = false
        calibrationProgress = 0
        _baseline.fill(0f)
        calibrationBuffers.forEach { it.clear() }
        continuousAdaptRate = 0f
    }

    companion object {
        /** 1024-pt FFT → 513 bins */
        const val DEFAULT_SPECTRUM_SIZE = 513

        /** ~5 seconds at 86fps */
        const val DEFAULT_CALIBRATION_FRAMES = 430

        /** Default noise power for uncalibrated bins */
        private const val DEFAULT_NOISE_POWER = 1e-8f

        /** Minimum over-subtraction factor (high SNR → gentle subtraction) */
        private const val ALPHA_MIN = 1.0f

        /** Maximum over-subtraction factor (low SNR → aggressive subtraction) */
        private const val ALPHA_MAX = 3.0f

        /** Rate at which α decreases per dB of SNR */
        private const val ALPHA_SLOPE = 0.1f

        /** Spectral floor as fraction of baseline (prevents musical noise) */
        private const val SPECTRAL_FLOOR = 0.01f

        /** Pass-through factor before calibration completes */
        private const val UNCALIBRATED_PASSTHROUGH = 0.8f

        /** Continuous adaptation rate after calibration (very slow) */
        private const val CONTINUOUS_ADAPT_RATE = 0.001f

        /** Only adapt bins that are below this multiple of baseline (skip active events) */
        private const val ADAPT_CEILING_FACTOR = 2.0f
    }
}
