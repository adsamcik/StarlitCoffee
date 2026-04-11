package com.adsamcik.starlitcoffee.scan.model

/**
 * Source attribution for a field value passed to the LLM.
 * Tells the LLM how each existing value was determined so it can weigh them appropriately.
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

/**
 * Emitted by [FrameEvidenceAccumulator] when high-value fields remain
 * unresolved for too many consensus cycles. The LiveScanViewModel
 * subscribes and forwards this to an LLM provider for enrichment.
 */
data class LlmEscalationRequest(
    /** Best quality frame captured so far (JPEG bytes), if available. */
    val goldenFrameBytes: ByteArray?,
    /** Fields already resolved, with source attribution for the LLM. */
    val existingFields: Map<String, FieldContext>,
    /** Fields that are still SCANNING or CONFLICT and need LLM help. */
    val fieldsNeeded: Set<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlmEscalationRequest) return false
        return (goldenFrameBytes?.contentEquals(other.goldenFrameBytes)
            ?: (other.goldenFrameBytes == null)) &&
            existingFields == other.existingFields &&
            fieldsNeeded == other.fieldsNeeded
    }

    override fun hashCode(): Int {
        var result = goldenFrameBytes?.contentHashCode() ?: 0
        result = 31 * result + existingFields.hashCode()
        result = 31 * result + fieldsNeeded.hashCode()
        return result
    }
}
