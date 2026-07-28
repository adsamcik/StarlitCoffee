package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.ActiveClockAnchor
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockReconciliation
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageActuals
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertPolicy
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageEquipmentRequirement
import com.adsamcik.starlitcoffee.domain.brewing.session.StageEquipmentStateId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMarkerId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageObservationId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage

enum class SessionStorageDocument {
    COMPILED_PLAN,
    RUNTIME,
    EXECUTION_CONTEXT,
}

data class RestoredSessionStorage(
    val state: SessionRuntimeState,
    val executionContext: SessionExecutionContextSnapshotV1,
)

/**
 * Restoration never substitutes defaults for unavailable data. A caller can
 * surface the document and raw payload for repair while keeping the durable
 * record intact.
 */
sealed interface SessionStorageRestoreResult {
    data class Restored(val value: RestoredSessionStorage) : SessionStorageRestoreResult

    data class UnsupportedDocument(
        val document: SessionStorageDocument,
        val schemaVersion: Int,
        val rawJson: String,
    ) : SessionStorageRestoreResult

    data class InvalidDocument(
        val document: SessionStorageDocument,
        val reason: String,
        val rawJson: String? = null,
    ) : SessionStorageRestoreResult
}

private sealed interface SessionStorageDocumentResult<out T> {
    data class Value<T>(val value: T) : SessionStorageDocumentResult<T>
    data class Failure(val result: SessionStorageRestoreResult) : SessionStorageDocumentResult<Nothing>
}

/**
 * Converts the Android-free session engine to explicit storage documents. The
 * mapper is the only place where discriminator strings are interpreted back
 * into sealed domain types.
 */
object SessionStorageMapper {

    fun snapshot(
        state: SessionRuntimeState,
        executionContext: SessionExecutionContextSnapshotV1,
    ): SessionStorageSnapshots = SessionStorageSnapshots(
        compiledPlan = state.stagePlan.toSnapshot(),
        runtime = state.toSnapshot(),
        executionContext = executionContext,
    )

    fun encode(
        state: SessionRuntimeState,
        executionContext: SessionExecutionContextSnapshotV1,
    ): EncodedSessionStorageDocuments = snapshot(state, executionContext).let { snapshots ->
        EncodedSessionStorageDocuments(
            compiledPlanSchemaVersion = snapshots.compiledPlan.schemaVersion,
            compiledPlanJson = SessionStorageSnapshotCodec.encodeCompiledPlan(snapshots.compiledPlan),
            runtimeSchemaVersion = snapshots.runtime.schemaVersion,
            runtimeJson = SessionStorageSnapshotCodec.encodeRuntime(snapshots.runtime),
            executionContextSchemaVersion = snapshots.executionContext.schemaVersion,
            executionContextJson = SessionStorageSnapshotCodec.encodeExecutionContext(
                snapshots.executionContext,
            ),
        )
    }

    fun restore(snapshots: SessionStorageSnapshots): SessionStorageRestoreResult {
        val plan = when (val result = mapDocument(SessionStorageDocument.COMPILED_PLAN) {
            snapshots.compiledPlan.toDomain()
        }) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val runtime = when (val result = mapDocument(SessionStorageDocument.RUNTIME) {
            snapshots.runtime.toDomain(plan)
        }) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val context = when (val result = mapDocument(SessionStorageDocument.EXECUTION_CONTEXT) {
            snapshots.executionContext.validated()
        }) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        return SessionStorageRestoreResult.Restored(RestoredSessionStorage(runtime, context))
    }

