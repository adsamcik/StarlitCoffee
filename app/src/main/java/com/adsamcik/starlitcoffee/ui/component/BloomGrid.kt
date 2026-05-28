package com.adsamcik.starlitcoffee.ui.component

import android.util.Log

/**
 * Geometric description of how a spritesheet image maps to animation frames.
 *
 * Carried as a value type (no Android dependencies) so it can be derived from
 * pixel dimensions in unit tests without spinning up the framework. The
 * renderer treats the grid as the single source of truth for frame slicing;
 * callers should NEVER recompute `width / frameSizePx` ad hoc.
 */
data class BloomGrid(
    val frameSizePx: Int,
    val columns: Int,
    val rows: Int,
) {
    val frameCount: Int get() = columns * rows

    fun sourceLeftOf(frameIndex: Int): Int = (frameIndex % columns) * frameSizePx
    fun sourceTopOf(frameIndex: Int): Int = (frameIndex / columns) * frameSizePx
}

/**
 * Reasons grid parsing might reject a spritesheet. Surfaced via a single log
 * tag so a misimported asset is loud rather than silently producing scrambled
 * frames (the previous behaviour of `coerceAtLeast(1)` would mask all of
 * these as "1×1 grid renders the whole image as one frame").
 */
internal sealed class BloomGridError(val reason: String) {
    object EmptyDimensions : BloomGridError("image has zero width or height")
    data class FrameSizeNotPositive(val frameSizePx: Int) :
        BloomGridError("frameSizePx=$frameSizePx must be > 0")
    data class WidthNotDivisible(val width: Int, val frameSizePx: Int) :
        BloomGridError("image width $width is not divisible by frame size $frameSizePx")
    data class HeightNotDivisible(val height: Int, val frameSizePx: Int) :
        BloomGridError("image height $height is not divisible by frame size $frameSizePx")
    data class ColumnsMismatch(val expected: Int, val derived: Int, val width: Int, val frameSizePx: Int) :
        BloomGridError(
            "expected $expected columns but image width $width / frameSizePx $frameSizePx = $derived"
        )
    data class RowsMismatch(val expected: Int, val derived: Int, val height: Int, val frameSizePx: Int) :
        BloomGridError(
            "expected $expected rows but image height $height / frameSizePx $frameSizePx = $derived"
        )
}

/**
 * Result of attempting to derive a [BloomGrid] from raw image dimensions.
 * Sealed so callers can pattern-match on success vs each failure mode rather
 * than handling a generic null.
 */
internal sealed interface BloomGridParseResult {
    data class Success(val grid: BloomGrid) : BloomGridParseResult
    data class Failure(val error: BloomGridError) : BloomGridParseResult
}

/**
 * Pure grid parser — no Compose, no Android. Asserts the spritesheet is laid
 * out as a strict `columns × rows` grid of `frameSizePx`-square cells with no
 * padding between frames.
 *
 * Pass `expectedColumns` / `expectedRows` to lock the grid to the values the
 * UI option declares; pass nulls to auto-derive them from dimensions. Both
 * paths still validate strict divisibility.
 */
internal fun parseBloomGrid(
    imageWidth: Int,
    imageHeight: Int,
    frameSizePx: Int,
    expectedColumns: Int? = null,
    expectedRows: Int? = null,
): BloomGridParseResult {
    val error = validateBloomGridInputs(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        frameSizePx = frameSizePx,
        expectedColumns = expectedColumns,
        expectedRows = expectedRows,
    )
    if (error != null) return BloomGridParseResult.Failure(error)
    return BloomGridParseResult.Success(
        BloomGrid(
            frameSizePx = frameSizePx,
            columns = imageWidth / frameSizePx,
            rows = imageHeight / frameSizePx,
        ),
    )
}

@Suppress("ReturnCount") // Early-return chain is clearer than a fused expression here.
private fun validateBloomGridInputs(
    imageWidth: Int,
    imageHeight: Int,
    frameSizePx: Int,
    expectedColumns: Int?,
    expectedRows: Int?,
): BloomGridError? {
    if (imageWidth <= 0 || imageHeight <= 0) return BloomGridError.EmptyDimensions
    if (frameSizePx <= 0) return BloomGridError.FrameSizeNotPositive(frameSizePx)
    if (imageWidth % frameSizePx != 0) {
        return BloomGridError.WidthNotDivisible(imageWidth, frameSizePx)
    }
    if (imageHeight % frameSizePx != 0) {
        return BloomGridError.HeightNotDivisible(imageHeight, frameSizePx)
    }
    val derivedColumns = imageWidth / frameSizePx
    val derivedRows = imageHeight / frameSizePx
    if (expectedColumns != null && expectedColumns != derivedColumns) {
        return BloomGridError.ColumnsMismatch(expectedColumns, derivedColumns, imageWidth, frameSizePx)
    }
    if (expectedRows != null && expectedRows != derivedRows) {
        return BloomGridError.RowsMismatch(expectedRows, derivedRows, imageHeight, frameSizePx)
    }
    return null
}

/**
 * Convenience wrapper that logs failures with the spritesheet id for context
 * and returns the [BloomGrid] or null. Use from Compose where you want a
 * graceful "skip rendering" path rather than a thrown exception.
 */
internal fun resolveBloomGridOrLog(
    spritesheetId: String,
    imageWidth: Int,
    imageHeight: Int,
    frameSizePx: Int,
    expectedColumns: Int? = null,
    expectedRows: Int? = null,
): BloomGrid? = when (
    val result = parseBloomGrid(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        frameSizePx = frameSizePx,
        expectedColumns = expectedColumns,
        expectedRows = expectedRows,
    )
) {
    is BloomGridParseResult.Success -> result.grid
    is BloomGridParseResult.Failure -> {
        Log.w(
            "BloomGrid",
            "Rejected spritesheet '$spritesheetId' (${imageWidth}x${imageHeight}, " +
                "frameSizePx=$frameSizePx): ${result.error.reason}",
        )
        null
    }
}
