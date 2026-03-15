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

    @Test
    fun `adaptive alpha is stronger at low SNR than high SNR`() {
        // Calibrate with moderate noise
        val ambient = FloatArray(spectrumSize) { 0.01f }
        repeat(10) { baseline.feedCalibrationFrame(ambient) }

        // High-SNR signal (10x above ambient): should get mild subtraction
        val highSnr = FloatArray(spectrumSize) { 0.01f }
        highSnr[50] = 0.1f // 10x = 10dB SNR
        val residualHigh = baseline.subtract(highSnr)

        // Low-SNR signal (just barely above ambient): should get stronger subtraction
        val lowSnr = FloatArray(spectrumSize) { 0.01f }
        lowSnr[50] = 0.012f // 1.2x ≈ 0.8dB SNR
        val residualLow = baseline.subtract(lowSnr)

        // At high SNR, more signal should survive
        // At low SNR, more should be subtracted
        assertTrue(
            "High-SNR residual (${residualHigh[50]}) should be > low-SNR residual (${residualLow[50]})",
            residualHigh[50] > residualLow[50],
        )
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
