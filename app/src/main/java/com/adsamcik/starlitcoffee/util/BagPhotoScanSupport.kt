package com.adsamcik.starlitcoffee.util

import java.util.Locale

enum class BagCaptureSide {
    FRONT,
    BACK,
}

enum class BagFieldSourceType {
    OCR,
    CONSENSUS,
    QR_LINK_LOOKUP,
    OBSERVED_BARCODE_STEM,
    LOCAL_BARCODE_MATCH,
    BARCODE_LOOKUP,
    LLM,
}

enum class BagFieldConfidence {
    HIGH,
    MEDIUM,
    LOW,
    NEEDS_REVIEW,
}

enum class BagReviewSeverity {
    INFO,
    WARNING,
}

data class BagPhotoRect(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float,
)

data class BagCaptureQuality(
    val blurScore: Float,
    val glarePercent: Float,
    val overexposedPercent: Float,
    val underexposedPercent: Float,
    val textBlockCount: Int,
    val textDetected: Boolean,
) {
    val sharpEnough: Boolean
        get() = blurScore >= 12f

    val glareOkay: Boolean
        get() = glarePercent <= 0.18f

    val exposureOkay: Boolean
        get() = overexposedPercent <= 0.25f && underexposedPercent <= 0.55f

    val readyForCapture: Boolean
        get() = sharpEnough && glareOkay && exposureOkay && textDetected

    val issues: List<String>
        get() = buildList {
            if (!sharpEnough) add("Keep phone steady")
            if (!glareOkay) add("Reduce glare")
            if (!exposureOkay) add("Improve lighting")
            if (!textDetected || textBlockCount <= 0) add("Center the label text")
        }
}

data class BagFieldCandidate(
    val fieldName: String,
    val value: String,
    val rawValue: String = value,
    val canonicalKey: String? = null,
    val sourceType: BagFieldSourceType,
    val side: BagCaptureSide? = null,
    val confidenceHint: BagFieldConfidence = BagFieldConfidence.LOW,
    val sourceCount: Int = 1,
    val matchStrategy: CoffeeMetadataMatchStrategy? = null,
    val supportingText: String? = null,
    val previewUri: String? = null,
    val previewRect: BagPhotoRect? = null,
)

data class BagFieldEvidence(
    val fieldName: String,
    val value: String,
    val rawValue: String = value,
    val canonicalKey: String? = null,
    val sourceType: BagFieldSourceType,
    val confidence: BagFieldConfidence,
    val side: BagCaptureSide? = null,
    val matchStrategy: CoffeeMetadataMatchStrategy? = null,
    val supportingText: String? = null,
    val previewUri: String? = null,
    val previewRect: BagPhotoRect? = null,
) {
    fun summaryLabel(): String {
        val sourceLabel = when (sourceType) {
            BagFieldSourceType.OCR -> "Detected"
            BagFieldSourceType.CONSENSUS -> "Confirmed"
            BagFieldSourceType.QR_LINK_LOOKUP -> "QR website"
            BagFieldSourceType.OBSERVED_BARCODE_STEM -> "Observed stem"
            BagFieldSourceType.LOCAL_BARCODE_MATCH -> "Saved bag match"
            BagFieldSourceType.BARCODE_LOOKUP -> "Barcode lookup"
            BagFieldSourceType.LLM -> "AI analysis"
        }
        val confidenceLabel = when (confidence) {
            BagFieldConfidence.HIGH -> "high confidence"
            BagFieldConfidence.MEDIUM -> "medium confidence"
            BagFieldConfidence.LOW -> "low confidence"
            BagFieldConfidence.NEEDS_REVIEW -> "needs review"
        }
        val strategyLabel = when (matchStrategy) {
            CoffeeMetadataMatchStrategy.RELATION_INFERENCE -> "canonical inference"
            CoffeeMetadataMatchStrategy.EXACT_ALIAS,
            CoffeeMetadataMatchStrategy.CONTAINS_ALIAS,
            CoffeeMetadataMatchStrategy.RAW_FALLBACK,
            null,
            -> null
        }
        val sideLabel = side?.name?.lowercase()
        return listOfNotNull(sourceLabel, strategyLabel, sideLabel, confidenceLabel).joinToString(" · ")
    }
}

data class BagPhotoAnalysis(
    val uri: String,
    val side: BagCaptureSide,
    val quality: BagCaptureQuality,
    val extractedText: String? = null,
)

