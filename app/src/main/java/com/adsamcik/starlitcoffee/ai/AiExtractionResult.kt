package com.adsamcik.starlitcoffee.ai

import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult

/**
 * Result of AI-powered label extraction. Mirrors [OcrExtractionResult] fields
 * but adds per-field confidence scores from grounding validation.
 *
 * Fields with [FieldConfidence.UNGROUNDED] confidence should be discarded
 * before presenting to the user.
 */
data class AiExtractionResult(
    val name: String? = null,
    val roaster: String? = null,
    val origin: String? = null,
    val region: String? = null,
    val farm: String? = null,
    val variety: String? = null,
    val altitude: String? = null,
    val processType: String? = null,
    val tastingNotes: String? = null,
    val roastLevel: String? = null,
    val roastDate: String? = null,
    val weight: String? = null,
    /** Per-field confidence from grounding check. Key = field name. */
    val fieldConfidence: Map<String, FieldConfidence> = emptyMap(),
) {
    /** Returns only fields that are GROUNDED or INFERRED (drops hallucinations). */
    fun withoutUngrounded(): AiExtractionResult {
        fun <T> keepIf(fieldName: String, value: T?): T? {
            if (value == null) return null
            return if (fieldConfidence[fieldName] == FieldConfidence.UNGROUNDED) null else value
        }
        return copy(
            name = keepIf("name", name),
            roaster = keepIf("roaster", roaster),
            origin = keepIf("origin", origin),
            region = keepIf("region", region),
            farm = keepIf("farm", farm),
            variety = keepIf("variety", variety),
            altitude = keepIf("altitude", altitude),
            processType = keepIf("processType", processType),
            tastingNotes = keepIf("tastingNotes", tastingNotes),
            roastLevel = keepIf("roastLevel", roastLevel),
            roastDate = keepIf("roastDate", roastDate),
            weight = keepIf("weight", weight),
        )
    }

    /** Converts to [OcrExtractionResult] for compatibility with existing prefill logic. */
    fun toOcrExtractionResult(): OcrExtractionResult = OcrExtractionResult(
        name = name,
        roaster = roaster,
        origin = origin,
        region = region,
        variety = variety,
        processType = processType,
        altitude = altitude,
        tastingNotes = tastingNotes,
        roastLevel = roastLevel,
        roastDate = roastDate,
        weight = weight,
    )

    companion object {
        /** All extractable field names for iteration. */
        val FIELD_NAMES = listOf(
            "name", "roaster", "origin", "region", "farm", "variety",
            "altitude", "processType", "tastingNotes", "roastLevel",
            "roastDate", "weight",
        )
    }
}
