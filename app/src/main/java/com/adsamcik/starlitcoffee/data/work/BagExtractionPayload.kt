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

private const val MAX_WORK_RESULT_JSON_BYTES = 7_500
private const val MAX_WORK_KNOWN_VALUES_JSON_BYTES = 5_000
private const val MAX_WORK_KNOWN_VALUES_PER_FIELD = 12
private const val MAX_WORK_FIELD_VALUE_BYTES = 96
private const val MAX_WORK_CAPTURED_PHOTO_URIS_BYTES = 1_024
private const val MAX_WORK_REVIEW_HINTS = 4
private const val MAX_PROGRESS_DATA_BYTES = 8_000
private const val MAX_PROGRESS_FIELD_VALUE_BYTES = 64
private const val MAX_PROGRESS_CAPTURED_PHOTO_URIS_BYTES = 1_024
private const val MAX_PROGRESS_QR_URL_BYTES = 512
private const val MAX_PROGRESS_LOOKUP_VALUE_BYTES = 128

private val BagExtractionJson = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

@Serializable
enum class BagReviewMode {
    ADD_NEW,
    RESCAN,
}

@Serializable
data class BagReviewContext(
    val mode: BagReviewMode,
    val targetBagId: Long? = null,
) {
    init {
        require(
            (mode == BagReviewMode.ADD_NEW && targetBagId == null) ||
                (mode == BagReviewMode.RESCAN && targetBagId != null && targetBagId > 0L),
        ) { "Bag review context does not match its mode" }
    }

    companion object {
        fun addNew(): BagReviewContext = BagReviewContext(BagReviewMode.ADD_NEW)

        fun rescan(targetBagId: Long): BagReviewContext =
            BagReviewContext(BagReviewMode.RESCAN, targetBagId)
    }
}

fun encodeBagReviewContext(context: BagReviewContext?): String? =
    context?.let { BagExtractionJson.encodeToString(it) }

fun decodeBagReviewContext(encoded: String?): BagReviewContext? {
    if (encoded.isNullOrBlank()) return null
    return runCatching {
        BagExtractionJson.decodeFromString<BagReviewContext>(encoded)
    }.getOrNull()
}

fun isAddNewBagReview(context: BagReviewContext?): Boolean =
    context?.mode != BagReviewMode.RESCAN

fun bagReviewContextsMatch(
    expected: BagReviewContext,
    actual: BagReviewContext?,
): Boolean = when (expected.mode) {
    BagReviewMode.ADD_NEW -> isAddNewBagReview(actual)
    BagReviewMode.RESCAN -> actual?.mode == BagReviewMode.RESCAN &&
        actual.targetBagId == expected.targetBagId
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
    val tastingNotes: List<String> = emptyList(),
    val farms: List<String> = emptyList(),
    val altitudes: List<String> = emptyList(),
)

fun BagPhotoProcessingResult.encodeToJson(): String {
    val payload = toPayload(stripLargeOptionalFields = false)
    val encoded = BagExtractionJson.encodeToString(payload)
    if (encoded.utf8ByteCount() <= MAX_WORK_RESULT_JSON_BYTES) return encoded

    val compact = BagExtractionJson.encodeToString(toPayload(stripLargeOptionalFields = true))
    if (compact.utf8ByteCount() <= MAX_WORK_RESULT_JSON_BYTES) return compact

    return BagExtractionJson.encodeToString(toMinimalWorkPayload()).also { minimal ->
        check(minimal.utf8ByteCount() <= MAX_WORK_RESULT_JSON_BYTES) {
            "Minimal bag extraction result exceeds WorkManager data budget"
        }
    }
}

fun BagPhotoProcessingResult.encodeToStoredJson(): String =
    BagExtractionJson.encodeToString(toPayload(stripLargeOptionalFields = false))

/**
 * Serializes the field values needed by the read-only, in-progress inventory
 * card. WorkManager progress is capped at 10 KB, so evidence that is only used
 * by the final review is omitted while the display values remain available.
 */
