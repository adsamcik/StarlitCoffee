package com.adsamcik.starlitcoffee.scan

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.adsamcik.starlitcoffee.scan.observability.ScanPerfTracer
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagCaptureQualityAnalyzer
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LiveScanAnalyzer"

/** Quality threshold below which we don't bother JPEG-encoding for golden frames. */
private const val GOLDEN_FRAME_BLUR_THRESHOLD = 12f
private const val DEFAULT_OCR_THROTTLE_MS = 700L
private const val GOLDEN_FRAME_JPEG_QUALITY = 85
private const val BURST_MAX_FRAMES = 5
private const val BURST_TIMEOUT_MS = 500L
private const val LOG_FIRST_FRAMES = 5
private const val LOG_EVERY_N_FRAMES = 30
private const val LUMA_GRID_SIZE = 8
private const val LUMA_SAMPLE_STRIDE = 4

/**
 * Bundles the five callbacks the live-scan analyzer fires back into the
 * `LiveScanViewModel` (and, for OCR results, into Compose state). Bundling
 * them keeps the analyzer constructor under detekt's parameter-count limit
 * and gives screens a single object to wire up in `DisposableEffect`.
 */
data class LiveScanAnalyzerCallbacks(
    val onRawFrame: (quality: BagCaptureQuality, lumaGrid: ByteArray?) -> Unit,
    val onOcrResult: (
        ocrResult: OcrFieldExtractor.OcrExtractionResult,
        quality: BagCaptureQuality,
        lumaGrid: ByteArray?,
    ) -> Unit,
    val onGoldenFrameCapture: (
        jpegBytes: ByteArray,
        quality: BagCaptureQuality,
        ocrResult: OcrFieldExtractor.OcrExtractionResult,
    ) -> Unit,
    val onBarcodeDetected: (String) -> Unit,
    val onBlankFrame: () -> Unit,
)

/**
 * CameraX [ImageAnalysis.Analyzer] that drives the live scan pipeline:
 *  * computes per-frame quality (blur / glare / exposure) from luma data;
 *  * gates ML Kit text recognition on an adaptive throttle plus an
 *    in-flight flag to avoid stacking concurrent OCR requests;
 *  * runs barcode detection on the same `InputImage` until first detection;
 *  * pre-encodes a JPEG only when quality crosses the golden-frame
 *    threshold (encoding every frame is far too expensive);
 *  * supports a manual "burst capture" mode that snapshots the next handful
 *    of frames and surfaces the sharpest one.
 *
 * ## Threading
 *
 * The analyzer is bound to CameraX's image-analysis executor (a single-thread
 * background executor passed by the screen). All ML Kit listener callbacks
 * (`addOnSuccessListener`, `addOnFailureListener`, `addOnCompleteListener`)
 * are explicitly scheduled on the same [mlKitExecutor] so that
 * [ImageProxy.close] and the analyzer's mutable state mutations stay
 * thread-confined off Main. Running listeners on Main caused a real freeze:
 * an LLM escalation (or any other Main-thread work) would delay
 * `image.close()`, CameraX would back-pressure the analyzer, and the camera
 * preview visibly stalled.
 */
