package com.adsamcik.starlitcoffee.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DateParserTest {

    @Test
    fun `parses ISO date`() {
        val millis = DateParser.parse("2026-02-20")
        assertNotNull(millis)
        val cal = Calendar.getInstance().apply { timeInMillis = millis!! }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `parses European dot format`() {
        val millis = DateParser.parse("20.02.2026")
        assertNotNull(millis)
    }

    @Test
    fun `parses short year European`() {
        val millis = DateParser.parse("09.12.25")
        assertNotNull(millis)
        val cal = Calendar.getInstance().apply { timeInMillis = millis!! }
        assertEquals(2025, cal.get(Calendar.YEAR))
    }

    @Test
    fun `parses slash format`() {
        val millis = DateParser.parse("15/01/2026")
        assertNotNull(millis)
    }

    @Test
    fun `parses month name format`() {
        val millis = DateParser.parse("January 15, 2026")
        assertNotNull(millis)
    }

    @Test
    fun `parses day month year format`() {
        val millis = DateParser.parse("15 January 2026")
        assertNotNull(millis)
    }

    @Test
    fun `returns null for garbage`() {
        assertNull(DateParser.parse("not a date"))
        assertNull(DateParser.parse(""))
        assertNull(DateParser.parse(null))
    }

    @Test
    fun `format produces readable string`() {
        val millis = DateParser.parse("2026-02-20")!!
        val formatted = DateParser.format(millis)
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains("20"))
    }

    @Test
    fun `freshness RESTING for recent roast`() {
        val threeDaysAgo = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        assertEquals(DateParser.Freshness.RESTING, DateParser.assessFreshness(threeDaysAgo))
    }

    @Test
    fun `freshness PEAK for 14 day old roast`() {
        val fourteenDaysAgo = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
        assertEquals(DateParser.Freshness.PEAK, DateParser.assessFreshness(fourteenDaysAgo))
    }

    @Test
    fun `freshness STALE for 75 day old roast`() {
        val old = System.currentTimeMillis() - 75L * 24 * 60 * 60 * 1000
        assertEquals(DateParser.Freshness.STALE, DateParser.assessFreshness(old))
    }

    @Test
    fun `isPastExpiry returns true for past date`() {
        val past = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        assertTrue(DateParser.isPastExpiry(past))
    }

    @Test
    fun `isPastExpiry returns false for future date`() {
        val future = System.currentTimeMillis() + 24 * 60 * 60 * 1000
        assertFalse(DateParser.isPastExpiry(future))
    }
}
