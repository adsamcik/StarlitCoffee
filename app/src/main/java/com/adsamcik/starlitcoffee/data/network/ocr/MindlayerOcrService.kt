package com.adsamcik.starlitcoffee.data.network.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.adsamcik.mindlayer.ServiceCapabilities
import com.adsamcik.mindlayer.sdk.Mindlayer
import com.adsamcik.mindlayer.sdk.OcrProfile
import com.adsamcik.mindlayer.sdk.OcrResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.time.Duration

private const val TAG = "MindlayerOcr"

/**
 * OCR service that uses Mindlayer's PaddleOCR-backed one-shot image API
 * ([Mindlayer.ocr]) for single-image recognition.
 *
 * Each call PNG-encodes the bitmap, runs a single [Mindlayer.ocr] pass with the
 * `GeneralDocument` profile and bounding boxes enabled, awaits the
 * [OcrResult], and assembles a [RecognizedText] in image pixel coordinates.
 *
 * # Why one-shot per call (and not a long-lived session)
 *
 * Starlit Coffee scans coffee bags from the gallery and the live camera. The
 * gallery path is one image at a time. The live camera could in principle
 * stream into a long-lived multi-frame session, but the current
 * `LiveScanAnalyzer` architecture treats each frame independently for ML Kit
 * text-block counts. Migrating it to a continuous session is a larger refactor;
 * for now we use the one-shot API, which is API-compatible with the ML Kit
 * one-shot pattern.
 *
 * # Coordinate space
 *
 * The v1 SDK returns [OcrLine.boundingBox] already de-normalized to
 * `[left, top, right, bottom]` source-image pixels, so no normalization math is
 * needed here. The boxes are axis-aligned: per-line rotation (which the legacy
 * normalized-quad API carried) is not available, so [cornerPoints] are the four
 * corners of the axis-aligned rectangle and `ImagePreprocessor.computeAlignment`
 * sees a horizontal line angle.
 *
 * # Capability degradation
 *
 * If the connected Mindlayer service does not advertise
 * [ServiceCapabilities.FEATURE_OCR_IMAGE_ONESHOT] (older service, OCR feature
 * flag off, asset pack still extracting), [recognize] logs a warning and
 * returns `null`. Callers degrade gracefully — for the bag-photo path this
 * means the photo proceeds with no OCR text, and the LLM enrichment still
 * runs against the raw image.
 */
class MindlayerOcrService(context: Context) : OcrService {

    private val mindlayer: Mindlayer = Mindlayer.connect(context.applicationContext)

    @Volatile
    private var capabilityChecked: Boolean = false

    @Volatile
    private var capabilityAvailable: Boolean = false

    /** Free the underlying Mindlayer binding. Safe to call multiple times. */
    override fun close() {
        mindlayer.disconnect()
    }

    /** Cheap check used by callers that want to short-circuit empty bitmaps. */
    override suspend fun isAvailable(): Boolean = ensureCapability()

    /**
     * Recognise text in [bitmap]. Returns `null` if OCR is unavailable or the
     * call failed before producing a result. Logs detail; never throws unless
     * cancellation propagates.
     */
    override suspend fun recognize(bitmap: Bitmap): RecognizedText? {
        if (!ensureCapability()) {
            Log.w(TAG, "OCR capability not advertised by Mindlayer service")
            return null
        }

        val pngBytes = withContext(Dispatchers.Default) { encodePng(bitmap) } ?: run {
            Log.w(TAG, "PNG encode returned null — skipping OCR")
            return null
        }

        return runCatching {
            withTimeout(SESSION_TIMEOUT_MS) {
                // Boundary catch is implicit via runCatching: the one-shot can
                // throw a typed MindlayerException (e.g. CONCURRENT_LIMIT,
                // RESOURCE_EXHAUSTED, NOT_SUPPORTED). Returning null lets
                // callers degrade gracefully.
                val result = mindlayer.ocr {
                    image(pngBytes, "image/png")
                    profile(OcrProfile.GeneralDocument)
                    emitBoundingBoxes()
                }.awaitResult()
                buildRecognizedText(result)
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "OCR call failed: ${error.message}", error)
        }.getOrNull()
    }

    private suspend fun ensureCapability(): Boolean {
        if (capabilityChecked) return capabilityAvailable
        @Suppress("TooGenericExceptionCaught")
        return try {
            val caps = mindlayer.awaitConnected(Duration.INFINITE)
            capabilityAvailable = caps.supports(ServiceCapabilities.FEATURE_OCR_IMAGE_ONESHOT)
            capabilityChecked = true
            capabilityAvailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Capability check failed: ${e.message}", e)
            false
        }
    }

    companion object {
        /** Overall budget for a single one-shot OCR call. */
        internal const val SESSION_TIMEOUT_MS: Long = 60_000L

        /** PNG quality — PNG is lossless so this is effectively the compression level. */
        private const val PNG_QUALITY: Int = 100

        internal fun encodePng(bitmap: Bitmap): ByteArray? {
            @Suppress("TooGenericExceptionCaught")
            return try {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                out.toByteArray()
            } catch (_: OutOfMemoryError) {
                null
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Build a [RecognizedText] from a one-shot [OcrResult]. One
         * [RecognizedTextBlock] is emitted per recognised line so the existing
         * field-extraction pipeline (which iterates blocks) keeps working.
         */
        internal fun buildRecognizedText(result: OcrResult): RecognizedText {
            val blocks = result.lines.map { line ->
                val rect = line.boundingBox?.let { rectFromBox(it) }
                val corners = rect?.let { cornerPointsFromRect(it) }
                val recognizedLine = RecognizedTextLine(
                    text = line.text,
                    boundingBox = rect,
                    cornerPoints = corners,
                )
                RecognizedTextBlock(
                    text = line.text,
                    boundingBox = rect,
                    lines = listOf(recognizedLine),
                )
            }
            val fullText = result.lines.joinToString("\n") { it.text }.trim()
            return RecognizedText(fullText = fullText, blocks = blocks)
        }

        /**
         * Convert a `[left, top, right, bottom]` pixel box into a [Rect].
         * Returns `null` for malformed or degenerate boxes.
         */
        internal fun rectFromBox(box: List<Int>): Rect? {
            if (box.size != BOX_COMPONENT_COUNT) return null
            val (left, top, right, bottom) = box
            if (left >= right || top >= bottom) return null
            return Rect(left, top, right, bottom)
        }

        /** Four corner points clockwise from top-left for an axis-aligned [rect]. */
        internal fun cornerPointsFromRect(rect: Rect): Array<Point> = arrayOf(
            Point(rect.left, rect.top),
            Point(rect.right, rect.top),
            Point(rect.right, rect.bottom),
            Point(rect.left, rect.bottom),
        )

        private const val BOX_COMPONENT_COUNT: Int = 4
    }
}
