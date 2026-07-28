package com.adsamcik.starlitcoffee.domain.brewing.session

/**
 * Pure reducer for active brew sessions. It neither reads clocks nor executes
 * effects: callers inject a [SessionClockReading], persist the returned state,
 * then deliver its durable outbox in order.
 */
object SessionReducer {
    fun reduce(
        state: SessionRuntimeState,
        event: SessionEvent,
        now: SessionClockReading,
    ): SessionTransition = SessionTransitionReducer().reduce(state, event, now)
}

private const val MAX_RETAINED_EVENT_IDS = 128
private const val MAX_RETAINED_EFFECT_IDS = 256

/**
 * Transition implementation kept behind [SessionReducer]'s stable facade.
 * Stage lookup and pure progression rules live at file scope so each event
 * handler describes only its transition and effect ordering.
 */
private class SessionTransitionReducer {

    fun reduce(
        state: SessionRuntimeState,
        event: SessionEvent,
        now: SessionClockReading,
    ): SessionTransition {
        if (event.eventId != null && event.eventId in state.processedEventIds) {
            return SessionTransition.unchanged(state)
        }

        val reduction = when (event) {
            is SessionEvent.Start -> start(state, now)
            is SessionEvent.Pause -> pause(state, now)
            is SessionEvent.Resume -> resume(state, now)
            is SessionEvent.Tick -> advanceTime(state, now, TimeAdvanceSource.MONOTONIC)
            is SessionEvent.Reconcile -> advanceTime(state, now, TimeAdvanceSource.WALL_RECONCILE)
            is SessionEvent.Restore -> advanceTime(state, now, TimeAdvanceSource.WALL_RESTORE)
            is SessionEvent.ManualAdvance -> manualAdvance(state, now)
            is SessionEvent.RecordActual -> recordActual(state, event.value, now)
            is SessionEvent.RecordObservation -> recordObservation(state, event.observationId, now)
            is SessionEvent.RecordMarker -> recordMarker(state, event.markerId, now)
            is SessionEvent.Skip -> skip(state, now)
            is SessionEvent.Cancel -> cancel(state, now)
            is SessionEvent.Finish -> finish(state, now)
            is SessionEvent.AcknowledgeEffect -> acknowledgeEffect(state, event.effectIdToAcknowledge)
        } ?: return SessionTransition.unchanged(state)

        return persistTransition(
            previous = state,
            reduction = reduction,
            eventId = event.eventId,
        )
    }

    private fun start(
        state: SessionRuntimeState,
        now: SessionClockReading,
    ): Reduction? {
        if (state.status != BrewSessionStatus.READY) return null
        val effects = mutableListOf<PendingSessionEffect>()
        val started = state.copy(
            status = BrewSessionStatus.RUNNING,
            startedAtWallClockMillis = now.wallClockMillis,
            updatedAtWallClockMillis = now.wallClockMillis,
            activeClockAnchor = ActiveClockAnchor(now.monotonicMillis, now.wallClockMillis),
        )
        val activated = activateCurrentStage(started, now, effects)
        return completeAutomaticStages(activated, now, effects)
    }

    private fun pause(
        state: SessionRuntimeState,
        now: SessionClockReading,
    ): Reduction? {
        if (state.status != BrewSessionStatus.RUNNING) return null
        val advanced = advanceTime(state, now, TimeAdvanceSource.MONOTONIC) ?: return null
        var working = advanced.state
        val effects = advanced.effects.toMutableList()
        if (working.status != BrewSessionStatus.RUNNING) return Reduction(working, effects)

        currentStageWithProgress(working)?.let { (stage, _) ->
            cancelDeadline(working, stage, "pause", effects)
        }
        working = working.copy(
            status = BrewSessionStatus.PAUSED,
            pausedAtWallClockMillis = now.wallClockMillis,
            updatedAtWallClockMillis = now.wallClockMillis,
            activeClockAnchor = null,
        )
        return Reduction(working, effects)
    }

