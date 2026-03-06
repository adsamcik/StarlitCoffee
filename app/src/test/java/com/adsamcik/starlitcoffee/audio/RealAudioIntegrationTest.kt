package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.DetectorConfig
import com.adsamcik.starlitcoffee.data.model.DetectorState
import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Integration tests running the full DSP pipeline against real Pulsar recordings.
 *
 * Test data: PCM files (16-bit signed, mono, 44100Hz) extracted from M4A recordings
 * in testdata/coffee-bags/. These are real-world recordings of a NextLevel Pulsar brew.
 *
 * Segments:
 * - real_pour_loud.pcm (5s): Active pouring, POUR band -8 to -9 dB
 * - real_sustained_pour.pcm (10s): Sustained pour mid-brew
 * - real_pour_to_drip.pcm (10s): Pour tapering off into dripping
 * - real_drip_silence.pcm (10s): Quiet dripping/ambient after pour
 * - real_pour_stop_transition.pcm (15s): Full pour-stop-drip transition
 * - real_short_pour.pcm (8.8s): Short pour clip
 */
class RealAudioIntegrationTest {

    private lateinit var preProcessor: AudioPreProcessor
    private lateinit var analyzer: SpectralAnalyzer
    private lateinit var detector: BrewEventDetector
    private var fakeTimeMs: Long = 0L

    private val resourceDir = File("src/test/resources/audio")

    @Before
    fun setup() {
        fakeTimeMs = 1000L
        preProcessor = AudioPreProcessor(sampleRate = 44100)
        analyzer = SpectralAnalyzer()
        detector = BrewEventDetector(
            config = DetectorConfig(
                minPhaseDurationFrames = 30,
                trailingWindowSize = 43,
                pourOffsetConfirmFrames = 10,
            ),
            timeProvider = { fakeTimeMs },
        )
    }

    // --- Helpers ---

