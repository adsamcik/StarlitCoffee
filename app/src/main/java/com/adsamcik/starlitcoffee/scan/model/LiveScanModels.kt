package com.adsamcik.starlitcoffee.scan.model

import com.adsamcik.starlitcoffee.util.BagCaptureSide
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import com.adsamcik.starlitcoffee.util.BagFieldEvidence
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.OcrFieldExtractor

/**
 * What the camera analyzer submits to the accumulator for each processed frame.
 */
data class FrameResult(
    val ocrResult: OcrFieldExtractor.OcrExtractionResult,
    val quality: BagCaptureQuality,
    val frameIndex: Int,
    val timestampMs: Long,
    val side: Int = 0,
    val isGoldenFrame: Boolean = false,
)

/**
 * State of a single candidate value for a field, accumulated across frames.
 *
 * The [normalizedValue] is the canonical string after case-folding and known-value matching.
 * [rawVariants] preserves the original OCR strings that mapped to this candidate.
 */
data class CandidateState(
    val normalizedValue: String,
    val rawVariants: List<String>,
    val posteriorProbability: Float,
    val qualityWeightedVotes: Float,
    val observationCount: Int,
    val lastSeenFrameIndex: Int,
    val sourceTypes: Set<BagFieldSourceType>,
    val bestConfidenceHint: BagFieldConfidence,
    val sides: Set<Int>,
    /** True when this candidate matched a value from the user's bag history and received a prior boost. */
    val matchedKnownValue: Boolean = false,
    /** Highest quality-weighted votes ever reached — used as a decay floor (Option B). */
    val peakVotes: Float = 0f,
    /** Last consensus cycle this candidate received a reinforcing vote (Option C). */
    val lastReinforcedCycle: Int = 0,
)

/**
 * Accumulation state for a single field (e.g., "origin", "roaster").
 *
 * The [candidates] map is keyed by normalized value. The [status] state machine governs
 * how the field is presented and whether the accumulator continues processing it.
 */
data class FieldAccumulation(
    val fieldName: String,
    val candidates: Map<String, CandidateState>,
    val status: FieldStatus,
    val lockScore: Float = 0f,
    val consecutiveLockCycles: Int = 0,
    val consecutiveConflictCycles: Int = 0,
    val framesSinceLastChange: Int = 0,
    val resolvedValue: String? = null,
    val resolvedEvidence: BagFieldEvidence? = null,
) {
    /** The leading candidate by posterior probability, if any. */
    val topCandidate: CandidateState?
        get() = candidates.values.maxByOrNull { it.posteriorProbability }

    /** The second-place candidate, for conflict detection. */
    val runnerUp: CandidateState?
        get() = candidates.values
            .sortedByDescending { it.posteriorProbability }
            .getOrNull(1)

    /** Whether this field has a resolved (locked or user-locked) value. */
    val isResolved: Boolean
        get() = status == FieldStatus.LOCKED ||
                status == FieldStatus.USER_LOCKED
}

/**
 * Guidance message for micro-mission coaching during live scanning.
 */
data class ScanGuidance(
    val message: String,
    val targetField: String? = null,
    val type: GuidanceType,
)

/**
 * Overall state emitted by the [FrameEvidenceAccumulator] after each consensus cycle.
 *
 * This is the single source of truth for the UI — the ViewModel maps it to visual state.
 */
data class AccumulatedEvidence(
    val fields: Map<String, FieldAccumulation>,
    val currentSide: Int = 0,
    val sideCount: Int = 1,
    val totalFramesProcessed: Int = 0,
    val totalFramesRejected: Int = 0,
    val scanStartTimeMs: Long = 0L,
    val lastConsensusTimeMs: Long = 0L,
    val guidance: ScanGuidance? = null,
    val scanProgress: Float = 0f,
    val isComplete: Boolean = false,
    val detectedBarcode: String? = null,
    val detectedQrUrl: String? = null,
) {
    companion object {
        val EMPTY = AccumulatedEvidence(fields = emptyMap())
    }

    /** Count of fields that have reached LOCKED or USER_LOCKED status. */
    val resolvedFieldCount: Int
        get() = fields.values.count { it.isResolved }

    /** Count of fields that are in CONFLICT status (need user attention). */
    val conflictFieldCount: Int
        get() = fields.values.count { it.status == FieldStatus.CONFLICT }
}
