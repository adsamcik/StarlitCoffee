package com.adsamcik.starlitcoffee.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class BrewNotificationTimeTest {
    @Test
    fun `long timers omit seconds and do not pad minute units`() {
        assertEquals("12h50m", formatBrewNotificationTime((12 * 3_600L + 50 * 60L + 36) * 1_000, false))
        assertEquals("1h0m", formatBrewNotificationTime(3_600_000, false))
        assertEquals("50m", formatBrewNotificationTime(3_036_000, false))
    }

    @Test
    fun `final minutes include seconds without an early zero`() {
        assertEquals("2m59s", formatBrewNotificationTime(179_999, true))
        assertEquals("1m0s", formatBrewNotificationTime(60_000, true))
        assertEquals("59s", formatBrewNotificationTime(59_999, true))
        assertEquals("1s", formatBrewNotificationTime(1, true))
        assertEquals("0s", formatBrewNotificationTime(0, true))
    }

    @Test
    fun `refresh crosses the seconds threshold without waiting a minute`() {
        assertEquals(1L, brewNotificationRefreshDelay(180_000, true))
        assertEquals(37L, brewNotificationRefreshDelay(180_036, true))
        assertEquals(1_000L, brewNotificationRefreshDelay(179_999, true))
        assertEquals(60_000L, brewNotificationRefreshDelay(60_000, false))
        assertEquals(59_500L, brewNotificationRefreshDelay(60_500, false))
        assertEquals(36_001L, brewNotificationRefreshDelay(216_000, true))
    }
}
