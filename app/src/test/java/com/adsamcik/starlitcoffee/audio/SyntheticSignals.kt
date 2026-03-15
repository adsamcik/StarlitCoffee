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

    /**
     * Bubble-resonance drip transient — research-supported drip model.
     *
     * The airborne "plink" of a drop hitting a liquid surface is driven by
     * resonant oscillation of an entrained air bubble, with dominant frequency
     * near 8.66 kHz (Phillips et al. 2018). The waveform is a delayed decaying
     * wave-packet initiated at bubble pinch-off.
     *
     * @param durationMs total duration of the transient (typically 10-30ms)
     * @param frequencyHz bubble resonance frequency (default 8660 Hz from literature)
     * @param amplitude peak amplitude (0.0-1.0)
     * @param decayRate exponential decay rate (higher = faster decay)
     */
    fun bubbleResonanceDrip(
        durationMs: Int = 15,
        frequencyHz: Double = 8660.0,
        amplitude: Double = 0.4,
        decayRate: Double = 0.15,
        sampleRate: Int = 44100,
    ): ShortArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        // 2ms onset delay (bubble pinch-off), then decaying oscillation
        val delaySamples = (sampleRate * 0.002).toInt()
        return ShortArray(numSamples) { i ->
            if (i < delaySamples) {
                0 // Silent before bubble pinch-off
            } else {
                val t = (i - delaySamples).toDouble() / sampleRate
                val decay = exp(-t / (durationMs * 0.001 * decayRate))
                val sample = decay * amplitude * sin(2.0 * PI * frequencyHz * t)
                (sample * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }

    /**
     * Non-monotonic drip sequence with plateaus and local rebounds.
     *
     * Research: drip rate decay is "conditionally supported" — real drawdown has
     * regime changes, fines-driven stalls, channel-opening rebounds, and a
     * transition from continuous flow to discrete dripping.
     *
     * @param durationMs total sequence duration
     * @param initialIntervalMs starting interval between drips
     * @param finalIntervalMs ending interval (longer = slower drips)
     * @param plateauAtMs time (ms) where a plateau/rebound occurs
     * @param plateauDurationMs how long the plateau lasts
     */
    fun nonMonotonicDripSequence(
        durationMs: Int = 30000,
        initialIntervalMs: Int = 300,
        finalIntervalMs: Int = 2000,
        plateauAtMs: Int = 15000,
        plateauDurationMs: Int = 5000,
        dripAmplitude: Double = 0.3,
        sampleRate: Int = 44100,
    ): ShortArray {
        val totalSamples = (sampleRate * durationMs / 1000.0).toInt()
        val buffer = ShortArray(totalSamples)

        var pos = 0
        while (pos < totalSamples) {
            val timeMs = (pos.toDouble() / sampleRate * 1000).toInt()

            // Compute current interval with plateau/rebound
            val interval = when {
                timeMs in plateauAtMs..(plateauAtMs + plateauDurationMs) -> {
                    // Plateau: interval temporarily shortens (rebound)
                    val baseInterval = lerp(initialIntervalMs, finalIntervalMs, timeMs.toDouble() / durationMs)
                    (baseInterval * 0.7).toInt() // 30% faster during plateau
                }
                else -> {
                    lerp(initialIntervalMs, finalIntervalMs, timeMs.toDouble() / durationMs).toInt()
                }
            }

            // Insert a bubble-resonance drip transient
            val drip = bubbleResonanceDrip(
                durationMs = 15,
                amplitude = dripAmplitude,
                sampleRate = sampleRate,
            )
            for (j in drip.indices) {
                if (pos + j < totalSamples) {
                    buffer[pos + j] = (buffer[pos + j] + drip[j]).toShort()
                }
            }

            pos += (sampleRate * interval / 1000.0).toInt()
        }
        return buffer
    }

    /**
     * Gradual pour onset — models a slow, gentle pour start.
     *
     * Unlike a sudden splash, some pours start as a thin stream that gradually
     * widens. The broadband energy ramps up over several seconds.
     *
     * @param rampDurationMs how long the onset takes to reach full level
     * @param fullDurationMs duration at full level after ramp
     * @param amplitude final amplitude
     */
    fun softPourOnset(
        rampDurationMs: Int = 3000,
        fullDurationMs: Int = 5000,
        amplitude: Double = 0.3,
        sampleRate: Int = 44100,
    ): ShortArray {
        val random = java.util.Random(99)
        val rampSamples = (sampleRate * rampDurationMs / 1000.0).toInt()
        val fullSamples = (sampleRate * fullDurationMs / 1000.0).toInt()
        val total = rampSamples + fullSamples
        return ShortArray(total) { i ->
            val envelope = if (i < rampSamples) {
                i.toDouble() / rampSamples // linear ramp 0→1
            } else {
                1.0
            }
            val noise = (random.nextDouble() * 2.0 - 1.0) * amplitude * envelope
            (noise * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Regime change from continuous flow to discrete dripping.
     *
     * Research identifies a hidden regime switch: continuous-flow acoustics
     * (broadband noise) transitions to discrete-drip acoustics (transient events).
     * This is a key feature for robust detection.
     */
    fun regimeChangeSequence(
        continuousDurationMs: Int = 10000,
        transitionDurationMs: Int = 3000,
        dripDurationMs: Int = 15000,
        pourAmplitude: Double = 0.3,
        dripAmplitude: Double = 0.3,
        sampleRate: Int = 44100,
    ): ShortArray {
        val continuous = whiteNoise(continuousDurationMs, pourAmplitude, sampleRate)

        // Transition: fading broadband + emerging drips
        val transitionSamples = (sampleRate * transitionDurationMs / 1000.0).toInt()
        val transition = ShortArray(transitionSamples)
        val random = java.util.Random(77)
        val dripInterval = (sampleRate * 0.5).toInt() // 2 drips/sec during transition
        for (i in transition.indices) {
            val fade = 1.0 - i.toDouble() / transitionSamples
            val noise = (random.nextDouble() * 2.0 - 1.0) * pourAmplitude * fade
            transition[i] = (noise * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        // Add drips to transition
        var pos = dripInterval
        while (pos < transitionSamples) {
            val drip = bubbleResonanceDrip(amplitude = dripAmplitude * 0.5, sampleRate = sampleRate)
            for (j in drip.indices) {
                if (pos + j < transitionSamples) {
                    transition[pos + j] = (transition[pos + j] + drip[j]).toShort()
                }
            }
            pos += dripInterval
        }

        // Pure dripping phase (using bubble-resonance drips with non-monotonic intervals)
        val drips = nonMonotonicDripSequence(
            durationMs = dripDurationMs,
            initialIntervalMs = 500,
            finalIntervalMs = 2000,
            plateauAtMs = dripDurationMs / 2,
            plateauDurationMs = 3000,
            dripAmplitude = dripAmplitude,
            sampleRate = sampleRate,
        )

        return concatenate(continuous, transition, drips)
    }

    private fun lerp(a: Int, b: Int, t: Double): Double {
        return a + (b - a) * t.coerceIn(0.0, 1.0)
    }
}
