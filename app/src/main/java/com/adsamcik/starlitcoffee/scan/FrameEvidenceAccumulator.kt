package com.adsamcik.starlitcoffee.scan

import com.adsamcik.starlitcoffee.scan.model.AccumulatedEvidence
import com.adsamcik.starlitcoffee.scan.model.AccumulatorConfig
import com.adsamcik.starlitcoffee.scan.model.FieldAccumulation
import com.adsamcik.starlitcoffee.scan.model.FieldStatus
import com.adsamcik.starlitcoffee.scan.model.FrameResult
import com.adsamcik.starlitcoffee.scan.model.GuidanceType
import com.adsamcik.starlitcoffee.scan.model.LlmEscalationRequest
import com.adsamcik.starlitcoffee.scan.model.ScanGuidance
import com.adsamcik.starlitcoffee.util.BagCaptureQuality
import com.adsamcik.starlitcoffee.util.BagFieldSourceType
import com.adsamcik.starlitcoffee.util.KnownFieldValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware accumulator that processes live OCR frames and builds
 * consensus field evidence over time.
 *
 * Architecture:
 * - Owns its own [CoroutineScope] (tied to camera session, not ViewModel)
 * - Frame submission is fire-and-forget via [Channel]
 * - Golden frames use a separate priority [Channel] (never dropped)
 * - Emits [AccumulatedEvidence] via [StateFlow] every [AccumulatorConfig.consensusIntervalMs]
 * - All heavy computation runs on [Dispatchers.Default]
 *
 * Usage:
 * ```
 * val accumulator = FrameEvidenceAccumulator(knownValues = knownFieldValues)
 * accumulator.start()
 *
 * // From camera analyzer:
 * accumulator.submitFrame(frameResult)
 *
 * // From ViewModel:
 * accumulator.evidence.collect { state -> updateUi(state) }
 *
 * // On camera session end:
 * accumulator.stop()
 * ```
 */
