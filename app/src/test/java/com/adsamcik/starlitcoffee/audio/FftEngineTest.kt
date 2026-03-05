package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class FftEngineTest {

    private lateinit var engine: FftEngine

    @Before
    fun setup() {
        engine = FftEngine(1024)
    }

    // --- FFT Size Validation ---

    @Test(expected = IllegalArgumentException::class)
    fun `non-power-of-2 size throws`() {
        FftEngine(1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero size throws`() {
        FftEngine(0)
    }

    @Test
    fun `small FFT size works`() {
        val small = FftEngine(8)
        assertEquals(8, small.size)
    }

    // --- Hann Window ---

    @Test
    fun `Hann window is zero at endpoints`() {
        assertEquals(0f, engine.hannWindow[0], 1e-6f)
        assertEquals(0f, engine.hannWindow[engine.size - 1], 1e-6f)
    }

    @Test
    fun `Hann window peaks at center`() {
        val center = engine.size / 2
        assertEquals(1f, engine.hannWindow[center], 0.01f)
    }

    @Test
    fun `Hann window is symmetric`() {
        for (i in 0 until engine.size / 2) {
            assertEquals(
                engine.hannWindow[i],
                engine.hannWindow[engine.size - 1 - i],
                1e-6f,
            )
        }
    }

    @Test
    fun `Hann power normalization is approximately 2_667`() {
        assertEquals(2.667f, engine.hannPowerNormalization, 0.01f)
    }

    // --- FFT Accuracy ---

    @Test
    fun `FFT of DC signal has energy only in bin 0`() {
        val real = FloatArray(1024) { 1f }
        val imag = FloatArray(1024) { 0f }
        engine.fft(real, imag)

        // Bin 0 should have all the energy (N * amplitude)
        assertEquals(1024f, real[0], 0.01f)
        assertEquals(0f, imag[0], 0.01f)

        // All other bins should be ~0
        for (k in 1 until 1024) {
            assertTrue(
                "Bin $k should be near zero, got (${real[k]}, ${imag[k]})",
                abs(real[k]) < 0.01f && abs(imag[k]) < 0.01f,
            )
        }
    }

    @Test
    fun `FFT of pure sine peaks at correct bin`() {
        // 440 Hz at 44100 Hz sample rate, 1024 samples
        // Expected bin: 440 / (44100/1024) = 440 / 43.07 ≈ 10.22
        val sampleRate = 44100
        val frequency = 440.0
        val real = FloatArray(1024) { i ->
            sin(2.0 * PI * frequency * i / sampleRate).toFloat()
        }
        val imag = FloatArray(1024) { 0f }

        engine.fft(real, imag)

        val power = FloatArray(513)
        engine.powerSpectrum(real, imag, power)

        // Find peak bin
        var maxBin = 0
        var maxPower = 0f
        for (k in 1..512) {
            if (power[k] > maxPower) {
                maxPower = power[k]
                maxBin = k
            }
        }

        // Expected bin ≈ 10 (440 Hz / 43.07 Hz/bin)
        assertTrue("Peak bin should be near 10, got $maxBin", maxBin in 9..11)
    }

    @Test
    fun `FFT round-trip preserves signal energy (Parseval's theorem)`() {
        // Generate a known signal
        val n = 1024
        val real = FloatArray(n) { i ->
            (sin(2.0 * PI * 5 * i.toDouble() / n) + 0.5 * cos(2.0 * PI * 13 * i.toDouble() / n)).toFloat()
        }
        val imag = FloatArray(n) { 0f }

        // Time-domain energy
        var timeEnergy = 0.0
        for (x in real) timeEnergy += x.toDouble() * x

        engine.fft(real, imag)

        // Frequency-domain energy (Parseval: Σ|X|²/N = Σ|x|²)
        var freqEnergy = 0.0
        for (k in 0 until n) {
            freqEnergy += real[k].toDouble() * real[k] + imag[k].toDouble() * imag[k]
        }
        freqEnergy /= n

        assertEquals(timeEnergy, freqEnergy, timeEnergy * 0.001)
    }

    @Test
    fun `FFT of two tones shows two peaks`() {
        val sampleRate = 44100
        val f1 = 500.0 // bin ≈ 11.6
        val f2 = 2000.0 // bin ≈ 46.4
        val real = FloatArray(1024) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2.0 * PI * f1 * t) + sin(2.0 * PI * f2 * t)).toFloat()
        }
        val imag = FloatArray(1024) { 0f }

        engine.fft(real, imag)
        val power = FloatArray(513)
        engine.powerSpectrum(real, imag, power)

        // Find two highest peaks
        val topBins = power.indices.sortedByDescending { power[it] }.take(4)

        val nearBin1 = topBins.any { it in 10..13 } // f1 ≈ bin 11-12
        val nearBin2 = topBins.any { it in 45..48 } // f2 ≈ bin 46-47
        assertTrue("Should find peak near bin 11-12 for 500Hz", nearBin1)
        assertTrue("Should find peak near bin 46-47 for 2000Hz", nearBin2)
    }

    // --- Power/Magnitude Spectrum ---

    @Test
    fun `power spectrum length is N div 2 plus 1`() {
        val real = FloatArray(1024) { 0f }
        val imag = FloatArray(1024) { 0f }
        val power = FloatArray(513)
        engine.powerSpectrum(real, imag, power)
        // No exception = correct
    }

    @Test
    fun `magnitude spectrum is sqrt of power spectrum`() {
        val real = FloatArray(1024) { sin(2.0 * PI * 5.0 * it / 1024).toFloat() }
        val imag = FloatArray(1024) { 0f }
        engine.fft(real, imag)

        val power = FloatArray(513)
        val magnitude = FloatArray(513)
        engine.powerSpectrum(real, imag, power)
        engine.magnitudeSpectrum(real, imag, magnitude)

        for (k in 0..512) {
            assertEquals(sqrt(power[k]), magnitude[k], 0.01f)
        }
    }

    // --- Hann Window Application ---

    @Test
    fun `applyHannWindow normalizes shorts to float range`() {
        val samples = ShortArray(1024) { Short.MAX_VALUE }
        val output = FloatArray(1024)
        engine.applyHannWindow(samples, 0, 1024, output)

        // At center (Hann = 1.0), should be close to 1.0
        assertEquals(1f, output[512], 0.01f)
        // At endpoints (Hann = 0.0), should be 0
        assertEquals(0f, output[0], 0.01f)
    }

    @Test
    fun `applyHannWindow zero-fills when count is less than size`() {
        val samples = ShortArray(512) { Short.MAX_VALUE }
        val output = FloatArray(1024)
        engine.applyHannWindow(samples, 0, 512, output)

        // Second half should be zeros
        for (i in 512 until 1024) {
            assertEquals(0f, output[i], 0.01f)
        }
    }
}
