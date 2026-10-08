package com.adsamcik.starlitcoffee.ui.session

import com.adsamcik.starlitcoffee.data.brewing.session.RestoredActiveBrewSession
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.data.brewing.guides.contentId
import com.adsamcik.starlitcoffee.data.brewing.guides.title

/** Root-shell projection only. It cannot advance a stage or own a clock. */
data class BrewActivityPresentation(
    val sessionId: String,
    val methodLabel: String,
    val stageAction: BrewStageAction?,
    val state: BrewActivityState,
    val timeMillis: Long,
    val stageTitle: String? = null,
) {
    val needsAttention: Boolean
        get() = state == BrewActivityState.TIMING_REACHED || state == BrewActivityState.COMPLETED
}

enum class BrewActivityState {
    READY,
    ELAPSED,
    TIME_LEFT,
    TIMING_REACHED,
    PAUSED,
    COMPLETED,
    UNKNOWN_START,
}

object BrewActivityPresentationMapper {
    fun map(session: RestoredActiveBrewSession, nowWallClockMillis: Long): BrewActivityPresentation? {
        val presentation = ActiveBrewSessionPresentationMapper.map(session, nowWallClockMillis)
            as? ActiveBrewSessionPresentation.Available ?: return null
        if (presentation.status == BrewSessionStatus.CANCELLED) return null
        if (presentation.status == BrewSessionStatus.COMPLETED && presentation.isTimerOnly) return null
        if (presentation.status == BrewSessionStatus.COMPLETED && session.entity.completedLogId != null) return null
        val stage = presentation.currentStage
        val remaining = presentation.userTimerRemainingMillis ?: timingRemaining(
            stage,
            session.runtime.currentStage?.definition?.advanceConstraint?.isConstrained == true,
        )
        val state = when {
            presentation.status == BrewSessionStatus.RUNNING && !presentation.hasPhysicalClockStarted -> BrewActivityState.READY
            presentation.status == BrewSessionStatus.RUNNING && !presentation.isPhysicalOriginKnown -> BrewActivityState.UNKNOWN_START
            else -> activityState(presentation.status, remaining)
        }
        return BrewActivityPresentation(
            sessionId = presentation.sessionId,
            methodLabel = session.executionContext.logPresentation.methodLabel,
            stageAction = stage?.action,
            stageTitle = session.recipe.reviewedGuide?.let { guide ->
                guide.steps.firstOrNull { guide.contentId(it) == stage?.contentId }?.title()
            },
            state = state,
            timeMillis = if (state == BrewActivityState.TIME_LEFT) {
                requireNotNull(remaining)
            } else {
                presentation.totalActiveElapsedMillis
            },
        )
    }

    internal fun timingRemaining(stage: CurrentBrewStagePresentation?, hasAdvanceConstraint: Boolean): Long? {
        // Only completion semantics and source-defined advance constraints can
        // supply a deadline. Informational recipe reference cues never can.
        val timedRemaining = when (val completion = stage?.completion) {
            is BrewStageCompletionPresentation.Countdown -> completion.remainingMillis
            is BrewStageCompletionPresentation.ElapsedRange -> completion.minimumRemainingMillis
            else -> null
        }
        val constraintRemaining = stage?.advanceConstraint?.let {
            maxOf(it.stageRemainingMillis, it.brewRemainingMillis)
        } ?: 0L
        return timedRemaining?.let { maxOf(it, constraintRemaining) }
            ?: constraintRemaining.takeIf { hasAdvanceConstraint }
    }

    private fun activityState(status: BrewSessionStatus, remaining: Long?): BrewActivityState =
        when (status) {
            BrewSessionStatus.READY -> BrewActivityState.READY
            BrewSessionStatus.PAUSED -> BrewActivityState.PAUSED
            BrewSessionStatus.COMPLETED -> BrewActivityState.COMPLETED
            BrewSessionStatus.RUNNING -> when {
                remaining == null -> BrewActivityState.ELAPSED
                remaining > 0L -> BrewActivityState.TIME_LEFT
                else -> BrewActivityState.TIMING_REACHED
            }
            // Cancelled sessions are excluded before presentation.
            BrewSessionStatus.CANCELLED -> error("Cancelled brew cannot have active presentation")
        }

    /** Stable input order breaks ties; the focused brew supplies its own status. */
    fun visible(
        sessions: List<BrewActivityPresentation>,
        focusedSessionId: String?,
    ): List<BrewActivityPresentation> = sessions
        .filterNot { it.sessionId == focusedSessionId }
        .sortedByDescending { it.needsAttention }
}
