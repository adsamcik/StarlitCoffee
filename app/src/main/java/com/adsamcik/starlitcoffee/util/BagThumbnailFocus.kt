package com.adsamcik.starlitcoffee.util

/**
 * Pure geometry for picking the thumbnail crop region of a coffee-bag photo.
 *
 * The scan pipeline reads the bag label via OCR; the union of the text-block
 * rectangles is the region the analysis actually "looked at" (the label), which
 * makes a far better square thumbnail than a blind centre-crop of the whole
 * photo (often mostly background or a hand). [labelRegion] computes that union
 * and [squareCrop] turns it into a centred, image-fitting square crop.
 *
 * Everything here is deterministic and free of Android dependencies so it can
 * be unit-tested without a device.
 */
object BagThumbnailFocus {

    /** Default extra margin applied around the detected label region. */
    const val DEFAULT_EXPANSION = 1.15f

    /**
     * Bounding-box union of the normalized text rectangles — the label region.
     * Returns null when [rects] is empty or degenerate.
     */
    fun labelRegion(rects: List<BagPhotoRect>): BagPhotoRect? {
        if (rects.isEmpty()) return null
        val left = rects.minOf { it.leftFraction }
        val top = rects.minOf { it.topFraction }
        val right = rects.maxOf { it.rightFraction }
        val bottom = rects.maxOf { it.bottomFraction }
        if (right <= left || bottom <= top) return null
        return BagPhotoRect(left, top, right, bottom)
    }

    /**
     * Expand [focus] about its centre by [expansion] (keeping aspect, NOT
     * squared), clamped to the `[0, 1]` unit box. Used to add a little margin
     * around the detected label before cropping it for the vision pass, so a
     * tight OCR union doesn't shave off edge glyphs or a logo. Returns [focus]
     * unchanged when [expansion] <= 1 or the result would be degenerate.
     */
    fun paddedRegion(focus: BagPhotoRect, expansion: Float = DEFAULT_EXPANSION): BagPhotoRect {
        if (expansion <= 1f) return focus
        val centerX = (focus.leftFraction + focus.rightFraction) / 2f
        val centerY = (focus.topFraction + focus.bottomFraction) / 2f
        val halfWidth = (focus.rightFraction - focus.leftFraction) / 2f * expansion
        val halfHeight = (focus.bottomFraction - focus.topFraction) / 2f * expansion
        val left = (centerX - halfWidth).coerceIn(0f, 1f)
        val right = (centerX + halfWidth).coerceIn(0f, 1f)
        val top = (centerY - halfHeight).coerceIn(0f, 1f)
        val bottom = (centerY + halfHeight).coerceIn(0f, 1f)
        if (right <= left || bottom <= top) return focus
        return BagPhotoRect(left, top, right, bottom)
    }

    /**
     * A centred, pixel-square crop (normalized fractions) around [focus],
     * suitable for a square thumbnail slot. The square side is the larger of the
     * focus dimensions times [expansion], clamped so the crop fits inside the
     * image, and the box is shifted (not squashed) to stay within [0, 1].
     *
     * [imageWidthPx] / [imageHeightPx] are needed because a square in normalized
     * space is not square in pixels unless the source is itself square.
     */
    @Suppress("ReturnCount")
    fun squareCrop(
        focus: BagPhotoRect,
        imageWidthPx: Int,
        imageHeightPx: Int,
        expansion: Float = DEFAULT_EXPANSION,
    ): BagPhotoRect {
        if (imageWidthPx <= 0 || imageHeightPx <= 0) return focus

        val centerX = (focus.leftFraction + focus.rightFraction) / 2f
        val centerY = (focus.topFraction + focus.bottomFraction) / 2f

        val focusWidthPx = (focus.rightFraction - focus.leftFraction) * imageWidthPx
        val focusHeightPx = (focus.bottomFraction - focus.topFraction) * imageHeightPx
        val maxSidePx = minOf(imageWidthPx, imageHeightPx).toFloat()
        val sidePx = (maxOf(focusWidthPx, focusHeightPx) * expansion).coerceIn(1f, maxSidePx)

        val halfWidthFrac = (sidePx / imageWidthPx) / 2f
        val halfHeightFrac = (sidePx / imageHeightPx) / 2f

        var left = centerX - halfWidthFrac
        var right = centerX + halfWidthFrac
        var top = centerY - halfHeightFrac
        var bottom = centerY + halfHeightFrac

        // Shift the box back inside the image rather than clamping a single edge
        // (which would distort the square).
        if (left < 0f) { right -= left; left = 0f }
        if (right > 1f) { left -= (right - 1f); right = 1f }
        if (top < 0f) { bottom -= top; top = 0f }
        if (bottom > 1f) { top -= (bottom - 1f); bottom = 1f }

        left = left.coerceIn(0f, 1f)
        right = right.coerceIn(0f, 1f)
        top = top.coerceIn(0f, 1f)
        bottom = bottom.coerceIn(0f, 1f)
        if (right <= left || bottom <= top) return focus
        return BagPhotoRect(left, top, right, bottom)
    }
}