class FrameEvidenceAccumulator(
    private val config: AccumulatorConfig = AccumulatorConfig.DEFAULT,
    knownValues: KnownFieldValues = KnownFieldValues.EMPTY,
    private val consensusEngine: ConsensusEngine = ConsensusEngine(config),
) {
    @Volatile
    private var knownValues: KnownFieldValues = knownValues

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()

    // Ring buffer of recent frame results
    private val ringBuffer = ArrayDeque<FrameResult>(config.ringBufferSize)

    // Channels for frame submission
    private val frameChannel = Channel<FrameResult>(Channel.CONFLATED)
    private val goldenFrameChannel = Channel<FrameResult>(Channel.UNLIMITED)

    // Enrichment channel (barcode/QR/API results)
    private val enrichmentChannel = Channel<EnrichmentPayload>(Channel.UNLIMITED)

    // Accumulated field state
    private var fieldAccumulations: Map<String, FieldAccumulation> = emptyMap()

    // Side detection state
    private var currentSide: Int = 0
    private var sideCount: Int = 1
    private var lastPerceptualHash: LongArray? = null
    private var sideFlipPendingSince: Long = 0L

    // Counters
    private var totalFramesProcessed: Int = 0
    private var totalFramesRejected: Int = 0
    private var frameIndex: Int = 0
    private var scanStartTimeMs: Long = 0L
    private var isQualityRelaxed: Boolean = false
    private var lastAdmittedFrameTimeMs: Long = 0L
    private var lastFrameQuality: BagCaptureQuality? = null
    private var consensusCycleCount: Int = 0
    private var blankFrameCount: Int = 0

    // Enrichment dedup
    private val seenBarcodes = mutableSetOf<String>()
    private val seenQrUrls = mutableSetOf<String>()

    // Detected barcodes/QR (for UI)
    private var detectedBarcode: String? = null
    private var detectedQrUrl: String? = null

    // Jobs
    private var consensusJob: Job? = null
    private var frameProcessingJob: Job? = null
    private var goldenProcessingJob: Job? = null
    private var enrichmentProcessingJob: Job? = null

    // Output
    private val _evidence = MutableStateFlow(AccumulatedEvidence.EMPTY)
    val evidence: StateFlow<AccumulatedEvidence> = _evidence.asStateFlow()

    // Frame rejection feedback
    private val _lastRejection = MutableStateFlow<FrameRejection?>(null)
    val lastRejection: StateFlow<FrameRejection?> = _lastRejection.asStateFlow()

    // LLM escalation
    private val _llmEscalation = MutableSharedFlow<LlmEscalationRequest>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val llmEscalation: SharedFlow<LlmEscalationRequest> = _llmEscalation.asSharedFlow()
    private val escalationStaleCycles = mutableMapOf<String, Int>()
    private val alreadyEscalatedFields = mutableSetOf<String>()

    @Volatile
    private var lastGoldenFrameBytes: ByteArray? = null

    /**
     * Start the accumulator's processing loops.
     * Call once when the camera session begins.
     */
    fun start() {
        android.util.Log.d("Accumulator", "start() called — launching processing loops")
        scanStartTimeMs = System.currentTimeMillis()
        consensusCycleCount = 0
        blankFrameCount = 0
        startFrameProcessing()
        startGoldenFrameProcessing()
        startEnrichmentProcessing()
        startConsensusLoop()
    }

    /**
     * Stop all processing and cancel the coroutine scope.
     * Call when the camera session ends.
     */
    fun stop() {
        consensusJob?.cancel()
        frameProcessingJob?.cancel()
        goldenProcessingJob?.cancel()
        enrichmentProcessingJob?.cancel()
        consensusCycleCount = 0
        blankFrameCount = 0
        scope.cancel()
    }

    /**
     * Submit a regular frame for processing. Fire-and-forget.
     * If the channel is full, the oldest unprocessed frame is dropped (CONFLATED).
     */
    fun submitFrame(frame: FrameResult) {
        frameChannel.trySend(frame)
    }

    /**
     * Submit a golden (high-quality) frame. Never dropped.
     */
    fun submitGoldenFrame(frame: FrameResult) {
        goldenFrameChannel.trySend(frame.copy(isGoldenFrame = true))
    }

    /**
     * Submit enrichment evidence from barcode/QR/API lookup.
     * Deduplicates by barcode or URL.
     */
    fun submitEnrichment(
        fieldValues: Map<String, String>,
        sourceType: BagFieldSourceType,
        barcode: String? = null,
        qrUrl: String? = null,
    ) {
        // Dedup
        barcode?.let {
            if (!seenBarcodes.add(it)) return
            detectedBarcode = it
        }
        qrUrl?.let {
            if (!seenQrUrls.add(it)) return
            detectedQrUrl = it
        }

        enrichmentChannel.trySend(
            EnrichmentPayload(
                fieldValues = fieldValues,
                sourceType = sourceType,
            ),
        )
    }

    /**
     * Signal that the camera analyzer received a blank frame (no text detected).
     * Accumulates blank-frame count; penalty is applied in the next consensus cycle (Option D).
     */
    fun submitBlankFrame() {
        synchronized(stateLock) {
            blankFrameCount++
        }
    }

    /**
    fun boostKnownValues(updated: KnownFieldValues) {
        synchronized(stateLock) {
            knownValues = updated
        }
    }

    /**
     * Provide raw JPEG bytes of the best golden frame for LLM escalation.
     * Called by the camera analyzer when a golden frame is captured.
     */
    fun setGoldenFrameBytes(bytes: ByteArray) {
        lastGoldenFrameBytes = bytes
    }

    /**
     * User resolves a conflict by choosing a specific value for a field.
     * The field becomes USER_LOCKED and stops accumulating.
     */
    fun userResolveField(fieldName: String, chosenValue: String) {
        synchronized(stateLock) {
            val field = fieldAccumulations[fieldName] ?: return
            val candidate = field.candidates[chosenValue]
                ?: field.candidates.values.firstOrNull {
                    it.rawVariants.any { raw -> raw.equals(chosenValue, ignoreCase = true) }
                } ?: return

            fieldAccumulations = fieldAccumulations + (fieldName to field.copy(
                status = FieldStatus.USER_LOCKED,
                resolvedValue = candidate.normalizedValue,
                resolvedEvidence = field.resolvedEvidence?.copy(
                    value = consensusEngine.selectMedoid(candidate.rawVariants),
                ),
            ))
            emitEvidenceLocked()
        }
    }

    /**
     * User resets a locked field back to SCANNING.
     */
    fun userResetField(fieldName: String) {
        synchronized(stateLock) {
            val field = fieldAccumulations[fieldName] ?: return
            fieldAccumulations = fieldAccumulations + (fieldName to field.copy(
                status = FieldStatus.SCANNING,
                lockScore = 0f,
                consecutiveLockCycles = 0,
                resolvedValue = null,
                resolvedEvidence = null,
            ))
            emitEvidenceLocked()
        }
    }

    /**
     * Check if a frame should be admitted based on quality thresholds.
     */
    fun shouldAdmitFrame(frame: FrameResult): Boolean {
        return synchronized(stateLock) {
            consensusEngine.framePassesQualityGate(frame, isRelaxed = isQualityRelaxed)
        }
    }

    /**
     * Compute the current adaptive throttle interval based on confidence state.
     */
    fun currentThrottleMs(): Long {
        return synchronized(stateLock) {
            if (fieldAccumulations.isEmpty()) return@synchronized config.throttleFastMs

            val resolvedCount = fieldAccumulations.values.count { it.isResolved }
            val resolvedFractionOfAllFields = resolvedCount.toFloat() / config.allFields.size.coerceAtLeast(1)
            val allObservedLocked = fieldAccumulations.values.all {
                it.status == FieldStatus.LOCKED || it.status == FieldStatus.USER_LOCKED
            }

            when {
                allObservedLocked && resolvedFractionOfAllFields >= config.autoSaveThreshold ->
                    config.throttleMaintenanceMs

                resolvedFractionOfAllFields > 0.8f -> config.throttleSlowMs
                else -> config.throttleFastMs
            }
        }
    }

    // --- Internal processing loops ---

    private fun startFrameProcessing() {
        frameProcessingJob = scope.launch {
            for (frame in frameChannel) {
                processFrame(frame)
            }
        }
    }

    private fun startGoldenFrameProcessing() {
        goldenProcessingJob = scope.launch {
            for (frame in goldenFrameChannel) {
                processFrame(frame)
            }
        }
    }

    private fun startEnrichmentProcessing() {
        enrichmentProcessingJob = scope.launch {
            for (payload in enrichmentChannel) {
                synchronized(stateLock) {
                    fieldAccumulations = consensusEngine.integrateEnrichment(
                        currentFields = fieldAccumulations,
                        fieldValues = payload.fieldValues,
                        sourceType = payload.sourceType,
                        frameIndex = frameIndex,
                        side = currentSide,
                    )
                    emitEvidenceLocked()
                }
            }
        }
    }

    private fun startConsensusLoop() {
        consensusJob = scope.launch {
            while (true) {
                delay(config.consensusIntervalMs)
                runConsensus()
            }
        }
    }

    private fun processFrame(frame: FrameResult) {
        synchronized(stateLock) {
            frameIndex++
            lastFrameQuality = frame.quality

            val passesQuality = consensusEngine.framePassesQualityGate(frame, isRelaxed = isQualityRelaxed)
            if (!passesQuality && !frame.isGoldenFrame) {
                totalFramesRejected++
                val blurThreshold = if (isQualityRelaxed) config.minBlurScore * config.qualityRelaxationFactor else config.minBlurScore
                val glareThreshold = if (isQualityRelaxed) config.maxGlarePercent / config.qualityRelaxationFactor else config.maxGlarePercent
                val reason = buildString {
                    if (frame.quality.blurScore < blurThreshold) {
                        append("Too blurry (${frame.quality.blurScore.toInt()})")
                    }
                    if (frame.quality.glarePercent > glareThreshold) {
                        if (isNotEmpty()) append(", ")
                        append("Too much glare (${(frame.quality.glarePercent * 100).toInt()}%)")
                    }
                }
                _lastRejection.value = FrameRejection(
                    reason = reason.ifEmpty { "Quality too low" },
                    blurScore = frame.quality.blurScore,
                    glarePercent = frame.quality.glarePercent,
                    frameIndex = frameIndex,
                )
                android.util.Log.d("Accumulator", "Frame #$frameIndex REJECTED: " +
                    "blur=${frame.quality.blurScore}, glare=${frame.quality.glarePercent}, " +
                    "relaxed=$isQualityRelaxed, total rejected=$totalFramesRejected")
                checkQualityRelaxationLocked()
                return
            }

            totalFramesProcessed++
            lastAdmittedFrameTimeMs = System.currentTimeMillis()
            isQualityRelaxed = false

            synchronized(ringBuffer) {
                if (ringBuffer.size >= config.ringBufferSize) {
                    ringBuffer.removeFirst()
                }
                ringBuffer.addLast(frame)
            }

            checkSideFlipLocked(frame)
            val ocrResult = frame.ocrResult
            val fieldNames = listOfNotNull(
                ocrResult.name?.let { "name" },
                ocrResult.roaster?.let { "roaster" },
                ocrResult.origin?.let { "origin" },
                ocrResult.region?.let { "region" },
            )
            android.util.Log.d("Accumulator", "Frame #$frameIndex ADMITTED: " +
                "blur=${frame.quality.blurScore}, golden=${frame.isGoldenFrame}, " +
                "fields=[${fieldNames.joinToString(",")}], total processed=$totalFramesProcessed")

            fieldAccumulations = consensusEngine.integrateFrame(
                currentFields = fieldAccumulations,
                frame = frame.copy(side = currentSide),
            )

            android.util.Log.d("Accumulator", "After integrate: ${fieldAccumulations.size} field accumulations, " +
                "fields=${fieldAccumulations.keys.joinToString(",")}")
        }
    }

    private fun runConsensus() {
        synchronized(stateLock) {
            if (fieldAccumulations.isEmpty()) {
                android.util.Log.d("Accumulator", "runConsensus: no fields yet — skipping")
                return
            }

            consensusCycleCount++

            android.util.Log.d("Accumulator", "runConsensus: ${fieldAccumulations.size} fields, " +
                "cycle=$consensusCycleCount, " +
                "statuses=${fieldAccumulations.map { "${it.key}=${it.value.status}" }}")

            // Option C: stamp lastReinforcedCycle on candidates that received votes this cycle
            fieldAccumulations = fieldAccumulations.mapValues { (_, field) ->
                field.copy(candidates = field.candidates.mapValues { (_, candidate) ->
                    if (candidate.lastSeenFrameIndex >= frameIndex - 1) {
                        candidate.copy(lastReinforcedCycle = consensusCycleCount)
                    } else {
                        candidate
                    }
                })
            }

            fieldAccumulations = consensusEngine.runStateMachine(
                fields = fieldAccumulations,
                knownValues = knownValues,
                currentCycle = consensusCycleCount,
            )

            // Option D: apply blank-frame penalty
            val blanks = blankFrameCount
            if (blanks > 0) {
                blankFrameCount = 0
                val penalty = config.blankFramePenaltyPerCycle * blanks
                fieldAccumulations = fieldAccumulations.mapValues { (_, field) ->
                    if (field.status == FieldStatus.USER_LOCKED ||
                        field.status == FieldStatus.LOCKED) {
                        field
                    } else {
                        val penalized = field.candidates.mapValues { (_, candidate) ->
                            val floor = candidate.peakVotes * config.voteFloorFraction
                            candidate.copy(
                                qualityWeightedVotes = (candidate.qualityWeightedVotes - penalty)
                                    .coerceAtLeast(floor),
                            )
                        }
                        field.copy(candidates = penalized)
                    }
                }
            }

            checkLlmEscalationLocked()
            emitEvidenceLocked()
            android.util.Log.d("Accumulator", "runConsensus: emitted evidence, " +
                "processed=$totalFramesProcessed, rejected=$totalFramesRejected")
        }
    }

    private fun emitEvidence() {
        synchronized(stateLock) {
            emitEvidenceLocked()
        }
    }

    private fun emitEvidenceLocked() {
        val fields = fieldAccumulations
        val totalFields = config.allFields.size.toFloat()
        val resolvedCount = fields.values.count { it.isResolved }
        val progress = if (totalFields > 0) resolvedCount / totalFields else 0f

        val coreResolved = config.coreFields.count { fieldName ->
            fields[fieldName]?.isResolved == true ||
                    fields[fieldName]?.status == FieldStatus.PROVISIONAL
        }

        val isComplete = progress >= config.autoSaveThreshold

        _evidence.value = AccumulatedEvidence(
            fields = fields,
            currentSide = currentSide,
            sideCount = sideCount,
            totalFramesProcessed = totalFramesProcessed,
            totalFramesRejected = totalFramesRejected,
            scanStartTimeMs = scanStartTimeMs,
            lastConsensusTimeMs = System.currentTimeMillis(),
            guidance = buildGuidance(fields),
            scanProgress = progress,
            isComplete = isComplete,
            detectedBarcode = detectedBarcode,
            detectedQrUrl = detectedQrUrl,
        )
    }

    private fun buildGuidance(fields: Map<String, FieldAccumulation>): ScanGuidance? {
        // Priority: scan complete > quality issue > missing core field > almost done > flip > missing non-core
        val resolvedCount = fields.values.count { it.isResolved }
        val totalTracked = fields.size.coerceAtLeast(1)

        if (resolvedCount.toFloat() / totalTracked >= config.autoSaveThreshold) {
            return ScanGuidance(
                message = "Bag looks complete!",
                type = GuidanceType.SCAN_COMPLETE,
            )
        }

        // Quality issues take priority — they prevent effective scanning
        lastFrameQuality?.let { quality ->
            if (!quality.sharpEnough) {
                return ScanGuidance(
                    message = "Hold phone steadier for clearer text",
                    type = GuidanceType.QUALITY_ISSUE,
                )
            }
            if (!quality.glareOkay) {
                return ScanGuidance(
                    message = "Tilt slightly to reduce glare",
                    type = GuidanceType.QUALITY_ISSUE,
                )
            }
        }

        // Find highest-priority missing core field
        val missingCoreField = config.coreFields.firstOrNull { fieldName ->
            fields[fieldName]?.isResolved != true &&
                    fields[fieldName]?.status != FieldStatus.PROVISIONAL
        }
        if (missingCoreField != null) {
            val message = when (missingCoreField) {
                "name" -> "Look for the coffee name"
                "roaster" -> "Find the roaster name"
                "origin" -> "Look for the origin country"
                else -> "Find $missingCoreField"
            }
            return ScanGuidance(
                message = message,
                targetField = missingCoreField,
                type = GuidanceType.MISSING_FIELD,
            )
        }

        // Almost done encouragement
        val allFieldCount = config.allFields.size.coerceAtLeast(1).toFloat()
        val scanProgress = resolvedCount / allFieldCount
        if (scanProgress > 0.8f) {
            return ScanGuidance(
                message = "Almost done! Checking remaining fields…",
                type = GuidanceType.ALMOST_DONE,
            )
        }

        // Suggest flip if we haven't seen the other side
        if (sideCount < 2 && totalFramesProcessed > 15) {
            val hasBackFields = fields.values.any { field ->
                field.candidates.values.any { it.sides.contains(1) }
            }
            if (!hasBackFields) {
                return ScanGuidance(
                    message = "Flip bag to scan the back label",
                    type = GuidanceType.FLIP_SUGGESTION,
                )
            }
        }

        // Find non-core missing field
        val missingField = config.allFields.firstOrNull { fieldName ->
            !config.coreFields.contains(fieldName) &&
                    fields[fieldName]?.isResolved != true &&
                    fields[fieldName]?.status != FieldStatus.PROVISIONAL
        }
        if (missingField != null) {
            val message = when (missingField) {
                "roastDate" -> "Tilt for roast date"
                "variety" -> "Look for coffee variety"
                "processType" -> "Find the process type"
                "altitude" -> "Check for altitude info"
                "tastingNotes" -> "Look for tasting notes"
                "weight" -> "Find the bag weight"
                else -> "Look for $missingField"
            }
            return ScanGuidance(
                message = message,
                targetField = missingField,
                type = GuidanceType.MISSING_FIELD,
            )
        }

        return null
    }

    // --- LLM escalation ---

    /**
     * Check if any high-value fields have been stuck in SCANNING/CONFLICT
     * for too many consensus cycles and emit an escalation request.
     * Must be called under [stateLock].
     */
    private fun checkLlmEscalationLocked() {
        if (config.llmEscalationFields.isEmpty()) return

        val fieldsNeedingEscalation = mutableSetOf<String>()

        for (fieldName in config.llmEscalationFields) {
            if (fieldName in alreadyEscalatedFields) continue

            val field = fieldAccumulations[fieldName]
            val isStuck = field == null ||
                field.status == FieldStatus.SCANNING ||
                field.status == FieldStatus.CONFLICT

            if (isStuck) {
                val count = (escalationStaleCycles[fieldName] ?: 0) + 1
                escalationStaleCycles[fieldName] = count
                if (count >= config.llmEscalationCycles) {
                    fieldsNeedingEscalation.add(fieldName)
                }
            } else {
                // Field resolved — reset its stale counter
                escalationStaleCycles.remove(fieldName)
            }
        }

        if (fieldsNeedingEscalation.isNotEmpty()) {
            alreadyEscalatedFields.addAll(fieldsNeedingEscalation)

            val existingFields = fieldAccumulations
                .filter { it.value.isResolved }
                .mapValues { entry ->
                    entry.value.resolvedValue
                        ?: entry.value.topCandidate?.normalizedValue
                        ?: ""
                }
                .filter { it.value.isNotBlank() }

            val request = LlmEscalationRequest(
                goldenFrameBytes = lastGoldenFrameBytes,
                existingFields = existingFields,
                fieldsNeeded = fieldsNeedingEscalation,
            )
            _llmEscalation.tryEmit(request)
            android.util.Log.d(
                "Accumulator",
                "LLM escalation emitted for fields: $fieldsNeedingEscalation",
            )
        }
    }

    // --- Side detection ---

    private fun checkSideFlip(frame: FrameResult) {
        synchronized(stateLock) {
            checkSideFlipLocked(frame)
        }
    }

    private fun checkSideFlipLocked(frame: FrameResult) {
        // Simple perceptual hash: average luma of 8×8 grid
        // We approximate from the quality metrics — in the real implementation,
        // this would compute from the actual frame pixels
        val now = System.currentTimeMillis()

        if (sideFlipPendingSince > 0) {
            // Waiting for text confirmation after hash spike
            if (now - sideFlipPendingSince > config.sideFlipTextConfirmMs) {
                sideFlipPendingSince = 0L // timeout, cancel pending flip
            } else if (frame.quality.textBlockCount >= 2) {
                // Text confirmed on new side
                confirmSideFlip()
            }
        }
    }

    /**
     * Notify the accumulator of a perceptual hash spike detected by the camera analyzer.
     * The accumulator will wait for text confirmation before committing the flip.
     */
    fun notifyPotentialSideFlip() {
        synchronized(stateLock) {
            if (sideCount >= 2) return
            sideFlipPendingSince = System.currentTimeMillis()
        }
    }

    private fun confirmSideFlip() {
        sideFlipPendingSince = 0L
        currentSide = 1
        sideCount = 2
    }

    // --- Quality relaxation ---

    private fun checkQualityRelaxation() {
        synchronized(stateLock) {
            checkQualityRelaxationLocked()
        }
    }

    private fun checkQualityRelaxationLocked() {
        if (isQualityRelaxed) return
        val now = System.currentTimeMillis()
        // Use lastAdmittedFrameTimeMs if any frame was admitted, otherwise use scanStartTimeMs
        val referenceTime = if (lastAdmittedFrameTimeMs > 0) lastAdmittedFrameTimeMs else scanStartTimeMs
        val timeSinceReference = now - referenceTime
        if (referenceTime > 0 && timeSinceReference > config.qualityRelaxationTimeMs) {
            isQualityRelaxed = true
            _lastRejection.value = null
            android.util.Log.d("Accumulator", "Quality RELAXED: " +
                "timeSinceRef=${timeSinceReference}ms, " +
                "admitted=$totalFramesProcessed, rejected=$totalFramesRejected")
        }
    }

    // --- Internal types ---

    private data class EnrichmentPayload(
        val fieldValues: Map<String, String>,
        val sourceType: BagFieldSourceType,
    )

    data class FrameRejection(
        val reason: String,
        val blurScore: Float,
        val glarePercent: Float,
        val frameIndex: Int,
        val timestampMs: Long = System.currentTimeMillis(),
    )
}