data class BagPhotoReviewHint(
    val severity: BagReviewSeverity,
    val message: String,
)

enum class LlmEnrichmentStatus {
    NOT_RUN,
    SUCCEEDED,
    FAILED,
    UNAVAILABLE,
}

data class BagPhotoProcessingResult(
    val ocrPrefill: OcrFieldExtractor.OcrExtractionResult? = null,
    val capturedPhotoUris: String? = null,
    val detectedBarcode: String? = null,
    val detectedQrUrl: String? = null,
    val offLookupName: String? = null,
    val offLookupRoaster: String? = null,
    val fieldEvidence: Map<String, BagFieldEvidence> = emptyMap(),
    val photoAnalyses: List<BagPhotoAnalysis> = emptyList(),
    val reviewHints: List<BagPhotoReviewHint> = emptyList(),
    val llmStatus: LlmEnrichmentStatus = LlmEnrichmentStatus.NOT_RUN,
    // Normalized label region the scan detected on the FRONT photo, used to
    // crop a focused square thumbnail at save time. Null when no usable text
    // region was found (falls back to the full photo).
    val thumbnailFocus: BagPhotoRect? = null,
) {
    val shouldSuggestRetake: Boolean
        get() = reviewHints.any { it.severity == BagReviewSeverity.WARNING }
}

object BagPhotoScanSupport {
    private val sanitizedEvidenceFields = setOf(
        "origin",
        "region",
        "processType",
        "roastLevel",
        "variety",
        "weight",
        "isDecaf",
        "roastDate",
        "expiryDate",
    )

    fun resolveField(
        fieldName: String,
        candidates: List<BagFieldCandidate>,
    ): BagFieldEvidence? {
        if (candidates.isEmpty()) return null

        val grouped = candidates
            .filter { it.fieldName == fieldName }
            .groupBy { it.canonicalKey ?: normalizeValue(it.rawValue) }
            .mapValues { (_, group) -> scoreGroup(group) }

        val winningGroup = grouped.maxByOrNull { (_, scored) -> scored.score }?.value ?: return null
        val representative = winningGroup.representative

        return BagFieldEvidence(
            fieldName = fieldName,
            value = representative.value,
            rawValue = representative.rawValue,
            canonicalKey = representative.canonicalKey,
            sourceType = if (winningGroup.isConsensus) BagFieldSourceType.CONSENSUS else representative.sourceType,
            confidence = when {
                winningGroup.score >= 11 -> BagFieldConfidence.HIGH
                winningGroup.score >= 8 -> BagFieldConfidence.MEDIUM
                winningGroup.score >= 5 -> BagFieldConfidence.LOW
                else -> BagFieldConfidence.NEEDS_REVIEW
            },
            side = representative.side,
            matchStrategy = representative.matchStrategy,
            supportingText = representative.supportingText,
            previewUri = representative.previewUri,
            previewRect = representative.previewRect,
        )
    }

    fun buildReviewHints(
        photoAnalyses: List<BagPhotoAnalysis>,
        resolvedFields: Map<String, BagFieldEvidence>,
        additionalHints: List<BagPhotoReviewHint> = emptyList(),
        scanServiceUnavailable: Boolean = false,
    ): List<BagPhotoReviewHint> {
        val hints = mutableListOf<BagPhotoReviewHint>()
        val criticalFields = listOf("name", "roaster")
        val missingCritical = criticalFields.filterNot { resolvedFields.containsKey(it) }
        val hasAnyFields = resolvedFields.isNotEmpty()

        photoAnalyses
            .filter { it.quality.issues.isNotEmpty() }
            .forEach { analysis ->
                val sideLabel = analysis.side.name.lowercase().replaceFirstChar { it.uppercase() }
                hints += BagPhotoReviewHint(
                    severity = BagReviewSeverity.INFO,
                    message = "$sideLabel photo: ${analysis.quality.issues.joinToString()}",
                )
            }

        hints += additionalHints

        if (missingCritical.isNotEmpty() && !hasAnyFields) {
            // No fields at all. Only blame the photo + suggest a retake when the
            // scan/AI service actually ran — retaking can't help when the empty
            // result is because the on-device service was unavailable. In that
            // case the LLM-status card ("AI enrichment is not available right
            // now") is the single source of truth and offers the retry path, so
            // we suppress this otherwise-contradictory WARNING.
            if (!scanServiceUnavailable) {
                hints += BagPhotoReviewHint(
                    severity = BagReviewSeverity.WARNING,
                    message = "Could not read any fields — retake the photo or add details manually.",
                )
            }
        } else if (missingCritical.isNotEmpty()) {
            // Some fields found but missing name/roaster — common for small bags
            hints += BagPhotoReviewHint(
                severity = BagReviewSeverity.INFO,
                message = "Missing ${missingCritical.joinToString(" and ")} — you can add ${if (missingCritical.size == 1) "it" else "them"} below.",
            )
        }

        return hints
    }

