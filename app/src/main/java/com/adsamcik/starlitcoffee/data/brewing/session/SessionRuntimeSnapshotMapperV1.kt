package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.session.ActiveClockAnchor
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockReconciliation
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageActuals
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMarkerId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageObservationId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress

/** Bidirectional mapping for runtime state stored independently from its plan. */
internal object SessionRuntimeSnapshotMapperV1 {

    fun toSnapshot(value: SessionRuntimeState): SessionRuntimeSnapshotV1 =
        SessionRuntimeSnapshotV1(
            sessionId = value.sessionId.value,
            status = value.status.name,
            currentStageIndex = value.currentStageIndex,
            stageProgress = value.stageProgress.map(::progressToSnapshot),
            totalActiveElapsedMillis = value.totalActiveElapsedMillis,
            activeClockAnchor = value.activeClockAnchor?.let { anchor ->
                ActiveClockAnchorSnapshotV1(anchor.monotonicMillis, anchor.wallClockMillis)
            },
            startedAtWallClockMillis = value.startedAtWallClockMillis,
            pausedAtWallClockMillis = value.pausedAtWallClockMillis,
            endedAtWallClockMillis = value.endedAtWallClockMillis,
            updatedAtWallClockMillis = value.updatedAtWallClockMillis,
            revision = value.revision,
            // These bounded histories are chronological; their order affects future eviction.
            processedEventIds = value.processedEventIds.map(SessionEventId::value),
            pendingEffects = value.pendingEffects.map(::effectToSnapshot),
            // Acknowledgements use the same append-and-retain history semantics as events.
            acknowledgedEffectIds = value.acknowledgedEffectIds.map(SessionEffectId::value),
            lastClockReconciliation = value.lastClockReconciliation?.let { reconciliation ->
                ClockReconciliationSnapshotV1(
                    kind = reconciliation.kind.name,
                    observedDeltaMillis = reconciliation.observedDeltaMillis,
                    appliedDeltaMillis = reconciliation.appliedDeltaMillis,
                )
            },
        )

    fun toDomain(
        value: SessionRuntimeSnapshotV1,
        stagePlan: CompiledStagePlan,
    ): SessionRuntimeState {
        require(value.schemaVersion == SessionRuntimeSnapshotV1.SCHEMA_VERSION) {
            "Unsupported runtime snapshot schema: ${value.schemaVersion}"
        }
        require(value.stageProgress.size == stagePlan.stages.size) {
            "Runtime progress must have one entry for each compiled stage"
        }
        require(value.totalActiveElapsedMillis >= 0L) {
            "Total active elapsed time cannot be negative"
        }
        require(value.revision >= 0L) { "Session revision cannot be negative" }
        value.currentStageIndex?.let { index ->
            require(index in stagePlan.stages.indices) {
                "Current stage index is outside the compiled plan"
            }
        }

        val sessionId = SessionId(value.sessionId)
        val pendingEffects = value.pendingEffects.map { effect ->
            effectToDomain(effect, sessionId, stagePlan)
        }
        val pendingEffectIds = pendingEffects.map(PendingSessionEffect::effectId)
        require(pendingEffectIds.distinct().size == pendingEffectIds.size) {
            "Pending effect IDs must be unique"
        }

        return SessionRuntimeState(
            sessionId = sessionId,
            stagePlan = stagePlan,
            status = SessionSnapshotValueDecoder.enumValue(value.status, "session status"),
            currentStageIndex = value.currentStageIndex,
            stageProgress = value.stageProgress.map(::progressToDomain),
            totalActiveElapsedMillis = value.totalActiveElapsedMillis,
            activeClockAnchor = value.activeClockAnchor?.let(::anchorToDomain),
            startedAtWallClockMillis = value.startedAtWallClockMillis,
            pausedAtWallClockMillis = value.pausedAtWallClockMillis,
            endedAtWallClockMillis = value.endedAtWallClockMillis,
            updatedAtWallClockMillis = value.updatedAtWallClockMillis,
            revision = value.revision,
            processedEventIds = value.processedEventIds.map(::SessionEventId),
            pendingEffects = pendingEffects,
            acknowledgedEffectIds = value.acknowledgedEffectIds.map(::SessionEffectId),
            lastClockReconciliation = value.lastClockReconciliation?.let(::reconciliationToDomain),
        )
    }

