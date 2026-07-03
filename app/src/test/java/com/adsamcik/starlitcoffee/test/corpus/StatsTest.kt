package com.adsamcik.starlitcoffee.test.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    @Test
    fun `wilson interval is null for zero trials`() {
        assertNull(Stats.wilson95(0, 0))
        assertNull(Stats.wilson95HalfWidthPct(3, 0))
    }

    @Test
    fun `wilson interval brackets the point estimate`() {
        val (lo, hi) = Stats.wilson95(15, 30)!!
        // p = 0.5; Wilson center ~0.5, interval symmetric-ish and inside 0..1.
        assertTrue(lo in 0.0..0.5)
        assertTrue(hi in 0.5..1.0)
    }

    @Test
    fun `small samples have wide intervals and large samples narrow`() {
        val narrowAtN30 = Stats.wilson95HalfWidthPct(15, 30)!!
        val narrowAtN400 = Stats.wilson95HalfWidthPct(200, 400)!!
        assertTrue("n=30 near 50% must be roughly a dozen pp wide", narrowAtN30 > 12.0)
        assertTrue("n=400 near 50% must tighten to ~5pp", narrowAtN400 < 6.0)
        assertTrue("more data must never widen the CI", narrowAtN400 < narrowAtN30)
    }

    @Test
    fun `half width stays within bounds at the extremes`() {
        // All-correct: interval hugs 1.0, so half-width is modest, never > 100.
        val hw = Stats.wilson95HalfWidthPct(20, 20)!!
        assertTrue(hw in 0.0..100.0)
    }

    @Test
    fun `interval clamps to the unit range`() {
        val (lo, hi) = Stats.wilson95(0, 5)!!
        assertEquals(0.0, lo, 1e-9)
        assertTrue(hi <= 1.0)
    }
}