    /**
     * Applies the same deterministic field contracts used by the editable scan
     * prefill while retaining the winning evidence's provenance for relocated
     * or normalized values.
     */
    fun sanitizeFieldEvidence(
        resolvedFields: Map<String, BagFieldEvidence>,
    ): Map<String, BagFieldEvidence> {
        fun value(fieldName: String): String? = resolvedFields[fieldName]?.value

        // isDecaf is Boolean? with three-state semantics:
        //   true  → definitely decaf  (LLM/OCR confirmed a decaf marker)
        //   false → definitely regular (LLM explicitly returned `false`)
        //   null  → unknown / not visible
        // The candidate value is the string the LLM or OCR emitted — "true",
        // "false", "yes", "no", or a freeform marker. We only flip to true
        // when the value parses as an affirmative; "false" must round-trip
        // as false, not as "field present, therefore decaf". The earlier
        // bug treated mere presence of the key as truth, which caused every
        // bag where the LLM responded with `isDecaf: false` to render as
        // decaf in the review sheet.
        val isDecaf = value("isDecaf")?.let(::parseExtractedDecaf)
        // Enforce field contracts before the values reach the review chips and
        // the saved bag. The extraction step trusts the raw on-device-LLM
        // output, which routinely misfiles correctly-read tokens on bilingual /
        // structured labels (a country name in `region`, a decaf marker in
        // `process`, an OCR-merged weight, a bare species name masquerading as
        // origin/variety, a bean-form word leaking into process). This is the
        // single choke point for every prefill path, so one call fixes chips +
        // save together. roastDate/expiryDate are format-validated separately
        // below (sanitizeExtraction has no dictionary to check dates against).
        val sanitized = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = value("origin"),
            region = value("region"),
            processType = value("processType"),
            roastLevel = value("roastLevel"),
            variety = value("variety"),
            weight = value("weight"),
            isDecaf = isDecaf,
        )

        fun sourceEvidence(fieldName: String, outputValue: String): BagFieldEvidence? {
            resolvedFields[fieldName]
                ?.takeIf { it.value.trim() == outputValue }
                ?.let { return it }

            val relocatedFrom = sanitized.corrections.firstOrNull { correction ->
                correction.action == ScanFieldCorrectionAction.RELOCATED &&
                    correction.to == fieldName &&
                    correction.from?.trim() == outputValue
            }?.field
            if (relocatedFrom != null) return resolvedFields[relocatedFrom]

            if (fieldName == "isDecaf" && sanitized.isDecaf == true) {
                val decafSource = sanitized.corrections.firstOrNull { correction ->
                    correction.action == ScanFieldCorrectionAction.FLAGGED_DECAF
                }?.field
                if (decafSource != null) return resolvedFields[decafSource]
            }

            return resolvedFields[fieldName]
        }

        fun sanitizedEvidence(fieldName: String, outputValue: String): BagFieldEvidence? {
            val source = sourceEvidence(fieldName, outputValue) ?: return null
            val normalized = when (fieldName) {
                "origin", "region", "processType", "roastLevel", "variety" ->
                    CoffeeMetadataNormalizer.normalizeField(fieldName, outputValue, Locale.ENGLISH)
                else -> null
            }
            return source.copy(
                fieldName = fieldName,
                value = outputValue,
                canonicalKey = when (fieldName) {
                    "isDecaf" -> outputValue
                    else -> normalized?.canonicalKey
                        ?: source.canonicalKey.takeIf { source.fieldName == fieldName }
                },
                matchStrategy = normalized?.matchStrategy
                    ?: source.matchStrategy.takeIf { source.fieldName == fieldName },
            )
        }

