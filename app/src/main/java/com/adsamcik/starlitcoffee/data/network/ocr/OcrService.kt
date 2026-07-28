package com.adsamcik.starlitcoffee.data.network.ocr

import android.graphics.Bitmap

/**
 * Pluggable single-image OCR contract. Lets the bag-scan pipeline swap
 * implementations ([MindlayerOcrService] raw single-pass vs.
 * [HierarchicalOcrService] region-targeted refinement) without touching
 * call sites.
 *
 * The interface intentionally hides everything implementation-specific
 * (Mindlayer SDK bind state, capability negotiation, bitmap encoding) and
 * exposes only the shape downstream code needs: ask once, get text back.
 */
interface OcrService {
    /** Free underlying resources. Safe to call multiple times. */
    fun close()

    /** Cheap availability check used by callers that want to short-circuit. */
    suspend fun isAvailable(): Boolean

    /**
     * Recognise text in [bitmap]. Returns `null` when OCR is unavailable or
     * the call failed before producing a result.
     *
     * [bitmap] is borrowed for the duration of this call. Ownership remains
     * with the caller: implementations must neither recycle it nor retain it
     * after this suspend function returns or throws. Implementations own any
     * intermediate bitmaps they allocate and must recycle them on success,
     * failure, and cancellation.
     */
    suspend fun recognize(bitmap: Bitmap): RecognizedText?
}
