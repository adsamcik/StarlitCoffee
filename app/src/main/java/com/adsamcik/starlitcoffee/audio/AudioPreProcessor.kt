package com.adsamcik.starlitcoffee.audio

/**
 * Pre-processes raw PCM audio before spectral analysis.
 *
 * Pipeline:
 * 1. DC removal — subtracts running mean to eliminate ADC offset drift
 * 2. 150 Hz high-pass filter — 2nd-order Butterworth biquad removes fridge hum, HVAC rumble
 * 3. Ring buffer — maintains 50% overlap for consistent spectral flux computation
 *
 * Stateful: maintains filter state and ring buffer across calls.
 * Not thread-safe — call from a single coroutine.
 */
class AudioPreProcessor(
    private val fftSize: Int = 1024,
    sampleRate: Int = 44100,
    hpfCutoffHz: Float = 150f,
) {
    private val hopSize = fftSize / 2

    // DC removal state
    private var dcEstimate: Double = 0.0

    // Biquad HPF coefficients (precomputed for given cutoff and sample rate)
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double

    // Biquad filter state (Direct Form II Transposed)
    private var z1: Double = 0.0
    private var z2: Double = 0.0

    // Ring buffer for 50% overlap
    private val ringBuffer = ShortArray(fftSize)
    private var ringWritePos = 0
    private var samplesAccumulated = 0

    init {
        // 2nd-order Butterworth high-pass filter design
        val omega0 = 2.0 * Math.PI * hpfCutoffHz / sampleRate
        val cosOmega = kotlin.math.cos(omega0)
        val sinOmega = kotlin.math.sin(omega0)
        val alpha = sinOmega / (2.0 * BUTTERWORTH_Q)

        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosOmega) / 2.0) / a0
        b1 = (-(1.0 + cosOmega)) / a0
        b2 = ((1.0 + cosOmega) / 2.0) / a0
        a1 = (-2.0 * cosOmega) / a0
        a2 = (1.0 - alpha) / a0
    }

    /**
     * Processes a raw PCM buffer and returns 0, 1, or 2 overlapped frames
     * ready for FFT. Each frame is [fftSize] samples.
     *
     * @param samples raw PCM 16-bit mono buffer from AudioCaptureSession
     * @return list of filtered, overlapped frames (ShortArrays of length [fftSize])
     */
    fun process(samples: ShortArray): List<ShortArray> {
        val frames = mutableListOf<ShortArray>()
        val filtered = applyFilters(samples)

        for (sample in filtered) {
            ringBuffer[ringWritePos] = sample
            ringWritePos = (ringWritePos + 1) % fftSize
            samplesAccumulated++

            // Emit a frame every hopSize samples once we have a full fftSize window
            if (samplesAccumulated >= fftSize && (samplesAccumulated - fftSize) % hopSize == 0) {
                frames.add(extractFrame())
            }
        }

        return frames
    }

    /**
     * Resets all internal state (filter, ring buffer, DC estimate).
     * Call when starting a new monitoring session.
     */
    fun reset() {
        dcEstimate = 0.0
        z1 = 0.0
        z2 = 0.0
        ringBuffer.fill(0)
        ringWritePos = 0
        samplesAccumulated = 0
    }

    private fun applyFilters(samples: ShortArray): ShortArray {
        val output = ShortArray(samples.size)

        for (i in samples.indices) {
            val raw = samples[i].toDouble()

            // Stage 1: DC removal (running mean subtraction)
            dcEstimate += DC_ALPHA * (raw - dcEstimate)
            val dcRemoved = raw - dcEstimate

            // Stage 2: Butterworth HPF biquad (Direct Form II Transposed)
            val filtered = b0 * dcRemoved + z1
            z1 = b1 * dcRemoved - a1 * filtered + z2
            z2 = b2 * dcRemoved - a2 * filtered

            output[i] = filtered.toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }

        return output
    }

    private fun extractFrame(): ShortArray {
        val frame = ShortArray(fftSize)
        // Read fftSize samples ending at current write position
        val startPos = (ringWritePos - fftSize + ringBuffer.size) % ringBuffer.size
        for (i in 0 until fftSize) {
            frame[i] = ringBuffer[(startPos + i) % ringBuffer.size]
        }
        return frame
    }

    companion object {
        /** DC removal EMA coefficient — slow tracking to avoid affecting signal */
        private const val DC_ALPHA = 0.001

        /** Butterworth Q factor for critically damped response */
        private const val BUTTERWORTH_Q = 0.7071067811865476 // 1/√2
    }
}
