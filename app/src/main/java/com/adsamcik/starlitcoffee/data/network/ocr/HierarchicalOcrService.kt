package com.adsamcik.starlitcoffee.data.network.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlin.math.max
import kotlin.math.min

private const val TAG = "HierarchicalOcr"

/**
 * OCR wrapper that adds region-targeted re-recognition on top of
 * [MindlayerOcrService].
 *
 * # Why
 *
 * PaddleOCR is a two-stage pipeline: a detector (DBNet) produces text-region
 * polygons, then a recognizer (CRNN/SVTR) reads each crop after resizing it
 * to a fixed height (~32-48 px). The detector itself runs on a resized
 * version of the input image (longest side capped, default 960 px). For a
 * coffee bag photo (commonly 3000-4000 px on the long side), small printed
 * sticker text at ~30 px in the original becomes ~7 px in detection space —
 * the detector cannot separate adjacent words on the sticker, so it emits
 * one wide bounding box containing several tokens. The recognizer then
 * compresses that wide multi-word crop into a single fixed-height line, and
 * letters drop out — the empirical artefact is the Merrybeans bag's back
 * sticker producing tokens like `MerybeansKubeBGADCA-` (`Merrybeans
 * Kolumbie TUMBAGA DECAF` with one `r` dropped and all spaces lost).
 *
 * # How
 *
 * This service runs the existing single-pass OCR, identifies "problem
 * regions" (wide aspect ratio with long text, internal case transitions,
 * long unspaced runs — all language-agnostic), then re-OCRs each problem
 * region by cropping the original bitmap to its bounding box and feeding
 * just that crop back through [MindlayerOcrService]. The detector now sees
 * the sticker as the entire image, so the same `det_limit_side_len` cap
 * preserves much more of the original pixel detail (a 2000 x 1000 crop is
 * downscaled ~2x, vs ~50 x 25 inside a 3000 px page that is downscaled
 * ~30x).
 *
 * # No dictionary, no manual list
 *
 * The problem-region classifier looks at structural properties of the OCR
 * output (geometry + character patterns) — never at the actual content or
 * a list of known brands. The user explicitly rejected dictionary-based
 * recovery as brittle; this approach works on any script and any brand
 * without per-language data.
 *
 * # Latency budget
 *
 * Refinement is opportunistic and capped. Typical bag photos produce 1-3
 * problem regions; each re-OCR is much faster than the full-image pass
 * because the crop is smaller. Worst-case cap [maxRefineRegions] prevents
 * a degenerate photo from blowing the latency budget. The bag-scan path is
 * a one-shot from the gallery picker, not real-time, so a ~1.5x cost is
 * user-tolerable.
 */
