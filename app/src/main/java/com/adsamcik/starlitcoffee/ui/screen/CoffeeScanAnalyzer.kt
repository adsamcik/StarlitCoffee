package com.adsamcik.starlitcoffee.ui.screen

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.net.toUri
import com.adsamcik.starlitcoffee.data.network.SafeQrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureQualityAnalyzer
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal data class ScannedCoffeeCode(val value: String, val isWebLink: Boolean)

/** Keep complete coffee identifiers; opening links belongs to the existing review approval. */
internal fun classifyCoffeeCode(rawValue: String?, valueType: Int): ScannedCoffeeCode? {
    if (rawValue.isNullOrBlank()) return null
    if (valueType !in setOf(Barcode.TYPE_UNKNOWN, Barcode.TYPE_TEXT, Barcode.TYPE_PRODUCT, Barcode.TYPE_URL)) return null
    val trimmed = rawValue.trim()
    if (trimmed.startsWith("http:", true) || trimmed.startsWith("https:", true) || valueType == Barcode.TYPE_URL) {
        return SafeQrLinkMetadataExplorer.sanitizePublicWebUrl(trimmed)?.let {
            ScannedCoffeeCode(rawValue, isWebLink = true)
        }
    }
    val nonCoffeePrefixes = listOf(
        "WIFI:", "MECARD:", "BEGIN:VCARD", "BEGIN:VEVENT", "mailto:", "tel:", "sms:", "smsto:",
        "geo:", "intent:", "javascript:", "data:", "file:", "otpauth:", "bitcoin:", "ethereum:",
    )
    if (nonCoffeePrefixes.any { trimmed.startsWith(it, ignoreCase = true) }) return null
    return ScannedCoffeeCode(rawValue, isWebLink = false)
}

internal fun selectCoffeeCode(codes: List<ScannedCoffeeCode>): ScannedCoffeeCode? =
    codes.firstOrNull { !it.isWebLink } ?: codes.firstOrNull()

private fun List<Barcode>.coffeeCodes(): List<ScannedCoffeeCode> =
    mapNotNull { classifyCoffeeCode(it.rawValue, it.valueType) }.distinct()

private object CoffeeScanAnalyzerTraceboxTemplates {
    val FAILED_TO_PROCESS_CAMERA_FRAME = LogTemplate.of("Failed to process coffee scan camera frame")
    val FAILED_TO_CLOSE_CODE_SCANNER = LogTemplate.of("Failed to close coffee code scanner")
}

/** One CameraX analyzer owns each frame until both framing and ML Kit have finished. */
internal class CoffeeScanAnalyzer(
    private val mainExecutor: Executor,
    private val onFraming: (BagCaptureQuality, Float) -> Unit,
    private val onCode: (ScannedCoffeeCode?) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val scanner = BarcodeScanning.getClient()
    private val lifecyclePolicy = BarcodeCameraLifecyclePolicy()
    private var lastProcessedMs: Long? = null
    private var previousGrid: ByteArray? = null

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val previousTime = lastProcessedMs
        if (!lifecyclePolicy.isActive || (previousTime != null && now - previousTime < THROTTLE_MS)) {
            image.close()
            return
        }
        lastProcessedMs = now
        try {
            publishFraming(image)
            val mediaImage = image.image
            if (mediaImage == null) {
                image.close()
                return
            }
            scanner.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
                .addOnCompleteListener(mainExecutor) { task ->
                    try {
                        if (lifecyclePolicy.isActive && task.isSuccessful) {
                            onCode(selectCoffeeCode(task.result.coffeeCodes()))
                        }
                    } finally {
                        // Completion still owns the frame after the screen or scanner has closed.
                        image.close()
                    }
                }
        } catch (error: Exception) {
            image.close()
            if (lifecyclePolicy.isActive) {
                Tracebox.log.error(error, CoffeeScanAnalyzerTraceboxTemplates.FAILED_TO_PROCESS_CAMERA_FRAME)
            }
        }
    }

    private fun publishFraming(image: ImageProxy) {
        if (image.width < 2 || image.height < 2) return
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = minOf(PREVIEW_GRID_WIDTH, image.width)
        val height = (width.toLong() * image.height / image.width).toInt().coerceAtLeast(1)
        val grid = ByteArray(width * height)
        for (y in 0 until height) {
            val sourceY = (y.toLong() * image.height / height).toInt()
            for (x in 0 until width) {
                val sourceX = (x.toLong() * image.width / width).toInt()
                grid[y * width + x] = buffer.get(sourceY * plane.rowStride + sourceX * plane.pixelStride)
            }
        }
        val quality = BagCaptureQualityAnalyzer.analyzeLumaFrame(grid, width, height)
        val motion = previousGrid?.let { meanAbsoluteDelta(it, grid) } ?: Float.MAX_VALUE
        previousGrid = grid
        mainExecutor.execute {
            if (lifecyclePolicy.isActive) onFraming(quality, motion)
        }
    }

    override fun close() {
        if (lifecyclePolicy.dispose()) {
            runCatching { scanner.close() }
                .onFailure { Tracebox.log.error(it, CoffeeScanAnalyzerTraceboxTemplates.FAILED_TO_CLOSE_CODE_SCANNER) }
        }
    }

    private fun meanAbsoluteDelta(previous: ByteArray, current: ByteArray): Float {
        if (previous.size != current.size || current.isEmpty()) return Float.MAX_VALUE
        var sum = 0L
        for (index in current.indices) {
            sum += kotlin.math.abs((current[index].toInt() and 0xFF) - (previous[index].toInt() and 0xFF))
        }
        return sum.toFloat() / current.size
    }

    private companion object {
        const val THROTTLE_MS = 120L
        const val PREVIEW_GRID_WIDTH = 160
    }
}

/** Decodes an imported image without retaining its URI or following a detected link. */
internal suspend fun readCoffeeCodes(context: Context, photoUri: String): List<ScannedCoffeeCode> =
    withContext(Dispatchers.IO) {
        val input = InputImage.fromFilePath(context.applicationContext, photoUri.toUri())
        val scanner = BarcodeScanning.getClient()
        try {
            suspendCancellableCoroutine { continuation ->
                scanner.process(input)
                    .addOnSuccessListener { codes ->
                        if (continuation.isActive) continuation.resume(codes.coffeeCodes())
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    .addOnCanceledListener { continuation.cancel() }
            }
        } finally {
            scanner.close()
        }
    }

internal class BarcodeCameraLifecyclePolicy {
    private val disposed = AtomicBoolean(false)

    val isActive: Boolean
        get() = !disposed.get()

    /** Returns true only to the caller that takes ownership of disposal. */
    fun dispose(): Boolean = disposed.compareAndSet(false, true)

    fun continueOrRelease(release: () -> Unit): Boolean {
        if (isActive) return true
        release()
        return false
    }
}
