package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.starlitcoffee.scan.model.FieldContext
import com.adsamcik.starlitcoffee.util.KnownFieldValues

/**
 * Request payload for LLM-based bag field extraction.
 *
 * # Architecture
 *
 * In the text-only architecture, [rawOcrText] is the **primary** input fed
 * to the LLM. [imageBytes] are retained as the cache key and as a
 * provenance pointer back to the original capture, but `MindlayerLlmInferenceProvider`
 * does NOT decode or transmit them to the model. The image-to-text bridge
 * lives entirely in `MindlayerOcrService` (PaddleOCR / PP-OCRv5).
 *
 * Carries everything the LLM needs: the merged OCR text, fields already
 * resolved by other sources (with source attribution so the LLM knows
 * what to trust), and the set of fields we still need.
 */
data class LlmExtractionRequest(
    /**
     * JPEG-encoded bag label photo bytes. Retained for cache-key derivation
     * (`LlmCacheKey`) and as audit metadata pointing back to the captured
     * frame; NOT sent to the LLM in the text-only architecture.
     */
    val imageBytes: ByteArray,
    /** Fields already resolved, with source attribution (user / LLM / OCR / lookup). */
    val existingFields: Map<String, FieldContext>,
    /** Which fields we want the LLM to try extracting (e.g., "name", "roaster", "tastingNotes"). */
    val fieldsNeeded: Set<String>,
    /**
     * Merged OCR text from PaddleOCR (`MindlayerOcrService`) — the
     * primary input for text-only LLM extraction. Must be non-blank for
     * the LLM to produce meaningful output; when null/blank, callers
     * receive [LlmExtractionResult.Unavailable].
     */
    val rawOcrText: String? = null,
    /** User's known field values for grounding (origins, varieties, roasters, etc.). */
    val knownFieldValues: KnownFieldValues? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlmExtractionRequest) return false
        return imageBytes.contentEquals(other.imageBytes) &&
            existingFields == other.existingFields &&
            fieldsNeeded == other.fieldsNeeded &&
            rawOcrText == other.rawOcrText &&
            knownFieldValues == other.knownFieldValues
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + existingFields.hashCode()
        result = 31 * result + fieldsNeeded.hashCode()
        result = 31 * result + (rawOcrText?.hashCode() ?: 0)
        result = 31 * result + (knownFieldValues?.hashCode() ?: 0)
        return result
    }
}
