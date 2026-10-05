package com.adsamcik.starlitcoffee.notification

import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentationMapper
import com.adsamcik.starlitcoffee.ui.session.BrewActivityPresentationMapper

internal enum class DurableBrewStatusClock { ELAPSED, COUNTDOWN, TIMING_REACHED, UNKNOWN_START, EXTRACTION_ENDED }

/** A display projection. It cannot advance a stage, schedule work or consume an observation. */
internal data class DurableBrewStatusPresentation(
    val action: BrewStageAction,
    val clock: DurableBrewStatusClock,
    val chronometerWallClockMillis: Long?,
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
    return DurableBrewStatusPresentation(stage.action, clock, chronometer)
}