    private fun resume(
        state: SessionRuntimeState,
        now: SessionClockReading,
    ): Reduction? {
        if (state.status != BrewSessionStatus.PAUSED) return null
        val effects = mutableListOf<PendingSessionEffect>()
        var working = state.copy(
            status = BrewSessionStatus.RUNNING,
            pausedAtWallClockMillis = null,
            updatedAtWallClockMillis = now.wallClockMillis,
            activeClockAnchor = ActiveClockAnchor(now.monotonicMillis, now.wallClockMillis),
        )
        currentStageWithProgress(working)?.let { (stage, progress) ->
            scheduleDeadline(working, stage, progress, now, effects)
        }
        return completeAutomaticStages(working, now, effects)
    }

    private fun advanceTime(
        state: SessionRuntimeState,
        now: SessionClockReading,
        source: TimeAdvanceSource,
    ): Reduction? {
        if (state.status != BrewSessionStatus.RUNNING) return null
        val anchor = state.activeClockAnchor
            ?: ActiveClockAnchor(monotonicMillis = null, wallClockMillis = now.wallClockMillis)
        val (observedDelta, appliedDelta, kind) = when (source) {
            TimeAdvanceSource.MONOTONIC -> {
                if (anchor.monotonicMillis != null) {
                    val observed = now.monotonicMillis - anchor.monotonicMillis
                    Triple(observed, observed.coerceAtLeast(0L), ClockReconciliationKind.MONOTONIC_TICK)
                } else {
                    val observed = now.wallClockMillis - anchor.wallClockMillis
                    Triple(
                        observed,
                        observed.coerceAtLeast(0L),
                        if (observed < 0L) {
                            ClockReconciliationKind.WALL_CLOCK_BACKWARD_CLAMPED
                        } else {
                            ClockReconciliationKind.RESTORE_WALL_CLOCK
                        },
                    )
                }
            }

            TimeAdvanceSource.WALL_RESTORE,
            TimeAdvanceSource.WALL_RECONCILE,
            -> {
                val observed = now.wallClockMillis - anchor.wallClockMillis
                val reconciliationKind = when {
                    observed < 0L -> ClockReconciliationKind.WALL_CLOCK_BACKWARD_CLAMPED
                    source == TimeAdvanceSource.WALL_RESTORE -> ClockReconciliationKind.RESTORE_WALL_CLOCK
                    else -> ClockReconciliationKind.WALL_CLOCK_FORWARD
                }
                Triple(observed, observed.coerceAtLeast(0L), reconciliationKind)
            }
        }

        val currentIndex = state.currentStageIndex ?: return null
        val progress = state.stageProgress.getOrNull(currentIndex) ?: return null
        if (progress.status != StageRunStatus.ACTIVE) return null

        val updatedProgress = state.stageProgress.replaceAt(
            currentIndex,
            progress.copy(elapsedActiveMillis = saturatingAdd(progress.elapsedActiveMillis, appliedDelta)),
        )
        val updated = state.copy(
            stageProgress = updatedProgress,
            totalActiveElapsedMillis = saturatingAdd(state.totalActiveElapsedMillis, appliedDelta),
            activeClockAnchor = ActiveClockAnchor(now.monotonicMillis, now.wallClockMillis),
            updatedAtWallClockMillis = now.wallClockMillis,
            lastClockReconciliation = ClockReconciliation(
                kind = kind,
                observedDeltaMillis = observedDelta,
                appliedDeltaMillis = appliedDelta,
            ),
        )
        return completeAutomaticStages(updated, now, mutableListOf())
    }

    private fun manualAdvance(
        state: SessionRuntimeState,
        now: SessionClockReading,
    ): Reduction? {
        val timeAdjusted = if (state.status == BrewSessionStatus.RUNNING) {
            advanceTime(state, now, TimeAdvanceSource.MONOTONIC)
        } else {
            null
        }
        var working = timeAdjusted?.state ?: state
        val effects = timeAdjusted?.effects?.toMutableList() ?: mutableListOf()
        val current = currentStageWithProgress(working) ?: return timeAdjusted
        val (stage, progress) = current
        if (!stage.definition.completionMode.allowsManualAdvance(progress.elapsedActiveMillis)) {
            return timeAdjusted
        }
        working = completeCurrentStage(working, StageCompletionKind.MANUAL, now, effects)
        return completeAutomaticStages(working, now, effects)
    }

