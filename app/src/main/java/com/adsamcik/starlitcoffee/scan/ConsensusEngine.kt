package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.scan.model.CandidateState
import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.scan.model.FieldAccumulation
import com.adsamcik.starlitcoffee.scan.model.FieldStatus
import com.adsamcik.starlitcoffee.scan.model.FrameResult
import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.CoffeeMetadataNormalizer
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor.OcrExtractionResult
import kotlin.math.min
import kotlin.math.pow

/**
 * Core consensus engine for live scanning. Stateless — takes accumulated candidates
 * and frame results, returns updated field accumulations.
 *
 * Algorithms:
 * - Levenshtein medoid clustering: groups near-miss OCR variants before voting
 * - Bayesian posterior updates: uses KnownFieldValues as priors
 * - Quality-weighted voting: sharper frames contribute more
 */
class ConsensusEngine(
    private val config: AccumulatorConfig = AccumulatorConfig.DEFAULT,
) {

    // --- Levenshtein medoid clustering ---

    /**
     * Compute the Levenshtein edit distance between two strings.
     * Uses the classic dynamic-programming algorithm, O(m×n).
     */
    fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost,
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
    }

    /**
     * Select the medoid of a cluster: the string that minimizes total edit distance
     * to all other members. For single-member clusters, returns that member.
     */
    fun selectMedoid(cluster: List<String>): String {
        if (cluster.size <= 1) return cluster.first()
        return cluster.minByOrNull { candidate ->
            cluster.sumOf { other -> levenshteinDistance(candidate.lowercase(), other.lowercase()) }
        } ?: cluster.first()
    }

    // --- Bayesian consensus ---

    /**
     * Build a prior distribution from [KnownFieldValues] for a given field.
     * Returns a map of lowercase known value → prior probability.
     * Unknown values get a uniform smoothing weight.
     */
    fun buildPrior(
        fieldName: String,
        knownValues: KnownFieldValues,
    ): Map<String, Float> {
        val values = knownValuesForField(fieldName, knownValues)
        if (values.isEmpty()) return emptyMap()

        val totalCount = values.size.toFloat()
        val freqs = values.groupBy { it.lowercase() }
        return freqs.mapValues { (_, occurrences) -> occurrences.size / totalCount }
    }

    /**
     * Return the set of lowercase known values for [fieldName] that have been
     * observed at least [minObservations] times in the user's history.
     * These are eligible for the prior boost in [computePosteriors].
     */
    fun getEligibleKnownValues(
        fieldName: String,
        knownValues: KnownFieldValues,
        minObservations: Int,
    ): Set<String> {
        val values = knownValuesForField(fieldName, knownValues)
        if (values.isEmpty()) return emptySet()

        return values
            .groupBy { it.lowercase() }
            .filter { (_, occurrences) -> occurrences.size >= minObservations }
            .keys
    }

    /** Resolve the raw list of known values for [fieldName] from [knownValues]. */
    private fun knownValuesForField(
        fieldName: String,
        knownValues: KnownFieldValues,
    ): List<String> = when (fieldName) {
        "name" -> knownValues.names
        "roaster" -> knownValues.roasters
        "origin" -> knownValues.origins
        "region" -> knownValues.regions
        "variety" -> knownValues.varieties
        "processType" -> knownValues.processTypes
        "roastLevel" -> knownValues.roastLevels
        "farm" -> knownValues.farms
        else -> emptyList()
    }

    /**
     * Compute the Bayesian posterior for all candidates of a single field,
     * given accumulated observations and optional priors from known values.
     *
     * Each observation contributes likelihood = sourceWeight × qualityWeight.
     * The posterior is proportional to prior × cumulative likelihood.
     *
     * When [eligibleKnownValues] is provided, candidates whose lowercase key
     * appears in the set receive a multiplicative [knownValueBoost] to their
     * raw posterior before normalization — making known values converge faster.
     */
    fun computePosteriors(
        candidates: Map<String, CandidateState>,
        priors: Map<String, Float>,
        eligibleKnownValues: Set<String> = emptySet(),
        knownValueBoost: Float = 1f,
    ): Map<String, Float> {
        if (candidates.isEmpty()) return emptyMap()

        val uniformPrior = 1f / (candidates.size + 1).toFloat()
        val rawPosteriors = candidates.mapValues { (key, state) ->
            val prior = priors[key.lowercase()] ?: uniformPrior
            // Option B: use floored vote count so sharp-frame evidence has stickiness
            val effectiveVotes = maxOf(
                state.qualityWeightedVotes,
                state.peakVotes * config.voteFloorFraction,
            )
            val likelihood = effectiveVotes
            val boost = if (key.lowercase() in eligibleKnownValues) knownValueBoost else 1f
            prior * likelihood * boost
        }

        val total = rawPosteriors.values.sum()
        if (total <= 0f) return candidates.mapValues { 1f / candidates.size.toFloat() }

        return rawPosteriors.mapValues { (_, v) -> v / total }
    }

    // --- Frame integration ---

    /**
     * Extract field-value pairs from an [OcrExtractionResult], returning a map
     * of fieldName → (value, confidenceHint).
     */
    fun extractFieldValues(
        result: OcrExtractionResult,
    ): Map<String, Pair<String, BagFieldConfidence>> {
        val fields = mutableMapOf<String, Pair<String, BagFieldConfidence>>()
        val conf = result.fieldConfidence

        result.name?.let { fields["name"] = it to (conf["name"] ?: BagFieldConfidence.LOW) }
        result.roaster?.let { fields["roaster"] = it to (conf["roaster"] ?: BagFieldConfidence.LOW) }
        result.origin?.let { fields["origin"] = it to (conf["origin"] ?: BagFieldConfidence.LOW) }
        result.region?.let { fields["region"] = it to (conf["region"] ?: BagFieldConfidence.LOW) }
        result.farm?.let { fields["farm"] = it to (conf["farm"] ?: BagFieldConfidence.LOW) }
        result.variety?.let { fields["variety"] = it to (conf["variety"] ?: BagFieldConfidence.LOW) }
        result.processType?.let {
            fields["processType"] = it to (conf["processType"] ?: BagFieldConfidence.LOW)
        }
        result.altitude?.let { fields["altitude"] = it to (conf["altitude"] ?: BagFieldConfidence.LOW) }
        result.tastingNotes?.let {
            fields["tastingNotes"] = it to (conf["tastingNotes"] ?: BagFieldConfidence.LOW)
        }
        result.roastLevel?.let {
            fields["roastLevel"] = it to (conf["roastLevel"] ?: BagFieldConfidence.LOW)
        }
        result.roastDate?.let { fields["roastDate"] = it to (conf["roastDate"] ?: BagFieldConfidence.LOW) }
        result.expiryDate?.let {
            fields["expiryDate"] = it to (conf["expiryDate"] ?: BagFieldConfidence.LOW)
        }
        result.weight?.let { fields["weight"] = it to (conf["weight"] ?: BagFieldConfidence.LOW) }
        if (result.isDecaf == true) {
            fields["isDecaf"] = "Decaf" to (conf["isDecaf"] ?: BagFieldConfidence.LOW)
        }

        return fields
    }

    /**
     * Integrate a single frame's observations into the existing field accumulations.
     *
     * For each field detected in the frame:
     * 1. Canonicalize the raw OCR string (lowercase, trim)
     * 2. Find or create a candidate cluster via Levenshtein distance
     * 3. Update the candidate's vote count, quality weight, source types, etc.
     * 4. Evict lowest-vote candidates if over [AccumulatorConfig.maxCandidatesPerField]
     */
    fun integrateFrame(
        currentFields: Map<String, FieldAccumulation>,
        frame: FrameResult,
        sourceType: BagFieldSourceType = BagFieldSourceType.OCR,
    ): Map<String, FieldAccumulation> {
        val fieldValues = extractFieldValues(frame.ocrResult)
        if (fieldValues.isEmpty()) return currentFields

        val qualityWeight = if (frame.isGoldenFrame) {
            config.goldenFrameWeight * (frame.quality.blurScore / 100f)
        } else {
            frame.quality.blurScore / 100f
        }

        val sourceWeight = when (sourceType) {
            BagFieldSourceType.LOCAL_BARCODE_MATCH -> config.sourceWeightLocalMatch
            BagFieldSourceType.BARCODE_LOOKUP -> config.sourceWeightBarcodeLookup
            BagFieldSourceType.QR_LINK_LOOKUP -> config.sourceWeightQrLink
            BagFieldSourceType.LLM -> config.sourceWeightLlm
            else -> config.sourceWeightOcr
        }

        val result = currentFields.toMutableMap()

        for ((fieldName, pair) in fieldValues) {
            val (rawValue, confidenceHint) = pair
            val existing = result[fieldName]

            // Skip fields the user has locked
            if (existing?.status == FieldStatus.USER_LOCKED) continue

            val normalizedValue = rawValue.trim().lowercase()
            if (normalizedValue.isBlank()) continue

            val currentCandidates = existing?.candidates?.toMutableMap() ?: mutableMapOf()

            // Find matching cluster by edit distance
            val matchKey = findMatchingCandidate(normalizedValue, currentCandidates.keys)

            val combinedWeight = qualityWeight * sourceWeight

            if (matchKey != null) {
                // Update existing candidate
                val state = currentCandidates.getValue(matchKey)
                val newVotes = state.qualityWeightedVotes + combinedWeight
                val newPeak = maxOf(state.peakVotes, newVotes)
                currentCandidates[matchKey] = state.copy(
                    rawVariants = (state.rawVariants + rawValue).distinct().takeLast(20),
                    qualityWeightedVotes = newVotes,
                    peakVotes = newPeak,
                    observationCount = state.observationCount + 1,
                    lastSeenFrameIndex = frame.frameIndex,
                    sourceTypes = state.sourceTypes + sourceType,
                    bestConfidenceHint = maxConfidence(state.bestConfidenceHint, confidenceHint),
                    sides = state.sides + frame.side,
                )
            } else {
                // New candidate
                currentCandidates[normalizedValue] = CandidateState(
                    normalizedValue = normalizedValue,
                    rawVariants = listOf(rawValue),
                    posteriorProbability = 0f,
                    qualityWeightedVotes = combinedWeight,
                    peakVotes = combinedWeight,
                    observationCount = 1,
                    lastSeenFrameIndex = frame.frameIndex,
                    sourceTypes = setOf(sourceType),
                    bestConfidenceHint = confidenceHint,
                    sides = setOf(frame.side),
                )
            }

            // Evict lowest-vote candidates if over cap
            val trimmed = if (currentCandidates.size > config.maxCandidatesPerField) {
                currentCandidates.entries
                    .sortedByDescending { it.value.qualityWeightedVotes }
                    .take(config.maxCandidatesPerField)
                    .associate { it.key to it.value }
            } else {
                currentCandidates.toMap()
            }

            result[fieldName] = (existing ?: FieldAccumulation(
                fieldName = fieldName,
                candidates = emptyMap(),
                status = FieldStatus.SCANNING,
            )).copy(candidates = trimmed)
        }

        return result
    }

    /**
     * Integrate enrichment evidence (barcode lookup, QR, local match) into accumulations.
     * These come as direct field→value pairs, not from OCR.
     */
    fun integrateEnrichment(
        currentFields: Map<String, FieldAccumulation>,
        fieldValues: Map<String, String>,
        sourceType: BagFieldSourceType,
        frameIndex: Int,
        side: Int = 0,
    ): Map<String, FieldAccumulation> {
        val syntheticOcr = OcrExtractionResult(
            name = fieldValues["name"],
            roaster = fieldValues["roaster"],
            origin = fieldValues["origin"],
            region = fieldValues["region"],
            farm = fieldValues["farm"],
            variety = fieldValues["variety"],
            processType = fieldValues["processType"],
            altitude = fieldValues["altitude"],
            tastingNotes = fieldValues["tastingNotes"],
            roastLevel = fieldValues["roastLevel"],
            roastDate = fieldValues["roastDate"],
            weight = fieldValues["weight"],
            isDecaf = fieldValues["isDecaf"]
                ?.let(CoffeeMetadataNormalizer::containsDecafMarker)
                ?.takeIf { it },
            fieldConfidence = fieldValues.keys.associateWith { BagFieldConfidence.HIGH },
        )
        val syntheticFrame = FrameResult(
            ocrResult = syntheticOcr,
            quality = ENRICHMENT_QUALITY,
            frameIndex = frameIndex,
            timestampMs = System.currentTimeMillis(),
            side = side,
            isGoldenFrame = true,
        )
        return integrateFrame(currentFields, syntheticFrame, sourceType)
    }

    // --- Field state machine ---

    /**
     * Run the field state machine for all fields after a consensus cycle.
     *
     * Updates posteriors, then transitions fields through:
     * SCANNING → PROVISIONAL → LOCKED (or CONFLICT)
     */
    fun runStateMachine(
        fields: Map<String, FieldAccumulation>,
        knownValues: KnownFieldValues,
        currentCycle: Int = 0,
    ): Map<String, FieldAccumulation> {
        // Option C: apply temporal decay to stale candidates before computing posteriors
        val decayedFields = fields.mapValues { (_, field) ->
            if (field.status == FieldStatus.USER_LOCKED) return@mapValues field

            val decayedCandidates = field.candidates.mapValues { (_, candidate) ->
                val cyclesSinceReinforced = currentCycle - candidate.lastReinforcedCycle
                if (cyclesSinceReinforced > config.staleGraceCycles) {
                    val decayFactor = (1f - config.staleDecayRate)
                        .pow(cyclesSinceReinforced - config.staleGraceCycles)
                    val flooredVotes = candidate.peakVotes * config.voteFloorFraction
                    candidate.copy(
                        qualityWeightedVotes = maxOf(
                            candidate.qualityWeightedVotes * decayFactor,
                            flooredVotes,
                        )
                    )
                } else {
                    candidate
                }
            }
            field.copy(candidates = decayedCandidates)
        }

        return decayedFields.mapValues { (_, field) ->
            if (field.status == FieldStatus.USER_LOCKED) return@mapValues field

            // Update posteriors with history prior boost
            val priors = buildPrior(field.fieldName, knownValues)
            val eligible = getEligibleKnownValues(
                field.fieldName, knownValues, config.knownValueMinObservations,
            )
            val posteriors = computePosteriors(
                field.candidates, priors, eligible, config.knownValuePriorBoost,
            )
            val updatedCandidates = field.candidates.mapValues { (key, state) ->
                state.copy(
                    posteriorProbability = posteriors[key] ?: 0f,
                    matchedKnownValue = key.lowercase() in eligible,
                )
            }

            val updatedField = field.copy(candidates = updatedCandidates)
            transitionField(updatedField)
        }
    }

    /**
     * Transition a single field through the state machine based on current posteriors.
     */
    internal fun transitionField(field: FieldAccumulation): FieldAccumulation {
        val top = field.topCandidate ?: return field.copy(
            status = FieldStatus.SCANNING,
            consecutiveLockCycles = 0,
            consecutiveConflictCycles = 0,
        )
        val runnerUp = field.runnerUp

        // Check for conflict: two strong candidates
        val hasConflict = runnerUp != null &&
                top.posteriorProbability < (1f - config.conflictCandidateMinShare) &&
                runnerUp.posteriorProbability >= config.conflictCandidateMinShare

        return when (field.status) {
            FieldStatus.SCANNING -> {
                if (hasConflict) {
                    val newConflictCycles = field.consecutiveConflictCycles + 1
                    if (newConflictCycles >= config.conflictCycles) {
                        field.copy(
                            status = FieldStatus.CONFLICT,
                            consecutiveConflictCycles = newConflictCycles,
                            consecutiveLockCycles = 0,
                        )
                    } else {
                        field.copy(consecutiveConflictCycles = newConflictCycles)
                    }
                } else if (top.posteriorProbability >= config.resolveThreshold) {
                    field.copy(
                        status = FieldStatus.PROVISIONAL,
                        consecutiveLockCycles = 1,
                        consecutiveConflictCycles = 0,
                        resolvedValue = top.normalizedValue,
                        resolvedEvidence = buildEvidence(field.fieldName, top),
                    )
                } else {
                    field.copy(
                        consecutiveLockCycles = 0,
                        consecutiveConflictCycles = if (hasConflict) field.consecutiveConflictCycles + 1 else 0,
                    )
                }
            }

            FieldStatus.PROVISIONAL -> {
                if (top.posteriorProbability >= config.resolveThreshold) {
                    val newLockCycles = field.consecutiveLockCycles + 1
                    if (newLockCycles >= config.lockCycles) {
                        field.copy(
                            status = FieldStatus.LOCKED,
                            consecutiveLockCycles = newLockCycles,
                            lockScore = 1f,
                            resolvedValue = top.normalizedValue,
                            resolvedEvidence = buildEvidence(field.fieldName, top),
                        )
                    } else {
                        field.copy(
                            consecutiveLockCycles = newLockCycles,
                            resolvedValue = top.normalizedValue,
                            resolvedEvidence = buildEvidence(field.fieldName, top),
                        )
                    }
                } else {
                    // Option A: high-water mark — preserve best candidate unless overtaken
                    val keepResolved = if (field.resolvedValue != null &&
                        top.normalizedValue == field.resolvedValue) {
                        // Same candidate still on top — keep it, just reset lock cycles
                        true
                    } else if (field.resolvedValue != null &&
                        top.posteriorProbability > (field.lockScore * 0.7f)) {
                        // Different candidate overtook with significant confidence — replace
                        false
                    } else {
                        // Different candidate but weak — keep high-water mark
                        true
                    }

                    if (keepResolved) {
                        field.copy(
                            status = FieldStatus.PROVISIONAL,
                            consecutiveLockCycles = 0,
                            // resolvedValue and resolvedEvidence PRESERVED
                        )
                    } else {
                        field.copy(
                            status = FieldStatus.SCANNING,
                            consecutiveLockCycles = 0,
                            resolvedValue = top.normalizedValue,
                            resolvedEvidence = buildEvidence(field.fieldName, top),
                        )
                    }
                }
            }

            FieldStatus.LOCKED -> {
                // Counter-evidence decay
                val currentResolved = field.resolvedValue
                if (currentResolved != null && top.normalizedValue != currentResolved) {
                    val newLockScore = (field.lockScore - config.lockDecayRate).coerceAtLeast(0f)
                    if (newLockScore <= 0f) {
                        // Lock broken — back to scanning
                        field.copy(
                            status = FieldStatus.SCANNING,
                            lockScore = 0f,
                            consecutiveLockCycles = 0,
                            resolvedValue = null,
                            resolvedEvidence = null,
                        )
                    } else {
                        field.copy(lockScore = newLockScore)
                    }
                } else {
                    field // stable lock
                }
            }

            FieldStatus.CONFLICT -> {
                // Can transition out of conflict if one candidate pulls ahead
                if (!hasConflict && top.posteriorProbability >= config.resolveThreshold) {
                    field.copy(
                        status = FieldStatus.PROVISIONAL,
                        consecutiveLockCycles = 1,
                        consecutiveConflictCycles = 0,
                        resolvedValue = top.normalizedValue,
                        resolvedEvidence = buildEvidence(field.fieldName, top),
                    )
                } else {
                    field
                }
            }

            FieldStatus.USER_LOCKED -> field // never changes
        }
    }

    // --- Quality gating ---

    /**
     * Check if a frame passes quality thresholds for admission.
     * Returns true if the frame should be processed.
     */
    fun framePassesQualityGate(
        frame: FrameResult,
        isRelaxed: Boolean = false,
    ): Boolean {
        val blurThreshold = if (isRelaxed) {
            config.minBlurScore * config.qualityRelaxationFactor
        } else {
            config.minBlurScore
        }
        val glareThreshold = if (isRelaxed) {
            config.maxGlarePercent / config.qualityRelaxationFactor
        } else {
            config.maxGlarePercent
        }
        return frame.quality.blurScore >= blurThreshold &&
                frame.quality.glarePercent <= glareThreshold
    }

    /**
     * Determine if a frame qualifies as a "golden frame" for heavy processing.
     */
    fun isGoldenFrame(frame: FrameResult): Boolean {
        return frame.quality.blurScore >= config.minBlurScore * 1.2f &&
                frame.quality.glareOkay &&
                frame.quality.exposureOkay &&
                frame.quality.textBlockCount >= 2
    }

    // --- Helpers ---

    private fun findMatchingCandidate(
        normalizedValue: String,
        existingKeys: Set<String>,
    ): String? {
        val threshold = editDistanceThreshold(normalizedValue)
        return existingKeys.firstOrNull { key ->
            levenshteinDistance(key.lowercase(), normalizedValue.lowercase()) <= threshold
        }
    }

    private fun editDistanceThreshold(value: String): Int {
        return if (value.length <= 8) config.levenshteinThresholdShort
        else config.levenshteinThresholdLong
    }

    private fun maxConfidence(
        a: BagFieldConfidence,
        b: BagFieldConfidence,
    ): BagFieldConfidence {
        return if (a.ordinal <= b.ordinal) a else b // lower ordinal = higher confidence
    }

    private fun buildEvidence(
        fieldName: String,
        candidate: CandidateState,
    ): BagFieldEvidence {
        val bestRaw = selectMedoid(candidate.rawVariants)
        val side = when {
            candidate.sides.size > 1 -> null
            candidate.sides.contains(0) -> BagCaptureSide.FRONT
            candidate.sides.contains(1) -> BagCaptureSide.BACK
            else -> null
        }
        val confidence = when {
            candidate.posteriorProbability >= 0.95f -> BagFieldConfidence.HIGH
            candidate.posteriorProbability >= 0.85f -> BagFieldConfidence.MEDIUM
            candidate.posteriorProbability >= 0.70f -> BagFieldConfidence.LOW
            else -> BagFieldConfidence.NEEDS_REVIEW
        }
        return BagFieldEvidence(
            fieldName = fieldName,
            value = bestRaw,
            rawValue = candidate.rawVariants.firstOrNull() ?: bestRaw,
            sourceType = if (candidate.sourceTypes.size > 1) {
                BagFieldSourceType.CONSENSUS
            } else {
                candidate.sourceTypes.firstOrNull() ?: BagFieldSourceType.OCR
            },
            confidence = confidence,
            side = side,
        )
    }

    companion object {
        /** Synthetic quality for enrichment evidence (always treated as high quality). */
        private val ENRICHMENT_QUALITY = com.adsamcik.starlitcoffee.util.BagCaptureQuality(
            blurScore = 100f,
            glarePercent = 0f,
            overexposedPercent = 0f,
            underexposedPercent = 0f,
            textBlockCount = 10,
            textDetected = true,
        )
    }
}
