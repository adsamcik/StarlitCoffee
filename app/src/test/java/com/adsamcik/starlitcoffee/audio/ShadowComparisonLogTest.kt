package com.adsamcik.starlitcoffee.audio

import com.adsamcik.starlitcoffee.data.model.BrewAudioEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ShadowComparisonLogTest {

    private lateinit var log: ShadowComparisonLog
    private lateinit var tempFile: File

    @Before
    fun setup() {
        log = ShadowComparisonLog()
        tempFile = File.createTempFile("shadow_test_", ".jsonl")
    }

    @After
    fun teardown() {
        if (log.isOpen) log.close()
        tempFile.delete()
    }

    // --- Initial state ---

    @Test
    fun `initial state is closed`() {
        assertFalse(log.isOpen)
    }

    // --- Open / close lifecycle ---

    @Test
    fun `open creates file and sets state`() {
        log.open(tempFile)
        assertTrue(log.isOpen)
        assertTrue(tempFile.exists())
    }

    // --- Recording ---

    @Test
    fun `recordUserTap writes JSON line`() {
        log.open(tempFile)
        log.recordUserTap(0, "Bloom pour")
        val summary = log.close()

        val lines = tempFile.readLines()
        val tapLine = lines.first { it.contains("\"type\":\"user_tap\"") }
        assertTrue(tapLine.contains("\"phase\":0"))
        assertTrue(tapLine.contains("\"label\":\"Bloom pour\""))
        assertNotNull(summary)
        assertEquals(1, summary!!.userTapCount)
    }

    @Test
    fun `recordDetectorEvent writes JSON line`() {
        log.open(tempFile)
        log.recordDetectorEvent(BrewAudioEvent.PourStarted(confidenceDb = -12.5f))
        val summary = log.close()

        val lines = tempFile.readLines()
        val detLine = lines.first { it.contains("\"type\":\"detector\"") }
        assertTrue(detLine.contains("\"event\":\"PourStarted\""))
        assertTrue(detLine.contains("confidence=-12.5"))
        assertNotNull(summary)
        assertEquals(1, summary!!.detectorEventCount)
    }

    // --- Summary matching ---

    @Test
    fun `close returns summary with matched events`() {
        log.open(tempFile)
        // Record tap and detector event close together — they occur within ms of each other
        log.recordUserTap(1, "Main pour")
        log.recordDetectorEvent(BrewAudioEvent.PourStarted(confidenceDb = -10f))

        val summary = log.close()

        assertNotNull(summary)
        assertEquals(1, summary!!.userTapCount)
        assertEquals(1, summary.detectorEventCount)
        assertEquals(1, summary.matchedCount)
        assertEquals(0, summary.unmatchedTaps)
        assertEquals(0, summary.falsePositives)
    }

    @Test
    fun `close returns summary with unmatched taps`() {
        log.open(tempFile)
        log.recordUserTap(0, "Bloom pour")
        // No detector event recorded — tap is unmatched
        // Sleep to ensure we're past the tolerance window? No — even without sleep
        // there's no detector event to match, so unmatchedTaps must be 1.
        val summary = log.close()

        assertNotNull(summary)
        assertEquals(1, summary!!.userTapCount)
        assertEquals(0, summary.detectorEventCount)
        assertEquals(0, summary.matchedCount)
        assertEquals(1, summary.unmatchedTaps)
    }

    @Test
    fun `close returns summary with false positives`() {
        log.open(tempFile)
        // No user tap recorded — detector event is a false positive
        log.recordDetectorEvent(BrewAudioEvent.DripDetected(energyDb = -20f))

        val summary = log.close()

        assertNotNull(summary)
        assertEquals(0, summary!!.userTapCount)
        assertEquals(1, summary.detectorEventCount)
        assertEquals(0, summary.matchedCount)
        assertEquals(0, summary.unmatchedTaps)
        assertEquals(1, summary.falsePositives)
    }

    @Test
    fun `summary computes mean and median delta`() {
        log.open(tempFile)

        // Record three matched pairs with controlled timing.
        // All events occur within ms of each other, so deltas ≈ 0.
        log.recordUserTap(0, "Phase 0")
        log.recordDetectorEvent(BrewAudioEvent.PourStarted(confidenceDb = -10f))
        Thread.sleep(15)
        log.recordUserTap(1, "Phase 1")
        log.recordDetectorEvent(BrewAudioEvent.PourStopped(durationMs = 5000))
        Thread.sleep(15)
        log.recordUserTap(2, "Phase 2")
        log.recordDetectorEvent(BrewAudioEvent.DrawdownComplete(totalDrainTimeMs = 10000))

        val summary = log.close()

        assertNotNull(summary)
        assertEquals(3, summary!!.matchedCount)
        assertNotNull(summary.meanDeltaMs)
        assertNotNull(summary.medianDeltaMs)
        // Deltas should be very small since tap+event happen back-to-back
        assertTrue("Mean delta should be small", kotlin.math.abs(summary.meanDeltaMs!!) < 1000)
        assertTrue("Median delta should be small", kotlin.math.abs(summary.medianDeltaMs!!) < 1000)
    }

    // --- Closed log behavior ---

    @Test
    fun `closed log ignores records`() {
        // Log is not open — calls should be no-ops
        log.recordUserTap(0, "Should be ignored")
        log.recordDetectorEvent(BrewAudioEvent.PourStarted(confidenceDb = -10f))

        // close() on an already-closed log returns null
        val summary = log.close()
        assertNull(summary)
    }
}
