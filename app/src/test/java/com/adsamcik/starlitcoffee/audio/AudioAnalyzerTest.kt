package com.adsamcik.starlitcoffee.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAnalyzerTest {

    // --- RMS dB Computation ---

    @Test
    fun `silence buffer produces very low dB`() {
        val silence = ShortArray(1024) { 0 }
        val rmsDb = AudioAnalyzer.computeRmsDb(silence)
        assertEquals(AudioAnalyzer.SILENCE_DB, rmsDb, 0.01f)
    }

    @Test
    fun `full scale sine produces 0 dB or close`() {
        // Full scale = all samples at Short.MAX_VALUE
        val fullScale = ShortArray(1024) { Short.MAX_VALUE }
        val rmsDb = AudioAnalyzer.computeRmsDb(fullScale)
        // RMS of constant max = 0 dBFS
        assertEquals(0f, rmsDb, 0.5f)
    }

    @Test
    fun `half amplitude produces roughly -6 dB`() {
        val halfScale = ShortArray(1024) { (Short.MAX_VALUE / 2).toShort() }
        val rmsDb = AudioAnalyzer.computeRmsDb(halfScale)
        // -6.02 dBFS for half amplitude
        assertEquals(-6f, rmsDb, 1f)
    }

    @Test
    fun `low amplitude produces low dB`() {
        val lowAmp = ShortArray(1024) { 100 }
        val rmsDb = AudioAnalyzer.computeRmsDb(lowAmp)
        // 100/32767 ≈ 0.00305 → 20*log10(0.00305) ≈ -50.3 dB
        assertTrue("Expected dB < -40, got $rmsDb", rmsDb < -40f)
    }

    @Test
    fun `empty buffer returns silence dB`() {
        val empty = ShortArray(0)
        assertEquals(AudioAnalyzer.SILENCE_DB, AudioAnalyzer.computeRmsDb(empty), 0.01f)
    }

    // --- Peak dB Computation ---

    @Test
    fun `peak of all zeros is silence`() {
        val silence = ShortArray(1024) { 0 }
        val peakDb = AudioAnalyzer.computePeakDb(silence)
        assertEquals(AudioAnalyzer.SILENCE_DB, peakDb, 0.01f)
    }

    @Test
    fun `peak detects single spike`() {
        val buffer = ShortArray(1024) { 0 }
        buffer[512] = Short.MAX_VALUE
        val peakDb = AudioAnalyzer.computePeakDb(buffer)
        // Single max sample → 0 dBFS peak
        assertEquals(0f, peakDb, 0.5f)
    }

    @Test
    fun `peak of negative values uses absolute`() {
        val buffer = ShortArray(1024) { 0 }
        buffer[100] = Short.MIN_VALUE
        val peakDb = AudioAnalyzer.computePeakDb(buffer)
        // abs(MIN_VALUE) ≈ MAX_VALUE, so peak ≈ 0 dBFS
        assertEquals(0f, peakDb, 1f)
    }

    // --- Zero Crossing Rate ---

    @Test
    fun `constant signal has zero crossings`() {
        val constant = ShortArray(1024) { 1000 }
        val zcr = AudioAnalyzer.computeZeroCrossingRate(constant)
        assertEquals(0f, zcr, 0.01f)
    }

    @Test
    fun `alternating signal has maximum crossings`() {
        val alternating = ShortArray(1024) { if (it % 2 == 0) 1000 else -1000 }
        val zcr = AudioAnalyzer.computeZeroCrossingRate(alternating)
        // Every pair crosses → ZCR ≈ 1.0
        assertEquals(1f, zcr, 0.01f)
    }

    @Test
    fun `square wave has proportional crossings`() {
        // 50-sample half period → 1024/50 ≈ 20 crossings in 1023 pairs
        val buffer = ShortArray(1024) { if ((it / 50) % 2 == 0) 5000 else -5000 }
        val zcr = AudioAnalyzer.computeZeroCrossingRate(buffer)
        // ~20 crossings / 1023 ≈ 0.0195
        assertTrue("Expected ZCR between 0.01 and 0.03, got $zcr", zcr in 0.01f..0.03f)
    }

    @Test
    fun `single sample returns zero ZCR`() {
        val single = shortArrayOf(1000)
        val zcr = AudioAnalyzer.computeZeroCrossingRate(single)
        assertEquals(0f, zcr, 0.01f)
    }

    // --- Dominant Frequency Estimation ---

    @Test
    fun `silence has no dominant frequency`() {
        val silence = ShortArray(4096) { 0 }
        val freq = AudioAnalyzer.estimateDominantFrequency(silence, sampleRate = 44100)
        assertEquals(0f, freq, 0.01f)
    }

    @Test
    fun `pure tone detected at correct frequency`() {
        // Generate 440 Hz sine wave at 44100 Hz sample rate
        val sampleRate = 44100
        val frequency = 440.0
        val numSamples = 4096
        val buffer = ShortArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (kotlin.math.sin(2.0 * Math.PI * frequency * t) * 16000).toInt().toShort()
        }
        val detected = AudioAnalyzer.estimateDominantFrequency(buffer, sampleRate = sampleRate)
        // Should be close to 440 Hz (within ~5 Hz tolerance due to autocorrelation resolution)
        assertEquals(440f, detected, 10f)
    }

    @Test
    fun `too few samples returns zero`() {
        val tiny = ShortArray(64) { 1000 }
        val freq = AudioAnalyzer.estimateDominantFrequency(tiny, sampleRate = 44100)
        assertEquals(0f, freq, 0.01f)
    }

    // --- Full Analysis ---

    @Test
    fun `analyze returns consistent features for known signal`() {
        val buffer = ShortArray(1024) { (Short.MAX_VALUE / 2).toShort() }
        val features = AudioAnalyzer.analyze(buffer, sampleRate = 44100)
        assertEquals(-6f, features.rmsDb, 1f)
        assertTrue("Peak should be >= RMS", features.peakDb >= features.rmsDb)
        assertEquals(0f, features.zeroCrossingRate, 0.01f)
    }

    @Test
    fun `analyze empty buffer returns silence`() {
        val features = AudioAnalyzer.analyze(ShortArray(0))
        assertEquals(AudioAnalyzer.SILENCE_DB, features.rmsDb, 0.01f)
        assertEquals(AudioAnalyzer.SILENCE_DB, features.peakDb, 0.01f)
        assertEquals(0f, features.zeroCrossingRate, 0.01f)
        assertEquals(0f, features.dominantFrequencyHz, 0.01f)
    }
}
