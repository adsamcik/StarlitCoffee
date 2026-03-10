package com.adsamcik.starlitcoffee.util

import android.graphics.Bitmap
import kotlin.math.max

object BagCaptureQualityAnalyzer {
    fun analyzeBitmap(
        bitmap: Bitmap,
        textBlockCount: Int = 0,
        textDetected: Boolean = false,
    ): BagCaptureQuality {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 2 || height <= 2) {
            return BagCaptureQuality(
                blurScore = 0f,
                glarePercent = 1f,
                overexposedPercent = 1f,
                underexposedPercent = 1f,
                textBlockCount = textBlockCount,
                textDetected = textDetected,
            )
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luma = ByteArray(width * height)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            luma[index] = ((red * 0.299f) + (green * 0.587f) + (blue * 0.114f)).toInt().toByte()
        }

        return analyzeLumaFrame(
            luma = luma,
            width = width,
            height = height,
            textBlockCount = textBlockCount,
            textDetected = textDetected,
        )
    }

    fun analyzeLumaFrame(
        luma: ByteArray,
        width: Int,
        height: Int,
        textBlockCount: Int = 0,
        textDetected: Boolean = false,
    ): BagCaptureQuality {
        val effectiveWidth = max(width, 1)
        val effectiveHeight = max(height, 1)

        return BagCaptureQuality(
            blurScore = calculateBlurScore(luma, effectiveWidth, effectiveHeight),
            glarePercent = calculatePercent(luma, effectiveWidth, effectiveHeight, threshold = 250, above = true),
            overexposedPercent = calculatePercent(luma, effectiveWidth, effectiveHeight, threshold = 240, above = true),
            underexposedPercent = calculatePercent(luma, effectiveWidth, effectiveHeight, threshold = 40, above = false),
            textBlockCount = textBlockCount,
            textDetected = textDetected,
        )
    }

    fun calculateBlurScore(
        luma: ByteArray,
        width: Int,
        height: Int,
    ): Float {
        if (width < 3 || height < 3) return 0f

        var sum = 0.0
        var sumSquares = 0.0
        var count = 0

        val xStart = width / 6
        val xEnd = width - xStart
        val yStart = height / 6
        val yEnd = height - yStart

        for (y in (yStart + 1) until (yEnd - 1)) {
            for (x in (xStart + 1) until (xEnd - 1)) {
                val center = luma[(y * width) + x].toInt() and 0xFF
                val laplacian = (4 * center) -
                    (luma[((y - 1) * width) + x].toInt() and 0xFF) -
                    (luma[((y + 1) * width) + x].toInt() and 0xFF) -
                    (luma[(y * width) + (x - 1)].toInt() and 0xFF) -
                    (luma[(y * width) + (x + 1)].toInt() and 0xFF)
                sum += laplacian.toDouble()
                sumSquares += laplacian.toDouble() * laplacian.toDouble()
                count++
            }
        }

        if (count == 0) return 0f
        val mean = sum / count
        return ((sumSquares / count) - (mean * mean)).toFloat() / 100f
    }

    private fun calculatePercent(
        luma: ByteArray,
        width: Int,
        height: Int,
        threshold: Int,
        above: Boolean,
    ): Float {
        if (luma.isEmpty()) return 0f

        val xStart = width / 6
        val xEnd = width - xStart
        val yStart = height / 6
        val yEnd = height - yStart

        var matches = 0
        var count = 0
        for (y in yStart until yEnd) {
            for (x in xStart until xEnd) {
                val value = luma[(y * width) + x].toInt() and 0xFF
                val isMatch = if (above) value >= threshold else value <= threshold
                if (isMatch) matches++
                count++
            }
        }

        return if (count == 0) 0f else matches.toFloat() / count.toFloat()
    }
}
