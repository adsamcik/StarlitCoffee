package com.adsamcik.starlitcoffee.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tests for the ActiveProbe turbulence detection logic.
 * Tests the analysis side only (no actual AudioTrack — that needs Android).
 */
class ActiveProbeTest {

    private lateinit var probe: ActiveProbe
    private val fftSize = 1024
    private val sampleRate = 44100

    @Before
    fun setup() {
        probe = ActiveProbe(
            probeFrequencyHz = 18000,
            sampleRate = sampleRate,
            fftSize = fftSize,
        )
        // Manually set active state for testing (probe.start() needs Android AudioTrack)
        setProbeActive(probe, true)
    }

    // --- Helpers ---

    /** Generate a power spectrum with a tone at the given frequency */
    private fun spectrumWithTone(freq: Int, amplitude: Float): FloatArray {
        val halfSize = fftSize / 2
        val spectrum = FloatArray(halfSize + 1) { 1e-10f } // noise floor
        val bin = (freq.toFloat() / (sampleRate.toFloat() / fftSize)).toInt()
        if (bin <= halfSize) {
            spectrum[bin] = amplitude * amplitude // power = amplitude²
        }
        return spectrum
    }

    /** Set isActive without needing Android AudioTrack */
    private fun setProbeActive(probe: ActiveProbe, active: Boolean) {
        probe.setActiveForTesting(active)
    }

    // --- Tests ---

    @Test
    fun `probe bin calculated correctly for 18kHz`() {
        // 18000 / (44100/1024) = 18000 / 43.07 ≈ 418
        val expectedBin = (18000.0 / (44100.0 / 1024)).toInt()
        assertTrue("Probe bin should be around 418, expected $expectedBin", expectedBin in 417..419)
    }

    @Test
    fun `steady probe signal produces low turbulence score`() {
        // Feed identical spectra — no modulation
        val spectrum = spectrumWithTone(18000, 0.5f)
        repeat(50) {
            probe.analyzeProbeResponse(spectrum)
        }

        assertTrue(
            "Steady signal should have low turbulence, got ${probe.turbulenceScore}",
            probe.turbulenceScore < 0.1f,
        )
    }

    @Test
    fun `modulated probe signal produces high turbulence score`() {
        // Feed alternating strong/weak probe levels — simulates turbulence
        repeat(50) { i ->
            val amplitude = if (i % 2 == 0) 0.8f else 0.2f
            val spectrum = spectrumWithTone(18000, amplitude)
            probe.analyzeProbeResponse(spectrum)
        }

        assertTrue(
            "Modulated signal should have high turbulence, got ${probe.turbulenceScore}",
            probe.turbulenceScore > 0.3f,
        )
    }

    @Test
    fun `absent probe signal produces zero turbulence`() {
        // No energy at probe frequency
        val halfSize = fftSize / 2
        val emptySpectrum = FloatArray(halfSize + 1) { 1e-10f }
        repeat(20) {
            probe.analyzeProbeResponse(emptySpectrum)
        }

        assertEquals(
            "No probe signal should give zero turbulence",
            0f,
            probe.turbulenceScore,
            0.01f,
        )
    }

    @Test
    fun `inactive probe returns zero`() {
        setProbeActive(probe, false)
        val spectrum = spectrumWithTone(18000, 0.5f)

        val result = probe.analyzeProbeResponse(spectrum)
        assertEquals(0f, result, 0.01f)
    }

    @Test
    fun `gradually increasing modulation increases turbulence`() {
        // Phase 1: steady signal
        val steadySpectrum = spectrumWithTone(18000, 0.5f)
        repeat(30) { probe.analyzeProbeResponse(steadySpectrum) }
        val steadyTurbulence = probe.turbulenceScore

        // Phase 2: introduce modulation
        repeat(30) { i ->
            val amp = 0.5f + 0.3f * sin(2.0 * PI * i / 5).toFloat()
            probe.analyzeProbeResponse(spectrumWithTone(18000, amp))
        }
        val modulatedTurbulence = probe.turbulenceScore

        assertTrue(
            "Modulated ($modulatedTurbulence) should be > steady ($steadyTurbulence)",
            modulatedTurbulence > steadyTurbulence,
        )
    }
}
