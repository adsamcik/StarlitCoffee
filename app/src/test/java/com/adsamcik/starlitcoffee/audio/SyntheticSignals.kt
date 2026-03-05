package com.adsamcik.starlitcoffee.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministic synthetic audio signal generators for testing [AudioAnalyzer].
 *
 * All generators produce PCM 16-bit mono buffers at the specified sample rate.
 * Random generators use fixed seeds for reproducibility across test runs.
 */
object SyntheticSignals {

    /**
     * Pure sine wave — models tonal components (e.g., appliance hum, notification tones).
     *
     * @param frequencyHz fundamental frequency
     * @param durationMs signal length in milliseconds
     * @param amplitude normalized amplitude 0.0–1.0 (1.0 = 0 dBFS)
     * @param sampleRate samples per second
     */
    fun sineWave(
        frequencyHz: Double,
        durationMs: Int,
        amplitude: Double = 0.5,
        sampleRate: Int = 44100,
    ): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        return ShortArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2.0 * PI * frequencyHz * t) * amplitude * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * White noise — models broadband signals like pouring water (200–4000 Hz energy).
     *
     * @param seed deterministic random seed for reproducibility
     */
    fun whiteNoise(
        durationMs: Int,
        amplitude: Double = 0.3,
        sampleRate: Int = 44100,
        seed: Int = 42,
    ): ShortArray {
        val random = Random(seed)
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        return ShortArray(numSamples) {
            ((random.nextDouble() * 2.0 - 1.0) * amplitude * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Digital silence — zero-valued samples.
     */
    fun silence(durationMs: Int, sampleRate: Int = 44100): ShortArray {
        return ShortArray((sampleRate * durationMs / 1000.0).toInt())
    }

    /**
     * Impulse train modeling periodic drip impacts.
     * Each drip is a short burst (~5–15 ms) with exponential decay at ~2 kHz
     * carrier frequency, approximating the "tick" sound of a water droplet
     * hitting a liquid surface.
     *
     * @param durationMs total length of the drip train
     * @param dripIntervalMs time between drip impacts (faster = denser dripping)
     * @param dripDurationMs length of each impact transient
     * @param dripAmplitude peak amplitude of each drip (0.0–1.0)
     */
    fun dripTrain(
        durationMs: Int,
        dripIntervalMs: Int = 500,
        dripDurationMs: Int = 10,
        dripAmplitude: Double = 0.4,
        sampleRate: Int = 44100,
    ): ShortArray {
        val totalSamples = (sampleRate * durationMs / 1000.0).toInt()
        val buffer = ShortArray(totalSamples)
        val intervalSamples = (sampleRate * dripIntervalMs / 1000.0).toInt()
        val dripSamples = (sampleRate * dripDurationMs / 1000.0).toInt()
        val carrierHz = 2000.0

        var pos = 0
        while (pos < totalSamples) {
            for (j in 0 until dripSamples.coerceAtMost(totalSamples - pos)) {
                val decay = exp(-j.toDouble() / (dripSamples * 0.3))
                val sample = decay * dripAmplitude * Short.MAX_VALUE *
                    sin(2.0 * PI * carrierHz * j / sampleRate)
                buffer[pos + j] = sample.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            pos += intervalSamples
        }
        return buffer
    }

    /**
     * Concatenate multiple signal segments into a single buffer.
     * Use to build complete brew scenarios from individual phases.
     */
    fun concatenate(vararg segments: ShortArray): ShortArray {
        val total = segments.sumOf { it.size }
        val result = ShortArray(total)
        var offset = 0
        for (segment in segments) {
            segment.copyInto(result, offset)
            offset += segment.size
        }
        return result
    }

    /**
     * Overlay additive noise onto an existing signal.
     *
     * @param signal original signal
     * @param noiseAmplitude amplitude of added noise (0.0–1.0)
     * @param seed deterministic random seed
     */
    fun addNoise(
        signal: ShortArray,
        noiseAmplitude: Double = 0.05,
        seed: Int = 123,
    ): ShortArray {
        val random = Random(seed)
        return ShortArray(signal.size) { i ->
            val noise = (random.nextDouble() * 2.0 - 1.0) * noiseAmplitude * Short.MAX_VALUE
            (signal[i] + noise.toInt())
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