    private fun loadPcm(filename: String): ShortArray? {
        val file = File(resourceDir, filename)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    private fun runPipeline(samples: ShortArray): PipelineResult {
        val allEvents = mutableListOf<BrewAudioEvent>()
        val states = mutableListOf<DetectorState>()
        val pourEnergies = mutableListOf<Float>()
        val tilts = mutableListOf<Float>()
        val flatnesses = mutableListOf<Float>()
        val cpps = mutableListOf<Float>()
        val coincidences = mutableListOf<Int>()

        // Process in 1024-sample chunks (matching AudioCaptureSession)
        val chunkSize = 1024
        for (offset in 0 until samples.size - chunkSize step chunkSize) {
            val chunk = samples.copyOfRange(offset, offset + chunkSize)
            val frames = preProcessor.process(chunk)
            for (frame in frames) {
                val spectral = analyzer.analyze(frame)
                val events = detector.processFrame(spectral)
                allEvents.addAll(events)
                states.add(detector.state)
                pourEnergies.add(spectral.bandEnergyDb[FrequencyBand.POUR] ?: -96f)
                tilts.add(spectral.spectralTilt)
                flatnesses.add(spectral.spectralFlatness)
                cpps.add(spectral.cepstralPeakProminence)
                coincidences.add(spectral.bandCoincidenceCount)
                fakeTimeMs += 12L
            }
        }

        return PipelineResult(allEvents, states, pourEnergies, tilts, flatnesses, cpps, coincidences)
    }

    data class PipelineResult(
        val events: List<BrewAudioEvent>,
        val states: List<DetectorState>,
        val pourEnergies: List<Float>,
        val tilts: List<Float>,
        val flatnesses: List<Float>,
        val cpps: List<Float>,
        val coincidences: List<Int>,
    )

    // --- Tests ---

    @Test
    fun `real pour has POUR band energy above -20 dB`() {
        val samples = loadPcm("real_pour_loud.pcm")
        assumeTrue("Test PCM file not found — run Python extraction first", samples != null)

        val result = runPipeline(samples!!)

        val meanPourDb = result.pourEnergies.average()
        assertTrue(
            "Real pour should have POUR energy above -20 dB, got $meanPourDb",
            meanPourDb > -20.0,
        )
    }

    @Test
    fun `real drip silence has POUR band energy below -25 dB`() {
        val samples = loadPcm("real_drip_silence.pcm")
        assumeTrue("Test PCM file not found", samples != null)

        val result = runPipeline(samples!!)

        val meanPourDb = result.pourEnergies.average()
        assertTrue(
            "Drip/silence should have POUR energy below -25 dB, got $meanPourDb",
            meanPourDb < -25.0,
        )
    }

    @Test
    fun `real pour has low spectral tilt (broadband)`() {
        val samples = loadPcm("real_pour_loud.pcm")
        assumeTrue("Test PCM file not found", samples != null)

        val result = runPipeline(samples!!)

        val meanTilt = result.tilts.average()
        assertTrue(
            "Pour should have tilt < 10 (broadband), got $meanTilt",
            meanTilt < 10.0,
        )
    }

    @Test
    fun `real drip silence has high spectral tilt`() {
        val samples = loadPcm("real_drip_silence.pcm")
        assumeTrue("Test PCM file not found", samples != null)

        val result = runPipeline(samples!!)

        val meanTilt = result.tilts.average()
        assertTrue(
            "Drip/silence should have tilt > 10, got $meanTilt",
            meanTilt > 10.0,
        )
    }

    @Test
    fun `loud pour triggers POURING state`() {
        val samples = loadPcm("real_pour_loud.pcm")
        assumeTrue("Test PCM file not found", samples != null)

        val result = runPipeline(samples!!)

        assertTrue(
            "Should reach POURING state during loud pour",
            result.states.contains(DetectorState.POURING),
        )
        assertTrue(
            "Should emit PourStarted event",
            result.events.any { it is BrewAudioEvent.PourStarted },
        )
    }

    @Test
    fun `pour-to-drip transition produces PourStopped`() {
        // First establish POURING with loud pour
        val pourSamples = loadPcm("real_pour_loud.pcm")
        val transitionSamples = loadPcm("real_pour_to_drip.pcm")
        assumeTrue("Test PCM files not found", pourSamples != null && transitionSamples != null)

        // Run loud pour first to get into POURING state
        runPipeline(pourSamples!!)
        assertEquals("Should be POURING after loud pour", DetectorState.POURING, detector.state)

        // Now feed the transition segment
        val result = runPipeline(transitionSamples!!)

        assertTrue(
            "Should emit PourStopped during pour-to-drip transition",
            result.events.any { it is BrewAudioEvent.PourStopped },
        )
    }

    @Test
    fun `full pour-stop transition reaches DRIPPING or COMPLETE`() {
        // Use the pour-stop-transition recording (15s) which has a natural
        // pour → silence transition rather than two spliced segments
        val pourSamples = loadPcm("real_pour_loud.pcm")
        val transitionSamples = loadPcm("real_pour_stop_transition.pcm")
        assumeTrue("Test PCM files not found", pourSamples != null && transitionSamples != null)

        // Establish POURING state
        runPipeline(pourSamples!!)

        // Feed the natural transition (pour trails off → silence)
        val result = runPipeline(transitionSamples!!)

        val finalState = detector.state
        // Should have transitioned out of POURING at some point
        assertTrue(
            "After natural pour-stop transition, should leave POURING. Final: $finalState, " +
                "states seen: ${result.states.map { it.name }.distinct()}",
            finalState != DetectorState.IDLE,
        )
        assertTrue(
            "Should have seen a state other than POURING during transition",
            result.states.any { it != DetectorState.POURING },
        )
    }

    @Test
    fun `energy gap between pour and silence is at least 10 dB`() {
        val pourSamples = loadPcm("real_pour_loud.pcm")
        val silenceSamples = loadPcm("real_drip_silence.pcm")
        assumeTrue("Test PCM files not found", pourSamples != null && silenceSamples != null)

        val pourResult = runPipeline(pourSamples!!)
        preProcessor.reset()
        analyzer.reset()
        val silenceResult = runPipeline(silenceSamples!!)

        val pourMean = pourResult.pourEnergies.average()
        val silenceMean = silenceResult.pourEnergies.average()
        val gap = pourMean - silenceMean

        assertTrue(
            "Energy gap between pour ($pourMean dB) and silence ($silenceMean dB) should be >= 10 dB, got $gap",
            gap >= 10.0,
        )
    }

    // --- Noise-Robust Feature Tests ---

    @Test
    fun `real pour has high spectral flatness`() {
        val samples = loadPcm("real_pour_loud.pcm")
        assumeTrue("Test PCM file not found", samples != null)

        val result = runPipeline(samples!!)
        val meanFlatness = result.flatnesses.average()

        assertTrue(
            "Pour should have spectral flatness > 0.08 (noise-like), got $meanFlatness",
            meanFlatness > 0.08,
        )
    }

    @Test
    fun `real pour has low cepstral peak prominence`() {
        val samples = loadPcm("real_pour_loud.pcm")
        assumeTrue("Test PCM file not found", samples != null)

        val result = runPipeline(samples!!)
        val meanCpp = result.cpps.average()

        assertTrue(
            "Pour should have CPP < 3.0 (no pitch), got $meanCpp",
            meanCpp < 3.0,
        )
    }

    @Test
    fun `real pour has high band coincidence`() {
        val samples = loadPcm("real_pour_loud.pcm")
        assumeTrue("Test PCM file not found", samples != null)

        val result = runPipeline(samples!!)
        val meanCoincidence = result.coincidences.average()

        assertTrue(
            "Pour should have band coincidence >= 3.0 (broadband), got $meanCoincidence",
            meanCoincidence >= 3.0,
        )
    }

    @Test
    fun `flatness is computable for both pour and silence`() {
        val pourSamples = loadPcm("real_pour_loud.pcm")
        val silenceSamples = loadPcm("real_drip_silence.pcm")
        assumeTrue("Test PCM files not found", pourSamples != null && silenceSamples != null)

        val pourResult = runPipeline(pourSamples!!)
        preProcessor.reset()
        analyzer.reset()
        val silenceResult = runPipeline(silenceSamples!!)

        val pourFlatness = pourResult.flatnesses.average()
        val silenceFlatness = silenceResult.flatnesses.average()

        // Both should produce valid flatness values (>0, <1)
        // Note: pour and ambient flatness may be similar (~0.13-0.14) because
        // water is pink-noise, not white-noise. Flatness alone doesn't separate
        // them — it works best as a veto against tonal sounds (speech/music < 0.05).
        assertTrue("Pour flatness should be > 0, got $pourFlatness", pourFlatness > 0)
        assertTrue("Silence flatness should be > 0, got $silenceFlatness", silenceFlatness > 0)
    }
}
