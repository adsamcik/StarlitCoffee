package com.adsamcik.starlitcoffee.scan.model

import com.adsamcik.starlitcoffee.domain.scanfield.FieldContext
import com.adsamcik.starlitcoffee.util.KnownFieldValues

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
    /** Raw OCR text from ML Kit to provide as additional LLM context. */
    val rawOcrText: String? = null,
    /** User's known field values for grounding (origins, varieties, roasters, etc.). */
    val knownFieldValues: KnownFieldValues? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlmEscalationRequest) return false
        return (goldenFrameBytes?.contentEquals(other.goldenFrameBytes)
            ?: (other.goldenFrameBytes == null)) &&
            existingFields == other.existingFields &&
            fieldsNeeded == other.fieldsNeeded &&
            rawOcrText == other.rawOcrText &&
            knownFieldValues == other.knownFieldValues
    }

    override fun hashCode(): Int {
        var result = goldenFrameBytes?.contentHashCode() ?: 0
        result = 31 * result + existingFields.hashCode()
        result = 31 * result + fieldsNeeded.hashCode()
        result = 31 * result + (rawOcrText?.hashCode() ?: 0)
        result = 31 * result + (knownFieldValues?.hashCode() ?: 0)
        return result
    }
}
