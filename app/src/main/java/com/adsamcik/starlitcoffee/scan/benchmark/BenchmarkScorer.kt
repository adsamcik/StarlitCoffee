package com.adsamcik.starlitcoffee.scan.benchmark

import kotlin.math.abs

/**
 * Scores pipeline-extracted values against human-annotated ground truth.
 */
object BenchmarkScorer {

    private const val SHORT_STRING_THRESHOLD = 8
    private const val SHORT_LEVENSHTEIN_MAX = 2
    private const val LONG_LEVENSHTEIN_MAX = 3
    private const val ALTITUDE_TOLERANCE_M = 100
    private const val WEIGHT_TOLERANCE_G = 1.0
    private const val JACCARD_SEMANTIC_THRESHOLD = 0.5

    // ── Single field ────────────────────────────────────────────────

    fun scoreField(
        fieldName: String,
        extracted: String?,
        groundTruth: GroundTruthField,
    ): FieldScore {
        val gt = groundTruth.groundTruth?.trim()
        val ext = extracted?.trim()?.ifEmpty { null }

        val scoreType = when {
            // Both null
            ext == null && gt == null -> {
                if (!groundTruth.isOnLabel) FieldScoreType.CORRECT_NULL
                else FieldScoreType.CORRECT_NULL // not on label or annotator marked null
            }
            // Pipeline returned null but value is on label
            ext == null && gt != null -> FieldScoreType.MISSING
            // Pipeline returned something but nothing is on label
            ext != null && gt == null -> {
                if (!groundTruth.isOnLabel) FieldScoreType.HALLUCINATED
                else FieldScoreType.WRONG // label has field but annotator couldn't read it
            }
            // Both non-null — delegate to field-specific matching
            else -> matchField(fieldName, ext!!, gt!!)
        }

        return FieldScore(
            fieldName = fieldName,
            scoreType = scoreType,
            extracted = ext,
            groundTruth = gt,
        )
    }

    // ── Session ─────────────────────────────────────────────────────

    fun scoreSession(
        extracted: Map<String, String?>,
        entry: GroundTruthEntry,
    ): SessionScore {
        val fieldScores = entry.fields.map { (fieldName, gtField) ->
            scoreField(fieldName, extracted[fieldName], gtField)
        }

        val onLabelFields = entry.fields.filter { it.value.isOnLabel }
        val totalOnLabel = onLabelFields.size

        val correctCount = fieldScores.count {
            it.scoreType == FieldScoreType.EXACT || it.scoreType == FieldScoreType.SEMANTIC
        }
        val hallucinatedCount = fieldScores.count { it.scoreType == FieldScoreType.HALLUCINATED }

        val ratio = if (totalOnLabel > 0) correctCount.toFloat() / totalOnLabel else 1f

        val outcome = when {
            hallucinatedCount > 0 -> SessionOutcome.FAILED
            ratio >= 0.8f -> SessionOutcome.COMPLETE
            ratio >= 0.4f -> SessionOutcome.PARTIAL
            else -> SessionOutcome.FAILED
        }

        return SessionScore(
            bagId = entry.bagId,
            fieldScores = fieldScores,
            outcome = outcome,
            correctCount = correctCount,
            totalOnLabel = totalOnLabel,
            hallucinatedCount = hallucinatedCount,
        )
    }

    // ── Batch ───────────────────────────────────────────────────────

    fun scoreBatch(
        entries: List<Pair<Map<String, String?>, GroundTruthEntry>>,
    ): BatchScore {
        val sessionScores = entries.map { (extracted, gt) -> scoreSession(extracted, gt) }

        val completeCount = sessionScores.count { it.outcome == SessionOutcome.COMPLETE }
        val successRate = if (sessionScores.isNotEmpty()) {
            completeCount.toFloat() / sessionScores.size
        } else {
            0f
        }

        val totalFields = sessionScores.sumOf { it.fieldScores.size }
        val totalHallucinated = sessionScores.sumOf { it.hallucinatedCount }
        val hallucinationRate = if (totalFields > 0) {
            totalHallucinated.toFloat() / totalFields
        } else {
            0f
        }

        // Per-field accuracy across all sessions
        val fieldGroups = sessionScores.flatMap { it.fieldScores }.groupBy { it.fieldName }
        val perFieldAccuracy = fieldGroups.mapValues { (_, scores) ->
            val correct = scores.count {
                it.scoreType == FieldScoreType.EXACT ||
                    it.scoreType == FieldScoreType.SEMANTIC ||
                    it.scoreType == FieldScoreType.CORRECT_NULL
            }
            if (scores.isNotEmpty()) correct.toFloat() / scores.size else 0f
        }

        val verdict = when {
            successRate < 0.5f || hallucinationRate > 0.3f -> BenchmarkVerdict.RETHINK
            successRate >= 0.7f && hallucinationRate <= 0.2f -> BenchmarkVerdict.SHIP
            else -> BenchmarkVerdict.ITERATE
        }

        return BatchScore(
            sessionScores = sessionScores,
            overallSuccessRate = successRate,
            overallHallucinationRate = hallucinationRate,
            perFieldAccuracy = perFieldAccuracy,
            verdict = verdict,
        )
    }

    // ── Field-specific matching ─────────────────────────────────────

