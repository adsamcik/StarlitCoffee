package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagThumbnailFocusTest {

    @Test
    fun `labelRegion returns null for empty input`() {
        assertNull(BagThumbnailFocus.labelRegion(emptyList()))
    }

    @Test
    fun `labelRegion is the bounding box union of all rects`() {
        val rects = listOf(
            BagPhotoRect(0.1f, 0.2f, 0.3f, 0.4f),
            BagPhotoRect(0.5f, 0.1f, 0.7f, 0.6f),
        )
        val union = BagThumbnailFocus.labelRegion(rects)!!
        assertEquals(0.1f, union.leftFraction, 1e-4f)
        assertEquals(0.1f, union.topFraction, 1e-4f)
        assertEquals(0.7f, union.rightFraction, 1e-4f)
        assertEquals(0.6f, union.bottomFraction, 1e-4f)
    }

    @Test
    fun `squareCrop produces a pixel-square region centred on the focus`() {
        val focus = BagPhotoRect(0.4f, 0.4f, 0.6f, 0.5f)
        val crop = BagThumbnailFocus.squareCrop(focus, imageWidthPx = 1000, imageHeightPx = 2000)

        val widthPx = (crop.rightFraction - crop.leftFraction) * 1000
        val heightPx = (crop.bottomFraction - crop.topFraction) * 2000
        assertEquals(widthPx, heightPx, 1f)

        val centerX = (crop.leftFraction + crop.rightFraction) / 2f
        val centerY = (crop.topFraction + crop.bottomFraction) / 2f
        assertEquals(0.5f, centerX, 1e-3f)
        assertEquals(0.45f, centerY, 1e-3f)
    }

    @Test
    fun `squareCrop shifts the box inside the image instead of distorting it`() {
        // Focus hugging the left edge.
        val focus = BagPhotoRect(0.0f, 0.45f, 0.1f, 0.55f)
        val crop = BagThumbnailFocus.squareCrop(focus, imageWidthPx = 1000, imageHeightPx = 1000)

        assertEquals(0f, crop.leftFraction, 1e-4f)
        assertTrue(crop.rightFraction in 0f..1f)
        assertTrue(crop.topFraction in 0f..1f)
        assertTrue(crop.bottomFraction in 0f..1f)
        // Still square in pixels (square image → square normalized).
        val width = crop.rightFraction - crop.leftFraction
        val height = crop.bottomFraction - crop.topFraction
        assertEquals(width, height, 1e-3f)
    }

    @Test
    fun `squareCrop stays within bounds for a corner focus`() {
        val focus = BagPhotoRect(0.85f, 0.85f, 1.0f, 1.0f)
        val crop = BagThumbnailFocus.squareCrop(focus, imageWidthPx = 800, imageHeightPx = 800)

        assertTrue(crop.leftFraction >= 0f)
        assertTrue(crop.topFraction >= 0f)
        assertEquals(1.0f, crop.rightFraction, 1e-4f)
        assertEquals(1.0f, crop.bottomFraction, 1e-4f)
        assertTrue(crop.rightFraction > crop.leftFraction)
        assertTrue(crop.bottomFraction > crop.topFraction)
    }

    @Test
    fun `squareCrop returns the focus unchanged for non-positive image size`() {
        val focus = BagPhotoRect(0.2f, 0.2f, 0.4f, 0.4f)
        assertEquals(focus, BagThumbnailFocus.squareCrop(focus, 0, 0))
    }

    @Test
    fun `paddedRegion expands about the centre keeping aspect`() {
        val focus = BagPhotoRect(0.4f, 0.4f, 0.6f, 0.5f)
        val padded = BagThumbnailFocus.paddedRegion(focus, expansion = 1.5f)

        // Centre is preserved.
        assertEquals(0.5f, (padded.leftFraction + padded.rightFraction) / 2f, 1e-4f)
        assertEquals(0.45f, (padded.topFraction + padded.bottomFraction) / 2f, 1e-4f)
        // Each dimension grew by ~1.5x (0.2 -> 0.3 wide, 0.1 -> 0.15 tall).
        assertEquals(0.3f, padded.rightFraction - padded.leftFraction, 1e-4f)
        assertEquals(0.15f, padded.bottomFraction - padded.topFraction, 1e-4f)
    }

    @Test
    fun `paddedRegion clamps to the unit box at the edges`() {
        val focus = BagPhotoRect(0.0f, 0.0f, 0.2f, 0.2f)
        val padded = BagThumbnailFocus.paddedRegion(focus, expansion = 2.0f)

        assertEquals(0f, padded.leftFraction, 1e-4f)
        assertEquals(0f, padded.topFraction, 1e-4f)
        assertTrue(padded.rightFraction <= 1f)
        assertTrue(padded.bottomFraction <= 1f)
        assertTrue(padded.rightFraction > padded.leftFraction)
    }

    @Test
    fun `paddedRegion returns the focus unchanged when expansion is not greater than one`() {
        val focus = BagPhotoRect(0.2f, 0.2f, 0.4f, 0.4f)
        assertEquals(focus, BagThumbnailFocus.paddedRegion(focus, expansion = 1.0f))
        assertEquals(focus, BagThumbnailFocus.paddedRegion(focus, expansion = 0.5f))
    }
}