        val sanitizedValues = mapOf(
            "origin" to sanitized.origin,
            "region" to sanitized.region,
            "processType" to sanitized.processType,
            "roastLevel" to sanitized.roastLevel,
            "variety" to sanitized.variety,
            "weight" to sanitized.weight,
            "isDecaf" to sanitized.isDecaf?.toString(),
            "roastDate" to CoffeeMetadataNormalizer.sanitizeDate(value("roastDate")),
            "expiryDate" to CoffeeMetadataNormalizer.sanitizeDate(value("expiryDate")),
        )

        return buildMap {
            resolvedFields.forEach { (fieldName, evidence) ->
                if (fieldName !in sanitizedEvidenceFields) {
                    evidence.value.trim().takeIf(String::isNotBlank)?.let { cleanValue ->
                        put(fieldName, evidence.copy(value = cleanValue))
                    }
                }
            }
            sanitizedValues.forEach { (fieldName, outputValue) ->
                outputValue?.let { value ->
                    sanitizedEvidence(fieldName, value)?.let { put(fieldName, it) }
                }
            }
        }
    }

    fun buildPrefill(resolvedFields: Map<String, BagFieldEvidence>): OcrFieldExtractor.OcrExtractionResult {
        val sanitizedFields = sanitizeFieldEvidence(resolvedFields)
        fun value(fieldName: String): String? = sanitizedFields[fieldName]?.value
        val fieldConfidence = sanitizedFields.mapValues { (_, evidence) -> evidence.confidence }
        return OcrFieldExtractor.OcrExtractionResult(
            name = value("name"),
            roaster = value("roaster"),
            origin = value("origin"),
            region = value("region"),
            farm = value("farm"),
            variety = value("variety"),
            processType = value("processType"),
            altitude = value("altitude"),
            tastingNotes = value("tastingNotes"),
            roastLevel = value("roastLevel"),
            roastDate = value("roastDate"),
            expiryDate = value("expiryDate"),
            weight = value("weight"),
            isDecaf = value("isDecaf")?.let(::parseExtractedDecaf),
            fieldConfidence = fieldConfidence,
        )
    }

    private fun parseExtractedDecaf(raw: String): Boolean = when (raw.trim().lowercase()) {
        "true", "yes", "1", "y", "decaf", "decaffeinated", "bezkofeinová", "bezkofeinova" -> true
        "false", "no", "0", "n", "regular", "caffeinated" -> false
        else -> true
    }

    private data class ScoredGroup(
        val representative: BagFieldCandidate,
        val score: Int,
        val isConsensus: Boolean,
    )

    private fun scoreGroup(group: List<BagFieldCandidate>): ScoredGroup {
        val bestCandidate = group.maxByOrNull { candidate -> candidateWeight(candidate) } ?: group.first()
        val sides = group.mapNotNull { it.side }.toSet().size
        val sourceTypes = group.map { it.sourceType }.toSet().size
        val repetitions = group.sumOf { it.sourceCount.coerceAtLeast(1) }
        val sideBonus = (sides - 1).coerceAtLeast(0)
        val sourceTypeBonus = (sourceTypes - 1).coerceAtLeast(0)
        val score = group.sumOf { candidateWeight(it) } +
            ((group.size - 1) * 3) +
            (sideBonus * 4) +
            (sourceTypeBonus * 2) +
            (repetitions - group.size)

        return ScoredGroup(
            representative = bestCandidate,
            score = score,
            isConsensus = group.size > 1 || sides > 1 || sourceTypes > 1,
        )
    }

    private fun candidateWeight(it: BagFieldCandidate): Int {
        val sourceWeight = when (it.sourceType) {
            BagFieldSourceType.BARCODE_LOOKUP -> 8
            BagFieldSourceType.QR_LINK_LOOKUP -> 6
            BagFieldSourceType.OBSERVED_BARCODE_STEM -> 2
            BagFieldSourceType.LOCAL_BARCODE_MATCH -> 9
            BagFieldSourceType.CONSENSUS -> 6
            BagFieldSourceType.OCR -> 4
            BagFieldSourceType.LLM -> 10
        }
        val confidenceWeight = when (it.confidenceHint) {
            BagFieldConfidence.HIGH -> 4
            BagFieldConfidence.MEDIUM -> 3
            BagFieldConfidence.LOW -> 2
            BagFieldConfidence.NEEDS_REVIEW -> 1
        }
        return sourceWeight + confidenceWeight
    }

    private fun normalizeValue(value: String): String = value.trim().lowercase()
}