    internal fun matchField(fieldName: String, extracted: String, groundTruth: String): FieldScoreType {
        return when (fieldName) {
            "tastingNotes" -> matchTastingNotes(extracted, groundTruth)
            "weight" -> matchWeight(extracted, groundTruth)
            "altitude" -> matchAltitude(extracted, groundTruth)
            "roastDate" -> matchRoastDate(extracted, groundTruth)
            "name", "roastLevel" -> matchExactOrLevenshtein(extracted, groundTruth)
            else -> matchSemanticDefault(extracted, groundTruth)
        }
    }

    // ── Tasting notes (Jaccard) ─────────────────────────────────────

    internal fun matchTastingNotes(extracted: String, groundTruth: String): FieldScoreType {
        val extSet = parseTastingNotes(extracted)
        val gtSet = parseTastingNotes(groundTruth)
        if (extSet.isEmpty() && gtSet.isEmpty()) return FieldScoreType.EXACT

        val intersection = extSet.intersect(gtSet).size
        val union = extSet.union(gtSet).size
        val jaccard = if (union > 0) intersection.toFloat() / union else 0f

        return when {
            jaccard >= 1.0f -> FieldScoreType.EXACT
            jaccard > JACCARD_SEMANTIC_THRESHOLD -> FieldScoreType.SEMANTIC
            else -> FieldScoreType.WRONG
        }
    }

    internal fun parseTastingNotes(value: String): Set<String> {
        return value.split(",", ";")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    // ── Weight ──────────────────────────────────────────────────────

    internal fun matchWeight(extracted: String, groundTruth: String): FieldScoreType {
        val extGrams = parseWeightToGrams(extracted)
        val gtGrams = parseWeightToGrams(groundTruth)

        if (extGrams == null || gtGrams == null) {
            return matchSemanticDefault(extracted, groundTruth)
        }

        val diff = abs(extGrams - gtGrams)
        return when {
            diff <= WEIGHT_TOLERANCE_G -> FieldScoreType.EXACT
            // Same numeric value, different format (e.g., "340g" vs "340 grams")
            diff <= WEIGHT_TOLERANCE_G * 2 -> FieldScoreType.SEMANTIC
            else -> FieldScoreType.WRONG
        }
    }

    internal fun parseWeightToGrams(value: String): Double? {
        val cleaned = value.trim().lowercase()
        val match = Regex("""([\d.]+)\s*(g|grams?|oz|ounces?|lb|lbs?|pounds?|kg|kilograms?)""")
            .find(cleaned) ?: return null

        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]

        return when {
            unit.startsWith("oz") || unit.startsWith("ounce") -> number * 28.3495
            unit.startsWith("lb") || unit.startsWith("pound") -> number * 453.592
            unit.startsWith("kg") || unit.startsWith("kilogram") -> number * 1000.0
            else -> number // grams
        }
    }

    // ── Altitude ────────────────────────────────────────────────────

    internal fun matchAltitude(extracted: String, groundTruth: String): FieldScoreType {
        val extMid = parseAltitudeMidpoint(extracted)
        val gtMid = parseAltitudeMidpoint(groundTruth)

        if (extMid == null || gtMid == null) {
            return matchSemanticDefault(extracted, groundTruth)
        }

        val diff = abs(extMid - gtMid)
        return when {
            diff == 0.0 -> FieldScoreType.EXACT
            diff <= ALTITUDE_TOLERANCE_M -> FieldScoreType.SEMANTIC
            else -> FieldScoreType.WRONG
        }
    }

    internal fun parseAltitudeMidpoint(value: String): Double? {
        val cleaned = value.trim().replace(Regex("[^\\d.\\-–]"), " ").trim()
        // Range: "1800-2100" or "1800–2100"
        val rangeParts = cleaned.split(Regex("[\\-–]"))
            .mapNotNull { it.trim().toDoubleOrNull() }

        return when {
            rangeParts.size >= 2 -> (rangeParts[0] + rangeParts[1]) / 2.0
            rangeParts.size == 1 -> rangeParts[0]
            else -> null
        }
    }

    // ── Roast date ──────────────────────────────────────────────────

    internal fun matchRoastDate(extracted: String, groundTruth: String): FieldScoreType {
        val extNorm = extracted.trim()
        val gtNorm = groundTruth.trim()
        return if (extNorm.equals(gtNorm, ignoreCase = true)) {
            FieldScoreType.EXACT
        } else {
            FieldScoreType.WRONG
        }
    }

    // ── Default semantic (Levenshtein) ──────────────────────────────

    internal fun matchSemanticDefault(extracted: String, groundTruth: String): FieldScoreType {
        val extNorm = extracted.trim().lowercase()
        val gtNorm = groundTruth.trim().lowercase()

        if (extNorm == gtNorm) return FieldScoreType.EXACT

        val distance = levenshtein(extNorm, gtNorm)
        val threshold = if (gtNorm.length <= SHORT_STRING_THRESHOLD) {
            SHORT_LEVENSHTEIN_MAX
        } else {
            LONG_LEVENSHTEIN_MAX
        }

        return if (distance <= threshold) FieldScoreType.SEMANTIC else FieldScoreType.WRONG
    }

    internal fun matchExactOrLevenshtein(extracted: String, groundTruth: String): FieldScoreType {
        return matchSemanticDefault(extracted, groundTruth)
    }

    // ── Levenshtein distance ────────────────────────────────────────

    internal fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[m][n]
    }
}