fun BagPhotoProcessingResult.encodeForProgressJson(): String? {
    val preview = copy(
        capturedPhotoUris = capturedPhotoUris?.takeIf {
            it.utf8ByteCount() <= MAX_PROGRESS_CAPTURED_PHOTO_URIS_BYTES
        },
        detectedBarcode = detectedBarcode?.truncateUtf8(MAX_PROGRESS_FIELD_VALUE_BYTES),
        detectedQrUrl = detectedQrUrl?.truncateUtf8(MAX_PROGRESS_QR_URL_BYTES),
        offLookupName = offLookupName?.truncateUtf8(MAX_PROGRESS_LOOKUP_VALUE_BYTES),
        offLookupRoaster = offLookupRoaster?.truncateUtf8(MAX_PROGRESS_LOOKUP_VALUE_BYTES),
        fieldEvidence = fieldEvidence.mapValues { (_, evidence) ->
            evidence.copy(
                value = evidence.value.truncateUtf8(MAX_PROGRESS_FIELD_VALUE_BYTES),
                rawValue = evidence.rawValue.truncateUtf8(MAX_PROGRESS_FIELD_VALUE_BYTES),
                canonicalKey = evidence.canonicalKey?.truncateUtf8(MAX_PROGRESS_FIELD_VALUE_BYTES),
                supportingText = null,
                previewUri = null,
                previewRect = null,
            )
        },
        photoAnalyses = emptyList(),
        reviewHints = emptyList(),
    )
    val encoded = preview.encodeToJson()
    return encoded.takeIf { it.utf8ByteCount() <= MAX_PROGRESS_DATA_BYTES }
}

private fun String.utf8ByteCount(): Int = toByteArray(Charsets.UTF_8).size

