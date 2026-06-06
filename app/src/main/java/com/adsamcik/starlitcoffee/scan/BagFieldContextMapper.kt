package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.domain.scanfield.FieldContext
import com.adsamcik.starlitcoffee.domain.scanfield.FieldSource
import com.adsamcik.starlitcoffee.util.BagFieldCandidate
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType

/**
 * Pure mapping from accumulated bag-scan [BagFieldCandidate]s to the
 * [FieldContext] map that the LLM prompt understands.
 *
 * Extracted from BrewViewModel so the value-selection + source-taxonomy logic
 * can be unit-tested in isolation and reused by both bag-photo enrichment
 * entry points without carrying ViewModel state.
 */
object BagFieldContextMapper {

    private val SOURCE_RANK: Map<FieldSource, Int> = mapOf(
        FieldSource.USER to 0,
        FieldSource.LOOKUP to 1,
        FieldSource.LLM to 2,
        FieldSource.OCR to 3,
    )

    private val CONFIDENCE_RANK: Map<BagFieldConfidence, Int> = mapOf(
        BagFieldConfidence.HIGH to 0,
        BagFieldConfidence.MEDIUM to 1,
        BagFieldConfidence.LOW to 2,
        BagFieldConfidence.NEEDS_REVIEW to 3,
    )

    /**
     * Collapse the candidate list to a single best value per field, mapped to
     * the [FieldSource] taxonomy the LLM prompt understands.
     *
     * Priority order: USER (if any exist in candidates — rare in photo path),
     * LOOKUP (barcode/QR), LLM (from prior runs, if any), OCR. Only
     * HIGH/MEDIUM-confidence candidates are forwarded to avoid feeding the LLM
     * low-confidence OCR noise.
     */
    fun buildExistingFieldsContext(
        candidates: List<BagFieldCandidate>,
    ): Map<String, FieldContext> {
        val strong = candidates.filter {
            it.confidenceHint == BagFieldConfidence.HIGH ||
                it.confidenceHint == BagFieldConfidence.MEDIUM
        }
        return strong
            .groupBy { it.fieldName }
            .mapNotNull { (fieldName, group) ->
                val winner = group.minWithOrNull(
                    compareBy(
                        { SOURCE_RANK[sourceOf(it)] ?: Int.MAX_VALUE },
                        { CONFIDENCE_RANK[it.confidenceHint] ?: Int.MAX_VALUE },
                    ),
                ) ?: return@mapNotNull null
                val value = winner.value.trim()
                if (value.isEmpty()) {
                    null
                } else {
                    fieldName to FieldContext(
                        value = value,
                        source = sourceOf(winner),
                        confidence = winner.confidenceHint.name,
                    )
                }
            }
            .toMap()
    }

    private fun sourceOf(candidate: BagFieldCandidate): FieldSource =
        when (candidate.sourceType) {
            BagFieldSourceType.LLM -> FieldSource.LLM
            BagFieldSourceType.BARCODE_LOOKUP,
            BagFieldSourceType.LOCAL_BARCODE_MATCH,
            BagFieldSourceType.QR_LINK_LOOKUP,
            BagFieldSourceType.OBSERVED_BARCODE_STEM -> FieldSource.LOOKUP
            BagFieldSourceType.OCR,
            BagFieldSourceType.CONSENSUS -> FieldSource.OCR
        }
}