    private fun progressToSnapshot(value: StageRuntimeProgress): StageRuntimeProgressSnapshotV1 =
        StageRuntimeProgressSnapshotV1(
            status = value.status.name,
            elapsedActiveMillis = value.elapsedActiveMillis,
            startedAtWallClockMillis = value.startedAtWallClockMillis,
            completedAtWallClockMillis = value.completedAtWallClockMillis,
            completionKind = value.completionKind?.name,
            actuals = StageActualsSnapshotV1(
                addedAmountGrams = value.actuals.addedAmountGrams,
                cumulativeAmountGrams = value.actuals.cumulativeAmountGrams,
                beverageYieldGrams = value.actuals.beverageYieldGrams,
                observationIds = value.actuals.observations.map(StageObservationId::value).sorted(),
                markerIds = value.actuals.markers.map(StageMarkerId::value).sorted(),
            ),
        )

    private fun effectToSnapshot(value: PendingSessionEffect): PendingSessionEffectSnapshotV1 =
        when (value) {
            is PendingSessionEffect.StageAlert -> PendingSessionEffectSnapshotV1(
                kind = PendingSessionEffectSnapshotV1.STAGE_ALERT,
                effectId = value.effectId.value,
                sessionId = value.sessionId.value,
                stageInstance = StageInstanceSnapshotMapperV1.toSnapshot(value.stageInstanceId),
                alertKind = value.kind.name,
            )
            is PendingSessionEffect.ScheduleStageDeadline -> PendingSessionEffectSnapshotV1(
                kind = PendingSessionEffectSnapshotV1.SCHEDULE_STAGE_DEADLINE,
                effectId = value.effectId.value,
                sessionId = value.sessionId.value,
                stageInstance = StageInstanceSnapshotMapperV1.toSnapshot(value.stageInstanceId),
                scheduleToken = value.scheduleToken,
                dueAtWallClockMillis = value.dueAtWallClockMillis,
            )
            is PendingSessionEffect.CancelStageDeadline -> PendingSessionEffectSnapshotV1(
                kind = PendingSessionEffectSnapshotV1.CANCEL_STAGE_DEADLINE,
                effectId = value.effectId.value,
                sessionId = value.sessionId.value,
                scheduleToken = value.scheduleToken,
            )
            is PendingSessionEffect.FinalizeBrewLog -> PendingSessionEffectSnapshotV1(
                kind = PendingSessionEffectSnapshotV1.FINALIZE_BREW_LOG,
                effectId = value.effectId.value,
                sessionId = value.sessionId.value,
            )
            is PendingSessionEffect.CancelSessionWork -> PendingSessionEffectSnapshotV1(
                kind = PendingSessionEffectSnapshotV1.CANCEL_SESSION_WORK,
                effectId = value.effectId.value,
                sessionId = value.sessionId.value,
            )
        }

    private fun progressToDomain(value: StageRuntimeProgressSnapshotV1): StageRuntimeProgress {
        require(value.elapsedActiveMillis >= 0L) { "Stage elapsed time cannot be negative" }
        return StageRuntimeProgress(
            status = SessionSnapshotValueDecoder.enumValue(value.status, "stage runtime status"),
            elapsedActiveMillis = value.elapsedActiveMillis,
            startedAtWallClockMillis = value.startedAtWallClockMillis,
            completedAtWallClockMillis = value.completedAtWallClockMillis,
            completionKind = value.completionKind?.let { completionKind ->
                SessionSnapshotValueDecoder.enumValue(completionKind, "stage completion kind")
            },
            actuals = actualsToDomain(value.actuals),
        )
    }

