package com.adsamcik.starlitcoffee.audio

import org.junit.Test
import org.junit.Assert.*

class LabelReaderTest {

    @Test
    fun `reads point labels`() {
        val input = """
            5.120000	5.120000	pour_start
            18.450000	18.450000	pour_stop
        """.trimIndent().byteInputStream()

        val labels = LabelReader.read(input)
        assertEquals(2, labels.size)
        assertEquals("pour_start", labels[0].name)
        assertEquals(5.12, labels[0].startTimeS, 0.0001)
        assertEquals(5120L, labels[0].startTimeMs)
        assertTrue(labels[0].isPointLabel)

        assertEquals("pour_stop", labels[1].name)
        assertEquals(18.45, labels[1].startTimeS, 0.0001)
        assertEquals(18450L, labels[1].startTimeMs)
    }

    @Test
    fun `reads region labels`() {
        val input = "5.0\t18.0\tpouring_phase".byteInputStream()
        val labels = LabelReader.read(input)

        assertEquals(1, labels.size)
        assertFalse(labels[0].isPointLabel)
        assertEquals(5000L, labels[0].startTimeMs)
        assertEquals(18000L, labels[0].endTimeMs)
        assertEquals("pouring_phase", labels[0].name)
    }

    @Test
    fun `skips blank lines and comments`() {
        val input = """
            # This is a comment
            5.0	5.0	pour_start
            
            18.0	18.0	pour_stop
        """.trimIndent().byteInputStream()

        val labels = LabelReader.read(input)
        assertEquals(2, labels.size)
        assertEquals("pour_start", labels[0].name)
        assertEquals("pour_stop", labels[1].name)
    }

    @Test
    fun `handles empty file`() {
        val labels = LabelReader.read("".byteInputStream())
        assertTrue(labels.isEmpty())
    }

    @Test
    fun `preserves label names with special characters`() {
        val input = "1.0\t1.0\tpour_start (loud)".byteInputStream()
        val labels = LabelReader.read(input)

        assertEquals(1, labels.size)
        assertEquals("pour_start (loud)", labels[0].name)
    }

    @Test
    fun `handles labels with tabs in name`() {
        val input = "1.0\t2.0\tname\twith\ttabs".byteInputStream()
        val labels = LabelReader.read(input)

        assertEquals(1, labels.size)
        assertEquals("name\twith\ttabs", labels[0].name)
    }

    @Test
    fun `computes milliseconds from fractional seconds`() {
        val input = "0.001\t0.001\ttiny".byteInputStream()
        val labels = LabelReader.read(input)

        assertEquals(1L, labels[0].startTimeMs)
    }

    @Test
    fun `handles zero timestamps`() {
        val input = "0.000000\t0.000000\tambient_baseline".byteInputStream()
        val labels = LabelReader.read(input)

        assertEquals(1, labels.size)
        assertEquals(0L, labels[0].startTimeMs)
        assertEquals(0L, labels[0].endTimeMs)
        assertTrue(labels[0].isPointLabel)
    }
}
