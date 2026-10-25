package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult

object ScanFieldSupport {
    fun buildFieldEvidence(
        resolvedFields: Map<String, String>,
        sourceType: BagFieldSourceType = BagFieldSourceType.CONSENSUS,
        confidence: BagFieldConfidence = BagFieldConfidence.MEDIUM,
    ): Map<String, BagFieldEvidence> = resolvedFields.entries
        .mapNotNull { (fieldName, value) ->
            value.trim().takeIf { it.isNotBlank() }?.let { cleanValue ->
                fieldName to BagFieldEvidence(
                    fieldName = fieldName,
                    value = cleanValue,
                    sourceType = sourceType,
                    confidence = confidence,
                )
            }
        }
        .toMap()

    fun buildPrefill(resolvedFields: Map<String, String>): OcrExtractionResult {
        val evidence = buildFieldEvidence(resolvedFields)
        if (evidence.isEmpty()) return OcrExtractionResult()
        return BagPhotoScanSupport.buildPrefill(evidence)
    }
}
