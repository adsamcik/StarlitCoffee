package com.adsamcik.starlitcoffee.data.network.llm

/**
 * Request payload for LLM-based bag field extraction.
 *
 * Carries everything the LLM needs: the label image, fields already
 * resolved by other sources (so the LLM can focus on gaps), and the set of
 * fields we still need.
 */
data class LlmExtractionRequest(
    /** JPEG-encoded bag label photo. */
    val imageBytes: ByteArray,
    /** Fields already resolved by OCR/barcode — gives LLM context to focus on gaps. */
    val existingFields: Map<String, String>,
    /** Which fields we want the LLM to try extracting (e.g., "name", "roaster", "tastingNotes"). */
    val fieldsNeeded: Set<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlmExtractionRequest) return false
        return imageBytes.contentEquals(other.imageBytes) &&
            existingFields == other.existingFields &&
            fieldsNeeded == other.fieldsNeeded
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + existingFields.hashCode()
        result = 31 * result + fieldsNeeded.hashCode()
        return result
    }
}