private fun String.truncateUtf8(maxBytes: Int): String {
    if (utf8ByteCount() <= maxBytes) return this

    var end = 0
    var usedBytes = 0
    while (end < length) {
        val codePoint = codePointAt(end)
        val codePointChars = Character.charCount(codePoint)
        val codePointBytes = String(Character.toChars(codePoint)).utf8ByteCount()
        if (usedBytes + codePointBytes > maxBytes) break
        usedBytes += codePointBytes
        end += codePointChars
    }
    return substring(0, end)
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

fun KnownFieldValues.encodeToJson(): String {
    for (maxValuesPerField in MAX_WORK_KNOWN_VALUES_PER_FIELD downTo 0) {
        val encoded = BagExtractionJson.encodeToString(toDto(maxValuesPerField))
        if (encoded.utf8ByteCount() <= MAX_WORK_KNOWN_VALUES_JSON_BYTES) return encoded
    }
    return BagExtractionJson.encodeToString(KnownFieldValuesDto())
}

fun decodeKnownFieldValues(json: String?): KnownFieldValues {
    if (json.isNullOrBlank()) return KnownFieldValues.EMPTY
    return runCatching {
        BagExtractionJson.decodeFromString<KnownFieldValuesDto>(json).toKnownFieldValues()
    }.getOrDefault(KnownFieldValues.EMPTY)
}

private fun BagPhotoProcessingResult.toPayload(stripLargeOptionalFields: Boolean): BagExtractionPayload =
    BagExtractionPayload(
        fieldEvidence = fieldEvidence.values.map { it.toDto(stripLargeOptionalFields) },
        reviewHints = if (stripLargeOptionalFields) {
            reviewHints.take(MAX_WORK_REVIEW_HINTS).map { it.toDto(compact = true) }
        } else {
            reviewHints.map { it.toDto(compact = false) }
        },
        llmStatus = llmStatus.name,
        detectedBarcode = detectedBarcode.compactIf(stripLargeOptionalFields),
        detectedQrUrl = detectedQrUrl?.let {
            if (stripLargeOptionalFields) it.truncateUtf8(MAX_PROGRESS_QR_URL_BYTES) else it
        },
        offLookupName = offLookupName.compactIf(stripLargeOptionalFields),
        offLookupRoaster = offLookupRoaster.compactIf(stripLargeOptionalFields),
        thumbnailFocus = thumbnailFocus?.toDto(),
        capturedPhotoUris = capturedPhotoUris?.let {
            if (stripLargeOptionalFields) it.truncateUtf8(MAX_WORK_CAPTURED_PHOTO_URIS_BYTES) else it
        },
    )

private fun BagPhotoProcessingResult.toMinimalWorkPayload(): BagExtractionPayload =
    BagExtractionPayload(
        fieldEvidence = fieldEvidence.values.take(16).map { evidence ->
            FieldEvidenceDto(
                fieldName = evidence.fieldName.truncateUtf8(48),
                value = evidence.value.truncateUtf8(64),
                rawValue = evidence.rawValue.truncateUtf8(64),
                sourceType = evidence.sourceType.name,
                confidence = evidence.confidence.name,
            )
        },
        llmStatus = llmStatus.name,
        detectedBarcode = detectedBarcode?.truncateUtf8(64),
        detectedQrUrl = detectedQrUrl?.truncateUtf8(256),
        offLookupName = offLookupName?.truncateUtf8(64),
        offLookupRoaster = offLookupRoaster?.truncateUtf8(64),
        thumbnailFocus = thumbnailFocus?.toDto(),
        capturedPhotoUris = capturedPhotoUris?.truncateUtf8(512),
    )

private fun BagFieldEvidence.toDto(stripLargeOptionalFields: Boolean): FieldEvidenceDto =
    FieldEvidenceDto(
        fieldName = fieldName.compactIf(stripLargeOptionalFields).orEmpty(),
        value = value.compactIf(stripLargeOptionalFields).orEmpty(),
        rawValue = rawValue.compactIf(stripLargeOptionalFields).orEmpty(),
        canonicalKey = canonicalKey.compactIf(stripLargeOptionalFields),
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

private fun BagPhotoReviewHint.toDto(compact: Boolean): ReviewHintDto =
    ReviewHintDto(
        severity = severity.name,
        message = if (compact) message.truncateUtf8(MAX_WORK_FIELD_VALUE_BYTES) else message,
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

private fun KnownFieldValues.toDto(maxValuesPerField: Int): KnownFieldValuesDto =
    KnownFieldValuesDto(
        names = names.boundedKnownValues(maxValuesPerField),
        roasters = roasters.boundedKnownValues(maxValuesPerField),
        origins = origins.boundedKnownValues(maxValuesPerField),
        regions = regions.boundedKnownValues(maxValuesPerField),
        varieties = varieties.boundedKnownValues(maxValuesPerField),
        processTypes = processTypes.boundedKnownValues(maxValuesPerField),
        roastLevels = roastLevels.boundedKnownValues(maxValuesPerField),
        tastingNotes = tastingNotes.boundedKnownValues(maxValuesPerField),
        farms = farms.boundedKnownValues(maxValuesPerField),
        altitudes = altitudes.boundedKnownValues(maxValuesPerField),
    )

private fun String?.compactIf(compact: Boolean): String? =
    if (compact) this?.truncateUtf8(MAX_WORK_FIELD_VALUE_BYTES) else this

private fun List<String>.boundedKnownValues(maxValues: Int): List<String> =
    take(maxValues).map { it.truncateUtf8(MAX_WORK_FIELD_VALUE_BYTES) }

private fun KnownFieldValuesDto.toKnownFieldValues(): KnownFieldValues =
    KnownFieldValues(
        names = names,
        roasters = roasters,
        origins = origins,
        regions = regions,
        varieties = varieties,
        processTypes = processTypes,
        roastLevels = roastLevels,
        tastingNotes = tastingNotes,
        farms = farms,
        altitudes = altitudes,
    )
