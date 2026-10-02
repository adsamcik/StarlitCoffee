package com.adsamcik.starlitcoffee.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomAnimationTimingTest {
    @Test
    fun `growth starts promptly but opening stays in the final quarter`() {
        val elapsed = listOf(0f, 0.10f, 0.25f, 0.50f, 0.75f, 0.90f, 1f)
        val expectedFrames = listOf(0, 2, 4, 8, 14, 20, 24)
        assertEquals(expectedFrames, elapsed.map { resolveBloomFrameIndex(it, 25) })
    }

    @Test
    fun `completed pose appears only when the countdown finishes`() {
        listOf(15, 30, 45, 60, 120).forEach { duration ->
            assertTrue(resolveBloomFrameIndex(resolveBloomProgress(1, duration), 25) < 24)
            assertEquals(24, resolveBloomFrameIndex(resolveBloomProgress(0, duration), 25))
        }
        assertEquals(23, resolveBloomFrameIndex(0.99999f, 25))
    }

    @Test
    fun `growth visits every pose in order without moving backwards`() {
        val frames = (0..10_000).map { resolveBloomFrameIndex(it / 10_000f, 25) }
        assertEquals((0..24).toList(), frames.distinct())
        assertTrue(frames.zipWithNext().all { (before, after) -> after - before in 0..1 })
        assertTrue(frames.dropLast(1).none { it == 24 })
    }

    @Test
    fun `paused or restored countdown resolves the same pose without accumulated time`() {
        val halfway = resolveBloomProgress(30, 60)
        assertEquals(8, resolveBloomFrameIndex(halfway, 25))
        repeat(10) { assertEquals(8, resolveBloomFrameIndex(resolveBloomProgress(30, 60), 25)) }
        assertEquals(0, resolveBloomFrameIndex(resolveBloomProgress(60, 60), 25))
    }

    @Test
    fun `missing countdown and invalid duration stay at the seed`() {
        assertEquals(0f, resolveBloomProgress(null, 45), 0f)
        assertEquals(0f, resolveBloomProgress(0, 0), 0f)
        assertEquals(0f, resolveBloomProgress(0, -1), 0f)
    }

    @Test
    fun `out of range countdown is clamped without overflowing`() {
        assertEquals(0f, resolveBloomProgress(Int.MAX_VALUE, 45), 0f)
        assertEquals(1f, resolveBloomProgress(Int.MIN_VALUE, 45), 0f)
        assertEquals(0, resolveBloomFrameIndex(-1f, 25))
        assertEquals(24, resolveBloomFrameIndex(2f, 25))
    }

    @Test
    fun `single pose atlas has a stable frame`() {
        assertEquals(0, resolveBloomFrameIndex(0f, 1))
        assertEquals(0, resolveBloomFrameIndex(0.5f, 1))
        assertEquals(0, resolveBloomFrameIndex(1f, 1))
    }
}
