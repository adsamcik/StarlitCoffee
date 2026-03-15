package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult

data class ScannedBagDraft(
    val name: String,
    val roaster: String? = null,
    val origin: String? = null,
    val region: String? = null,
    val roastLevel: String? = null,
    val variety: String? = null,
    val processType: String? = null,
    val tastingNotes: String? = null,
    val weightG: Float? = null,
    val roastDateMillis: Long? = null,
    val expiryDateMillis: Long? = null,
    val isDecaf: Boolean = false,
)

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

    fun buildDraft(resolvedFields: Map<String, String>): ScannedBagDraft? {
        if (resolvedFields.isEmpty()) return null

        val prefill = buildPrefill(resolvedFields)
        return ScannedBagDraft(
            name = prefill.name?.trim()?.takeIf { it.isNotBlank() }
                ?: prefill.roaster?.trim()?.takeIf { it.isNotBlank() }
                ?: "Scanned Bag",
            roaster = prefill.roaster,
            origin = prefill.origin,
            region = prefill.region,
            roastLevel = prefill.roastLevel,
            variety = prefill.variety,
            processType = prefill.processType,
            tastingNotes = prefill.tastingNotes,
            weightG = WeightParser.parseToGrams(prefill.weight),
            roastDateMillis = prefill.roastDate?.let(DateParser::parse),
            expiryDateMillis = prefill.expiryDate?.let(DateParser::parse),
            isDecaf = prefill.isDecaf == true,
        )
    }
}
