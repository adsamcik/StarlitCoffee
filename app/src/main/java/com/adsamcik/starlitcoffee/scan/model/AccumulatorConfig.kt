package com.adsamcik.starlitcoffee.scan.model

/**
 * Configuration for the [FrameEvidenceAccumulator].
 *
 * All thresholds are tunable — the debug overlay exposes these at runtime.
 */
data class AccumulatorConfig(
    /** Ring buffer capacity — how many frame results to keep. */
    val ringBufferSize: Int = 20,

    /** How often (ms) to run the consensus algorithm. */
    val consensusIntervalMs: Long = 500L,

    /** Bayesian posterior probability threshold to consider a field resolved. */
    val resolveThreshold: Float = 0.85f,

    /** Consecutive consensus cycles a field must hold above threshold before locking. */
    val lockCycles: Int = 3,

    /** Consecutive cycles with 2+ strong candidates before declaring CONFLICT. */
    val conflictCycles: Int = 4,

    /** Minimum share of votes for a candidate to be considered "strong" in conflict detection. */
    val conflictCandidateMinShare: Float = 0.30f,

    /** Number of contradicting high-quality frames needed to start decaying a lock. */
    val counterEvidenceFrames: Int = 3,

    /** Rate at which lock score decays per counter-evidence frame (0–1). */
    val lockDecayRate: Float = 0.15f,

    /** Maximum candidates tracked per field (O(N²) safety cap for Levenshtein). */
    val maxCandidatesPerField: Int = 10,

    /** Levenshtein edit distance threshold for clustering short strings (≤8 chars). */
    val levenshteinThresholdShort: Int = 2,

    /** Levenshtein edit distance threshold for clustering long strings (>8 chars). */
    val levenshteinThresholdLong: Int = 3,

    /** Weight multiplier for golden (super-vote) frames. */
    val goldenFrameWeight: Float = 3f,

    /** Perceptual hash Hamming distance threshold for side-flip detection. */
    val sideFlipHammingThreshold: Int = 12,

    /** Time window (ms) after hash spike for text confirmation of side flip. */
    val sideFlipTextConfirmMs: Long = 2000L,

    /** Fields considered "core" for draft bag trigger and completion checks. */
    val coreFields: Set<String> = setOf("name", "roaster", "origin"),

    /** All known extractable fields for progress calculation. */
    val allFields: Set<String> = setOf(
        "name", "roaster", "origin", "region", "farm", "variety",
        "processType", "altitude", "tastingNotes", "roastLevel",
        "roastDate", "expiryDate", "weight", "isDecaf",
    ),

    /** Minimum core fields that must reach PROVISIONAL before showing draft bag. */
    val draftTriggerCoreFields: Int = 2,

    /** Fraction of total fields at HIGH confidence to trigger auto-save prompt. */
    val autoSaveThreshold: Float = 0.80f,

    // --- Adaptive throttle ---

    /** OCR throttle (ms) when < 50% of fields are confident. */
    val throttleFastMs: Long = 400L,

    /** OCR throttle (ms) when > 80% of fields are confident. */
    val throttleSlowMs: Long = 1200L,

    /** OCR throttle (ms) when all fields are locked (maintenance mode). */
    val throttleMaintenanceMs: Long = 2000L,

    /** Minimum time (ms) between throttle changes (hysteresis). */
    val throttleHysteresisMs: Long = 2000L,

    // --- Quality gating ---

    /** Minimum blur score for a frame to be admitted. */
    val minBlurScore: Float = 12f,

    /** Maximum glare percentage for a frame to be admitted. */
    val maxGlarePercent: Float = 0.18f,

    /** Time (ms) with insufficient frames before relaxing quality thresholds. */
    val qualityRelaxationTimeMs: Long = 5000L,

    /** Factor by which to relax quality thresholds when triggered. */
    val qualityRelaxationFactor: Float = 0.70f,

    // --- IMU gating ---

    /** Maximum linear acceleration magnitude (m/s²) for device "stillness". */
    val imuStillnessThreshold: Float = 1.5f,

    // --- History prior boost ---

    /** Multiply a candidate's raw posterior by this factor when it matches a known value from user history. */
    val knownValuePriorBoost: Float = 1.5f,

    /** Only apply prior boost if the known value appeared at least this many times in user history. */
    val knownValueMinObservations: Int = 2,

    // --- Source weights for Bayesian likelihood ---

    /** Weight when evidence comes from OCR text recognition. */
    val sourceWeightOcr: Float = 4f,

    /** Weight when evidence comes from barcode product lookup (OpenFoodFacts). */
    val sourceWeightBarcodeLookup: Float = 8f,

    /** Weight when evidence comes from local barcode match (existing bag in DB). */
    val sourceWeightLocalMatch: Float = 9f,

    /** Weight when evidence comes from QR link metadata extraction. */
    val sourceWeightQrLink: Float = 6f,

    /** Weight when evidence comes from LLM-based field extraction. */
    val sourceWeightLlm: Float = 7f,

    // --- Vote floor (Option B) ---

    /** Votes can't drop below this fraction of peak — preserves sharp-frame evidence. */
    val voteFloorFraction: Float = 0.5f,

    // --- Temporal decay (Option C) ---

    /** Per-cycle decay rate for unreinforced candidates (3% per consensus cycle). */
    val staleDecayRate: Float = 0.03f,

    /** Grace cycles before decay starts (~3 seconds at 500ms/cycle). */
    val staleGraceCycles: Int = 6,

    // --- Blank-frame penalty (Option D) ---

    /** Small vote penalty per blank frame (camera moved away from text). */
    val blankFramePenaltyPerCycle: Float = 0.02f,

    // --- LLM escalation ---

    /** Consensus cycles without resolution before escalating to LLM (~5s at 500ms/cycle). */
    val llmEscalationCycles: Int = 10,

    /** Fields eligible for LLM escalation (high-value fields that OCR struggles with). */
    val llmEscalationFields: Set<String> = setOf("name", "roaster", "tastingNotes"),
) {
    companion object {
        val DEFAULT = AccumulatorConfig()
    }
}