    private fun finish(
        state: SessionRuntimeState,
        now: SessionClockReading,
    ): Reduction? {
        val timeAdjusted = if (state.status == BrewSessionStatus.RUNNING) {
            advanceTime(state, now, TimeAdvanceSource.MONOTONIC)
        } else {
            null
        }
        var working = timeAdjusted?.state ?: state
        val effects = timeAdjusted?.effects?.toMutableList() ?: mutableListOf()
        val currentIndex = working.currentStageIndex ?: return timeAdjusted
        if (currentIndex != working.stagePlan.stages.lastIndex) return timeAdjusted
        val current = currentStageWithProgress(working) ?: return timeAdjusted
        if (!current.first.definition.completionMode.allowsManualAdvance(current.second.elapsedActiveMillis)) {
            return timeAdjusted
        }
        working = completeCurrentStage(working, StageCompletionKind.MANUAL, now, effects)
        return completeAutomaticStages(working, now, effects)
    }

    private fun recordActual(
        state: SessionRuntimeState,
        value: StageActualValue,
        now: SessionClockReading,
    ): Reduction? {
        if (!state.acceptsStageInput() || !value.isUsable()) return null
        val active = activeStageWithProgress(state) ?: return null

        val updatedActuals = active.progress.actuals.with(value)
        var working = state.copy(
            stageProgress = state.stageProgress.replaceAt(
                active.index,
                active.progress.copy(actuals = updatedActuals),
            ),
            updatedAtWallClockMillis = now.wallClockMillis,
        )
        val effects = mutableListOf<PendingSessionEffect>()
        if (active.stage.definition.completionMode.isSatisfiedBy(updatedActuals)) {
            working = completeCurrentStage(working, StageCompletionKind.MEASURED, now, effects)
        }
        return completeAutomaticStages(working, now, effects)
    }

    private fun recordObservation(
        state: SessionRuntimeState,
        observationId: StageObservationId,
        now: SessionClockReading,
    ): Reduction? = recordEventualActual(
        state = state,
        now = now,
        updateActuals = { actuals -> actuals.copy(observations = actuals.observations + observationId) },
        completes = { mode -> mode is StageCompletionMode.ObservedEvent && mode.observationId == observationId },
        completionKind = StageCompletionKind.OBSERVED,
    )

    private fun recordMarker(
        state: SessionRuntimeState,
        markerId: StageMarkerId,
        now: SessionClockReading,
    ): Reduction? = recordEventualActual(
        state = state,
        now = now,
        updateActuals = { actuals -> actuals.copy(markers = actuals.markers + markerId) },
        completes = { mode -> mode is StageCompletionMode.ExternalMarker && mode.markerId == markerId },
        completionKind = StageCompletionKind.EXTERNAL_MARKER,
    )

    private fun recordEventualActual(
        state: SessionRuntimeState,
        now: SessionClockReading,
        updateActuals: (StageActuals) -> StageActuals,
        completes: (StageCompletionMode) -> Boolean,
        completionKind: StageCompletionKind,
    ): Reduction? {
        if (!state.acceptsStageInput()) return null
        val active = activeStageWithProgress(state) ?: return null

        val actuals = updateActuals(active.progress.actuals)
        var working = state.copy(
            stageProgress = state.stageProgress.replaceAt(
                active.index,
                active.progress.copy(actuals = actuals),
            ),
            updatedAtWallClockMillis = now.wallClockMillis,
        )
        val effects = mutableListOf<PendingSessionEffect>()
        if (completes(active.stage.definition.completionMode)) {
            working = completeCurrentStage(working, completionKind, now, effects)
        }
        return completeAutomaticStages(working, now, effects)
    }

    private fun skip(
        state: SessionRuntimeState,
        now: SessionClockReading,
    ): Reduction? {
        if (state.status !in setOf(BrewSessionStatus.RUNNING, BrewSessionStatus.PAUSED)) return null
        val current = currentStageWithProgress(state) ?: return null
        if (!current.first.definition.isSkippable) return null
        val effects = mutableListOf<PendingSessionEffect>()
        val completed = completeCurrentStage(state, StageCompletionKind.SKIPPED, now, effects)
        return completeAutomaticStages(completed, now, effects)
    }

