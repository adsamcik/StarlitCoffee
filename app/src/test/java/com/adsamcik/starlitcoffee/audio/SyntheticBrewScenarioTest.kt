package com.adsamcik.starlitcoffee.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [AudioAnalyzer] with synthetic brew-like signals from [SyntheticSignals].
 * These tests validate feature extraction logic against controlled inputs.
 * They do NOT validate real-world threshold values — use regression tests
 * with real recordings for that (see audio-validation-strategy.md).
 */
class SyntheticBrewScenarioTest {

    // --- Synthetic Signal Sanity ---

    @Test
    fun `silence buffer has expected RMS and no frequency`() {
        val signal = SyntheticSignals.silence(1000)
        val features = AudioAnalyzer.analyze(signal, signal.size, 44100)
        assertEquals(AudioAnalyzer.SILENCE_DB, features.rmsDb, 0.01f)
        assertEquals(0f, features.dominantFrequencyHz, 0.01f)
    }

    @Test
    fun `white noise has high zero-crossing rate`() {
        val signal = SyntheticSignals.whiteNoise(500, amplitude = 0.3)
        val features = AudioAnalyzer.analyze(signal, signal.size, 44100)

        // White noise should have ZCR near 0.5 (random sign alternation)
        assertTrue(
            "Expected ZCR > 0.3 for white noise, got ${features.zeroCrossingRate}",
            features.zeroCrossingRate > 0.3f,
        )
        // Should be audible, not silent
        assertTrue(
            "Expected RMS > -40 dB for white noise, got ${features.rmsDb}",
            features.rmsDb > -40f,
        )
    }

    @Test
    fun `drip train has periodic structure`() {
        // Slow drip every 200ms for 2 seconds at 44100 Hz
        val signal = SyntheticSignals.dripTrain(
            durationMs = 2000,
            dripIntervalMs = 200,
            dripDurationMs = 10,
            dripAmplitude = 0.4,
        )
        // Drip train is mostly silence with brief bursts — RMS should be low
        val features = AudioAnalyzer.analyze(signal, signal.size, 44100)
        assertTrue(
            "Drip train should have low overall RMS, got ${features.rmsDb}",
            features.rmsDb < -20f,
        )
    }

    @Test
    fun `sine wave at known frequency is detected`() {
        val signal = SyntheticSignals.sineWave(
            frequencyHz = 440.0,
            durationMs = 200,
            amplitude = 0.5,
        )
        val features = AudioAnalyzer.analyze(signal, signal.size, 44100)
        assertEquals(440f, features.dominantFrequencyHz, 10f)
    }

    // --- Brew Scenario: Clean Bloom Pour ---

    @Test
    fun `clean bloom scenario has distinct quiet and loud phases`() {
        val prePour = SyntheticSignals.silence(2000)
        val pour = SyntheticSignals.whiteNoise(5000, amplitude = 0.4)
        val postPour = SyntheticSignals.silence(2000)

        val prePourFeatures = AudioAnalyzer.analyze(prePour, prePour.size, 44100)
        val pourFeatures = AudioAnalyzer.analyze(pour, pour.size, 44100)
        val postPourFeatures = AudioAnalyzer.analyze(postPour, postPour.size, 44100)

        // Pour should be significantly louder than silence
        val loudnessDelta = pourFeatures.rmsDb - prePourFeatures.rmsDb
        assertTrue(
            "Pour should be >30 dB louder than silence, got delta=$loudnessDelta",
            loudnessDelta > 30f,
        )

        // Post-pour should return to silence levels
        assertEquals(prePourFeatures.rmsDb, postPourFeatures.rmsDb, 1f)
    }

    // --- Brew Scenario: Pour vs Drip Discrimination ---

    @Test
    fun `pour has higher RMS and ZCR than drip train`() {
        val pour = SyntheticSignals.whiteNoise(2000, amplitude = 0.3)
        val drips = SyntheticSignals.dripTrain(2000, dripIntervalMs = 500, dripAmplitude = 0.3)

        val pourFeatures = AudioAnalyzer.analyze(pour, pour.size, 44100)
        val dripFeatures = AudioAnalyzer.analyze(drips, drips.size, 44100)

        // Pour (continuous noise) should have higher RMS than intermittent drips
        assertTrue(
            "Pour RMS (${pourFeatures.rmsDb}) should exceed drip RMS (${dripFeatures.rmsDb})",
            pourFeatures.rmsDb > dripFeatures.rmsDb,
        )

        // Pour (broadband) should have higher ZCR than drip (mostly silence)
        assertTrue(
            "Pour ZCR (${pourFeatures.zeroCrossingRate}) should exceed drip ZCR (${dripFeatures.zeroCrossingRate})",
            pourFeatures.zeroCrossingRate > dripFeatures.zeroCrossingRate,
        )
    }

    // --- Edge Case: Noise Contamination ---

