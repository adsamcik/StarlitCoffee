package com.adsamcik.starlitcoffee.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepScreenOnTest {

    // --- Happy path ---

    @Test
    fun `doubles a typical brew estimate`() {
        // 270s Pulsar target -> 540s safety window.
        assertEquals(540_000L, keepScreenOnTimeoutMillis(270))
    }

    @Test
    fun `doubles an estimate above the floor`() {
        // 90s estimate -> 180s, pure 2x since it is already above the 60s floor.
        assertEquals(180_000L, keepScreenOnTimeoutMillis(90))
    }

    // --- Floor protection ---

    @Test
    fun `floors a missing estimate to the minimum window`() {
        // A not-yet-computed estimate must not collapse to an instant timeout.
        assertEquals(120_000L, keepScreenOnTimeoutMillis(0))
    }

    @Test
    fun `floors a negative estimate to the minimum window`() {
        assertEquals(120_000L, keepScreenOnTimeoutMillis(-30))
    }

    @Test
    fun `sub-floor estimates are bumped to the floor before doubling`() {
        // 45s bloom is below the 60s floor, so it is raised to 60s -> 120s.
        assertEquals(120_000L, keepScreenOnTimeoutMillis(45))
        assertTrue(keepScreenOnTimeoutMillis(10) >= 120_000L)
    }
}
