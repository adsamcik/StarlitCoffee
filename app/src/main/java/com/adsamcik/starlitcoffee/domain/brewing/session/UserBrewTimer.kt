package com.adsamcik.starlitcoffee.domain.brewing.session

/** A reminder belongs to an exact physical stage; it is never a completion trigger. */
data class UserBrewTimer(
    val stageInstanceId: StageInstanceId,
    val revision: Long,
    val durationMillis: Long,
    val originStageElapsedMillis: Long,
    val originWallClockMillis: Long?,
    val deadlineAtWallClockMillis: Long?,
    val reached: Boolean = false,
) {
    init {
        require(revision > 0L && durationMillis in 1L..MAX_DURATION_MILLIS)
        require(originStageElapsedMillis >= 0L)
        require(originWallClockMillis == null || originWallClockMillis >= 0L)
        require(deadlineAtWallClockMillis == null || deadlineAtWallClockMillis >= 0L)
    }

    fun remainingMillis(stageElapsedMillis: Long): Long =
        (durationMillis - (stageElapsedMillis - originStageElapsedMillis).coerceAtLeast(0L)).coerceAtLeast(0L)

    fun scheduleToken(sessionId: SessionId): String =
        "${sessionId.value}:${stageInstanceId.persistentKey}:timer:$revision"

    companion object {
        // Contextual duration editing is bounded to seven days, with millisecond precision in storage.
        const val MAX_DURATION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}

enum class BrewTimerOrigin { STAGE_START, NOW }

internal data class UserTimerTransition(
    val state: SessionRuntimeState,
    val effects: List<PendingSessionEffect>,
)

internal fun SessionRuntimeState.withTimerTarget(
    event: SessionEvent.SetTimerTarget,
    now: SessionClockReading,
): SessionRuntimeState {
    if (status !in setOf(BrewSessionStatus.RUNNING, BrewSessionStatus.PAUSED) ||
        currentStage?.instanceId != event.stageInstanceId || timerRevision == Long.MAX_VALUE
    ) return this
    val duration = event.durationMillis
    if (duration != null && duration !in 1L..UserBrewTimer.MAX_DURATION_MILLIS) return this
    val old = userTimer
    val elapsed = requireNotNull(currentProgress).elapsedActiveMillis
    val originElapsed = old?.originStageElapsedMillis ?: when (event.origin) {
        BrewTimerOrigin.STAGE_START -> 0L
        BrewTimerOrigin.NOW -> elapsed
    }
    val originWall = old?.originWallClockMillis ?: when (event.origin) {
        BrewTimerOrigin.STAGE_START -> currentProgress?.startedAtWallClockMillis
        BrewTimerOrigin.NOW -> now.wallClockMillis
    }
    return copy(
        timerRevision = timerRevision + 1L,
        userTimer = duration?.let {
            UserBrewTimer(event.stageInstanceId, timerRevision + 1L, it, originElapsed, originWall, null)
        },
        updatedAtWallClockMillis = now.wallClockMillis,
    )
}

/** Every transition invalidates obsolete timer effects before it is persisted. */
internal fun reconcileUserTimer(
    previous: SessionRuntimeState,
    updated: SessionRuntimeState,
    now: SessionClockReading,
): UserTimerTransition {
    val effects = mutableListOf<PendingSessionEffect>()
    val candidate = updated.userTimer?.takeIf {
        updated.status in setOf(BrewSessionStatus.RUNNING, BrewSessionStatus.PAUSED) &&
            it.stageInstanceId == updated.currentStage?.instanceId
    }
    val timer = candidate?.reconciled(updated, now)
    val deadline = timer?.deadlineAtWallClockMillis
    val old = previous.userTimer
    val scheduleChanged = old?.revision != timer?.revision || old?.deadlineAtWallClockMillis != deadline
    if (old?.deadlineAtWallClockMillis != null && scheduleChanged) {
        effects += PendingSessionEffect.CancelStageDeadline(
            SessionEffectId("${previous.sessionId.value}:cancel_timer:${old.revision}:${updated.revision + 1L}"),
            previous.sessionId,
            old.scheduleToken(previous.sessionId),
        )
    }
    if (timer != null && deadline != null && scheduleChanged) {
        effects += PendingSessionEffect.ScheduleTimerDeadline(
            SessionEffectId("${updated.sessionId.value}:schedule_timer:${timer.revision}:$deadline"),
            updated.sessionId, timer.stageInstanceId, timer.scheduleToken(updated.sessionId), deadline,
        )
    }
    if (timer?.reached == true && old?.takeIf { it.revision == timer.revision }?.reached != true) {
        effects += PendingSessionEffect.TimerAlert(
            SessionEffectId("${updated.sessionId.value}:timer_reached:${timer.revision}"),
            updated.sessionId, timer.stageInstanceId, timer.revision,
        )
    }
    val pending = updated.pendingEffects.filter { effect ->
        when (effect) {
            is PendingSessionEffect.TimerAlert -> timer?.reached == true && timer.revision == effect.timerRevision
            is PendingSessionEffect.ScheduleTimerDeadline -> timer?.deadlineAtWallClockMillis ==
                effect.dueAtWallClockMillis && timer.scheduleToken(updated.sessionId) == effect.scheduleToken
            else -> true
        }
    }
    return UserTimerTransition(updated.copy(userTimer = timer, pendingEffects = pending), effects)
}

private fun UserBrewTimer.reconciled(state: SessionRuntimeState, now: SessionClockReading): UserBrewTimer {
    val remaining = remainingMillis(state.currentProgress?.elapsedActiveMillis ?: 0L)
    val isReached = reached || (originWallClockMillis != null && remaining == 0L)
    val hasRunningOrigin = state.status == BrewSessionStatus.RUNNING && originWallClockMillis != null
    val deadline = if (!isReached && hasRunningOrigin) {
        val anchor = state.activeClockAnchor?.wallClockMillis ?: now.wallClockMillis
        if (anchor > Long.MAX_VALUE - remaining) Long.MAX_VALUE else anchor + remaining
    } else null
    return copy(deadlineAtWallClockMillis = deadline, reached = isReached)
}
