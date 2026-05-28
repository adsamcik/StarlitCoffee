package com.adsamcik.starlitcoffee.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomGridTest {

    // --- Happy path ---

    @Test
    fun `parses a square 5x5 atlas at 256px`() {
        val result = parseBloomGrid(
            imageWidth = 1280,
            imageHeight = 1280,
            frameSizePx = 256,
        )
        result as BloomGridParseResult.Success
        assertEquals(BloomGrid(frameSizePx = 256, columns = 5, rows = 5), result.grid)
        assertEquals(25, result.grid.frameCount)
    }

    @Test
    fun `parses a wide non-square atlas`() {
        val result = parseBloomGrid(
            imageWidth = 256 * 9,
            imageHeight = 256 * 5,
            frameSizePx = 256,
        )
        result as BloomGridParseResult.Success
        assertEquals(9, result.grid.columns)
        assertEquals(5, result.grid.rows)
        assertEquals(45, result.grid.frameCount)
    }

    @Test
    fun `parses a custom frame size`() {
        val result = parseBloomGrid(
            imageWidth = 400,
            imageHeight = 600,
            frameSizePx = 200,
        )
        result as BloomGridParseResult.Success
        assertEquals(2, result.grid.columns)
        assertEquals(3, result.grid.rows)
    }

    // --- Expected grid lock ---

    @Test
    fun `accepts when expected columns and rows match derived values`() {
        val result = parseBloomGrid(
            imageWidth = 1280,
            imageHeight = 1280,
            frameSizePx = 256,
            expectedColumns = 5,
            expectedRows = 5,
        )
        assertTrue(result is BloomGridParseResult.Success)
    }

    @Test
    fun `rejects when expectedColumns disagrees with derived columns`() {
        val result = parseBloomGrid(
            imageWidth = 1280,
            imageHeight = 1280,
            frameSizePx = 256,
            expectedColumns = 9,
            expectedRows = 5,
        )
        result as BloomGridParseResult.Failure
        assertTrue(result.error is BloomGridError.ColumnsMismatch)
    }

    @Test
    fun `rejects when expectedRows disagrees with derived rows`() {
        val result = parseBloomGrid(
            imageWidth = 1280,
            imageHeight = 1280,
            frameSizePx = 256,
            expectedColumns = 5,
            expectedRows = 4,
        )
        result as BloomGridParseResult.Failure
        assertTrue(result.error is BloomGridError.RowsMismatch)
    }

    // --- Failure modes ---

    @Test
    fun `rejects empty dimensions`() {
        val zeroWidth = parseBloomGrid(imageWidth = 0, imageHeight = 1280, frameSizePx = 256)
        assertTrue((zeroWidth as BloomGridParseResult.Failure).error is BloomGridError.EmptyDimensions)
        val zeroHeight = parseBloomGrid(imageWidth = 1280, imageHeight = 0, frameSizePx = 256)
        assertTrue((zeroHeight as BloomGridParseResult.Failure).error is BloomGridError.EmptyDimensions)
        val negative = parseBloomGrid(imageWidth = -1, imageHeight = 1280, frameSizePx = 256)
        assertTrue((negative as BloomGridParseResult.Failure).error is BloomGridError.EmptyDimensions)
    }

    @Test
    fun `rejects non-positive frame size`() {
        val result = parseBloomGrid(imageWidth = 1280, imageHeight = 1280, frameSizePx = 0)
        result as BloomGridParseResult.Failure
        assertTrue(result.error is BloomGridError.FrameSizeNotPositive)
    }

    @Test
    fun `rejects image width not divisible by frame size`() {
        val result = parseBloomGrid(imageWidth = 1281, imageHeight = 1280, frameSizePx = 256)
        result as BloomGridParseResult.Failure
        val error = result.error as BloomGridError.WidthNotDivisible
        assertEquals(1281, error.width)
        assertEquals(256, error.frameSizePx)
    }

    @Test
    fun `rejects image height not divisible by frame size`() {
        val result = parseBloomGrid(imageWidth = 1280, imageHeight = 1281, frameSizePx = 256)
        result as BloomGridParseResult.Failure
        val error = result.error as BloomGridError.HeightNotDivisible
        assertEquals(1281, error.height)
    }

    // --- Source offset math ---

    @Test
    fun `sourceLeftOf and sourceTopOf compute correct frame origins`() {
        val grid = BloomGrid(frameSizePx = 256, columns = 5, rows = 5)
        // Frame 0 — top-left
        assertEquals(0, grid.sourceLeftOf(0))
        assertEquals(0, grid.sourceTopOf(0))
        // Frame 4 — top-right (last column of row 0)
        assertEquals(4 * 256, grid.sourceLeftOf(4))
        assertEquals(0, grid.sourceTopOf(4))
        // Frame 5 — start of row 1
        assertEquals(0, grid.sourceLeftOf(5))
        assertEquals(256, grid.sourceTopOf(5))
        // Frame 24 — bottom-right
        assertEquals(4 * 256, grid.sourceLeftOf(24))
        assertEquals(4 * 256, grid.sourceTopOf(24))
    }

    @Test
    fun `sourceLeftOf works for non-square grids`() {
        val grid = BloomGrid(frameSizePx = 100, columns = 3, rows = 4)
        // Frame 6 should be column 0 of row 2
        assertEquals(0, grid.sourceLeftOf(6))
        assertEquals(200, grid.sourceTopOf(6))
        // Frame 8 should be column 2 of row 2
        assertEquals(200, grid.sourceLeftOf(8))
        assertEquals(200, grid.sourceTopOf(8))
    }
}