    private fun actualsToDomain(value: StageActualsSnapshotV1): StageActuals {
        val observations = value.observationIds.map(::StageObservationId)
        val markers = value.markerIds.map(::StageMarkerId)
        require(observations.distinct().size == observations.size) {
            "Observation IDs must be unique"
        }
        require(markers.distinct().size == markers.size) { "Marker IDs must be unique" }
        SessionSnapshotValueDecoder.requireFiniteNonNegative(value.addedAmountGrams, "Added amount")
        SessionSnapshotValueDecoder.requireFiniteNonNegative(
            value.cumulativeAmountGrams,
            "Cumulative amount",
        )
        SessionSnapshotValueDecoder.requireFiniteNonNegative(
            value.beverageYieldGrams,
            "Beverage yield",
        )
        return StageActuals(
            addedAmountGrams = value.addedAmountGrams,
            cumulativeAmountGrams = value.cumulativeAmountGrams,
            beverageYieldGrams = value.beverageYieldGrams,
            observations = observations.toSet(),
            markers = markers.toSet(),
        )
    }

    private fun anchorToDomain(value: ActiveClockAnchorSnapshotV1): ActiveClockAnchor {
        value.monotonicMillis?.let { monotonicMillis ->
            require(monotonicMillis >= 0L) { "Monotonic clock anchor cannot be negative" }
        }
        return ActiveClockAnchor(value.monotonicMillis, value.wallClockMillis)
    }

    private fun reconciliationToDomain(
        value: ClockReconciliationSnapshotV1,
    ): ClockReconciliation = ClockReconciliation(
        kind = SessionSnapshotValueDecoder.enumValue(
            value.kind,
            "clock reconciliation kind",
        ),
        observedDeltaMillis = value.observedDeltaMillis,
        appliedDeltaMillis = value.appliedDeltaMillis,
    )

    private fun effectToDomain(
        value: PendingSessionEffectSnapshotV1,
        expectedSessionId: SessionId,
        stagePlan: CompiledStagePlan,
    ): PendingSessionEffect {
        val resolvedSessionId = SessionId(value.sessionId)
        require(resolvedSessionId == expectedSessionId) {
            "Pending effect session ID must match runtime session ID"
        }
        val resolvedEffectId = SessionEffectId(value.effectId)
        fun requiredStage(): StageInstanceId {
            val snapshot = requireNotNull(value.stageInstance) {
                "Effect ${value.kind} requires a stage instance"
            }
            val resolved = StageInstanceSnapshotMapperV1.toDomain(snapshot)
            require(resolved in stagePlan.stageIds) {
                "Effect ${value.kind} references a stage outside the plan"
            }
            return resolved
        }
        fun requiredScheduleToken(): String = SessionSnapshotValueDecoder.requireNotBlank(
            value.scheduleToken,
            "Effect schedule token",
        )

        return when (value.kind) {
            PendingSessionEffectSnapshotV1.STAGE_ALERT -> PendingSessionEffect.StageAlert(
                effectId = resolvedEffectId,
                sessionId = resolvedSessionId,
                stageInstanceId = requiredStage(),
                kind = SessionSnapshotValueDecoder.enumValue(
                    SessionSnapshotValueDecoder.requireNotBlank(value.alertKind, "Stage alert kind"),
                    "stage alert kind",
                ),
            )
            PendingSessionEffectSnapshotV1.SCHEDULE_STAGE_DEADLINE ->
                PendingSessionEffect.ScheduleStageDeadline(
                    effectId = resolvedEffectId,
                    sessionId = resolvedSessionId,
                    stageInstanceId = requiredStage(),
                    scheduleToken = requiredScheduleToken(),
                    dueAtWallClockMillis = requireNotNull(value.dueAtWallClockMillis) {
                        "Scheduled deadline effects require a due wall-clock time"
                    },
                )
            PendingSessionEffectSnapshotV1.CANCEL_STAGE_DEADLINE ->
                PendingSessionEffect.CancelStageDeadline(
                    effectId = resolvedEffectId,
                    sessionId = resolvedSessionId,
                    scheduleToken = requiredScheduleToken(),
                )
            PendingSessionEffectSnapshotV1.FINALIZE_BREW_LOG -> PendingSessionEffect.FinalizeBrewLog(
                effectId = resolvedEffectId,
                sessionId = resolvedSessionId,
            )
            PendingSessionEffectSnapshotV1.CANCEL_SESSION_WORK -> PendingSessionEffect.CancelSessionWork(
                effectId = resolvedEffectId,
                sessionId = resolvedSessionId,
            )
            else -> throw IllegalArgumentException(
                "Unknown pending-effect discriminator: ${value.kind}",
            )
        }
    }
}
