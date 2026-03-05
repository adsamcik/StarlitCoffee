package com.adsamcik.starlitcoffee.ai

/**
 * Confidence level for an AI-extracted field, determined by grounding
 * the LLM output against the source OCR text.
 */
enum class FieldConfidence {
    /** Field value found verbatim (or fuzzy-close) in OCR text. */
    GROUNDED,

    /** Field value was semantically derived (e.g., country inferred from region name). */
    INFERRED,

    /** Field value has no match in OCR text — likely hallucinated. */
    UNGROUNDED,
}
