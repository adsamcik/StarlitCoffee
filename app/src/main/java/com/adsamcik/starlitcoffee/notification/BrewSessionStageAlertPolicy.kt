package com.adsamcik.starlitcoffee.notification

import com.adsamcik.starlitcoffee.domain.brewing.session.ClockReconciliationKind
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertKind
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionKind
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode

/**
 * Stage alerts become notification-eligible when the matching live screen is
 * backgrounded. Durable effects from before that boundary are acknowledged as
 * already presented in-app rather than replayed as notification history.
 */
internal fun shouldPublishDurableBrewStageAlert(
    effect: PendingSessionEffect.StageAlert,
    runtime: SessionRuntimeState,
    backgroundedAtWallClockMillis: Long?,
): Boolean {
    if (effect.sessionId != runtime.sessionId) return false
    val occurredAt = stageAlertOccurredAtWallClockMillis(effect, runtime) ?: return false
    return backgroundedAtWallClockMillis == null || occurredAt >= backgroundedAtWallClockMillis
}

/** Resolves the timer boundary rather than the later reconciliation time. */
private fun stageAlertOccurredAtWallClockMillis(
    effect: PendingSessionEffect.StageAlert,
    runtime: SessionRuntimeState,
): Long? {
    val stageIndex = runtime.stagePlan.stages.indexOfFirst { stage ->
        stage.instanceId == effect.stageInstanceId
    }
    if (stageIndex < 0) return null
    val progress = runtime.stageProgress.getOrNull(stageIndex) ?: return null
    return when (effect.kind) {
        StageAlertKind.COMPLETED -> stageCompletionOccurredAtWallClockMillis(runtime, stageIndex)
        StageAlertKind.STARTED -> {
            val recordedStart = progress.startedAtWallClockMillis ?: return null
            if (stageIndex == 0) {
                recordedStart
            } else {
                // A delayed reconcile activates the next stage "now", although
                // its real start boundary was the previous stage's completion.
                stageCompletionOccurredAtWallClockMillis(runtime, stageIndex - 1)
                    ?.let { previousCompletion -> minOf(recordedStart, previousCompletion) }
                    ?: recordedStart
            }
        }
    }
}

private fun stageCompletionOccurredAtWallClockMillis(
    runtime: SessionRuntimeState,
    stageIndex: Int,
): Long? {
    val progress = runtime.stageProgress.getOrNull(stageIndex) ?: return null
    val recordedCompletion = progress.completedAtWallClockMillis ?: return null
    val latenessMillis = timerBoundaryLatenessMillis(runtime, stageIndex)
    return if (latenessMillis >= recordedCompletion) 0L else recordedCompletion - latenessMillis
}

/**
 * Reconciliation persists a transition at processing time. Deriving how far
 * the timer crossed its final required boundary recovers when it was actually
 * due, including absolute brew-time constraints used by exact recipes.
 */
private fun timerBoundaryLatenessMillis(
    runtime: SessionRuntimeState,
    stageIndex: Int,
): Long {
    val progress = runtime.stageProgress.getOrNull(stageIndex) ?: return 0L
    if (progress.completedAtWallClockMillis != runtime.updatedAtWallClockMillis) return 0L
    val reconciledFromWallClock = when (runtime.lastClockReconciliation?.kind) {
        ClockReconciliationKind.RESTORE_WALL_CLOCK,
        ClockReconciliationKind.WALL_CLOCK_FORWARD,
        -> true

        else -> false
    }
    if (progress.completionKind != StageCompletionKind.AUTOMATIC && !reconciledFromWallClock) {
        return 0L
    }

    val definition = runtime.stagePlan.stages.getOrNull(stageIndex)?.definition ?: return 0L
    val crossedBoundaryAges = buildList {
        when (val completion = definition.completionMode) {
            is StageCompletionMode.Countdown -> add(progress.elapsedActiveMillis - completion.durationMillis)
            is StageCompletionMode.ElapsedRange -> add(progress.elapsedActiveMillis - completion.maximumMillis)
            else -> Unit
        }
        definition.advanceConstraint.notBeforeStageElapsedMillis?.let { boundary ->
            add(progress.elapsedActiveMillis - boundary)
        }
        definition.advanceConstraint.notBeforeBrewElapsedMillis?.let { boundary ->
            add(runtime.totalActiveElapsedMillis - boundary)
        }
    }
    // Every boundary must be satisfied; the one crossed most recently is the
    // actual transition boundary.
    return crossedBoundaryAges
        .takeIf { ages -> ages.all { age -> age >= 0L } }
        ?.minOrNull()
        ?.coerceAtLeast(0L)
        ?: 0L
}
