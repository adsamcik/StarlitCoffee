package com.adsamcik.starlitcoffee.notification

import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentationMapper
import com.adsamcik.starlitcoffee.ui.session.BrewActivityPresentationMapper
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation

internal enum class DurableBrewStatusClock { ELAPSED, COUNTDOWN, TIMING_REACHED, UNKNOWN_START, EXTRACTION_ENDED }

/** A display projection. It cannot advance a stage, schedule work or consume an observation. */
internal data class DurableBrewStatusPresentation(
    val action: BrewStageAction,
    val clock: DurableBrewStatusClock,
    val chronometerWallClockMillis: Long?,
    val timeMillis: Long = 0L,
    val cupFillFraction: Float? = null,
)

internal fun durableBrewStatusPresentation(
    runtime: SessionRuntimeState,
    nowWallClockMillis: Long,
): DurableBrewStatusPresentation? {
    val presentation = ActiveBrewSessionPresentationMapper.map(runtime, nowWallClockMillis)
        as? ActiveBrewSessionPresentation.Available ?: return null
    if (presentation.status != BrewSessionStatus.RUNNING) return null
    if (!presentation.hasPhysicalClockStarted) return null
    val stage = presentation.currentStage ?: return null
    val remaining = presentation.userTimerRemainingMillis ?: BrewActivityPresentationMapper.timingRemaining(stage,
        runtime.currentStage?.definition?.advanceConstraint?.isConstrained == true)
    val clock = when {
        !presentation.isPhysicalOriginKnown -> DurableBrewStatusClock.UNKNOWN_START
        runtime.physicalClock?.endedElapsedMillis != null && presentation.userTimer == null ->
            DurableBrewStatusClock.EXTRACTION_ENDED
        remaining == null -> DurableBrewStatusClock.ELAPSED
        remaining > 0L -> DurableBrewStatusClock.COUNTDOWN
        else -> DurableBrewStatusClock.TIMING_REACHED
    }
    val chronometer = when (clock) {
        DurableBrewStatusClock.ELAPSED ->
            (nowWallClockMillis - presentation.totalActiveElapsedMillis).coerceAtLeast(0L)
        DurableBrewStatusClock.COUNTDOWN -> {
            val delay = requireNotNull(remaining)
            if (nowWallClockMillis > Long.MAX_VALUE - delay) Long.MAX_VALUE else nowWallClockMillis + delay
        }
        DurableBrewStatusClock.TIMING_REACHED, DurableBrewStatusClock.UNKNOWN_START,
        DurableBrewStatusClock.EXTRACTION_ENDED -> null
    }
    return DurableBrewStatusPresentation(
        stage.action, clock, chronometer,
        timeMillis = if (clock == DurableBrewStatusClock.ELAPSED) {
            presentation.totalActiveElapsedMillis
        } else remaining ?: 0L,
        cupFillFraction = cupFillFraction(runtime, presentation, remaining, clock),
    )
}

private fun cupFillFraction(
    runtime: SessionRuntimeState,
    presentation: ActiveBrewSessionPresentation.Available,
    remaining: Long?,
    clock: DurableBrewStatusClock,
): Float? {
    val stage = presentation.currentStage ?: return null
    val stageElapsed = stage.elapsedActiveMillis
    val duration = presentation.userTimer?.durationMillis ?: maxOf(
        when (val completion = stage.completion) {
            is BrewStageCompletionPresentation.Countdown -> completion.targetElapsedMillis
            is BrewStageCompletionPresentation.ElapsedRange -> completion.minimumElapsedMillis
            else -> 0L
        },
        runtime.currentStage?.definition?.advanceConstraint?.notBeforeStageElapsedMillis ?: 0L,
        (runtime.currentStage?.definition?.advanceConstraint?.notBeforeBrewElapsedMillis ?: 0L) -
            (presentation.totalActiveElapsedMillis - stageElapsed),
    )
    return if (remaining != null && duration > 0L &&
        clock in setOf(DurableBrewStatusClock.COUNTDOWN, DurableBrewStatusClock.TIMING_REACHED)
    ) {
        (1.0 - remaining.toDouble() / duration).coerceIn(0.0, 1.0).toFloat()
    } else null
}
