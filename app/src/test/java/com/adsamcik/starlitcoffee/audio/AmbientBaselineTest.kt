package com.adsamcik.starlitcoffee.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AmbientBaselineTest {

    private lateinit var baseline: AmbientBaseline
    private val spectrumSize = 513

    @Before
    fun setup() {
        baseline = AmbientBaseline(
            spectrumSize = spectrumSize,
            calibrationFrames = 10, // Short for testing
        )
    }

    // --- Calibration ---

    @Test
    fun `initial state is uncalibrated`() {
        assertFalse(baseline.isCalibrated)
        assertEquals(0, baseline.calibrationProgress)
    }

    @Test
    fun `calibration completes after required frames`() {
        val spectrum = FloatArray(spectrumSize) { 0.001f }
        repeat(9) {
            assertFalse(baseline.feedCalibrationFrame(spectrum))
        }
        assertTrue(baseline.feedCalibrationFrame(spectrum))
        assertTrue(baseline.isCalibrated)
    }

    @Test
    fun `baseline captures median of calibration frames`() {
        // Feed frames with values 1,2,3,4,5,6,7,8,9,10 at bin 100
        repeat(10) { i ->
            val spectrum = FloatArray(spectrumSize) { 0f }
            spectrum[100] = (i + 1).toFloat()
            baseline.feedCalibrationFrame(spectrum)
        }
        // Median of [1..10] = 5.0 (index 5 of sorted array)
        val baselineValue = baseline.baselineSpectrum[100]
        assertTrue(
            "Baseline should be median (~5-6), got $baselineValue",
            baselineValue in 4f..6f,
        )
    }

    // --- Subtraction ---

    @Test
    fun `subtraction removes ambient and returns residual`() {
        // Calibrate with constant noise
        val ambient = FloatArray(spectrumSize) { 0.01f }
        repeat(10) { baseline.feedCalibrationFrame(ambient) }

        // Signal with water at bin 50 (above ambient)
        val signal = FloatArray(spectrumSize) { 0.01f }
        signal[50] = 0.1f // Water is 10x above ambient

        val residual = baseline.subtract(signal)

        // Residual at bin 50 should preserve most of the water signal
        assertTrue(
            "Residual at water bin should be > 0, got ${residual[50]}",
            residual[50] > 0.01f,
        )
        // Residual at ambient-only bins should be near zero (floored)
        assertTrue(
            "Residual at ambient bin should be small, got ${residual[200]}",
            residual[200] < 0.01f,
        )
    }

    @Test
    fun `uncalibrated subtraction passes signal through`() {
        val signal = FloatArray(spectrumSize) { 0.05f }
        val residual = baseline.subtract(signal)

        // Should pass through at ~80%
        assertTrue(
            "Uncalibrated should pass through, got ${residual[100]}",
            residual[100] > 0.03f,
        )
    }

    // --- Water Template ---

    @Test
    fun `water-like residual scores high on water likeness`() {
        // Calibrate
        val ambient = FloatArray(spectrumSize) { 1e-8f }
        repeat(10) { baseline.feedCalibrationFrame(ambient) }

        // Create a water-like residual matching the real Pulsar template:
        // Peak at ~1kHz (bin 25), rolloff both below and above
        val waterResidual = FloatArray(spectrumSize) { 1e-8f }
        for (bin in 5..139) {
            val freq = bin * (44100f / 1024f)
            // Bell curve centered at 1kHz, plus secondary plateau at 4-6kHz
            val dist = kotlin.math.abs(kotlin.math.ln(freq / 1077.0))
            val amplitude = kotlin.math.exp(-dist * 1.5).toFloat()
            waterResidual[bin] = 0.1f * amplitude.coerceAtLeast(0.005f)
        }

        val score = baseline.scoreWaterLikeness(waterResidual)
        assertTrue("Water-like residual should score > 0.5, got $score", score > 0.5f)
    }

    @Test
    fun `narrowband residual scores low on water likeness`() {
        val ambient = FloatArray(spectrumSize) { 1e-8f }
        repeat(10) { baseline.feedCalibrationFrame(ambient) }

        // Narrowband: energy only at one frequency (like a tone)
        val toneResidual = FloatArray(spectrumSize) { 1e-8f }
        toneResidual[20] = 0.5f // Single bin spike

        val score = baseline.scoreWaterLikeness(toneResidual)
        assertTrue("Narrowband should score < 0.5, got $score", score < 0.5f)
    }

    // --- Reset ---

    @Test
    fun `reset clears calibration state`() {
        val spectrum = FloatArray(spectrumSize) { 0.01f }
        repeat(10) { baseline.feedCalibrationFrame(spectrum) }
        assertTrue(baseline.isCalibrated)

        baseline.reset()
        assertFalse(baseline.isCalibrated)
        assertEquals(0, baseline.calibrationProgress)
    }
}