class LiveScanAnalyzer(
    private val recognizer: TextRecognizer,
    private val barcodeScanner: BarcodeScanner,
    private val callbacks: LiveScanAnalyzerCallbacks,
    /**
     * Executor used for ML Kit Tasks listeners. Must be a non-main executor —
     * if callbacks run on Main, `image.close()` is delayed whenever the UI
     * thread is busy and CameraX visibly freezes the preview. Pass the same
     * single-threaded analysis executor that drives `setImageAnalysisAnalyzer`
     * to keep mutable analyzer state thread-confined.
     */
    private val mlKitExecutor: Executor,
    private val throttleMsProvider: () -> Long = { DEFAULT_OCR_THROTTLE_MS },
    private val perfTracer: ScanPerfTracer? = null,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var textDetectionInFlight = false

    @Volatile
    private var lastTextCheckAtMs = 0L

    @Volatile
    private var lastTextBlockCount = 0

    @Volatile
    private var lastTextDetected = false

    @Volatile
    private var lastTextRegions: List<android.graphics.Rect> = emptyList()

    @Volatile
    private var lastOcrResult: OcrFieldExtractor.OcrExtractionResult =
        OcrFieldExtractor.OcrExtractionResult()

    @Volatile
    private var barcodeDetected = false

    // --- Burst capture state ---
    @Volatile
    private var burstMode = false

    @Volatile
    private var burstFrames = mutableListOf<Pair<ByteArray, BagCaptureQuality>>()

    @Volatile
    private var burstCallback: ((bestJpeg: ByteArray, quality: BagCaptureQuality) -> Unit)? = null

    @Volatile
    private var burstStartMs = 0L

    fun startBurst(callback: (bestJpeg: ByteArray, quality: BagCaptureQuality) -> Unit) {
        burstFrames.clear()
        burstCallback = callback
        burstStartMs = android.os.SystemClock.elapsedRealtime()
        burstMode = true
    }

    private var analyzeCallCount = 0
    private var lastAnalyzeNanos = 0L

    // ImageProxy.getImage() is gated by @ExperimentalGetImage in CameraX. We use
    // it deliberately to hand the underlying Image to ML Kit's
    // InputImage.fromMediaImage, which is the recommended high-throughput path.
    // The opt-in is a Java-side marker that Kotlin @OptIn doesn't propagate to
    // Android Lint cleanly, so suppress at the function entry points instead.
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        analyzeCallCount++
        val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
        if (lastAnalyzeNanos != 0L) {
            perfTracer?.mark("frame_interval_ms", (nowNanos - lastAnalyzeNanos) / 1_000_000f)
        }
        lastAnalyzeNanos = nowNanos
        if (shouldVerboseLog()) {
            android.util.Log.d(TAG, "analyze() call #$analyzeCallCount, " +
                "size=${image.width}x${image.height}, format=${image.format}, " +
                "planes=${image.planes.size}, image.image=${image.image != null}")
        }

        val luma = extractLumaBytes(image)
        if (luma == null) {
            android.util.Log.w(TAG, "Luma extraction returned null — closing image")
            image.close()
            return
        }

        perfTracer?.startTimer("quality_ms")
        val quality = BagCaptureQualityAnalyzer.analyzeLumaFrame(
            luma = luma,
            width = image.width,
            height = image.height,
            textBlockCount = lastTextBlockCount,
            textDetected = lastTextDetected,
            textRegions = lastTextRegions,
        )
        perfTracer?.stopTimer("quality_ms")

        if (shouldVerboseLog()) {
            android.util.Log.d(TAG, "Quality: blur=${quality.blurScore}, " +
                "glare=${quality.glarePercent}, overExp=${quality.overexposedPercent}")
        }

        if (burstMode) {
            handleBurstFrame(image, quality)
            return
        }

        val goldenFrameJpeg: ByteArray? = encodeGoldenFrameJpegIfEligible(image, quality)

        // build8x8Grid does pure arithmetic over a luma byte array; the only
        // realistic failure is an unexpected dimension (e.g. zero width) that
        // produces an out-of-bounds. We swallow rather than propagate because
        // a missing grid is recoverable — side detection just skips this frame.
        @Suppress("TooGenericExceptionCaught")
        val lumaGrid = try {
            build8x8Grid(luma, image.width, image.height)
        } catch (_: Exception) {
            null
        }

        // The analyzer thread must keep running regardless of what the
        // ViewModel-side `onRawFrame` callback does. Treat callback faults
        // as observability data, not analyzer-loop failures.
        @Suppress("TooGenericExceptionCaught")
        try {
            callbacks.onRawFrame(quality, lumaGrid)
        } catch (_: Exception) {
            // Never let callback exceptions propagate.
        }

        runMlKitPipeline(image, quality, lumaGrid, goldenFrameJpeg)
    }

    private fun shouldVerboseLog(): Boolean =
        analyzeCallCount <= LOG_FIRST_FRAMES || analyzeCallCount % LOG_EVERY_N_FRAMES == 0

    private fun handleBurstFrame(image: ImageProxy, quality: BagCaptureQuality) {
        val jpeg = encodeToJpeg(image)
        if (jpeg != null) {
            burstFrames.add(jpeg to quality)
        }
        val elapsed = android.os.SystemClock.elapsedRealtime() - burstStartMs
        if (burstFrames.size >= BURST_MAX_FRAMES || elapsed >= BURST_TIMEOUT_MS) {
            val best = burstFrames.maxByOrNull { it.second.blurScore }
            burstMode = false
            val cb = burstCallback
            burstCallback = null
            if (best != null && cb != null) {
                cb(best.first, best.second)
            }
            burstFrames.clear()
        }
        image.close()
    }

    private fun encodeGoldenFrameJpegIfEligible(
        image: ImageProxy,
        quality: BagCaptureQuality,
    ): ByteArray? {
        // Note: textBlockCount uses the previous frame's count, so we don't
        // gate on it here. Blur + glare + exposure are sufficient quality
        // indicators. Encoding every frame is too expensive — only encode
        // candidates that could be promoted to the golden frame.
        if (quality.blurScore < GOLDEN_FRAME_BLUR_THRESHOLD) return null
        if (!quality.glareOkay) return null
        if (!quality.exposureOkay) return null
        perfTracer?.startTimer("jpeg_encode_ms")
        val jpeg = encodeToJpeg(image)
        perfTracer?.stopTimer("jpeg_encode_ms")
        return jpeg
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun runMlKitPipeline(
        image: ImageProxy,
        quality: BagCaptureQuality,
        lumaGrid: ByteArray?,
        goldenFrameJpeg: ByteArray?,
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        val timeSinceLastOcr = now - lastTextCheckAtMs
        val shouldRunOcr = !textDetectionInFlight && timeSinceLastOcr >= throttleMsProvider()
        val shouldRunBarcode = !barcodeDetected
        val hasMediaImage = image.image != null

        if (shouldVerboseLog()) {
            android.util.Log.d(TAG, "OCR gate: shouldRunOcr=$shouldRunOcr, " +
                "inFlight=$textDetectionInFlight, timeSinceLast=${timeSinceLastOcr}ms, " +
                "hasMediaImage=$hasMediaImage, shouldRunBarcode=$shouldRunBarcode")
        }

        if (!shouldRunOcr || !hasMediaImage) {
            image.close()
            return
        }

        textDetectionInFlight = true
        lastTextCheckAtMs = now
        android.util.Log.d(TAG, ">>> Starting ML Kit recognition (call #$analyzeCallCount)")

        val inputImage = InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees)

        // Track pending tasks so image is closed only after all complete
        val pendingTasks = AtomicInteger(if (shouldRunBarcode) 2 else 1)
        val maybeCloseImage = {
            if (pendingTasks.decrementAndGet() == 0) {
                image.close()
            }
        }

        runOcr(inputImage, quality, lumaGrid, goldenFrameJpeg, maybeCloseImage)
        if (shouldRunBarcode) {
            runBarcode(inputImage, maybeCloseImage)
        }
    }

    private fun runOcr(
        inputImage: InputImage,
        quality: BagCaptureQuality,
        lumaGrid: ByteArray?,
        goldenFrameJpeg: ByteArray?,
        maybeCloseImage: () -> Unit,
    ) {
        val ocrStartNanos = android.os.SystemClock.elapsedRealtimeNanos()
        recognizer.process(inputImage)
            .addOnSuccessListener(mlKitExecutor) { text ->
                perfTracer?.mark(
                    "ocr_ms",
                    (android.os.SystemClock.elapsedRealtimeNanos() - ocrStartNanos) / 1_000_000f,
                )

                lastTextBlockCount = text.textBlocks.size
                lastTextDetected = text.textBlocks.isNotEmpty()
                lastTextRegions = text.textBlocks.mapNotNull { it.boundingBox }

                android.util.Log.d(TAG, "ML Kit SUCCESS: " +
                    "blocks=${text.textBlocks.size}, " +
                    "textLen=${text.text.length}, " +
                    "preview='${text.text.take(120).replace('\n', ' ')}'")

                if (text.text.isNotBlank()) {
                    handleOcrText(text.text, quality, lumaGrid, goldenFrameJpeg)
                } else {
                    android.util.Log.d(TAG, "ML Kit returned blank text — submitting absence signal")
                    callbacks.onBlankFrame()
                }
            }
            .addOnFailureListener(mlKitExecutor) { e ->
                android.util.Log.e(TAG, "ML Kit recognition FAILED", e)
            }
            .addOnCompleteListener(mlKitExecutor) {
                android.util.Log.d(TAG, "ML Kit COMPLETE — resetting inFlight")
                textDetectionInFlight = false
                maybeCloseImage()
            }
    }

    private fun handleOcrText(
        text: String,
        quality: BagCaptureQuality,
        lumaGrid: ByteArray?,
        goldenFrameJpeg: ByteArray?,
    ) {
        // The OCR success listener runs on the analyzer's executor; if it
        // throws, ML Kit won't recover the analyzer loop on its own. We
        // catch broadly here as a defensive boundary — OcrFieldExtractor and
        // the user-supplied callbacks could in principle raise anything,
        // and dropping a single frame's results is preferable to dropping
        // the whole live-scan session.
        @Suppress("TooGenericExceptionCaught")
        try {
            perfTracer?.startTimer("field_extract_ms")
            // Field-level extraction is now LLM-only — see OcrFieldExtractor
            // for the architecture note. Live-scan frames carry the raw OCR
            // text forward without pre-LLM regex extraction. Downstream
            // accumulation/consensus code reads field properties with
            // null-tolerant `?.let { }` and gracefully contributes nothing
            // until the LLM populates them on golden-frame escalation.
            lastOcrResult = OcrFieldExtractor.OcrExtractionResult(rawText = text)
            perfTracer?.stopTimer("field_extract_ms")
            android.util.Log.d(TAG, "OCR raw text captured (${text.length} chars); no pre-LLM extraction")
            callbacks.onOcrResult(lastOcrResult, quality, lumaGrid)
            android.util.Log.d(TAG, "onOcrResult callback completed OK")
            goldenFrameJpeg?.let { jpeg ->
                @Suppress("TooGenericExceptionCaught")
                try {
                    callbacks.onGoldenFrameCapture(jpeg, quality, lastOcrResult)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Golden frame capture error", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "OCR callback error", e)
        }
    }

    private fun runBarcode(inputImage: InputImage, maybeCloseImage: () -> Unit) {
        val barcodeStartNanos = android.os.SystemClock.elapsedRealtimeNanos()
        barcodeScanner.process(inputImage)
            .addOnSuccessListener(mlKitExecutor) { barcodes ->
                perfTracer?.mark(
                    "barcode_ms",
                    (android.os.SystemClock.elapsedRealtimeNanos() - barcodeStartNanos) / 1_000_000f,
                )
                barcodes.firstOrNull()?.rawValue?.let { value ->
                    if (!barcodeDetected) {
                        barcodeDetected = true
                        android.util.Log.d(TAG, "Barcode detected: $value")
                        // Defensive boundary — see [handleOcrText].
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            callbacks.onBarcodeDetected(value)
                        } catch (_: Exception) {
                            // Don't let downstream UI bugs break the analyzer loop.
                        }
                    }
                }
            }
            .addOnCompleteListener(mlKitExecutor) {
                maybeCloseImage()
            }
    }

    private fun encodeToJpeg(image: ImageProxy, quality: Int = GOLDEN_FRAME_JPEG_QUALITY): ByteArray? {
        // Native JPEG encoding can fail for OOM, broken planes, or invalid
        // formats — all of them survivable for the next frame.
        @Suppress("TooGenericExceptionCaught")
        return try {
            val bitmap = image.toBitmap()
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
            bitmap.recycle()
            stream.toByteArray()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "JPEG encoding failed", e)
            null
        }
    }

    private fun extractLumaBytes(image: ImageProxy): ByteArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun build8x8Grid(luma: ByteArray, width: Int, height: Int): ByteArray {
        val grid = ByteArray(LUMA_GRID_SIZE * LUMA_GRID_SIZE)
        val cellW = width / LUMA_GRID_SIZE
        val cellH = height / LUMA_GRID_SIZE
        for (row in 0 until LUMA_GRID_SIZE) {
            for (col in 0 until LUMA_GRID_SIZE) {
                grid[row * LUMA_GRID_SIZE + col] = sampleCellMean(
                    luma = luma,
                    width = width,
                    height = height,
                    startX = col * cellW,
                    startY = row * cellH,
                    cellW = cellW,
                    cellH = cellH,
                )
            }
        }
        return grid
    }

    private fun sampleCellMean(
        luma: ByteArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        cellW: Int,
        cellH: Int,
    ): Byte {
        var sum = 0L
        var count = 0
        var y = startY
        while (y < (startY + cellH).coerceAtMost(height)) {
            var x = startX
            while (x < (startX + cellW).coerceAtMost(width)) {
                sum += (luma[y * width + x].toInt() and 0xFF)
                count++
                x += LUMA_SAMPLE_STRIDE
            }
            y += LUMA_SAMPLE_STRIDE
        }
        return if (count > 0) (sum / count).toByte() else 0
    }
}