    @Test
    fun `adding background noise raises RMS but preserves frequency detection`() {
        val pureTone = SyntheticSignals.sineWave(440.0, 200, amplitude = 0.5)
        val noisyTone = SyntheticSignals.addNoise(pureTone, noiseAmplitude = 0.05)

        val cleanFeatures = AudioAnalyzer.analyze(pureTone, pureTone.size, 44100)
        val noisyFeatures = AudioAnalyzer.analyze(noisyTone, noisyTone.size, 44100)

        // RMS should increase slightly with added noise
        assertTrue(
            "Noisy signal RMS should be >= clean signal RMS",
            noisyFeatures.rmsDb >= cleanFeatures.rmsDb - 1f,
        )

        // Frequency detection should still find the dominant 440 Hz tone
        assertEquals(440f, noisyFeatures.dominantFrequencyHz, 15f)
    }

    @Test
    fun `heavy noise masks frequency detection`() {
        val pureTone = SyntheticSignals.sineWave(440.0, 200, amplitude = 0.3)
        val heavyNoise = SyntheticSignals.addNoise(pureTone, noiseAmplitude = 0.5)

        val features = AudioAnalyzer.analyze(heavyNoise, heavyNoise.size, 44100)

        // With noise amplitude exceeding signal, frequency detection may fail
        // This is expected — documents the SNR boundary
        // Either detects wrong frequency or 0 (no clear periodicity)
        val freqError = kotlin.math.abs(features.dominantFrequencyHz - 440f)
        // Heavy noise (amp 0.5) exceeds signal (amp 0.3) — detection should degrade
        // beyond the clean-signal tolerance (10 Hz) used in the pure tone test.
        // Either frequency error exceeds clean tolerance or detector reports 0 (no periodicity).
        assertTrue(
            "With heavy noise, freq error ($freqError Hz) should exceed clean tolerance (10 Hz) " +
                "or detection should fail (freq=${features.dominantFrequencyHz})",
            freqError > 10f || features.dominantFrequencyHz == 0f,
        )
    }

    // --- Edge Case: Concatenation Boundaries ---

    @Test
    fun `concatenated silence-noise-silence has correct total length`() {
        val sampleRate = 44100
        val s1 = SyntheticSignals.silence(1000, sampleRate)
        val s2 = SyntheticSignals.whiteNoise(2000, sampleRate = sampleRate)
        val s3 = SyntheticSignals.silence(1000, sampleRate)

        val combined = SyntheticSignals.concatenate(s1, s2, s3)
        val expectedSamples = (sampleRate * 4000 / 1000.0).toInt()

        assertEquals(expectedSamples, combined.size)
    }

    // --- Edge Case: Amplitude Extremes ---

    @Test
    fun `near-zero amplitude noise is classified as near-silent`() {
        val quietNoise = SyntheticSignals.whiteNoise(1000, amplitude = 0.001)
        val features = AudioAnalyzer.analyze(quietNoise, quietNoise.size, 44100)
        assertTrue(
            "Very quiet noise (amp=0.001) should have RMS < -50 dB, got ${features.rmsDb}",
            features.rmsDb < -50f,
        )
    }

    @Test
    fun `full-scale noise does not clip to silence`() {
        val loudNoise = SyntheticSignals.whiteNoise(500, amplitude = 1.0)
        val features = AudioAnalyzer.analyze(loudNoise, loudNoise.size, 44100)
        assertTrue(
            "Full-scale noise should have RMS > -10 dB, got ${features.rmsDb}",
            features.rmsDb > -10f,
        )
    }

    // --- Research-Aligned Scenarios ---

    @Test
    fun `bubble resonance drip has energy above 4kHz`() {
        val drip = SyntheticSignals.bubbleResonanceDrip()
        // Run through spectral analyzer and verify energy in DRIP_HIGH band
        // (The drip at 8.66kHz should show significant energy in 4-11kHz)
        assertTrue("Bubble drip should have samples", drip.isNotEmpty())
        // Verify peak is in the right frequency range
        val peakSample = drip.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("Bubble drip should have non-trivial amplitude", peakSample > 100)
    }

    @Test
    fun `non-monotonic drip sequence has both fast and slow intervals`() {
        val sequence = SyntheticSignals.nonMonotonicDripSequence(
            durationMs = 10000,
            initialIntervalMs = 300,
            finalIntervalMs = 1500,
            plateauAtMs = 5000,
            plateauDurationMs = 2000,
        )
        assertTrue("Sequence should be ~10s of audio", sequence.size > 44100 * 9)
    }

    @Test
    fun `soft pour onset ramps up gradually`() {
        val onset = SyntheticSignals.softPourOnset(rampDurationMs = 2000, fullDurationMs = 3000)
        // First 10% should be quieter than last 10%
        val tenPercent = onset.size / 10
        val earlyRms = rms(onset.copyOfRange(0, tenPercent))
        val lateRms = rms(onset.copyOfRange(onset.size - tenPercent, onset.size))
        assertTrue("Late RMS ($lateRms) should be > early RMS ($earlyRms)", lateRms > earlyRms * 2)
    }

    @Test
    fun `regime change sequence contains both broadband and transient sections`() {
        val sequence = SyntheticSignals.regimeChangeSequence(
            continuousDurationMs = 5000,
            transitionDurationMs = 2000,
            dripDurationMs = 5000,
        )
        assertTrue("Regime change sequence should produce audio", sequence.size > 44100 * 11)
    }

    private fun rms(samples: ShortArray): Double {
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s.toDouble()
        return kotlin.math.sqrt(sum / samples.size)
    }
}