class HierarchicalOcrService(
    private val delegate: OcrService,
    private val maxRefineRegions: Int = MAX_REFINE_REGIONS_DEFAULT,
    private val classifier: ProblemRegionClassifier = ProblemRegionClassifier(),
) : OcrService {

    /**
     * Free the underlying Mindlayer binding. Delegates to the wrapped
     * [OcrService] — safe to call multiple times.
     */
    override fun close() {
        delegate.close()
    }

    override suspend fun isAvailable(): Boolean = delegate.isAvailable()

    /**
     * Recognise text in [bitmap] with hierarchical refinement.
     *
     * Pass 1: runs [delegate]'s single-pass OCR on the full bitmap.
     * Pass 2: for each problem region, crops + upscales, re-OCRs, and
     *         appends the refined blocks to the returned result.
     *
     * Refined block text is appended to [RecognizedText.fullText] so the
     * downstream prompt builder sees both the original (possibly mashed)
     * token and the refined tokens — the LLM's multi-page cross-reference
     * rule handles the disambiguation.
     */
    override suspend fun recognize(bitmap: Bitmap): RecognizedText? {
        val initial = delegate.recognize(bitmap) ?: return null
        val problemBlocks = initial.blocks.filter(classifier::isProblem)
        if (problemBlocks.isEmpty()) {
            Log.d(TAG, "No problem regions detected; skipping refinement")
            return initial
        }

        val candidates = problemBlocks.take(maxRefineRegions)
        Log.d(
            TAG,
            "Refining ${candidates.size}/${problemBlocks.size} problem region(s) " +
                "(cap=$maxRefineRegions)",
        )

        val refinedBlocks = mutableListOf<RecognizedTextBlock>()
        for (block in candidates) {
            val cropResult = reOcrRegion(bitmap, block) ?: continue
            refinedBlocks.addAll(cropResult.blocks)
        }

        if (refinedBlocks.isEmpty()) {
            Log.d(TAG, "Refinement produced no usable tokens; returning initial result")
            return initial
        }

        val refinedText = refinedBlocks.joinToString("\n") { it.text.trim() }
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val combinedFullText = if (refinedText.isBlank()) {
            initial.fullText
        } else {
            (initial.fullText + "\n" + refinedText).trim()
        }
        Log.d(
            TAG,
            "Refinement added ${refinedBlocks.size} block(s) " +
                "(${refinedText.length} chars)",
        )
        return RecognizedText(
            fullText = combinedFullText,
            blocks = initial.blocks + refinedBlocks,
        )
    }

    private suspend fun reOcrRegion(
        bitmap: Bitmap,
        block: RecognizedTextBlock,
    ): RecognizedText? {
        val bbox = block.boundingBox ?: return null
        val crop = cropAndMaybeUpscale(bitmap, bbox) ?: return null
        return delegate.recognize(crop)
    }

    /**
     * Crops [bitmap] to [bbox] with padding, then upscales when the smaller
     * dimension is below [TARGET_MIN_DIMENSION_PX] so the detector + recognizer
     * see the text region at higher relative resolution.
     */
    internal fun cropAndMaybeUpscale(bitmap: Bitmap, bbox: Rect): Bitmap? {
        val padX = max(1, (bbox.width() * CROP_PADDING_FRACTION).toInt())
        val padY = max(1, (bbox.height() * CROP_PADDING_FRACTION).toInt())
        val cropLeft = max(0, bbox.left - padX)
        val cropTop = max(0, bbox.top - padY)
        val cropRight = min(bitmap.width, bbox.right + padX)
        val cropBottom = min(bitmap.height, bbox.bottom + padY)
        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop
        if (cropW < MIN_CROP_DIMENSION_PX || cropH < MIN_CROP_DIMENSION_PX) {
            return null
        }

        val baseCrop = try {
            Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        }

        val smallerDim = min(cropW, cropH)
        if (smallerDim >= TARGET_MIN_DIMENSION_PX) return baseCrop

        val scale = min(
            MAX_UPSCALE_FACTOR.toFloat(),
            TARGET_MIN_DIMENSION_PX.toFloat() / smallerDim,
        )
        val newW = (cropW * scale).toInt().coerceAtLeast(MIN_CROP_DIMENSION_PX)
        val newH = (cropH * scale).toInt().coerceAtLeast(MIN_CROP_DIMENSION_PX)
        return try {
            Bitmap.createScaledBitmap(baseCrop, newW, newH, /* filter = */ true)
        } catch (_: IllegalArgumentException) {
            baseCrop
        } catch (_: OutOfMemoryError) {
            baseCrop
        }
    }

    companion object {
        /** Maximum number of problem regions to re-OCR per call. */
        const val MAX_REFINE_REGIONS_DEFAULT: Int = 5

        /** Fractional padding around each cropped region. */
        private const val CROP_PADDING_FRACTION: Float = 0.05f

        /** Minimum pixel dimension for a crop to be re-OCR-worthy. */
        private const val MIN_CROP_DIMENSION_PX: Int = 20

        /**
         * Target minimum dimension after upscale. Chosen to give PaddleOCR's
         * detector enough pixels to separate adjacent words after the
         * internal `det_limit_side_len` resize, while keeping the upscaled
         * bitmap well under typical bag-photo memory budgets.
         */
        private const val TARGET_MIN_DIMENSION_PX: Int = 600

        /** Cap on upscale factor — prevents 4000x blow-ups on tiny regions. */
        private const val MAX_UPSCALE_FACTOR: Int = 4
    }
}

/**
 * Decides whether a recognised text block looks like it needs to be re-OCR'd
 * at higher relative resolution. Three signals, all language-agnostic and
 * derived from block geometry + character patterns — no dictionaries.
 */
class ProblemRegionClassifier {

    fun isProblem(block: RecognizedTextBlock): Boolean {
        val bbox = block.boundingBox ?: return false
        val text = block.text.trim()
        if (text.isEmpty()) return false
        val width = bbox.right - bbox.left
        val height = bbox.bottom - bbox.top
        return isWideMashed(width, height, text) ||
            hasInternalCaseTransition(text) ||
            isLongUnspacedRun(text)
    }

    /**
     * Wide-aspect-ratio block carrying long text — typical of a single
     * detection that the detector glued together from multiple words on
     * one line, e.g. a small back-sticker label compressed into one bbox.
     *
     * Takes [width] and [height] as ints (not [Rect]) so the rule can be
     * unit-tested on the JVM without Android framework mocking.
     */
    internal fun isWideMashed(width: Int, height: Int, text: String): Boolean {
        if (height <= 0 || width <= 0) return false
        val aspect = width.toFloat() / height
        return aspect > WIDE_ASPECT_RATIO && text.length > LONG_TEXT_MIN
    }

    /**
     * Internal lowercase-to-uppercase or all-caps-to-lowercase transitions
     * (without whitespace) — typical of two joined words that lost their
     * separator in recognition (e.g. `MerrybeansKolumbie`, `BAGADECAF` next
     * to a lowercase token).
     */
    internal fun hasInternalCaseTransition(text: String): Boolean {
        for (i in 0 until text.length - 1) {
            val a = text[i]
            val b = text[i + 1]
            if (a.isLowerCase() && b.isUpperCase()) return true
            if (a.isUpperCase() && b.isUpperCase() && i + 2 < text.length) {
                // 3-char uppercase run followed by lowercase
                val c = text[i + 2]
                if (text[i + 1].isUpperCase() && c.isLowerCase()) return true
            }
        }
        return false
    }

    /**
     * Long run of non-whitespace characters — typical of multiple words
     * concatenated without spaces. Independent of case.
     */
    internal fun isLongUnspacedRun(text: String): Boolean {
        if (text.length < LONG_UNSPACED_MIN) return false
        return text.none { it.isWhitespace() }
    }

    companion object {
        private const val WIDE_ASPECT_RATIO: Float = 8f
        private const val LONG_TEXT_MIN: Int = 15
        private const val LONG_UNSPACED_MIN: Int = 20
    }
}
