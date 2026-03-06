package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.DetectorConfig
import com.adsamcik.starlitcoffee.data.model.DetectorState
import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import com.adsamcik.starlitcoffee.data.model.SpectralFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrewEventDetectorTest {

    private lateinit var detector: BrewEventDetector
    private var fakeTimeMs: Long = 0L

    @Before
    fun setup() {
        fakeTimeMs = 1000L
        detector = BrewEventDetector(
            config = DetectorConfig(
                // Use small min phase duration for testing
                minPhaseDurationFrames = 5,
                // Smaller trailing window for faster threshold adaptation
                trailingWindowSize = 10,
                dripRateWindowFrames = 50,
                drawdownCompleteFrames = 30,
            ),
            timeProvider = { fakeTimeMs },
        )
    }

    // --- Initial State ---

    @Test
    fun `initial state is IDLE`() {
        assertEquals(DetectorState.IDLE, detector.state)
    }

    @Test
    fun `initial drip rate is zero`() {
        assertEquals(0f, detector.dripRate, 0.01f)
    }

    // --- State Transitions ---

    @Test
    fun `silence frames keep state IDLE`() {
        repeat(50) {
            detector.processFrame(silenceFeatures())
            advanceTime()
        }
        assertEquals(DetectorState.IDLE, detector.state)
    }

    @Test
    fun `loud broadband onset transitions IDLE to POURING`() {
        // Feed some silence to establish baseline
        repeat(20) {
            detector.processFrame(silenceFeatures())
            advanceTime()
        }

        // Now feed loud pour-like features
        var foundPourStarted = false
        repeat(10) {
            val events = detector.processFrame(pourFeatures())
            advanceTime()
            if (events.any { it is BrewAudioEvent.PourStarted }) {
                foundPourStarted = true
            }
        }

        assertEquals(DetectorState.POURING, detector.state)
        assertTrue("Should emit PourStarted event", foundPourStarted)
    }

    @Test
    fun `energy drop during POURING transitions to DRIPPING`() {
        // Establish baseline, then pour
        feedSilence(20)
        feedPour(20)
        assertEquals(DetectorState.POURING, detector.state)

        // Now drop to dripping-level energy
        var foundPourStopped = false
        repeat(30) {
            val events = detector.processFrame(dripFeatures())
            advanceTime()
            if (events.any { it is BrewAudioEvent.PourStopped }) {
                foundPourStopped = true
            }
        }

        assertEquals(DetectorState.DRIPPING, detector.state)
        assertTrue("Should emit PourStopped event", foundPourStopped)
    }

    @Test
    fun `sustained silence during DRIPPING transitions to COMPLETE`() {
        feedSilence(20)
        feedPour(20)
        feedDrip(20)
        assertEquals(DetectorState.DRIPPING, detector.state)

        // Feed silence for longer than drawdown complete threshold
        var foundDrawdownComplete = false
        repeat(50) {
            val events = detector.processFrame(silenceFeatures())
            advanceTime()
            if (events.any { it is BrewAudioEvent.DrawdownComplete }) {
                foundDrawdownComplete = true
            }
        }

        assertEquals(DetectorState.COMPLETE, detector.state)
        assertTrue("Should emit DrawdownComplete event", foundDrawdownComplete)
    }

    @Test
    fun `pour restart during DRIPPING transitions back to POURING`() {
        feedSilence(20)
        feedPour(20)
        feedDrip(20)
        assertEquals(DetectorState.DRIPPING, detector.state)

        // Resume pouring
        var foundPourStarted = false
        repeat(10) {
            val events = detector.processFrame(pourFeatures())
            advanceTime()
            if (events.any { it is BrewAudioEvent.PourStarted }) {
                foundPourStarted = true
            }
        }

        assertEquals(DetectorState.POURING, detector.state)
        assertTrue("Should emit PourStarted on resume", foundPourStarted)
    }

    @Test
    fun `COMPLETE is terminal state`() {
        feedSilence(20)
        feedPour(20)
        feedDrip(20)
        feedSilence(50) // trigger complete

        assertEquals(DetectorState.COMPLETE, detector.state)

        // Feeding more pour does NOT change state
        repeat(20) {
            detector.processFrame(pourFeatures())
            advanceTime()
        }

        assertEquals(DetectorState.COMPLETE, detector.state)
    }

    // --- Drip Detection ---

    @Test
    fun `drip impulses emit DripDetected events during DRIPPING`() {
        feedSilence(20)
        feedPour(20)

        // Transition to dripping
        repeat(30) {
            detector.processFrame(dripFeatures())
            advanceTime()
        }

        assertEquals(DetectorState.DRIPPING, detector.state)

        // Now feed individual drip impulses interspersed with silence
        var dripCount = 0
        repeat(50) { i ->
            // Drip every 10 frames
            val features = if (i % 10 == 0) dripImpulseFeatures() else silenceFeatures()
            val events = detector.processFrame(features)
            advanceTime()
            dripCount += events.count { it is BrewAudioEvent.DripDetected }
        }

        assertTrue("Should detect at least 1 drip, got $dripCount", dripCount >= 1)
    }

    // --- Noise Floor ---

    @Test
    fun `noise floor adapts to ambient level`() {
        val initialFloor = detector.noiseFloorDb[FrequencyBand.POUR] ?: -40f

        // Feed ambient noise louder than initial floor
        repeat(100) {
            detector.processFrame(ambientNoiseFeatures(-25f))
            advanceTime()
        }

        val adaptedFloor = detector.noiseFloorDb[FrequencyBand.POUR] ?: -40f
        assertTrue(
            "Noise floor should adapt upward from $initialFloor toward -25, got $adaptedFloor",
            adaptedFloor > initialFloor,
        )
    }

    @Test
    fun `noise floor freezes during water signature detection`() {
        // Establish baseline with ambient (high tilt = no water)
        repeat(50) {
            detector.processFrame(silenceFeatures())
            advanceTime()
        }
        val floorBefore = detector.noiseFloorDb[FrequencyBand.POUR] ?: -40f

        // Feed pour features with low spectral tilt (water signature = broadband)
        repeat(50) {
            detector.processFrame(pourFeatures())
            advanceTime()
        }

        val floorAfter = detector.noiseFloorDb[FrequencyBand.POUR] ?: -40f

        // POUR band noise floor should not have jumped significantly
        // (water signature detection should freeze it)
        val change = kotlin.math.abs(floorAfter - floorBefore)
        assertTrue(
            "POUR noise floor should be frozen during water, change was $change dB",
            change < 10f,
        )
    }

    // --- Reset ---

    @Test
    fun `reset returns to IDLE`() {
        feedSilence(20)
        feedPour(20)
        assertEquals(DetectorState.POURING, detector.state)

        detector.reset()
        assertEquals(DetectorState.IDLE, detector.state)
        assertEquals(0L, detector.frameCount)
        assertEquals(0f, detector.dripRate, 0.01f)
    }

    // --- Noise Robustness ---

    @Test
    fun `speech with high CPP does not trigger POURING`() {
        feedSilence(20)

        // Feed speech-like features (loud but tonal with strong pitch)
        repeat(30) {
            detector.processFrame(speechFeatures())
            advanceTime()
        }

        assertEquals(
            "Speech should NOT trigger POURING (cepstral veto)",
            DetectorState.IDLE,
            detector.state,
        )
    }

    @Test
    fun `pour with high flatness and coincidence triggers POURING`() {
        feedSilence(20)
        feedPour(20)

        assertEquals(
            "Broadband water with high flatness and 5/5 bands should trigger POURING",
            DetectorState.POURING,
            detector.state,
        )
    }

    // --- Full Brew Scenario ---

    @Test
    fun `full brew scenario produces correct event sequence`() {
        val allEvents = mutableListOf<BrewAudioEvent>()

        // Phase 1: Ambient silence
        feedSilence(30, allEvents)

        // Phase 2: First pour
        feedPour(30, allEvents)

        // Phase 3: Dripping after pour
        feedDrip(30, allEvents)

        // Phase 4: Second pour (refill)
        feedPour(30, allEvents)

        // Phase 5: Final dripping
        feedDrip(30, allEvents)

        // Phase 6: Drawdown complete (silence)
        feedSilence(60, allEvents)

        // Verify event sequence contains expected types
        val eventTypes = allEvents.map { it::class.simpleName }
        assertTrue("Should have PourStarted", eventTypes.contains("PourStarted"))
        assertTrue("Should have PourStopped", eventTypes.contains("PourStopped"))
        assertTrue("Should end with DrawdownComplete", eventTypes.contains("DrawdownComplete"))
    }

    // --- RingBuffer ---

    @Test
    fun `RingBuffer percentile works correctly`() {
        val buffer = BrewEventDetector.RingBuffer(10)
        for (i in 1..10) buffer.add(i.toFloat())

        // 20th percentile of [1,2,3,4,5,6,7,8,9,10] → index 1 → 2.0
        assertEquals(2f, buffer.percentile(0.2f), 0.01f)

        // 50th percentile → index 4 → 5.0
        assertEquals(5f, buffer.percentile(0.5f), 0.01f)
    }

    @Test
    fun `RingBuffer wraps around correctly`() {
        val buffer = BrewEventDetector.RingBuffer(5)
        for (i in 1..8) buffer.add(i.toFloat())

        // Should contain [4,5,6,7,8]
        assertEquals(5, buffer.count)
        assertEquals(6f, buffer.mean(), 0.01f)
    }

    @Test
    fun `RingBuffer stdDev is correct`() {
        val buffer = BrewEventDetector.RingBuffer(5)
        // All same value → stdDev = 0
        repeat(5) { buffer.add(10f) }
        assertEquals(0f, buffer.stdDev(), 0.01f)
    }

    // --- Helpers ---

    private fun advanceTime(ms: Long = 12L) {
        fakeTimeMs += ms
    }

    private fun silenceFeatures(): SpectralFeatures = SpectralFeatures(
        bandEnergyDb = FrequencyBand.entries.associateWith { -35f },
        spectralFlux = FrequencyBand.entries.associateWith { 0f },
        spectralTilt = 20f,
        spectralFlatness = 0.05f, // Ambient: tonal/structured
        cepstralPeakProminence = 0f,
        bandCoincidenceCount = 1,
    )

    private fun pourFeatures(): SpectralFeatures = SpectralFeatures(
        bandEnergyDb = mapOf(
            FrequencyBand.POUR to -10f,
            FrequencyBand.DRIP to -15f,
            FrequencyBand.HIGH_MID to -25f,
        ),
        spectralFlux = mapOf(
            FrequencyBand.POUR to 15f,
            FrequencyBand.DRIP to 10f,
            FrequencyBand.HIGH_MID to 5f,
        ),
        spectralTilt = 4.0f,
        spectralFlatness = 0.5f, // Water: noise-like
        cepstralPeakProminence = 0.5f, // No pitch
        bandCoincidenceCount = 5, // All bands lit
    )

    /** Speech-like features: tonal, strong pitch, concentrated bands */
    private fun speechFeatures(): SpectralFeatures = SpectralFeatures(
        bandEnergyDb = mapOf(
            FrequencyBand.POUR to -10f,
            FrequencyBand.DRIP to -12f,
            FrequencyBand.HIGH_MID to -35f,
        ),
        spectralFlux = mapOf(
            FrequencyBand.POUR to 12f,
            FrequencyBand.DRIP to 8f,
            FrequencyBand.HIGH_MID to 1f,
        ),
        spectralTilt = 15f,
        spectralFlatness = 0.08f, // Tonal
        cepstralPeakProminence = 6.0f, // Strong pitch → veto
        bandCoincidenceCount = 2, // Only low bands
    )

    private fun dripFeatures(): SpectralFeatures = SpectralFeatures(
        bandEnergyDb = mapOf(
            FrequencyBand.POUR to -30f,
            FrequencyBand.DRIP to -28f,
            FrequencyBand.HIGH_MID to -40f,
        ),
        spectralFlux = mapOf(
            FrequencyBand.POUR to 0.5f,
            FrequencyBand.DRIP to 0.5f,
            FrequencyBand.HIGH_MID to 0.2f,
        ),
        spectralTilt = 20f,
        spectralFlatness = 0.1f,
        cepstralPeakProminence = 0f,
        bandCoincidenceCount = 2,
    )

    private fun dripImpulseFeatures(): SpectralFeatures = SpectralFeatures(
        bandEnergyDb = mapOf(
            FrequencyBand.POUR to -25f,
            FrequencyBand.DRIP to -18f,
            FrequencyBand.HIGH_MID to -35f,
        ),
        spectralFlux = mapOf(
            FrequencyBand.POUR to 8f,
            FrequencyBand.DRIP to 12f,
            FrequencyBand.HIGH_MID to 2f,
        ),
        spectralTilt = 12f,
        spectralFlatness = 0.2f,
        cepstralPeakProminence = 0f,
        bandCoincidenceCount = 3,
    )

    private fun ambientNoiseFeatures(level: Float): SpectralFeatures = SpectralFeatures(
        bandEnergyDb = FrequencyBand.entries.associateWith { level },
        spectralFlux = FrequencyBand.entries.associateWith { 0.1f },
        spectralTilt = 20f,
        spectralFlatness = 0.05f,
        cepstralPeakProminence = 0f,
        bandCoincidenceCount = 1,
    )

    private fun feedSilence(frames: Int, events: MutableList<BrewAudioEvent>? = null) {
        repeat(frames) {
            val e = detector.processFrame(silenceFeatures())
            events?.addAll(e)
            advanceTime()
        }
    }

    private fun feedPour(frames: Int, events: MutableList<BrewAudioEvent>? = null) {
        repeat(frames) {
            val e = detector.processFrame(pourFeatures())
            events?.addAll(e)
            advanceTime()
        }
    }

    private fun feedDrip(frames: Int, events: MutableList<BrewAudioEvent>? = null) {
        repeat(frames) {
            val e = detector.processFrame(dripFeatures())
            events?.addAll(e)
            advanceTime()
        }
    }
}
