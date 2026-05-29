package com.adsamcik.starlitcoffee.util

import android.graphics.Bitmap
import android.util.Log
import android.graphics.Matrix
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.text.Text
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Preprocesses camera photos to improve ML Kit OCR accuracy.
 * Pipeline: EXIF rotation → text-based alignment (deskew + crop) → CLAHE enhancement.
 */
object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"
    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            initialized = OpenCVLoader.initLocal()
        }
    }

    /** Skew angle and text bounding region from ML Kit text detection. */
    data class AlignmentInfo(
        val skewAngleDegrees: Float,
        val textBounds: Rect?,
    )

    /**
     * Applies EXIF orientation to a bitmap. BitmapFactory.decodeFile() ignores
     * EXIF rotation tags, causing photos to appear rotated sideways.
     */
    fun applyExifRotation(bitmap: Bitmap, filePath: String): Bitmap {
        try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            return applyExifOrientation(bitmap, orientation)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply EXIF rotation", e)
            return bitmap
        }
    }

    fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
        )
    }

    /**
     * Computes alignment from ML Kit text detection results.
     * Median text line angle → skew, union of text blocks → crop bounds.
     */
    @Suppress(
        // Iterates blocks → lines → corner points to compute skew + crop
        // union. Three real nested levels (block, line, point) plus a let
        // for nullability — natural for the data shape.
        "NestedBlockDepth",
    )
    fun computeAlignment(textBlocks: List<Text.TextBlock>): AlignmentInfo {
        val angles = mutableListOf<Float>()
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = 0
        var maxY = 0

        for (block in textBlocks) {
            block.boundingBox?.let { bb ->
                minX = min(minX, bb.left)
                minY = min(minY, bb.top)
                maxX = max(maxX, bb.right)
                maxY = max(maxY, bb.bottom)
            }
            for (line in block.lines) {
                val corners = line.cornerPoints ?: continue
                if (corners.size >= 2) {
                    val dx = (corners[1].x - corners[0].x).toFloat()
                    val dy = (corners[1].y - corners[0].y).toFloat()
                    if (abs(dx) > 20f) {
                        angles.add(
                            Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat(),
                        )
                    }
                }
            }
        }

        val medianAngle = if (angles.isNotEmpty()) {
            angles.sorted()[angles.size / 2]
        } else {
            0f
        }

        val bounds = if (minX < maxX && minY < maxY) {
            Rect(minX, minY, maxX, maxY)
        } else {
            null
        }

        return AlignmentInfo(skewAngleDegrees = medianAngle, textBounds = bounds)
    }

    /**
     * Applies alignment: deskews if angle > 1° and crops to text region.
     */
    fun applyAlignment(bitmap: Bitmap, alignment: AlignmentInfo): Bitmap {
        try {
            var result = bitmap

            if (abs(alignment.skewAngleDegrees) > 1f) {
                result = deskew(result, alignment.skewAngleDegrees)
            }

            alignment.textBounds?.let { bounds ->
                result = cropToTextRegion(result, bounds)
            }

            return result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply text alignment", e)
            return bitmap
        }
    }

    /** Rotates bitmap by the detected skew angle using OpenCV warpAffine. */
    private fun deskew(bitmap: Bitmap, angleDegrees: Float): Bitmap {
        ensureInitialized()
        if (!initialized) return bitmap

        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val center = Point(src.cols() / 2.0, src.rows() / 2.0)
        val rotMat = Imgproc.getRotationMatrix2D(center, angleDegrees.toDouble(), 1.0)

        // Expand canvas to avoid clipping corners
        val cos = abs(rotMat.get(0, 0)[0])
        val sin = abs(rotMat.get(0, 1)[0])
        val newW = (src.rows() * sin + src.cols() * cos).roundToInt()
        val newH = (src.rows() * cos + src.cols() * sin).roundToInt()
        rotMat.put(0, 2, rotMat.get(0, 2)[0] + (newW - src.cols()) / 2.0)
        rotMat.put(1, 2, rotMat.get(1, 2)[0] + (newH - src.rows()) / 2.0)

        val dst = Mat()
        Imgproc.warpAffine(
            src, dst, rotMat, Size(newW.toDouble(), newH.toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, Scalar(0.0),
        )

        val result = createBitmap(dst.cols(), dst.rows())
        Utils.matToBitmap(dst, result)

        src.release()
        dst.release()
        rotMat.release()

        return result
    }

    /** Crops bitmap to the text region with 5% padding. */
    private fun cropToTextRegion(bitmap: Bitmap, textBounds: Rect): Bitmap {
        val padding = (min(bitmap.width, bitmap.height) * 0.05f).toInt()
        val left = max(0, textBounds.left - padding)
        val top = max(0, textBounds.top - padding)
        val right = min(bitmap.width, textBounds.right + padding)
        val bottom = min(bitmap.height, textBounds.bottom + padding)
        val cropW = right - left
        val cropH = bottom - top

        // Only crop if it meaningfully reduces the image: minimum size +
        // measurable shrink in at least one dimension.
        @Suppress("ComplexCondition")
        val shouldCrop = cropW > 100 && cropH > 100 &&
            (cropW < bitmap.width * 0.9f || cropH < bitmap.height * 0.9f)
        if (shouldCrop) {
            return Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        }
        return bitmap
    }

    /**
     * CLAHE contrast enhancement + unsharp mask sharpening.
     * ML Kit's neural network works better with enhanced grayscale than binary.
     */
    fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        try {
            ensureInitialized()
            if (!initialized) return bitmap

            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

            val clahe: CLAHE = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            val enhanced = Mat()
            clahe.apply(gray, enhanced)

            val blurred = Mat()
            Imgproc.GaussianBlur(enhanced, blurred, Size(0.0, 0.0), 3.0)
            val sharpened = Mat()
            Core.addWeighted(enhanced, 1.5, blurred, -0.5, 0.0, sharpened)

            val result = createBitmap(sharpened.cols(), sharpened.rows())
            val rgba = Mat()
            Imgproc.cvtColor(sharpened, rgba, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(rgba, result)

            src.release()
            gray.release()
            enhanced.release()
            blurred.release()
            sharpened.release()
            rgba.release()

            return result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to preprocess image for OCR", e)
            return bitmap
        }
    }
}
