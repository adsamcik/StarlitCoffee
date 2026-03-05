package com.adsamcik.starlitcoffee.util

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.CLAHE

/**
 * Preprocesses camera photos to improve ML Kit OCR accuracy.
 * Uses light enhancement (CLAHE) rather than aggressive binarization,
 * since ML Kit's neural network works better with grayscale than binary images.
 */
object ImagePreprocessor {

    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            initialized = OpenCVLoader.initLocal()
        }
    }

    /**
     * Preprocesses a bitmap for optimal OCR recognition.
     * Pipeline: grayscale → CLAHE contrast enhancement (no binarization).
     * Returns the preprocessed bitmap, or the original if OpenCV fails.
     */
    fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        try {
            ensureInitialized()
            if (!initialized) return bitmap

            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            // Grayscale
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

            // CLAHE — enhances local contrast without amplifying noise
            val clahe: CLAHE = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            val enhanced = Mat()
            clahe.apply(gray, enhanced)

            // Light sharpen via unsharp mask: subtract blurred from enhanced
            val blurred = Mat()
            Imgproc.GaussianBlur(enhanced, blurred, Size(0.0, 0.0), 3.0)
            val sharpened = Mat()
            org.opencv.core.Core.addWeighted(enhanced, 1.5, blurred, -0.5, 0.0, sharpened)

            // Convert back to ARGB bitmap for ML Kit
            val result = Bitmap.createBitmap(sharpened.cols(), sharpened.rows(), Bitmap.Config.ARGB_8888)
            // Convert single-channel to 4-channel for bitmap
            val rgba = Mat()
            Imgproc.cvtColor(sharpened, rgba, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(rgba, result)

            // Release native mats
            src.release()
            gray.release()
            enhanced.release()
            blurred.release()
            sharpened.release()
            rgba.release()

            return result
        } catch (_: Exception) {
            return bitmap
        }
    }
}