    private fun cancel(
        state: SessionRuntimeState,
        now: SessionClockReading,
    ): Reduction? {
        if (state.status !in setOf(BrewSessionStatus.RUNNING, BrewSessionStatus.PAUSED)) return null
        val timeAdjusted = if (state.status == BrewSessionStatus.RUNNING) {
            advanceTime(state, now, TimeAdvanceSource.MONOTONIC)
        } else {
            null
        }
        var working = timeAdjusted?.state ?: state
        val effects = timeAdjusted?.effects?.toMutableList() ?: mutableListOf()
        if (working.status !in setOf(BrewSessionStatus.RUNNING, BrewSessionStatus.PAUSED)) {
            return timeAdjusted
        }
        val currentIndex = working.currentStageIndex
        val current = currentStageWithProgress(working)
        if (currentIndex != null && current != null) {
            cancelDeadline(working, current.first, "cancel", effects)
            working = working.copy(
                stageProgress = working.stageProgress.replaceAt(
                    currentIndex,
                    current.second.copy(
                        status = StageRunStatus.CANCELLED,
                        completedAtWallClockMillis = now.wallClockMillis,
                    ),
                ),
            )
        }
        effects += PendingSessionEffect.CancelSessionWork(
            effectId = effectId(working, "cancel_session_work"),
            sessionId = working.sessionId,
        )
        return Reduction(
            working.copy(
                status = BrewSessionStatus.CANCELLED,
                activeClockAnchor = null,
                endedAtWallClockMillis = now.wallClockMillis,
                updatedAtWallClockMillis = now.wallClockMillis,
            ),
            effects,
        )
    }

    private fun acknowledgeEffect(
        state: SessionRuntimeState,
        effectIdToAcknowledge: SessionEffectId,
    ): Reduction? {
        if (effectIdToAcknowledge in state.acknowledgedEffectIds) return null
        if (state.pendingEffects.none { it.effectId == effectIdToAcknowledge }) return null
        return Reduction(
            state.copy(
                pendingEffects = state.pendingEffects.filterNot { it.effectId == effectIdToAcknowledge },
                acknowledgedEffectIds = state.acknowledgedEffectIds
                    .plus(effectIdToAcknowledge)
                    .takeLast(MAX_RETAINED_EFFECT_IDS),
            ),
        )
    }

    private fun completeAutomaticStages(
        initial: SessionRuntimeState,
        now: SessionClockReading,
        effects: MutableList<PendingSessionEffect>,
    ): Reduction {
        var working = initial
        while (
            working.status == BrewSessionStatus.RUNNING &&
            activeStageWithProgress(working)?.isAutomaticCompletionDue() == true
        ) {
            working = completeCurrentStage(working, StageCompletionKind.AUTOMATIC, now, effects)
        }
        return Reduction(working, effects)
    }

    private fun activateCurrentStage(
        state: SessionRuntimeState,
        now: SessionClockReading,
        effects: MutableList<PendingSessionEffect>,
    ): SessionRuntimeState {
        val index = state.currentStageIndex ?: return state
        val stage = state.stagePlan.stages.getOrNull(index) ?: return state
        val progress = state.stageProgress.getOrNull(index) ?: return state
        if (progress.status != StageRunStatus.PENDING) return state

        val activated = state.copy(
            stageProgress = state.stageProgress.replaceAt(
                index,
                progress.copy(
                    status = StageRunStatus.ACTIVE,
                    startedAtWallClockMillis = now.wallClockMillis,
                ),
            ),
            activeClockAnchor = if (state.status == BrewSessionStatus.RUNNING) {
                ActiveClockAnchor(now.monotonicMillis, now.wallClockMillis)
            } else {
                null
            },
            updatedAtWallClockMillis = now.wallClockMillis,
        )
        if (stage.definition.alertPolicy.alertOnStart) {
            effects += PendingSessionEffect.StageAlert(
                effectId = effectId(activated, "stage_${stage.instanceId.persistentKey}_started"),
                sessionId = activated.sessionId,
                stageInstanceId = stage.instanceId,
                kind = StageAlertKind.STARTED,
            )
        }
        if (activated.status == BrewSessionStatus.RUNNING) {
            scheduleDeadline(activated, stage, activated.stageProgress[index], now, effects)
        }
        return activated
    }

