@file:Suppress("LongParameterList")

package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoReviewHint
import com.adsamcik.starlitcoffee.util.BagPhotoScanSupport
import com.adsamcik.starlitcoffee.util.BagReviewSeverity
import com.adsamcik.starlitcoffee.util.CoffeeMetadataMatchStrategy
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_WORK_DATA_JSON_CHARS = 9_000

private val BagExtractionJson = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

@Serializable
data class RectDto(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
)

@Serializable
data class FieldEvidenceDto(
    val fieldName: String,
    val value: String,
    val rawValue: String,
    val canonicalKey: String? = null,
    val sourceType: String,
    val confidence: String,
    val side: String? = null,
    val matchStrategy: String? = null,
    val supportingText: String? = null,
    val previewUri: String? = null,
    val previewRect: RectDto? = null,
)

@Serializable
data class ReviewHintDto(
    val severity: String,
    val message: String,
)

@Serializable
data class BagExtractionPayload(
    val fieldEvidence: List<FieldEvidenceDto> = emptyList(),
    val reviewHints: List<ReviewHintDto> = emptyList(),
    val llmStatus: String,
    val detectedBarcode: String? = null,
    val detectedQrUrl: String? = null,
    val offLookupName: String? = null,
    val offLookupRoaster: String? = null,
    val thumbnailFocus: RectDto? = null,
    val capturedPhotoUris: String? = null,
)

@Serializable
private data class KnownFieldValuesDto(
    val names: List<String> = emptyList(),
    val roasters: List<String> = emptyList(),
    val origins: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val varieties: List<String> = emptyList(),
    val processTypes: List<String> = emptyList(),
    val roastLevels: List<String> = emptyList(),
    val farms: List<String> = emptyList(),
)

fun BagPhotoProcessingResult.encodeToJson(): String {
    val payload = toPayload(stripLargeOptionalFields = false)
    val encoded = BagExtractionJson.encodeToString(payload)
    return if (encoded.length <= MAX_WORK_DATA_JSON_CHARS) {
        encoded
    } else {
        BagExtractionJson.encodeToString(toPayload(stripLargeOptionalFields = true))
    }
}

fun decodeBagExtractionResult(json: String): BagPhotoProcessingResult {
    val payload = BagExtractionJson.decodeFromString<BagExtractionPayload>(json)
    val evidence = payload.fieldEvidence
        .map { it.toEvidence() }
        .associateBy { it.fieldName }

    return BagPhotoProcessingResult(
        ocrPrefill = evidence.takeIf { it.isNotEmpty() }?.let(BagPhotoScanSupport::buildPrefill),
        capturedPhotoUris = payload.capturedPhotoUris,
        detectedBarcode = payload.detectedBarcode,
        detectedQrUrl = payload.detectedQrUrl,
        offLookupName = payload.offLookupName,
        offLookupRoaster = payload.offLookupRoaster,
        fieldEvidence = evidence,
        photoAnalyses = emptyList(),
        reviewHints = payload.reviewHints.map { it.toReviewHint() },
        llmStatus = LlmEnrichmentStatus.valueOf(payload.llmStatus),
        thumbnailFocus = payload.thumbnailFocus?.toRect(),
    )
}

fun KnownFieldValues.encodeToJson(): String = BagExtractionJson.encodeToString(toDto())

fun decodeKnownFieldValues(json: String?): KnownFieldValues {
    if (json.isNullOrBlank()) return KnownFieldValues.EMPTY
    return runCatching {
        BagExtractionJson.decodeFromString<KnownFieldValuesDto>(json).toKnownFieldValues()
    }.getOrDefault(KnownFieldValues.EMPTY)
}

private fun BagPhotoProcessingResult.toPayload(stripLargeOptionalFields: Boolean): BagExtractionPayload =
    BagExtractionPayload(
        fieldEvidence = fieldEvidence.values.map { it.toDto(stripLargeOptionalFields) },
        reviewHints = reviewHints.map { it.toDto() },
        llmStatus = llmStatus.name,
        detectedBarcode = detectedBarcode,
        detectedQrUrl = detectedQrUrl,
        offLookupName = offLookupName,
        offLookupRoaster = offLookupRoaster,
        thumbnailFocus = thumbnailFocus?.toDto(),
        capturedPhotoUris = capturedPhotoUris,
    )

private fun BagFieldEvidence.toDto(stripLargeOptionalFields: Boolean): FieldEvidenceDto =
    FieldEvidenceDto(
        fieldName = fieldName,
        value = value,
        rawValue = rawValue,
        canonicalKey = canonicalKey,
        sourceType = sourceType.name,
        confidence = confidence.name,
        side = side?.name,
        matchStrategy = matchStrategy?.name,
        supportingText = supportingText.takeUnless { stripLargeOptionalFields },
        previewUri = previewUri.takeUnless { stripLargeOptionalFields },
        previewRect = previewRect?.toDto(),
    )

private fun FieldEvidenceDto.toEvidence(): BagFieldEvidence =
    BagFieldEvidence(
        fieldName = fieldName,
        value = value,
        rawValue = rawValue,
        canonicalKey = canonicalKey,
        sourceType = BagFieldSourceType.valueOf(sourceType),
        confidence = BagFieldConfidence.valueOf(confidence),
        side = side?.let(BagCaptureSide::valueOf),
        matchStrategy = matchStrategy?.let(CoffeeMetadataMatchStrategy::valueOf),
        supportingText = supportingText,
        previewUri = previewUri,
        previewRect = previewRect?.toRect(),
    )

private fun BagPhotoReviewHint.toDto(): ReviewHintDto =
    ReviewHintDto(
        severity = severity.name,
        message = message,
    )

private fun ReviewHintDto.toReviewHint(): BagPhotoReviewHint =
    BagPhotoReviewHint(
        severity = BagReviewSeverity.valueOf(severity),
        message = message,
    )

private fun BagPhotoRect.toDto(): RectDto =
    RectDto(
        leftFraction = leftFraction,
        topFraction = topFraction,
        rightFraction = rightFraction,
        bottomFraction = bottomFraction,
    )

private fun RectDto.toRect(): BagPhotoRect =
    BagPhotoRect(
        leftFraction = leftFraction,
        topFraction = topFraction,
        rightFraction = rightFraction,
        bottomFraction = bottomFraction,
    )

private fun KnownFieldValues.toDto(): KnownFieldValuesDto =
    KnownFieldValuesDto(
        names = names,
        roasters = roasters,
        origins = origins,
        regions = regions,
        varieties = varieties,
        processTypes = processTypes,
        roastLevels = roastLevels,
        farms = farms,
    )

private fun KnownFieldValuesDto.toKnownFieldValues(): KnownFieldValues =
    KnownFieldValues(
        names = names,
        roasters = roasters,
        origins = origins,
        regions = regions,
        varieties = varieties,
        processTypes = processTypes,
        roastLevels = roastLevels,
        farms = farms,
    )
