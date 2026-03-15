package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import com.adsamcik.starlitcoffee.data.model.DetectorState
import com.adsamcik.starlitcoffee.data.model.FrequencyBand
import com.adsamcik.starlitcoffee.data.model.SpectralFeatures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FlightRecorderTest {

    private lateinit var recorder: FlightRecorder
    private lateinit var tempFile: File

    @Before
    fun setup() {
        recorder = FlightRecorder(intervalMs = 10) // Short interval for testing
        tempFile = File.createTempFile("flight_test_", ".jsonl")
    }

    @After
    fun teardown() {
        recorder.close()
        tempFile.delete()
    }

    private fun sampleFeatures() = SpectralFeatures(
        bandEnergyDb = mapOf(
            FrequencyBand.POUR to -12f,
            FrequencyBand.DRIP_LOW to -18f,
            FrequencyBand.DRIP_HIGH to -24f,
            FrequencyBand.HIGH_MID to -30f,
        ),
        spectralFlux = FrequencyBand.entries.associateWith { 2f },
        spectralTilt = 4f,
        spectralFlatness = 0.15f,
        cepstralPeakProminence = 0.5f,
        bandCoincidenceCount = 4,
    )

    private fun sampleSnapshot(
        detectorState: DetectorState = DetectorState.IDLE,
        noiseFloorDb: Map<FrequencyBand, Float> = emptyMap(),
        dripRate: Float = 0f,
        rmsDb: Float = -40f,
        brewPhaseLabel: String = "",
        trajectoryPhase: String = "Unknown",
        brewConfidence: Float = 0f,
        baselineCalibrated: Boolean = false,
        events: List<BrewAudioEvent> = emptyList(),
    ) = FlightRecorder.Snapshot(
        spectralFeatures = sampleFeatures(),
        detectorState = detectorState,
        noiseFloorDb = noiseFloorDb,
        dripRate = dripRate,
        rmsDb = rmsDb,
        brewPhaseLabel = brewPhaseLabel,
        trajectoryPhase = trajectoryPhase,
        brewConfidence = brewConfidence,
        baselineCalibrated = baselineCalibrated,
        events = events,
    )

    @Test
    fun `initial state is closed`() {
        assertFalse(recorder.isOpen)
    }

    @Test
    fun `open creates file and sets state`() {
        recorder.open(tempFile)
        assertTrue(recorder.isOpen)
        assertTrue(tempFile.exists())
    }

    @Test
    fun `close sets state to closed`() {
        recorder.open(tempFile)
        recorder.close()
        assertFalse(recorder.isOpen)
    }

    @Test
    fun `recordSnapshot writes JSON line`() {
        recorder.open(tempFile)

        recorder.recordSnapshot(sampleSnapshot(
            detectorState = DetectorState.POURING,
            noiseFloorDb = FrequencyBand.entries.associateWith { -35f },
            rmsDb = -15f,
            brewPhaseLabel = "Pour 1/3",
            trajectoryPhase = "Pouring",
            brewConfidence = 0.8f,
            baselineCalibrated = true,
        ))
        recorder.close()

        val lines= tempFile.readLines()
        assertEquals("Should write exactly 1 line", 1, lines.size)

        val line = lines[0]
        assertTrue("Should be JSON object", line.startsWith("{") && line.endsWith("}"))
        assertTrue("Should contain state", line.contains("\"state\":\"POURING\""))
        assertTrue("Should contain phase", line.contains("\"phase\":\"Pour 1/3\""))
        assertTrue("Should contain rmsDb", line.contains("\"rmsDb\":-15.0"))
        assertTrue("Should contain flatness", line.contains("\"flatness\":0.15"))
        assertTrue("Should contain confidence", line.contains("\"confidence\":0.8"))
    }

    @Test
    fun `events are included in snapshot`() {
        recorder.open(tempFile)

        recorder.recordSnapshot(sampleSnapshot(
            detectorState = DetectorState.POURING,
            noiseFloorDb = FrequencyBand.entries.associateWith { -35f },
            rmsDb = -15f,
            brewPhaseLabel = "Pour",
            trajectoryPhase = "Pouring",
            brewConfidence = 0.7f,
            baselineCalibrated = true,
            events = listOf(
                BrewAudioEvent.PourStarted(5.2f),
            ),
        ))
        recorder.close()

        val line = tempFile.readLines().first()
        assertTrue("Should contain events array", line.contains("\"events\":["))
        assertTrue("Should contain PourStarted", line.contains("PourStarted"))
    }

    @Test
    fun `throttles snapshots by interval`() {
        recorder = FlightRecorder(intervalMs = 100)
        recorder.open(tempFile)

        // Write multiple snapshots rapidly
        repeat(10) {
            recorder.recordSnapshot(sampleSnapshot())
        }
        recorder.close()

        val lines = tempFile.readLines()
        // With 100ms throttle and near-instant writes, should get only 1 snapshot
        assertTrue("Should throttle to ~1 snapshot, got ${lines.size}", lines.size <= 2)
    }

    @Test
    fun `multiple snapshots produce multiple lines`() {
        recorder.open(tempFile)

        repeat(3) {
            Thread.sleep(15) // Exceed the 10ms interval
            recorder.recordSnapshot(sampleSnapshot())
        }
        recorder.close()

        val lines = tempFile.readLines()
        assertTrue("Should write multiple lines, got ${lines.size}", lines.size >= 2)
    }

    @Test
    fun `closed recorder does not write`() {
        val result = recorder.recordSnapshot(sampleSnapshot())
        assertFalse(result)
    }
}
