package com.adsamcik.starlitcoffee.util

import kotlin.math.roundToInt

data class PhotoDimensions(
    val width: Int,
    val height: Int,
)

object PhotoStoragePolicy {
    const val AI_MAX_LONG_EDGE_PX = 2048
    const val THUMBNAIL_MAX_LONG_EDGE_PX = 512
    const val WEBP_QUALITY = 82
    const val THUMBNAIL_WEBP_QUALITY = 80

    fun boundedDecodeSampleSize(
        sourceLongEdgePx: Int,
        maxLongEdgePx: Int = AI_MAX_LONG_EDGE_PX,
    ): Int {
        require(sourceLongEdgePx > 0) { "Source long edge must be positive" }
        require(maxLongEdgePx > 0) { "Maximum long edge must be positive" }
        var sampleSize = 1
        while (sourceLongEdgePx / sampleSize > maxLongEdgePx) {
            sampleSize *= 2
        }
        return sampleSize
    }

    fun scaledDimensions(
        width: Int,
        height: Int,
        maxLongEdgePx: Int = AI_MAX_LONG_EDGE_PX,
    ): PhotoDimensions {
        require(width > 0 && height > 0) { "Photo dimensions must be positive" }
        require(maxLongEdgePx > 0) { "Maximum long edge must be positive" }

        val longestEdge = maxOf(width, height)
        if (longestEdge <= maxLongEdgePx) {
            return PhotoDimensions(width = width, height = height)
        }

        val scale = maxLongEdgePx.toDouble() / longestEdge
        return PhotoDimensions(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }
}
