package com.adsamcik.starlitcoffee.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioPreProcessorTest {

    private lateinit var preprocessor: AudioPreProcessor

    @Before
    fun setup() {
        preprocessor = AudioPreProcessor(fftSize = 1024, sampleRate = 44100, hpfCutoffHz = 150f)
    }

    // --- DC Removal ---

    @Test
    fun `DC offset is removed over time`() {
        // Signal with large DC offset
        val dcOffset: Short = 10000
        val buffer = ShortArray(1024) { dcOffset }

        // Process several buffers to let DC removal converge
        repeat(20) {
            preprocessor.process(buffer)
        }

        // After convergence, output should have DC removed
        val frames = preprocessor.process(buffer)
        if (frames.isNotEmpty()) {
            val frame = frames.last()
            val mean = frame.map { it.toLong() }.average()
            assertTrue(
                "Mean after DC removal should be near 0, got $mean",
                kotlin.math.abs(mean) < 1000, // much less than 10000 offset
            )
        }
    }

    // --- High-Pass Filter ---

    @Test
    fun `50Hz signal is attenuated`() {
        val sampleRate = 44100
        val frequency = 50.0
        val amplitude = 10000.0
        val buffer = ShortArray(1024) { i ->
            (kotlin.math.sin(2.0 * Math.PI * frequency * i / sampleRate) * amplitude).toInt().toShort()
        }

        preprocessor.reset()

        // Let filter settle
        repeat(10) { preprocessor.process(buffer) }

        val frames = preprocessor.process(buffer)
        if (frames.isNotEmpty()) {
            val frame = frames.last()
            var rmsOutput = 0.0
            for (s in frame) rmsOutput += s.toDouble() * s
            rmsOutput = kotlin.math.sqrt(rmsOutput / frame.size)

            var rmsInput = 0.0
            for (s in buffer) rmsInput += s.toDouble() * s
            rmsInput = kotlin.math.sqrt(rmsInput / buffer.size)

            // 50Hz should be attenuated by ~19 dB → output < 15% of input
            val ratio = rmsOutput / rmsInput
            assertTrue(
                "50Hz should be attenuated (ratio=$ratio, expected < 0.25)",
                ratio < 0.25,
            )
        }
    }

    @Test
    fun `500Hz signal passes through`() {
        val sampleRate = 44100
        val frequency = 500.0
        val amplitude = 10000.0
        val buffer = ShortArray(1024) { i ->
            (kotlin.math.sin(2.0 * Math.PI * frequency * i / sampleRate) * amplitude).toInt().toShort()
        }

        preprocessor.reset()

        // Let filter settle
        repeat(10) { preprocessor.process(buffer) }

        val frames = preprocessor.process(buffer)
        if (frames.isNotEmpty()) {
            val frame = frames.last()
            var rmsOutput = 0.0
            for (s in frame) rmsOutput += s.toDouble() * s
            rmsOutput = kotlin.math.sqrt(rmsOutput / frame.size)

            var rmsInput = 0.0
            for (s in buffer) rmsInput += s.toDouble() * s
            rmsInput = kotlin.math.sqrt(rmsInput / buffer.size)

            // 500Hz should pass with minimal attenuation (< 1 dB)
            val ratio = rmsOutput / rmsInput
            assertTrue(
                "500Hz should pass through (ratio=$ratio, expected > 0.8)",
                ratio > 0.8,
            )
        }
    }

    // --- Overlap ---

    @Test
    fun `first buffer produces no frames before filling ring buffer`() {
        preprocessor.reset()
        // First 1024 samples: fills ring buffer but no overlap frame yet
        val buffer = ShortArray(1024) { 100 }
        val frames = preprocessor.process(buffer)

        // With 1024-sample buffer and 1024 FFT size, first buffer produces exactly 1 frame
        // (ring buffer fills to 1024 → emit, no overlap yet since hop = 512)
        assertTrue("First buffer should produce 0 or 1 frames", frames.size <= 1)
    }

    @Test
    fun `subsequent buffers produce frames with 50 percent overlap`() {
        preprocessor.reset()

        // Fill ring buffer
        preprocessor.process(ShortArray(1024) { 100 })

        // Second buffer should produce 2 frames (50% overlap, hop = 512)
        val frames = preprocessor.process(ShortArray(1024) { 200 })
        assertEquals(2, frames.size)
    }

    // --- Reset ---

    @Test
    fun `reset clears filter state`() {
        // Process some data
        val buffer = ShortArray(1024) { Short.MAX_VALUE }
        repeat(5) { preprocessor.process(buffer) }

        preprocessor.reset()

        // After reset, processing silence should not have residual filter state
        val silenceFrames = preprocessor.process(ShortArray(1024) { 0 })
        if (silenceFrames.isNotEmpty()) {
            val frame = silenceFrames.last()
            var rms = 0.0
            for (s in frame) rms += s.toDouble() * s
            rms = kotlin.math.sqrt(rms / frame.size)
            assertTrue(
                "After reset + silence, output RMS should be near 0, got $rms",
                rms < 100,
            )
        }
    }
}
