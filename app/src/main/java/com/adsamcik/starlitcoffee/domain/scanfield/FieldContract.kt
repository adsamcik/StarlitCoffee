package com.adsamcik.starlitcoffee.domain.scanfield

/**
 * Source attribution for a field value passed to the LLM.
 * Tells the LLM how each existing value was determined so it can weigh them appropriately.
 *
 * Lives in a neutral, dependency-free package so both the scan pipeline
 * (`scan.*`) and the LLM layer (`data.network.llm`) can reference it without
 * creating a package cycle between them.
 */
enum class FieldSource {
    /** User manually chose this value — treat as ground truth. */
    USER,
    /** Extracted by a previous LLM run — may be hallucinated, verify against the image. */
    LLM,
    /** Extracted by OCR text recognition and consensus algorithm. */
    OCR,
    /** Resolved from barcode or QR code lookup. */
    LOOKUP,
}

/**
 * A resolved field value with its source attribution, passed as context to the LLM.
 */
data class FieldContext(
    val value: String,
    val source: FieldSource,
    val confidence: String? = null,
)
