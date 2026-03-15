package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectralAnalyzerTest {

    private lateinit var analyzer: SpectralAnalyzer

    @Before
    fun setup() {
        analyzer = SpectralAnalyzer()
    }

    // --- Band Energy ---

    @Test
    fun `silence has very low band energy`() {
        val silence = ShortArray(1024) { 0 }
        val features = analyzer.analyze(silence)

        for (band in FrequencyBand.entries) {
            assertTrue(
                "${band.displayName} energy should be < -80 dB for silence, got ${features.bandEnergyDb[band]}",
                (features.bandEnergyDb[band] ?: 0f) < -80f,
            )
        }
    }

    @Test
    fun `pure tone at 500Hz has high POUR band energy`() {
        val sampleRate = 44100
        val frequency = 500.0
        val buffer = ShortArray(1024) { i ->
            (sin(2.0 * PI * frequency * i / sampleRate) * 16000).toInt().toShort()
        }

        val features = analyzer.analyze(buffer)
        val pourEnergy = features.bandEnergyDb[FrequencyBand.POUR] ?: -96f
        val highMidEnergy = features.bandEnergyDb[FrequencyBand.HIGH_MID] ?: -96f

        // 500Hz is in POUR band (200-3000Hz), should be significantly louder than HIGH_MID
        assertTrue(
            "POUR energy ($pourEnergy) should be > HIGH_MID ($highMidEnergy) + 20dB",
            pourEnergy > highMidEnergy + 20f,
        )
    }

    @Test
    fun `pure tone at 4000Hz has high HIGH_MID band energy`() {
        val sampleRate = 44100
        val frequency = 4000.0
        val buffer = ShortArray(1024) { i ->
            (sin(2.0 * PI * frequency * i / sampleRate) * 16000).toInt().toShort()
        }

        val features = analyzer.analyze(buffer)
        val highMidEnergy = features.bandEnergyDb[FrequencyBand.HIGH_MID] ?: -96f
        val dripLowEnergy = features.bandEnergyDb[FrequencyBand.DRIP_LOW] ?: -96f

        // 4000Hz is in HIGH_MID band (3000-6000Hz), should be louder than DRIP_LOW (300-2000Hz)
        assertTrue(
            "HIGH_MID energy ($highMidEnergy) should exceed DRIP_LOW energy ($dripLowEnergy)",
            highMidEnergy > dripLowEnergy + 10f,
        )
    }

    @Test
    fun `broadband noise has energy across all bands`() {
        val noise = SyntheticSignals.whiteNoise(23, amplitude = 0.3) // ~1024 samples at 44100
        val features = analyzer.analyze(noise)

        for (band in FrequencyBand.entries) {
            assertTrue(
                "${band.displayName} should have energy > -60 dB for noise, got ${features.bandEnergyDb[band]}",
                (features.bandEnergyDb[band] ?: -96f) > -60f,
            )
        }
    }

    // --- Spectral Flux ---

    @Test
    fun `first frame has zero spectral flux`() {
        analyzer.reset()
        val buffer = SyntheticSignals.whiteNoise(23, amplitude = 0.3)
        val features = analyzer.analyze(buffer)

        for (band in FrequencyBand.entries) {
            assertEquals(
                "First frame should have zero spectral flux for ${band.displayName}",
                0f,
                features.spectralFlux[band] ?: -1f,
                0.01f,
            )
        }
    }

    @Test
    fun `spectral flux is high when noise appears after silence`() {
        analyzer.reset()

        // Frame 1: silence
        val silence = ShortArray(1024) { 0 }
        analyzer.analyze(silence)

        // Frame 2: loud noise — should produce high spectral flux
        val noise = SyntheticSignals.whiteNoise(23, amplitude = 0.5)
        val features = analyzer.analyze(noise)

        val pourFlux = features.spectralFlux[FrequencyBand.POUR] ?: 0f
        assertTrue(
            "Spectral flux should be high after silence→noise transition, got $pourFlux",
            pourFlux > 1f,
        )
    }

    @Test
    fun `spectral flux is low for steady noise`() {
        analyzer.reset()

        // Feed multiple frames of the same noise level
        val rng = java.util.Random(42)
        repeat(5) {
            val noise = ShortArray(1024) { (rng.nextGaussian() * 0.3 * Short.MAX_VALUE).toInt().toShort() }
            analyzer.analyze(noise)
        }

        // The last frame should have low flux (steady state)
        val steadyNoise = ShortArray(1024) {
            (rng.nextGaussian() * 0.3 * Short.MAX_VALUE).toInt().toShort()
        }
        val features = analyzer.analyze(steadyNoise)

        val pourFlux = features.spectralFlux[FrequencyBand.POUR] ?: 0f
        // Flux should be much lower than the silence→noise transition
        assertTrue(
            "Steady-state flux should be < 5, got $pourFlux",
            pourFlux < 5f,
        )
    }

    // --- Spectral Tilt ---

    @Test
    fun `low-frequency tone has high spectral tilt`() {
        val sampleRate = 44100
        val buffer = ShortArray(1024) { i ->
            (sin(2.0 * PI * 300.0 * i / sampleRate) * 16000).toInt().toShort()
        }

        val features = analyzer.analyze(buffer)
        assertTrue(
            "300Hz tone should have high spectral tilt, got ${features.spectralTilt}",
            features.spectralTilt > 1f,
        )
    }

    @Test
    fun `high-frequency tone has low spectral tilt`() {
        val sampleRate = 44100
        val buffer = ShortArray(1024) { i ->
            (sin(2.0 * PI * 2000.0 * i / sampleRate) * 16000).toInt().toShort()
        }

        val features = analyzer.analyze(buffer)
        assertTrue(
            "2kHz tone should have low spectral tilt, got ${features.spectralTilt}",
            features.spectralTilt < 1f,
        )
    }

    // --- Reset ---

    @Test
    fun `reset clears previous frame for spectral flux`() {
        val noise = SyntheticSignals.whiteNoise(23, amplitude = 0.3)
        analyzer.analyze(noise)
        analyzer.analyze(noise)

        analyzer.reset()

        // After reset, first frame should have zero flux again
        val features = analyzer.analyze(noise)
        for (band in FrequencyBand.entries) {
            assertEquals(
                "After reset, first frame flux should be 0 for ${band.displayName}",
                0f,
                features.spectralFlux[band] ?: -1f,
                0.01f,
            )
        }
    }
}
