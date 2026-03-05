package com.adsamcik.starlitcoffee.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure-Kotlin radix-2 FFT engine with precomputed twiddle factors.
 * No Android dependencies — fully testable.
 *
 * Supports only power-of-2 sizes. Default size is 1024 (matching AudioCaptureSession buffer).
 * Includes Hann window generation and magnitude/power spectrum computation.
 */
class FftEngine(val size: Int = DEFAULT_FFT_SIZE) {

    init {
        require(size > 0 && size and (size - 1) == 0) {
            "FFT size must be a positive power of 2, got $size"
        }
    }

    /** Precomputed Hann window coefficients */
    val hannWindow: FloatArray = FloatArray(size) { n ->
        (0.5 * (1.0 - cos(2.0 * PI * n / (size - 1)))).toFloat()
    }

    /** Hann window power normalization factor: 1 / (Σ w[n]² / N) ≈ 2.667 for Hann */
    val hannPowerNormalization: Float = run {
        var sumSq = 0.0
        for (w in hannWindow) sumSq += w.toDouble() * w
        (size.toDouble() / sumSq).toFloat()
    }

    // Precomputed twiddle factors for each FFT stage
    private val twiddleReal: Array<FloatArray>
    private val twiddleImag: Array<FloatArray>

    // Bit-reversal permutation table
    private val bitReversalTable: IntArray

    init {
        // Compute bit-reversal table
        val logN = Integer.numberOfTrailingZeros(size)
        bitReversalTable = IntArray(size) { i ->
            var reversed = 0
            var value = i
            for (bit in 0 until logN) {
                reversed = (reversed shl 1) or (value and 1)
                value = value shr 1
            }
            reversed
        }

        // Precompute twiddle factors for each stage
        val numStages = logN
        twiddleReal = Array(numStages) { stage ->
            val halfBlock = 1 shl stage
            FloatArray(halfBlock) { k ->
                cos(-PI * k / halfBlock).toFloat()
            }
        }
        twiddleImag = Array(numStages) { stage ->
            val halfBlock = 1 shl stage
            FloatArray(halfBlock) { k ->
                sin(-PI * k / halfBlock).toFloat()
            }
        }
    }

    /**
     * Applies Hann window to PCM samples in-place into the output buffer.
     * Normalizes Short samples to Float [-1.0, 1.0] range simultaneously.
     */
    fun applyHannWindow(samples: ShortArray, offset: Int, count: Int, output: FloatArray) {
        val limit = count.coerceAtMost(size)
        for (i in 0 until limit) {
            output[i] = (samples[offset + i].toFloat() / Short.MAX_VALUE) * hannWindow[i]
        }
        // Zero-fill if count < size
        for (i in limit until size) {
            output[i] = 0f
        }
    }

    /**
     * Computes in-place complex FFT.
     *
     * @param real real parts (modified in-place, length = [size])
     * @param imag imaginary parts (modified in-place, length = [size])
     */
    fun fft(real: FloatArray, imag: FloatArray) {
        require(real.size >= size && imag.size >= size) {
            "Arrays must be at least size $size"
        }

        // Bit-reversal permutation
        for (i in 0 until size) {
            val j = bitReversalTable[i]
            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
        }

        // Butterfly stages
        val logN = Integer.numberOfTrailingZeros(size)
        for (stage in 0 until logN) {
            val halfBlock = 1 shl stage
            val blockSize = halfBlock shl 1
            val twRe = twiddleReal[stage]
            val twIm = twiddleImag[stage]

            var blockStart = 0
            while (blockStart < size) {
                for (k in 0 until halfBlock) {
                    val evenIdx = blockStart + k
                    val oddIdx = evenIdx + halfBlock

                    val tRe = twRe[k] * real[oddIdx] - twIm[k] * imag[oddIdx]
                    val tIm = twRe[k] * imag[oddIdx] + twIm[k] * real[oddIdx]

                    real[oddIdx] = real[evenIdx] - tRe
                    imag[oddIdx] = imag[evenIdx] - tIm
                    real[evenIdx] = real[evenIdx] + tRe
                    imag[evenIdx] = imag[evenIdx] + tIm
                }
                blockStart += blockSize
            }
        }
    }

    /**
     * Computes power spectrum |X_k|² for bins 0..N/2.
     *
     * @param real real part of FFT output
     * @param imag imaginary part of FFT output
     * @param output power spectrum array (length N/2 + 1)
     */
    fun powerSpectrum(real: FloatArray, imag: FloatArray, output: FloatArray) {
        val halfSize = size / 2
        for (k in 0..halfSize) {
            output[k] = real[k] * real[k] + imag[k] * imag[k]
        }
    }

    /**
     * Computes magnitude spectrum |X_k| for bins 0..N/2.
     */
    fun magnitudeSpectrum(real: FloatArray, imag: FloatArray, output: FloatArray) {
        val halfSize = size / 2
        for (k in 0..halfSize) {
            output[k] = kotlin.math.sqrt(real[k] * real[k] + imag[k] * imag[k])
        }
    }

    companion object {
        const val DEFAULT_FFT_SIZE = 1024
    }
}