    private fun completeCurrentStage(
        state: SessionRuntimeState,
        completionKind: StageCompletionKind,
        now: SessionClockReading,
        effects: MutableList<PendingSessionEffect>,
    ): SessionRuntimeState {
        val active = activeStageWithProgress(state) ?: return state
        val (index, stage, progress) = active

        cancelDeadline(state, stage, "stage_complete", effects)
        if (stage.definition.alertPolicy.alertOnCompletion) {
            effects += PendingSessionEffect.StageAlert(
                effectId = effectId(state, "stage_${stage.instanceId.persistentKey}_completed"),
                sessionId = state.sessionId,
                stageInstanceId = stage.instanceId,
                kind = StageAlertKind.COMPLETED,
            )
        }

        val completedStatus = if (completionKind == StageCompletionKind.SKIPPED) {
            StageRunStatus.SKIPPED
        } else {
            StageRunStatus.COMPLETED
        }
        var working = state.copy(
            stageProgress = state.stageProgress.replaceAt(
                index,
                progress.copy(
                    status = completedStatus,
                    completedAtWallClockMillis = now.wallClockMillis,
                    completionKind = completionKind,
                ),
            ),
            activeClockAnchor = null,
            updatedAtWallClockMillis = now.wallClockMillis,
        )
        val nextIndex = index + 1
        return if (nextIndex >= working.stagePlan.stages.size) {
            effects += PendingSessionEffect.FinalizeBrewLog(
                effectId = effectId(working, "finalize_brew_log"),
                sessionId = working.sessionId,
            )
            working.copy(
                status = BrewSessionStatus.COMPLETED,
                currentStageIndex = null,
                endedAtWallClockMillis = now.wallClockMillis,
                activeClockAnchor = null,
            )
        } else {
            working = working.copy(currentStageIndex = nextIndex)
            activateCurrentStage(working, now, effects)
        }
    }

    private fun scheduleDeadline(
        state: SessionRuntimeState,
        stage: CompiledBrewStage,
        progress: StageRuntimeProgress,
        now: SessionClockReading,
        effects: MutableList<PendingSessionEffect>,
    ) {
        if (!stage.definition.alertPolicy.scheduleDeadline) return
        val deadlineDuration = stage.definition.completionMode.deadlineDurationMillis() ?: return
        val remaining = (deadlineDuration - progress.elapsedActiveMillis).coerceAtLeast(0L)
        val dueAt = saturatingAdd(now.wallClockMillis, remaining)
        effects += PendingSessionEffect.ScheduleStageDeadline(
            effectId = effectId(state, "schedule_${stage.instanceId.persistentKey}_$dueAt"),
            sessionId = state.sessionId,
            stageInstanceId = stage.instanceId,
            scheduleToken = deadlineToken(state, stage),
            dueAtWallClockMillis = dueAt,
        )
    }

    private fun cancelDeadline(
        state: SessionRuntimeState,
        stage: CompiledBrewStage,
        reason: String,
        effects: MutableList<PendingSessionEffect>,
    ) {
        if (stage.definition.completionMode.deadlineDurationMillis() == null) return
        effects += PendingSessionEffect.CancelStageDeadline(
            effectId = effectId(
                state,
                "cancel_${stage.instanceId.persistentKey}_${reason}_${state.revision + 1}",
            ),
            sessionId = state.sessionId,
            scheduleToken = deadlineToken(state, stage),
        )
    }

    private fun persistTransition(
        previous: SessionRuntimeState,
        reduction: Reduction,
        eventId: SessionEventId?,
    ): SessionTransition {
        var next = reduction.state
        if (eventId != null) {
            next = next.copy(
                processedEventIds = next.processedEventIds.plus(eventId).takeLast(MAX_RETAINED_EVENT_IDS),
            )
        }
        next = queueEffects(next, reduction.effects)
        next = next.copy(revision = previous.revision + 1L)
        val persist = SessionEffect.PersistRuntime(
            effectId = effectId(next, "persist_${next.revision}"),
            snapshot = next,
        )
        return SessionTransition(
            state = next,
            effects = listOf(persist) + next.pendingEffects,
        )
    }

    private fun queueEffects(
        state: SessionRuntimeState,
        candidates: List<PendingSessionEffect>,
    ): SessionRuntimeState {
        val knownIds = (state.pendingEffects.map(PendingSessionEffect::effectId) +
            state.acknowledgedEffectIds).toMutableSet()
        val newEffects = candidates.filter { candidate -> knownIds.add(candidate.effectId) }
        return if (newEffects.isEmpty()) state else state.copy(pendingEffects = state.pendingEffects + newEffects)
    }