    fun decodeAndRestore(
        compiledPlanJson: String,
        runtimeJson: String,
        executionContextJson: String,
    ): SessionStorageRestoreResult {
        val planSnapshot = when (
            val result = SessionStorageSnapshotCodec.decodeCompiledPlan(compiledPlanJson)
                .decodedOrFailure(SessionStorageDocument.COMPILED_PLAN)
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val runtimeSnapshot = when (
            val result = SessionStorageSnapshotCodec.decodeRuntime(runtimeJson)
                .decodedOrFailure(SessionStorageDocument.RUNTIME)
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val contextSnapshot = when (
            val result = SessionStorageSnapshotCodec.decodeExecutionContext(executionContextJson)
                .decodedOrFailure(SessionStorageDocument.EXECUTION_CONTEXT)
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }

        val plan = when (
            val result = mapDocument(SessionStorageDocument.COMPILED_PLAN, compiledPlanJson) {
            planSnapshot.toDomain()
        }
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val runtime = when (
            val result = mapDocument(SessionStorageDocument.RUNTIME, runtimeJson) {
            runtimeSnapshot.toDomain(plan)
        }
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        val context = when (
            val result = mapDocument(SessionStorageDocument.EXECUTION_CONTEXT, executionContextJson) {
            contextSnapshot.validated()
        }
        ) {
            is SessionStorageDocumentResult.Value -> result.value
            is SessionStorageDocumentResult.Failure -> return result.result
        }
        return SessionStorageRestoreResult.Restored(RestoredSessionStorage(runtime, context))
    }

    private fun <T> mapDocument(
        document: SessionStorageDocument,
        rawJson: String? = null,
        block: () -> T,
    ): SessionStorageDocumentResult<T> = try {
        SessionStorageDocumentResult.Value(block())
    } catch (error: IllegalArgumentException) {
        SessionStorageDocumentResult.Failure(
            SessionStorageRestoreResult.InvalidDocument(
                document = document,
                reason = error.message ?: "Invalid session storage document",
                rawJson = rawJson,
            ),
        )
    } catch (error: IllegalStateException) {
        SessionStorageDocumentResult.Failure(
            SessionStorageRestoreResult.InvalidDocument(
                document = document,
                reason = error.message ?: "Invalid session storage document",
                rawJson = rawJson,
            ),
        )
    }

    private fun <T> SnapshotDecodeResult<T>.decodedOrFailure(
        document: SessionStorageDocument,
    ): SessionStorageDocumentResult<T> = when (this) {
        is SnapshotDecodeResult.Decoded -> SessionStorageDocumentResult.Value(value)
        is SnapshotDecodeResult.Invalid -> SessionStorageDocumentResult.Failure(
            SessionStorageRestoreResult.InvalidDocument(document, reason, rawJson),
        )

        is SnapshotDecodeResult.UnsupportedVersion -> SessionStorageDocumentResult.Failure(
            SessionStorageRestoreResult.UnsupportedDocument(
                document = document,
                schemaVersion = schemaVersion,
                rawJson = rawJson,
            ),
        )
    }

    private fun CompiledStagePlan.toSnapshot(): CompiledStagePlanSnapshotV1 = CompiledStagePlanSnapshotV1(
        stagePlanId = id.value,
        stagePlanVersion = version,
        stages = stages.map { stage -> stage.toSnapshot() },
    )

    private fun CompiledBrewStage.toSnapshot(): CompiledBrewStageSnapshotV1 = CompiledBrewStageSnapshotV1(
        instance = instanceId.toSnapshot(),
        definition = definition.toSnapshot(),
    )

    private fun StageInstanceId.toSnapshot(): StageInstanceSnapshotV1 = StageInstanceSnapshotV1(
        sourceStageId = sourceStageId.value,
        occurrence = occurrence,
    )

    private fun BrewStageDefinition.toSnapshot(): BrewStageDefinitionSnapshotV1 = BrewStageDefinitionSnapshotV1(
        stageId = id.value,
        action = action.name,
        contentId = contentId.value,
        instructionAssetId = instructionAssetId?.value,
        requiresIllustration = requiresIllustration,
        safetyMessages = safetyMessages.map { message ->
            StageSafetyMessageSnapshotV1(message.code, message.severity.name)
        },
        requiredEquipmentStateId = equipmentRequirement?.requiredState?.value,
        completion = completionMode.toSnapshot(),
        referenceTargets = StageReferenceTargetsSnapshotMapper.toSnapshot(referenceTargets),
        alertPolicy = StageAlertPolicySnapshotV1(
            alertOnStart = alertPolicy.alertOnStart,
            alertOnCompletion = alertPolicy.alertOnCompletion,
            scheduleDeadline = alertPolicy.scheduleDeadline,
        ),
        isSkippable = isSkippable,
    )

    private fun StageCompletionMode.toSnapshot(): StageCompletionModeSnapshotV1 = when (this) {
        StageCompletionMode.Manual -> StageCompletionModeSnapshotV1(StageCompletionModeSnapshotV1.MANUAL)
        StageCompletionMode.Immediate -> StageCompletionModeSnapshotV1(StageCompletionModeSnapshotV1.IMMEDIATE)
        is StageCompletionMode.Countdown -> StageCompletionModeSnapshotV1(
            kind = StageCompletionModeSnapshotV1.COUNTDOWN,
            durationMillis = durationMillis,
        )

        is StageCompletionMode.ElapsedRange -> StageCompletionModeSnapshotV1(
            kind = StageCompletionModeSnapshotV1.ELAPSED_RANGE,
            minimumMillis = minimumMillis,
            maximumMillis = maximumMillis,
        )

        is StageCompletionMode.CumulativeAmount -> StageCompletionModeSnapshotV1(
            kind = StageCompletionModeSnapshotV1.CUMULATIVE_AMOUNT,
            targetGrams = targetGrams,
        )

        is StageCompletionMode.AddedAmount -> StageCompletionModeSnapshotV1(
            kind = StageCompletionModeSnapshotV1.ADDED_AMOUNT,
            targetGrams = targetGrams,
        )

        is StageCompletionMode.BeverageYield -> StageCompletionModeSnapshotV1(
            kind = StageCompletionModeSnapshotV1.BEVERAGE_YIELD,
            targetGrams = targetGrams,
        )

        is StageCompletionMode.ObservedEvent -> StageCompletionModeSnapshotV1(
            kind = StageCompletionModeSnapshotV1.OBSERVED_EVENT,
            observationId = observationId.value,
        )

        is StageCompletionMode.ExternalMarker -> StageCompletionModeSnapshotV1(
            kind = StageCompletionModeSnapshotV1.EXTERNAL_MARKER,
            markerId = markerId.value,
        )
    }

    private fun SessionRuntimeState.toSnapshot(): SessionRuntimeSnapshotV1 = SessionRuntimeSnapshotV1(
        sessionId = sessionId.value,
        status = status.name,
        currentStageIndex = currentStageIndex,
        stageProgress = stageProgress.map { progress -> progress.toSnapshot() },
        totalActiveElapsedMillis = totalActiveElapsedMillis,
        activeClockAnchor = activeClockAnchor?.let { anchor ->
            ActiveClockAnchorSnapshotV1(anchor.monotonicMillis, anchor.wallClockMillis)
        },
        startedAtWallClockMillis = startedAtWallClockMillis,
        pausedAtWallClockMillis = pausedAtWallClockMillis,
        endedAtWallClockMillis = endedAtWallClockMillis,
        updatedAtWallClockMillis = updatedAtWallClockMillis,
        revision = revision,
        processedEventIds = processedEventIds.map(SessionEventId::value),
        pendingEffects = pendingEffects.map { effect -> effect.toSnapshot() },
        acknowledgedEffectIds = acknowledgedEffectIds.map(SessionEffectId::value),
        lastClockReconciliation = lastClockReconciliation?.let { reconciliation ->
            ClockReconciliationSnapshotV1(
                kind = reconciliation.kind.name,
                observedDeltaMillis = reconciliation.observedDeltaMillis,
                appliedDeltaMillis = reconciliation.appliedDeltaMillis,
            )
        },
    )

    private fun StageRuntimeProgress.toSnapshot(): StageRuntimeProgressSnapshotV1 =
        StageRuntimeProgressSnapshotV1(
            status = status.name,
            elapsedActiveMillis = elapsedActiveMillis,
            startedAtWallClockMillis = startedAtWallClockMillis,
            completedAtWallClockMillis = completedAtWallClockMillis,
            completionKind = completionKind?.name,
            actuals = StageActualsSnapshotV1(
                addedAmountGrams = actuals.addedAmountGrams,
                cumulativeAmountGrams = actuals.cumulativeAmountGrams,
                beverageYieldGrams = actuals.beverageYieldGrams,
                observationIds = actuals.observations.map(StageObservationId::value).sorted(),
                markerIds = actuals.markers.map(StageMarkerId::value).sorted(),
            ),
        )

    private fun PendingSessionEffect.toSnapshot(): PendingSessionEffectSnapshotV1 = when (this) {
        is PendingSessionEffect.StageAlert -> PendingSessionEffectSnapshotV1(
            kind = PendingSessionEffectSnapshotV1.STAGE_ALERT,
            effectId = effectId.value,
            sessionId = sessionId.value,
            stageInstance = stageInstanceId.toSnapshot(),
            alertKind = kind.name,
        )

        is PendingSessionEffect.ScheduleStageDeadline -> PendingSessionEffectSnapshotV1(
            kind = PendingSessionEffectSnapshotV1.SCHEDULE_STAGE_DEADLINE,
            effectId = effectId.value,
            sessionId = sessionId.value,
            stageInstance = stageInstanceId.toSnapshot(),
            scheduleToken = scheduleToken,
            dueAtWallClockMillis = dueAtWallClockMillis,
        )

        is PendingSessionEffect.CancelStageDeadline -> PendingSessionEffectSnapshotV1(
            kind = PendingSessionEffectSnapshotV1.CANCEL_STAGE_DEADLINE,
            effectId = effectId.value,
            sessionId = sessionId.value,
            scheduleToken = scheduleToken,
        )

        is PendingSessionEffect.FinalizeBrewLog -> PendingSessionEffectSnapshotV1(
            kind = PendingSessionEffectSnapshotV1.FINALIZE_BREW_LOG,
            effectId = effectId.value,
            sessionId = sessionId.value,
        )

        is PendingSessionEffect.CancelSessionWork -> PendingSessionEffectSnapshotV1(
            kind = PendingSessionEffectSnapshotV1.CANCEL_SESSION_WORK,
            effectId = effectId.value,
            sessionId = sessionId.value,
        )
    }

    private fun CompiledStagePlanSnapshotV1.toDomain(): CompiledStagePlan {
        require(schemaVersion == CompiledStagePlanSnapshotV1.SCHEMA_VERSION) {
            "Unsupported compiled-plan snapshot schema: $schemaVersion"
        }
        require(stagePlanVersion > 0) { "Stage-plan version must be positive" }
        require(stages.isNotEmpty()) { "A compiled plan needs at least one stage" }
        val compiledStages = stages.map { stage -> stage.toDomain() }
        require(compiledStages.map(CompiledBrewStage::instanceId).distinct().size == compiledStages.size) {
            "Compiled stage instances must be unique"
        }
        return CompiledStagePlan(
            id = StagePlanId(stagePlanId),
            version = stagePlanVersion,
            stages = compiledStages,
        )
    }

    private fun CompiledBrewStageSnapshotV1.toDomain(): CompiledBrewStage {
        val definition = definition.toDomain()
        val instance = instance.toDomain()
        require(instance.sourceStageId == definition.id) {
            "Compiled stage instance must reference its definition's stage ID"
        }
        return CompiledBrewStage(instance, definition)
    }

    private fun StageInstanceSnapshotV1.toDomain(): StageInstanceId = StageInstanceId(
        sourceStageId = StageId(sourceStageId),
        occurrence = occurrence,
    )

    private fun BrewStageDefinitionSnapshotV1.toDomain(): BrewStageDefinition = BrewStageDefinition(
        id = StageId(stageId),
        action = enumValue(action, "stage action"),
        contentId = StageContentId(contentId),
        instructionAssetId = instructionAssetId?.let(::InstructionAssetId),
        requiresIllustration = requiresIllustration,
        safetyMessages = safetyMessages.map { message ->
            StageSafetyMessage(message.code, enumValue(message.severity, "safety severity"))
        },
        equipmentRequirement = requiredEquipmentStateId?.let { stateId ->
            StageEquipmentRequirement(StageEquipmentStateId(stateId))
        },
        completionMode = completion.toDomain(),
        referenceTargets = StageReferenceTargetsSnapshotMapper.toDomain(referenceTargets),
        alertPolicy = StageAlertPolicy(
            alertOnStart = alertPolicy.alertOnStart,
            alertOnCompletion = alertPolicy.alertOnCompletion,
            scheduleDeadline = alertPolicy.scheduleDeadline,
        ),
        isSkippable = isSkippable,
    )

    private fun StageCompletionModeSnapshotV1.toDomain(): StageCompletionMode = when (kind) {
        StageCompletionModeSnapshotV1.MANUAL -> StageCompletionMode.Manual
        StageCompletionModeSnapshotV1.IMMEDIATE -> StageCompletionMode.Immediate
        StageCompletionModeSnapshotV1.COUNTDOWN -> StageCompletionMode.Countdown(
            durationMillis = requiredPositive(durationMillis, "Countdown duration"),
        )

        StageCompletionModeSnapshotV1.ELAPSED_RANGE -> {
            val minimum = requiredNonNegative(minimumMillis, "Elapsed-range minimum")
            val maximum = requiredPositive(maximumMillis, "Elapsed-range maximum")
            require(maximum >= minimum) { "Elapsed-range maximum must not precede minimum" }
            StageCompletionMode.ElapsedRange(minimum, maximum)
        }

        StageCompletionModeSnapshotV1.CUMULATIVE_AMOUNT -> StageCompletionMode.CumulativeAmount(
            requiredPositive(targetGrams, "Cumulative amount target"),
        )

        StageCompletionModeSnapshotV1.ADDED_AMOUNT -> StageCompletionMode.AddedAmount(
            requiredPositive(targetGrams, "Added amount target"),
        )

        StageCompletionModeSnapshotV1.BEVERAGE_YIELD -> StageCompletionMode.BeverageYield(
            requiredPositive(targetGrams, "Beverage-yield target"),
        )

        StageCompletionModeSnapshotV1.OBSERVED_EVENT -> StageCompletionMode.ObservedEvent(
            StageObservationId(requireNotBlank(observationId, "Observation ID")),
        )

        StageCompletionModeSnapshotV1.EXTERNAL_MARKER -> StageCompletionMode.ExternalMarker(
            StageMarkerId(requireNotBlank(markerId, "Marker ID")),
        )

        else -> throw IllegalArgumentException("Unknown stage completion discriminator: $kind")
    }

    private fun SessionRuntimeSnapshotV1.toDomain(stagePlan: CompiledStagePlan): SessionRuntimeState {
        require(schemaVersion == SessionRuntimeSnapshotV1.SCHEMA_VERSION) {
            "Unsupported runtime snapshot schema: $schemaVersion"
        }
        require(stageProgress.size == stagePlan.stages.size) {
            "Runtime progress must have one entry for each compiled stage"
        }
        require(totalActiveElapsedMillis >= 0L) { "Total active elapsed time cannot be negative" }
        require(revision >= 0L) { "Session revision cannot be negative" }
        currentStageIndex?.let { index ->
            require(index in stagePlan.stages.indices) { "Current stage index is outside the compiled plan" }
        }

        val sessionId = SessionId(sessionId)
        val pendingEffects = pendingEffects.map { effect ->
            effect.toDomain(sessionId, stagePlan)
        }
        val pendingEffectIds = pendingEffects.map { effect -> effect.effectId }
        require(pendingEffectIds.distinct().size == pendingEffectIds.size) {
            "Pending effect IDs must be unique"
        }

        return SessionRuntimeState(
            sessionId = sessionId,
            stagePlan = stagePlan,
            status = enumValue(status, "session status"),
            currentStageIndex = currentStageIndex,
            stageProgress = stageProgress.map { progress -> progress.toDomain() },
            totalActiveElapsedMillis = totalActiveElapsedMillis,
            activeClockAnchor = activeClockAnchor?.toDomain(),
            startedAtWallClockMillis = startedAtWallClockMillis,
            pausedAtWallClockMillis = pausedAtWallClockMillis,
            endedAtWallClockMillis = endedAtWallClockMillis,
            updatedAtWallClockMillis = updatedAtWallClockMillis,
            revision = revision,
            processedEventIds = processedEventIds.map(::SessionEventId),
            pendingEffects = pendingEffects,
            acknowledgedEffectIds = acknowledgedEffectIds.map(::SessionEffectId),
            lastClockReconciliation = lastClockReconciliation?.toDomain(),
        )
    }

    private fun StageRuntimeProgressSnapshotV1.toDomain(): StageRuntimeProgress {
        require(elapsedActiveMillis >= 0L) { "Stage elapsed time cannot be negative" }
        return StageRuntimeProgress(
            status = enumValue(status, "stage runtime status"),
            elapsedActiveMillis = elapsedActiveMillis,
            startedAtWallClockMillis = startedAtWallClockMillis,
            completedAtWallClockMillis = completedAtWallClockMillis,
            completionKind = completionKind?.let { value ->
                enumValue(value, "stage completion kind")
            },
            actuals = actuals.toDomain(),
        )
    }

    private fun StageActualsSnapshotV1.toDomain(): StageActuals {
        val observations = observationIds.map(::StageObservationId)
        val markers = markerIds.map(::StageMarkerId)
        require(observations.distinct().size == observations.size) { "Observation IDs must be unique" }
        require(markers.distinct().size == markers.size) { "Marker IDs must be unique" }
        requireFiniteNonNegative(addedAmountGrams, "Added amount")
        requireFiniteNonNegative(cumulativeAmountGrams, "Cumulative amount")
        requireFiniteNonNegative(beverageYieldGrams, "Beverage yield")
        return StageActuals(
            addedAmountGrams = addedAmountGrams,
            cumulativeAmountGrams = cumulativeAmountGrams,
            beverageYieldGrams = beverageYieldGrams,
            observations = observations.toSet(),
            markers = markers.toSet(),
        )
    }

    private fun ActiveClockAnchorSnapshotV1.toDomain(): ActiveClockAnchor {
        monotonicMillis?.let { value ->
            require(value >= 0L) { "Monotonic clock anchor cannot be negative" }
        }
        return ActiveClockAnchor(monotonicMillis, wallClockMillis)
    }

    private fun ClockReconciliationSnapshotV1.toDomain(): ClockReconciliation = ClockReconciliation(
        kind = enumValue(kind, "clock reconciliation kind"),
        observedDeltaMillis = observedDeltaMillis,
        appliedDeltaMillis = appliedDeltaMillis,
    )

    private fun PendingSessionEffectSnapshotV1.toDomain(
        expectedSessionId: SessionId,
        stagePlan: CompiledStagePlan,
    ): PendingSessionEffect {
        val resolvedSessionId = SessionId(sessionId)
        require(resolvedSessionId == expectedSessionId) {
            "Pending effect session ID must match runtime session ID"
        }
        val resolvedEffectId = SessionEffectId(effectId)
        fun requiredStage(): StageInstanceId {
            val resolved = requireNotNull(stageInstance) { "Effect $kind requires a stage instance" }.toDomain()
            require(resolved in stagePlan.stageIds) { "Effect $kind references a stage outside the plan" }
            return resolved
        }
        fun requiredScheduleToken(): String = requireNotBlank(scheduleToken, "Effect schedule token")

        return when (kind) {
            PendingSessionEffectSnapshotV1.STAGE_ALERT -> PendingSessionEffect.StageAlert(
                effectId = resolvedEffectId,
                sessionId = resolvedSessionId,
                stageInstanceId = requiredStage(),
                kind = enumValue(requireNotBlank(alertKind, "Stage alert kind"), "stage alert kind"),
            )

            PendingSessionEffectSnapshotV1.SCHEDULE_STAGE_DEADLINE -> {
                PendingSessionEffect.ScheduleStageDeadline(
                    effectId = resolvedEffectId,
                    sessionId = resolvedSessionId,
                    stageInstanceId = requiredStage(),
                    scheduleToken = requiredScheduleToken(),
                    dueAtWallClockMillis = requireNotNull(dueAtWallClockMillis) {
                        "Scheduled deadline effects require a due wall-clock time"
                    },
                )
            }

            PendingSessionEffectSnapshotV1.CANCEL_STAGE_DEADLINE -> {
                PendingSessionEffect.CancelStageDeadline(
                    effectId = resolvedEffectId,
                    sessionId = resolvedSessionId,
                    scheduleToken = requiredScheduleToken(),
                )
            }

            PendingSessionEffectSnapshotV1.FINALIZE_BREW_LOG -> PendingSessionEffect.FinalizeBrewLog(
                effectId = resolvedEffectId,
                sessionId = resolvedSessionId,
            )

            PendingSessionEffectSnapshotV1.CANCEL_SESSION_WORK -> PendingSessionEffect.CancelSessionWork(
                effectId = resolvedEffectId,
                sessionId = resolvedSessionId,
            )

            else -> throw IllegalArgumentException("Unknown pending-effect discriminator: $kind")
        }
    }

    private fun SessionExecutionContextSnapshotV1.validated(): SessionExecutionContextSnapshotV1 {
        require(schemaVersion == SessionExecutionContextSnapshotV1.SCHEMA_VERSION) {
            "Unsupported execution-context snapshot schema: $schemaVersion"
        }
        require(logPresentation.methodLabel.isNotBlank()) { "Log method label cannot be blank" }
        requireFiniteNonNegative(logPresentation.doseG, "Log dose")
        requireFiniteNonNegative(logPresentation.waterG, "Log water")
        require(logPresentation.ratio.isFinite() && logPresentation.ratio > 0.0) {
            "Log ratio must be finite and positive"
        }
        return this
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String, label: String): T = try {
        enumValueOf(raw)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Unknown $label: $raw", error)
    }

    private fun requiredPositive(value: Long?, label: String): Long {
        val resolved = requireNotNull(value) { "$label is required" }
        require(resolved > 0L) { "$label must be positive" }
        return resolved
    }

    private fun requiredNonNegative(value: Long?, label: String): Long {
        val resolved = requireNotNull(value) { "$label is required" }
        require(resolved >= 0L) { "$label cannot be negative" }
        return resolved
    }

    private fun requiredPositive(value: Double?, label: String): Double {
        val resolved = requireNotNull(value) { "$label is required" }
        require(resolved.isFinite() && resolved > 0.0) { "$label must be finite and positive" }
        return resolved
    }

    private fun requireFiniteNonNegative(value: Double?, label: String) {
        if (value != null) {
            require(value.isFinite() && value >= 0.0) { "$label must be finite and non-negative" }
        }
    }

    private fun requireNotBlank(value: String?, label: String): String =
        requireNotNull(value) { "$label is required" }.also { resolved ->
            require(resolved.isNotBlank()) { "$label cannot be blank" }
        }
}
