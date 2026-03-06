package com.adsamcik.starlitcoffee.audio

import android.media.AudioAttributes
import androidx.annotation.VisibleForTesting
import android.util.Log
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Active Acoustic Probe — genuinely novel approach to water detection.
 *
 * Emits a near-ultrasonic continuous tone (default 18kHz) through the phone speaker
 * at low volume. When water flows between speaker and mic, the turbulent stream
 * scatters the acoustic probe signal, causing amplitude modulation at the probe
 * frequency. We detect this modulation as a "turbulence score."
 *
 * Physics: a laminar water stream acts as a time-varying acoustic scatterer.
 * The probe tone's received amplitude fluctuates with the turbulence's temporal
 * structure — typically 5-50Hz modulation from drop impacts and flow instabilities.
 *
 * Advantages:
 * - Physically immune to ambient noise (music, speech, fan) which doesn't
 *   modulate the specific probe frequency
 * - Works in any noise level — active sensing vs passive listening
 * - Unique acoustic fingerprint: only water/turbulence near the phone causes
 *   rapid amplitude flutter at exactly the probe frequency
 *
 * Limitations:
 * - Speaker/mic frequency response varies by device at 18kHz
 * - Young ears may hear the tone (though at low amplitude, ~60dB SPL)
 * - Phone orientation matters (speaker must face toward brewer)
 *
 * Usage:
 * 1. Call [start] to begin emitting the probe tone
 * 2. Feed FFT power spectrum frames via [analyzeProbeResponse]
 * 3. Read [turbulenceScore] — high values indicate water turbulence
 * 4. Call [stop] to silence the probe
 *
 * This component is experimental and off by default.
 */
class ActiveProbe(
    /** Probe frequency in Hz. 18kHz is inaudible to most adults. */
    private val probeFrequencyHz: Int = DEFAULT_PROBE_FREQ,
    /** Sample rate — must match capture session */
    private val sampleRate: Int = 44100,
    /** Probe amplitude (0.0-1.0). Low to minimize audibility. */
    private val amplitude: Float = DEFAULT_AMPLITUDE,
    /** FFT size used by the analyzer — needed for bin calculation */
    private val fftSize: Int = 1024,
) {
    /** Whether the probe tone is currently emitting */
    var isActive: Boolean = false
        private set

    /** Current turbulence score (0 = calm, higher = more turbulence).
     *  Based on coefficient of variation of probe bin magnitude. */
    var turbulenceScore: Float = 0f
        private set

    /** Raw probe bin magnitude (for debug display) */
    var probeBinMagnitude: Float = 0f
        private set

    private var audioTrack: AudioTrack? = null

    // Probe bin in FFT
    private val probeBin: Int = (probeFrequencyHz.toFloat() / (sampleRate.toFloat() / fftSize)).toInt()

    // Rolling window of probe bin magnitudes for variance estimation
    private val probeHistory = FloatArray(HISTORY_SIZE)
    private var historyPos = 0
    private var historyCount = 0

    /**
     * Starts emitting the probe tone through the speaker.
     * Call from a thread that can do audio I/O.
     */
    fun start() {
        if (isActive) return

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        // Generate one period of samples, then loop
        val periodSamples = sampleRate / probeFrequencyHz
        // Use at least bufferSize samples for smooth playback
        val numSamples = maxOf(bufferSize / 2, periodSamples * 10)
        val buffer = ShortArray(numSamples)

        for (i in buffer.indices) {
            val t = i.toDouble() / sampleRate
            val sample = amplitude * sin(2.0 * PI * probeFrequencyHz * t)
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.setLoopPoints(0, buffer.size, -1) // Loop forever
        track.play()

        audioTrack = track
        isActive = true
        resetHistory()
    }

    /**
     * Stops the probe tone.
     */
    fun stop() {
        audioTrack?.let { track ->
            try {
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop audio probe", e)
            }
        }
        audioTrack = null
        isActive = false
        turbulenceScore = 0f
        probeBinMagnitude = 0f
        resetHistory()
    }

    /**
     * Analyzes the FFT power spectrum for probe signal modulation.
     *
     * Call this once per FFT frame with the power spectrum from [SpectralAnalyzer].
     * The probe bin's magnitude is tracked over time; its variance indicates
     * whether turbulence is modulating the probe signal.
     *
     * @param powerSpectrum power spectrum array (N/2 + 1 bins)
     * @return turbulence score for this frame
     */
    fun analyzeProbeResponse(powerSpectrum: FloatArray): Float {
        if (!isActive) return 0f
        if (probeBin >= powerSpectrum.size) return 0f

        // Extract probe bin magnitude (use a small neighborhood for robustness)
        val lo = maxOf(0, probeBin - 1)
        val hi = minOf(powerSpectrum.size - 1, probeBin + 1)
        var maxMag = 0f
        for (k in lo..hi) {
            val mag = sqrt(powerSpectrum[k])
            if (mag > maxMag) maxMag = mag
        }
        probeBinMagnitude = maxMag

        // Add to rolling history
        probeHistory[historyPos] = maxMag
        historyPos = (historyPos + 1) % HISTORY_SIZE
        if (historyCount < HISTORY_SIZE) historyCount++

        // Compute coefficient of variation (std / mean)
        // High CV = probe signal is being modulated = water turbulence
        turbulenceScore = if (historyCount >= MIN_HISTORY) {
            val mean = computeMean()
            if (mean < PROBE_MIN_MAGNITUDE) {
                // Probe not detected (speaker too quiet or frequency not supported)
                0f
            } else {
                val stdDev = computeStdDev(mean)
                (stdDev / mean).coerceIn(0f, MAX_TURBULENCE_SCORE)
            }
        } else {
            0f
        }

        return turbulenceScore
    }

    private fun computeMean(): Float {
        var sum = 0.0
        for (i in 0 until historyCount) sum += probeHistory[i]
        return (sum / historyCount).toFloat()
    }

    private fun computeStdDev(mean: Float): Float {
        var sumSq = 0.0
        for (i in 0 until historyCount) {
            val diff = probeHistory[i] - mean
            sumSq += diff * diff
        }
        return sqrt(sumSq / historyCount).toFloat()
    }

    private fun resetHistory() {
        probeHistory.fill(0f)
        historyPos = 0
        historyCount = 0
    }

    @VisibleForTesting
    internal fun setActiveForTesting(active: Boolean) {
        isActive = active
    }

    companion object {
        private const val TAG = "ActiveProbe"

        /** Default probe frequency: 18kHz (inaudible to most adults >25y) */
        const val DEFAULT_PROBE_FREQ = 18000

        /** Low amplitude to minimize audibility (~-20dBFS) */
        const val DEFAULT_AMPLITUDE = 0.1f

        /** Rolling window size: ~0.5s at 86fps */
        private const val HISTORY_SIZE = 43

        /** Minimum frames before computing turbulence */
        private const val MIN_HISTORY = 10

        /** Minimum probe magnitude to consider the probe "detected" */
        private const val PROBE_MIN_MAGNITUDE = 0.001f

        /** Cap turbulence score */
        private const val MAX_TURBULENCE_SCORE = 5.0f
    }
}