    private fun List<StageRuntimeProgress>.replaceAt(
        index: Int,
        value: StageRuntimeProgress,
    ): List<StageRuntimeProgress> = toMutableList().also { it[index] = value }

    private fun effectId(state: SessionRuntimeState, suffix: String): SessionEffectId =
        SessionEffectId("${state.sessionId.value}:$suffix")

    private fun deadlineToken(state: SessionRuntimeState, stage: CompiledBrewStage): String =
        "${state.sessionId.value}:${stage.instanceId.persistentKey}:deadline"

    private enum class TimeAdvanceSource {
        MONOTONIC,
        WALL_RESTORE,
        WALL_RECONCILE,
    }

    private data class Reduction(
        val state: SessionRuntimeState,
        val effects: List<PendingSessionEffect> = emptyList(),
    )
}

private data class ActiveStage(
    val index: Int,
    val stage: CompiledBrewStage,
    val progress: StageRuntimeProgress,
)

private fun currentStageWithProgress(
    state: SessionRuntimeState,
): Pair<CompiledBrewStage, StageRuntimeProgress>? {
    val index = state.currentStageIndex ?: return null
    val stage = state.stagePlan.stages.getOrNull(index) ?: return null
    val progress = state.stageProgress.getOrNull(index) ?: return null
    return stage to progress
}

private fun activeStageWithProgress(state: SessionRuntimeState): ActiveStage? {
    val index = state.currentStageIndex ?: return null
    val stage = state.stagePlan.stages.getOrNull(index) ?: return null
    val progress = state.stageProgress.getOrNull(index) ?: return null
    return progress.takeIf { it.status == StageRunStatus.ACTIVE }?.let {
        ActiveStage(index, stage, it)
    }
}

private fun SessionRuntimeState.acceptsStageInput(): Boolean =
    status == BrewSessionStatus.RUNNING || status == BrewSessionStatus.PAUSED

private fun ActiveStage.isAutomaticCompletionDue(): Boolean =
    when (val mode = stage.definition.completionMode) {
        StageCompletionMode.Immediate -> true
        is StageCompletionMode.Countdown -> progress.elapsedActiveMillis >= mode.durationMillis
        is StageCompletionMode.ElapsedRange -> progress.elapsedActiveMillis >= mode.maximumMillis
        else -> false
    }

private fun StageActuals.with(value: StageActualValue): StageActuals = when (value) {
    is StageActualValue.AddedAmount -> copy(addedAmountGrams = value.grams)
    is StageActualValue.BeverageYield -> copy(beverageYieldGrams = value.grams)
    is StageActualValue.CumulativeAmount -> copy(cumulativeAmountGrams = value.grams)
}

private fun StageActualValue.isUsable(): Boolean = when (this) {
    is StageActualValue.AddedAmount -> grams.isFinite() && grams >= 0.0
    is StageActualValue.BeverageYield -> grams.isFinite() && grams >= 0.0
    is StageActualValue.CumulativeAmount -> grams.isFinite() && grams >= 0.0
}

private fun StageCompletionMode.isSatisfiedBy(actuals: StageActuals): Boolean = when (this) {
    is StageCompletionMode.AddedAmount -> (actuals.addedAmountGrams ?: 0.0) >= targetGrams
    is StageCompletionMode.BeverageYield -> (actuals.beverageYieldGrams ?: 0.0) >= targetGrams
    is StageCompletionMode.CumulativeAmount -> (actuals.cumulativeAmountGrams ?: 0.0) >= targetGrams
    else -> false
}

private fun StageCompletionMode.allowsManualAdvance(elapsedMillis: Long): Boolean = when (this) {
    StageCompletionMode.Manual -> true
    is StageCompletionMode.ElapsedRange -> elapsedMillis >= minimumMillis
    else -> false
}

private fun StageCompletionMode.deadlineDurationMillis(): Long? = when (this) {
    is StageCompletionMode.Countdown -> durationMillis
    is StageCompletionMode.ElapsedRange -> maximumMillis
    else -> null
}

private fun saturatingAdd(left: Long, right: Long): Long = when {
    right <= 0L -> left
    left > Long.MAX_VALUE - right -> Long.MAX_VALUE
    else -> left + right
}
