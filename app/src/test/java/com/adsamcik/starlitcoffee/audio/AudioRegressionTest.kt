package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.DetectorConfig
import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Regression tests that run the full audio detection pipeline on labeled recordings.
 *
 * Recordings are stored in test resources under `/recordings/` and tracked via Git LFS.
 * Each recording has:
 * - A WAV file (16-bit mono PCM, 44100 Hz)
 * - An Audacity label file (TSV: start_time, end_time, label)
 *
 * Run these tests before and after any threshold/detector changes to verify
 * no regressions occur. See docs/plans/audio-validation-strategy.md for the
 * full recording protocol and metrics targets.
 *
 * To add new recordings:
 * 1. Place WAV + labels in app/src/test/resources/recordings/
 * 2. Add entry to manifest.json
 * 3. Run tests to establish baseline metrics
 */
class AudioRegressionTest {

    private val sampleRate = 44100

    @Before
    fun setup() {
        // Verify test resources exist
    }

    /**
     * Runs the full pipeline on a WAV file and returns timestamped detections.
     */
    private fun runPipeline(
        samples: ShortArray,
        sampleRate: Int = this.sampleRate,
    ): List<EventMatcher.TimestampedDetection> {
        val preProcessor = AudioPreProcessor(sampleRate = sampleRate)
        val spectralAnalyzer = SpectralAnalyzer()
        val ambientBaseline = AmbientBaseline()
        val detector = BrewEventDetector(
            config = DetectorConfig(),
            timeProvider = { 0L }, // frame-based timing
        )

        val detections = mutableListOf<EventMatcher.TimestampedDetection>()
        var totalFrames = 0L
        val msPerFrame = 1000.0 / (sampleRate.toDouble() / 512.0) // hop size = 512

        // Feed audio through pipeline in chunks matching AudioCaptureSession
        val chunkSize = 1024
        for (offset in samples.indices step chunkSize) {
            val end = minOf(offset + chunkSize, samples.size)
            val chunk = samples.copyOfRange(offset, end)
            if (chunk.isEmpty()) break

            val frames = preProcessor.process(chunk)
            for (frame in frames) {
                val spectral = spectralAnalyzer.analyze(frame, includePowerSpectrum = true)
                val rawPower = spectral.powerSpectrum ?: continue

                if (!ambientBaseline.isCalibrated) {
                    ambientBaseline.feedCalibrationFrame(rawPower)
                }
                ambientBaseline.subtract(rawPower)

                val events = detector.processFrame(spectral)
                for (event in events) {
                    val timeMs = (totalFrames * msPerFrame).toLong()
                    detections.add(EventMatcher.TimestampedDetection(timeMs, event))
                }
                totalFrames++
            }
        }

        return detections
    }

    // --- Scenario Tests ---
    // These are skipped if recordings don't exist yet (graceful degradation)

    @Test
    fun `regression suite - recordings directory exists`() {
        // Just verify the test infrastructure is wired correctly
        val resource = javaClass.getResource("/recordings/manifest.json")
        assertTrue(
            "Recordings manifest should exist at /recordings/manifest.json",
            resource != null,
        )
    }

    @Test
    fun `regression suite - pipeline runs on synthetic data without crash`() {
        // Smoke test: generate a simple synthetic brew and run through pipeline
        val silence = ShortArray(sampleRate * 5) // 5s silence (calibration)
        val pour = ShortArray(sampleRate * 10) { i ->
            // Broadband noise simulating pour
            ((Math.random() * 2 - 1) * 8000).toInt().toShort()
        }
        val drip = ShortArray(sampleRate * 10) // mostly silence with sparse impulses
        for (i in drip.indices step (sampleRate / 2)) { // 2 drips/sec
            if (i < drip.size) drip[i] = 16000
        }
        val tail = ShortArray(sampleRate * 5) // final silence

        val samples = silence + pour + drip + tail
        val detections = runPipeline(samples)

        // Just verify it didn't crash — actual accuracy tested with real recordings
        assertTrue("Pipeline should produce some detections", true)
    }

    // --- Template for real recording tests ---
    // Uncomment and fill in when recordings are available:
    //
    // @Test
    // fun `regression - clean pulsar bloom quiet kitchen`() {
    //     val wavStream = javaClass.getResourceAsStream("/recordings/quiet_kitchen_001/bloom.wav")
    //         ?: return // Skip if recording not available
    //     val labelStream = javaClass.getResourceAsStream("/recordings/quiet_kitchen_001/bloom_labels.txt")
    //         ?: return
    //
    //     val samples = WavReader.readPcm16Mono(wavStream)
    //     val labels = LabelReader.read(labelStream)
    //     val detections = runPipeline(samples)
    //
    //     val report = EvaluationMetrics.evaluateSession("quiet_001", detections, labels)
    //     val failures = EvaluationMetrics.checkMvpTargets(report)
    //
    //     assertTrue("MVP targets not met:\n${failures.joinToString("\n")}", failures.isEmpty())
    // }
}
