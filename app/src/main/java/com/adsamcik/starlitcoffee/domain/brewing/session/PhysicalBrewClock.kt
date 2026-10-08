package com.adsamcik.starlitcoffee.domain.brewing.session

/** Explicit origin for new guided workflows; null runtime configuration preserves legacy clocks. */
data class PhysicalBrewClock(
    val startStageId: StageInstanceId,
    val hasStarted: Boolean = false,
    val originKnown: Boolean = true,
    val configuredOriginWallClockMillis: Long? = null,
    val reminderDurationMillis: Long? = null,
    val timerOnly: Boolean = false,
    val endStageId: StageInstanceId? = null,
    val endedElapsedMillis: Long? = null,
) {
    init {
        require(configuredOriginWallClockMillis == null || configuredOriginWallClockMillis >= 0L)
        require(reminderDurationMillis == null || reminderDurationMillis in 1L..UserBrewTimer.MAX_DURATION_MILLIS)
        require(originKnown || configuredOriginWallClockMillis == null)
        require(endedElapsedMillis == null || (hasStarted && endedElapsedMillis >= 0L))
    }
}

internal fun SessionRuntimeState.startPhysicalClock(now: SessionClockReading): SessionRuntimeState? {
    val clock = physicalClock
    if (clock != null && clock.startStageId != currentStage?.instanceId) return this
    val origin = if (clock?.originKnown == false) null else clock?.configuredOriginWallClockMillis ?: now.wallClockMillis
    if (origin != null && origin > now.wallClockMillis) return null
    val elapsed = origin?.let { now.wallClockMillis - it } ?: 0L
    val index = currentStageIndex ?: return null
    val progress = stageProgress[index]
    val timerDuration = clock?.reminderDurationMillis
    if (timerDuration != null && timerRevision == Long.MAX_VALUE) return null
    val timer = timerDuration?.let {
        UserBrewTimer(requireNotNull(currentStage).instanceId, timerRevision + 1L, it, 0L, origin, null)
    }
    return copy(
        physicalClock = clock?.copy(hasStarted = true),
        startedAtWallClockMillis = origin,
        activeClockAnchor = ActiveClockAnchor(now.monotonicMillis, now.wallClockMillis),
        totalActiveElapsedMillis = elapsed,
        stageProgress = stageProgress.toMutableList().also {
            it[index] = progress.copy(elapsedActiveMillis = elapsed, startedAtWallClockMillis = origin)
        },
        userTimer = timer,
        timerRevision = if (timer != null) timerRevision + 1L else timerRevision,
        updatedAtWallClockMillis = now.wallClockMillis,
    )
}

internal fun SessionRuntimeState.withEstablishedClockOrigin(
    event: SessionEvent.EstablishClockOrigin,
    now: SessionClockReading,
): SessionRuntimeState? {
    val clock = physicalClock ?: return null
    if (status != BrewSessionStatus.RUNNING || clock.originKnown || !clock.hasStarted) return null
    if (clock.startStageId != event.stageInstanceId || currentStage?.instanceId != event.stageInstanceId) return null
    if (event.originWallClockMillis !in 0L..now.wallClockMillis) return null
    val targetDuration = userTimer?.durationMillis ?: clock.reminderDurationMillis
    return copy(physicalClock = clock.copy(originKnown = true, hasStarted = false,
        configuredOriginWallClockMillis = event.originWallClockMillis, reminderDurationMillis = targetDuration))
        .startPhysicalClock(now)
}
