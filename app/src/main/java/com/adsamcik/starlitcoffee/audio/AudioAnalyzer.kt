package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.AudioFeatures
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Pure-computation audio analysis engine. No Android dependencies — fully testable.
 * Accepts raw PCM 16-bit mono buffers and returns [AudioFeatures].
 */
object AudioAnalyzer {

    /**
     * Analyzes a PCM 16-bit mono buffer and returns extracted features.
     *
     * @param samples PCM samples (Short range: -32768..32767)
     * @param count number of valid samples in the array
     * @param sampleRate sample rate in Hz (needed for frequency estimation)
     */
    fun analyze(samples: ShortArray, count: Int = samples.size, sampleRate: Int = 44100): AudioFeatures {
        if (count == 0) {
            return AudioFeatures(
                rmsDb = SILENCE_DB,
                peakDb = SILENCE_DB,
                zeroCrossingRate = 0f,
                dominantFrequencyHz = 0f,
            )
        }

        val rmsDb = computeRmsDb(samples, count)
        val peakDb = computePeakDb(samples, count)
        val zcr = computeZeroCrossingRate(samples, count)
        val dominantFreq = estimateDominantFrequency(samples, count, sampleRate)

        return AudioFeatures(
            rmsDb = rmsDb,
            peakDb = peakDb,
            zeroCrossingRate = zcr,
            dominantFrequencyHz = dominantFreq,
        )
    }

    /**
     * Computes RMS (Root Mean Square) amplitude in dBFS.
     * Reference: 0 dBFS = Short.MAX_VALUE (32767).
     */
    fun computeRmsDb(samples: ShortArray, count: Int = samples.size): Float {
        if (count == 0) return SILENCE_DB

        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = samples[i].toDouble() / Short.MAX_VALUE
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count)
        return if (rms < EPSILON) SILENCE_DB else (20.0 * log10(rms)).toFloat().coerceAtLeast(SILENCE_DB)
    }

    /**
     * Computes peak amplitude in dBFS.
     */
    fun computePeakDb(samples: ShortArray, count: Int = samples.size): Float {
        if (count == 0) return SILENCE_DB

        var maxAbs = 0
        for (i in 0 until count) {
            val absVal = abs(samples[i].toInt())
            if (absVal > maxAbs) maxAbs = absVal
        }
        val normalized = maxAbs.toDouble() / Short.MAX_VALUE
        return if (normalized < EPSILON) SILENCE_DB else (20.0 * log10(normalized)).toFloat().coerceAtLeast(SILENCE_DB)
    }

    /**
     * Computes zero-crossing rate: fraction of adjacent samples that cross zero.
     * High ZCR ≈ noisy/high-frequency content, low ZCR ≈ tonal/silence.
     */
    fun computeZeroCrossingRate(samples: ShortArray, count: Int = samples.size): Float {
        if (count < 2) return 0f

        var crossings = 0
        for (i in 1 until count) {
            // Sign-change detection: previous sample on one side of zero,
            // current on the other. The two clauses are clearer than a
            // single XOR-of-sign-bits expression.
            @Suppress("ComplexCondition")
            val crossed = (samples[i] >= 0 && samples[i - 1] < 0) ||
                (samples[i] < 0 && samples[i - 1] >= 0)
            if (crossed) crossings++
        }
        return crossings.toFloat() / (count - 1)
    }

    /**
     * Estimates dominant frequency via autocorrelation.
     * Looks for the first significant peak in the autocorrelation function
     * after the initial zero-lag peak. Suitable for detecting periodic signals
     * like dripping (1–10 Hz repetition) or tonal content.
     *
     * @return estimated frequency in Hz, or 0 if no clear periodicity detected
     */
    fun estimateDominantFrequency(
        samples: ShortArray,
        count: Int = samples.size,
        sampleRate: Int = 44100,
    ): Float {
        if (count < MIN_SAMPLES_FOR_FREQUENCY) return 0f

        // Limit search range: 50 Hz to sampleRate/2
        val minLag = sampleRate / MAX_FREQUENCY
        val maxLag = (sampleRate / MIN_FREQUENCY).coerceAtMost(count / 2)

        if (minLag >= maxLag) return 0f

        // Compute zero-lag energy for normalization
        var zeroLagEnergy = 0.0
        for (i in 0 until count) {
            zeroLagEnergy += samples[i].toDouble() * samples[i].toDouble()
        }
        if (zeroLagEnergy < EPSILON) return 0f

        // Compute normalized autocorrelation and find first peak above threshold.
        // "First peak" approach avoids sub-harmonic false matches that plague
        // global-max autocorrelation.
        val acf = DoubleArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            for (i in 0 until (count - lag)) {
                correlation += samples[i].toDouble() * samples[i + lag].toDouble()
            }
            acf[lag] = correlation / zeroLagEnergy
        }

        // Find first local maximum that exceeds the threshold
        for (lag in (minLag + 1) until maxLag) {
            if (acf[lag] > AUTOCORRELATION_THRESHOLD &&
                acf[lag] >= acf[lag - 1] &&
                acf[lag] >= acf[lag + 1]
            ) {
                return sampleRate.toFloat() / lag
            }
        }

        return 0f
    }

    const val SILENCE_DB = -96f
    private const val EPSILON = 1e-10
    private const val MIN_FREQUENCY = 50
    private const val MAX_FREQUENCY = 8000
    private const val MIN_SAMPLES_FOR_FREQUENCY = 256
    private const val AUTOCORRELATION_THRESHOLD = 0.3
}
