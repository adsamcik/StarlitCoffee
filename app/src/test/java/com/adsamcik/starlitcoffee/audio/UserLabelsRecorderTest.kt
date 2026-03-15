package com.adsamcik.starlitcoffee.audio

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class UserLabelsRecorderTest {

    private lateinit var recorder: UserLabelsRecorder
    private lateinit var tempFile: File

    @Before
    fun setup() {
        recorder = UserLabelsRecorder()
        tempFile = File.createTempFile("labels_test_", ".txt")
    }

    @After
    fun teardown() {
        recorder.close()
        tempFile.delete()
    }

    @Test
    fun `initial state is closed`() {
        assertFalse(recorder.isOpen)
        assertEquals(0, recorder.labelCount)
    }

    @Test
    fun `open creates file with recording_start label`() {
        recorder.open(tempFile, startTimeMs = 1000L)
        assertTrue(recorder.isOpen)
        recorder.close()

        val lines = tempFile.readLines().filter { !it.startsWith("#") }
        assertTrue("Should have recording_start", lines.any { it.contains("recording_start") })
    }

    @Test
    fun `markEvent writes Audacity-format label`() {
        recorder.open(tempFile, startTimeMs = 1000L)
        Thread.sleep(50) // Small delay so elapsed > 0
        recorder.markEvent("pour_start")
        recorder.close()

        val lines = tempFile.readLines().filter { !it.startsWith("#") }
        val pourLine = lines.find { it.contains("pour_start") }
        assertNotNull("Should contain pour_start label", pourLine)

        // Verify TSV format: time\ttime\tlabel
        val parts = pourLine!!.split("\t")
        assertEquals("Should have 3 tab-separated fields", 3, parts.size)
        assertEquals("Start and end time should match (point label)", parts[0], parts[1])
    }

    @Test
    fun `markProblem writes problem-prefixed label`() {
        recorder.open(tempFile, startTimeMs = 1000L)
        recorder.markProblem("detected_pour_too_early")
        recorder.close()

        val content = tempFile.readText()
        assertTrue("Should contain problem prefix", content.contains("problem:detected_pour_too_early"))
    }

    @Test
    fun `close writes recording_end label`() {
        recorder.open(tempFile, startTimeMs = 1000L)
        recorder.close()

        val content = tempFile.readText()
        assertTrue("Should contain recording_end", content.contains("recording_end"))
    }

    @Test
    fun `labels are readable by LabelReader`() {
        recorder.open(tempFile, startTimeMs = System.currentTimeMillis())
        Thread.sleep(20)
        recorder.markEvent("pour_start")
        Thread.sleep(20)
        recorder.markEvent("pour_stop")
        recorder.close()

        // Verify round-trip: our labels can be read back by LabelReader
        val labels = LabelReader.read(tempFile)
        val eventLabels = labels.filter { it.name != "recording_start" && it.name != "recording_end" }
        assertEquals("Should read 2 event labels", 2, eventLabels.size)
        assertEquals("pour_start", eventLabels[0].name)
        assertEquals("pour_stop", eventLabels[1].name)
        assertTrue("Labels should be point labels", eventLabels.all { it.isPointLabel })
        assertTrue("pour_stop should be after pour_start",
            eventLabels[1].startTimeS > eventLabels[0].startTimeS)
    }

    @Test
    fun `closed recorder ignores markEvent`() {
        val elapsed = recorder.markEvent("ignored")
        assertEquals(0.0, elapsed, 0.01)
    }

    @Test
    fun `labelCount tracks number of events`() {
        recorder.open(tempFile)
        // open() writes recording_start (count=1), then 3 more
        recorder.markEvent("a")
        recorder.markEvent("b")
        recorder.markEvent("c")
        assertEquals(4, recorder.labelCount)
        recorder.close()
    }

    @Test
    fun `header comments are written`() {
        recorder.open(tempFile)
        recorder.close()

        val lines = tempFile.readLines()
        assertTrue("Should have comment header", lines.any { it.startsWith("# Starlit Coffee") })
    }
}
