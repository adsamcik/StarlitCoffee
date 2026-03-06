package com.adsamcik.starlitcoffee.audio

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max

/**
 * Ambient baseline capture and spectral subtraction for Pulsar brew detection.
 *
 * Two-layer noise isolation:
 *
 * 1. **Adaptive ambient baseline**: Captures per-bin median power spectrum during
 *    a calibration window (first N frames). Static noise sources (fan, fridge, music)
 *    are learned and subtracted from subsequent frames. Only the *residual* — what
 *    changed since calibration — reaches the detector.
 *
 * 2. **Water spectral prior**: A pre-built template of water's expected spectral shape
 *    (derived from real Pulsar recordings). Used to score how "water-like" the residual
 *    looks, even when calibration is incomplete. This encodes domain knowledge:
 *    water has a characteristic broadband curve with gentle rolloff above 1kHz.
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
 * val waterScore = baseline.scoreWaterLikeness(residual)
 * ```
 *
 * Pulsar-specific: the water template was derived from NextLevel Pulsar recordings.
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

            // Spectral subtraction with over-subtraction factor for cleaner residual
            val subtracted = raw - OVER_SUBTRACTION * baseline

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

    /**
     * Scores how "water-like" a residual spectrum looks, using the Pulsar water template.
     *
     * The template encodes the expected spectral shape of water pouring:
     * - Broadband energy from 200Hz to 6kHz
     * - Gentle rolloff (~-3dB/octave above 1kHz)
     * - Relatively flat below 1kHz
     *
     * Returns a correlation score (0–1) between the residual's shape and the template.
     * High score = residual looks like water, regardless of absolute level.
     *
     * @param residualPowerSpectrum output from [subtract]
     * @return water likeness score (0 = nothing like water, 1 = perfect match)
     */
    fun scoreWaterLikeness(residualPowerSpectrum: FloatArray): Float {
        // Convert residual to dB shape (normalized — remove level, keep shape)
        val residualDb = FloatArray(WATER_TEMPLATE.size)
        var maxDb = -200f

        for (i in WATER_TEMPLATE.indices) {
            val bin = WATER_TEMPLATE_BINS[i]
            if (bin >= residualPowerSpectrum.size) continue
            val power = residualPowerSpectrum[bin]
            val db = if (power > EPSILON) (10.0 * log10(power.toDouble())).toFloat() else -96f
            residualDb[i] = db
            if (db > maxDb) maxDb = db
        }

        // Normalize to peak (shape only, level-independent)
        if (maxDb < -80f) return 0f // Nothing above noise floor
        for (i in residualDb.indices) {
            residualDb[i] -= maxDb
        }

        // Cosine similarity between normalized residual shape and water template
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in WATER_TEMPLATE.indices) {
            val a = residualDb[i].toDouble()
            val b = WATER_TEMPLATE[i].toDouble()
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        val denominator = kotlin.math.sqrt(normA * normB)
        if (denominator < EPSILON) return 0f

        // Cosine similarity range [-1, 1] → map to [0, 1]
        return ((dotProduct / denominator + 1.0) / 2.0).toFloat().coerceIn(0f, 1f)
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
        private const val EPSILON = 1e-12f

        /** 1024-pt FFT → 513 bins */
        const val DEFAULT_SPECTRUM_SIZE = 513

        /** ~5 seconds at 86fps */
        const val DEFAULT_CALIBRATION_FRAMES = 430

        /** Default noise power for uncalibrated bins */
        private const val DEFAULT_NOISE_POWER = 1e-8f

        /** Over-subtraction factor (1.0 = exact, >1 = aggressive, removes more noise) */
        private const val OVER_SUBTRACTION = 1.5f

        /** Spectral floor as fraction of baseline (prevents musical noise) */
        private const val SPECTRAL_FLOOR = 0.01f

        /** Pass-through factor before calibration completes */
        private const val UNCALIBRATED_PASSTHROUGH = 0.8f

        /** Continuous adaptation rate after calibration (very slow) */
        private const val CONTINUOUS_ADAPT_RATE = 0.001f

        /** Only adapt bins that are below this multiple of baseline (skip active events) */
        private const val ADAPT_CEILING_FACTOR = 2.0f

        // --- Pulsar Water Spectral Template ---
        // Derived from real recordings: normalized dB shape of water pouring.
        // Index → frequency band center, Value → expected relative level (dB below peak).
        // Water has broadband energy with gentle rolloff above 1kHz.

        /** Bin indices for template points (1024-pt FFT at 44100Hz) */
        private val WATER_TEMPLATE_BINS = intArrayOf(
            5,   // 215 Hz
            7,   // 301 Hz
            10,  // 431 Hz
            14,  // 603 Hz
            19,  // 818 Hz
            25,  // 1076 Hz
            35,  // 1507 Hz
            47,  // 2024 Hz
            65,  // 2798 Hz
            90,  // 3874 Hz
            120, // 5165 Hz
            139, // 5984 Hz
        )

        /**
         * Expected relative dB shape of Pulsar water pour (normalized to peak = 0).
         * From real recordings:
         * - Fairly flat 200-800 Hz (peak energy region)
         * - Gentle rolloff -3 to -5 dB/octave above 1kHz
         * - Still significant energy up to 6kHz
         */
        private val WATER_TEMPLATE = floatArrayOf(
            -2f,   // 215 Hz: slightly below peak
             0f,   // 301 Hz: peak region
            -1f,   // 431 Hz: near peak
            -2f,   // 603 Hz: near peak
            -3f,   // 818 Hz: slight rolloff starts
            -5f,   // 1076 Hz: rolloff
            -8f,   // 1507 Hz: moderate rolloff
            -11f,  // 2024 Hz: continuing rolloff
            -14f,  // 2798 Hz: significant rolloff
            -18f,  // 3874 Hz: steep rolloff
            -22f,  // 5165 Hz: low but present
            -25f,  // 5984 Hz: near noise floor
        )
    }
}
