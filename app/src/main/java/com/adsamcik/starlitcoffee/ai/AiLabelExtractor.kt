package com.adsamcik.starlitcoffee.ai

/**
 * Interface for AI-powered coffee bag label extraction.
 *
 * Implementations use an on-device LLM to parse raw OCR text into
 * structured coffee bag fields. The extraction is always optional —
 * callers must handle null returns by falling back to regex-based
 * [com.adsamcik.starlitcoffee.util.OcrFieldExtractor].
 */
interface AiLabelExtractor {

    /**
     * Returns true if the AI model is available and ready on this device.
     * Implementations should check model download status and hardware capability.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Extracts structured coffee bag fields from raw OCR text.
     *
     * @param frontText OCR text from the front of the bag (name, roaster, origin).
     * @param backText OCR text from the back of the bag (tasting notes, barcode area).
     * @return Extracted fields with per-field confidence, or null if extraction fails.
     */
    suspend fun extract(frontText: String?, backText: String?): AiExtractionResult?
}
