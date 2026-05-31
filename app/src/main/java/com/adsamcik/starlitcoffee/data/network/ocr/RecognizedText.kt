package com.adsamcik.starlitcoffee.data.network.ocr

import android.graphics.Point
import android.graphics.Rect

/**
 * Framework-neutral OCR result type. Mirrors the shape of ML Kit's
 * `com.google.mlkit.vision.text.Text` closely enough that the rest of the
 * Starlit Coffee scan pipeline (`ImagePreprocessor.computeAlignment`,
 * `OcrFieldExtractor`, `BrewViewModel.processBagPhoto`) can consume it
 * without depending on either ML Kit or the Mindlayer SDK directly.
 *
 * Coordinates are in image **pixel** space, matching the bitmap that
 * produced this result. Producers that work in normalized space (e.g.
 * Mindlayer OCR's `0..1` bbox quads) must de-normalize before populating.
 */
data class RecognizedText(
    val fullText: String,
    val blocks: List<RecognizedTextBlock>,
)

data class RecognizedTextBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<RecognizedTextLine>,
)

data class RecognizedTextLine(
    val text: String,
    val boundingBox: Rect?,
    /**
     * Four corner points in pixel coordinates, clockwise from top-left.
     * `null` when the recognizer cannot provide per-line bounding geometry.
     * `ImagePreprocessor.computeAlignment` only needs the first two
     * corners to derive the line angle; downstream code defends against
     * missing or short arrays.
     */
    val cornerPoints: Array<Point>?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecognizedTextLine) return false
        if (text != other.text) return false
        if (boundingBox != other.boundingBox) return false
        return when {
            cornerPoints == null && other.cornerPoints == null -> true
            cornerPoints == null || other.cornerPoints == null -> false
            else -> cornerPoints.contentEquals(other.cornerPoints)
        }
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + (boundingBox?.hashCode() ?: 0)
        result = 31 * result + (cornerPoints?.contentHashCode() ?: 0)
        return result
    }
}
