package com.adsamcik.starlitcoffee.util

/**
 * Carrier for raw OCR text + lightweight per-block geometry.
 *
 * **Architecture note:** field-level extraction (name / roaster / origin /
 * region / variety / process / roastLevel / weight / dates / tasting notes)
 * is the LLM's job — see [com.adsamcik.starlitcoffee.data.network.llm.MindlayerLlmInferenceProvider].
 * This object used to host a regex/keyword-based extractor that ran ahead of
 * the LLM as a pre-fill source. It produced wrong-confidence noise (Czech
 * UI labels classified as roaster, OCR typos surfaced as names, cursive
 * misreads passed through as canonical values) and was removed in favour of
 * the LLM-only extraction contract.
 *
 * What remains:
 *
 *  - [OcrExtractionResult] — data shape carrying the LLM-extracted fields,
 *    or all-nulls plus [OcrExtractionResult.rawText] when the LLM hasn't
 *    run yet. Downstream code (`ConsensusEngine`, `FrameEvidenceAccumulator`,
 *    `BagPhotoScanSupport`) reads field properties with null-tolerant
 *    `?.let { ... }`, so unpopulated fields gracefully contribute nothing.
 *  - [OcrTextBlock] — per-block geometry passed into image-preprocessing /
 *    quality heuristics.
 *  - [extractBarcodeFromText] — narrow EAN-13/12 numeric pattern detector,
 *    used as a fallback when ML Kit's barcode scanner missed a barcode but
 *    PaddleOCR happened to OCR the digits. Numeric format detection, not
 *    label-based field extraction.
 */
object OcrFieldExtractor {

    data class OcrExtractionResult(
        val name: String? = null,
        val roaster: String? = null,
        val origin: String? = null,
        val region: String? = null,
        val farm: String? = null,
        val variety: String? = null,
        val processType: String? = null,
        val altitude: String? = null,
        val tastingNotes: String? = null,
        val roastLevel: String? = null,
        val roastDate: String? = null,
        val expiryDate: String? = null,
        val weight: String? = null,
        val isDecaf: Boolean? = null,
        val fieldConfidence: Map<String, BagFieldConfidence> = emptyMap(),
        /** Full raw OCR text from PaddleOCR (via Mindlayer), preserved for LLM context. */
        val rawText: String = "",
    )

    /**
     * Lightweight representation of an OCR text block with spatial info.
     * [heightPx] is the bounding box height (font-size proxy).
     * [topPx] is the Y position from the top of the image.
     */
    data class OcrTextBlock(
        val text: String,
        val heightPx: Int,
        val topPx: Int,
        val leftPx: Int = 0,
        val widthPx: Int = 0,
        val imageWidthPx: Int = 0,
        val imageHeightPx: Int = 0,
    ) {
        @Suppress(
            // The early return guards four mandatory dimensions; combining
            // them into one expression would not improve readability and
            // would lose the per-condition implied error semantics.
            "ComplexCondition",
        )
        fun normalizedBounds(paddingFraction: Float = 0.04f): BagPhotoRect? {
            if (widthPx <= 0 || heightPx <= 0 || imageWidthPx <= 0 || imageHeightPx <= 0) return null
            val left = ((leftPx.toFloat() / imageWidthPx) - paddingFraction).coerceIn(0f, 1f)
            val top = ((topPx.toFloat() / imageHeightPx) - paddingFraction).coerceIn(0f, 1f)
            val right = (((leftPx + widthPx).toFloat() / imageWidthPx) + paddingFraction).coerceIn(0f, 1f)
            val bottom = (((topPx + heightPx).toFloat() / imageHeightPx) + paddingFraction).coerceIn(0f, 1f)
            if (right <= left || bottom <= top) return null
            return BagPhotoRect(
                leftFraction = left,
                topFraction = top,
                rightFraction = right,
                bottomFraction = bottom,
            )
        }
    }

    // EAN-13 / UPC-A barcode regex: OCR may read spaces between digit groups
    // (e.g. "8 594206 183060"). Range allows 12-17 chars to cover UPC-A (12),
    // EAN-13 (13), and a couple of inserted spaces. The follow-up length
    // check pins to exactly 12 or 13 digits after space removal.
    private val eanBarcodeRegex = Regex("""(\d[\d ]{10,15}\d)""")

    /**
     * Extracts a barcode number from OCR text as a fallback when ML Kit barcode
     * scanning fails. Looks for 12-13 digit sequences (possibly space-separated).
     *
     * This is NOT label-anchored field extraction — it's pure numeric pattern
     * detection that survives the LLM-only extraction rule because EAN-13/12
     * is an unambiguous, language-agnostic format.
     */
    fun extractBarcodeFromText(rawText: String): String? {
        for (match in eanBarcodeRegex.findAll(rawText)) {
            val digits = match.value.replace(" ", "")
            if (digits.length == 13 || digits.length == 12) {
                return digits
            }
        }
        return null
    }
}
