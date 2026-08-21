package com.adsamcik.starlitcoffee.ui.session

import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionRestoreResult
import com.adsamcik.starlitcoffee.data.brewing.session.RestoredActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.SessionStorageDocument
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageActuals
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMarkerId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageObservationId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference

/**
 * Resource-free state for the durable-brew screen.
 *
 * It carries stable IDs and semantic values instead of English copy, allowing
 * Compose to resolve localized labels and content descriptions without making
 * restore, timer, or safety behavior depend on UI resources. Safety messages
 * are intentionally separate from discretionary guidance and are never
 * filtered by this mapper.
 */
sealed interface ActiveBrewSessionPresentation {
    data class Available(
        val sessionId: String,
        val status: BrewSessionStatus,
        val totalActiveElapsedMillis: Long,
        val stageProgress: BrewSessionStageProgressPresentation,
        val currentStage: CurrentBrewStagePresentation?,
        /** Render outside guidance-density filtering. */
        val safetyMessages: List<StageSafetyMessage>,
        val actions: BrewSessionActionAvailability,
        val accessibility: BrewSessionAccessibilityPresentation,
    ) : ActiveBrewSessionPresentation

    /**
     * A repair state, never a substitute session. In particular, raw unknown
     * IDs and unsupported documents cannot fall through to a built-in recipe.
     */
    data class Unavailable(
        val sessionId: String?,
        val reason: ActiveBrewSessionUnavailableReason,
    ) : ActiveBrewSessionPresentation
}

sealed interface ActiveBrewSessionUnavailableReason {
    data class UnsupportedRecipe(val schemaVersion: Int) : ActiveBrewSessionUnavailableReason

    data object InvalidRecipe : ActiveBrewSessionUnavailableReason

    data object MissingExecutionContext : ActiveBrewSessionUnavailableReason

    data class UnsupportedStorage(
        val document: SessionStorageDocument,
        val schemaVersion: Int,
    ) : ActiveBrewSessionUnavailableReason

    data class InvalidStorage(
        val document: SessionStorageDocument,
    ) : ActiveBrewSessionUnavailableReason

    data object InconsistentEntity : ActiveBrewSessionUnavailableReason

    data class InvalidRuntime(
        val issue: SessionRuntimePresentationIssue,
    ) : ActiveBrewSessionUnavailableReason
}

/**
 * These codes give the UI a localized, actionable repair path without
 * exposing raw persisted JSON in an accessibility surface.
 */
enum class SessionRuntimePresentationIssue {
    EMPTY_STAGE_PLAN,
    STAGE_PROGRESS_COUNT_MISMATCH,
    INVALID_CURRENT_STAGE_INDEX,
    NEGATIVE_STAGE_ELAPSED_TIME,
    NEGATIVE_TOTAL_ELAPSED_TIME,
    NEGATIVE_REVISION,
    READY_STAGE_NOT_PENDING,
    ACTIVE_SESSION_STAGE_NOT_ACTIVE,
    COMPLETED_SESSION_HAS_CURRENT_STAGE,
}

data class BrewSessionStageProgressPresentation(
    /** Completed stages exclude explicitly skipped stages. */
    val completedStageCount: Int,
    val skippedStageCount: Int,
    val finishedStageCount: Int,
    val totalStageCount: Int,
    /** One-based while a stage is available; null once the session has ended. */
    val currentStageNumber: Int?,
)

data class CurrentBrewStagePresentation(
    val stageInstanceId: StageInstanceId,
    val action: BrewStageAction,
    val contentId: StageContentId,
    val instructionAssetId: InstructionAssetId?,
    val requiresIllustration: Boolean,
    val runStatus: StageRunStatus,
    val elapsedActiveMillis: Long,
    val completion: BrewStageCompletionPresentation,
    val advanceConstraint: BrewStageAdvanceConstraintPresentation =
        BrewStageAdvanceConstraintPresentation(),
    /** Render outside guidance-density filtering. */
    val safetyMessages: List<StageSafetyMessage>,
    /** Informational values only; [completion] remains the sole stage trigger. */
    val referenceCues: List<BrewStageReferenceCuePresentation> = emptyList(),
) {
    val stageId: StageId
        get() = stageInstanceId.sourceStageId
}

/** Remaining source-defined boundaries before the next stage may begin. */
data class BrewStageAdvanceConstraintPresentation(
    val stageRemainingMillis: Long = 0L,
    val brewRemainingMillis: Long = 0L,
) {
    val isSatisfied: Boolean
        get() = stageRemainingMillis == 0L && brewRemainingMillis == 0L
}

/**
 * Typed, localized-at-render-time reference information for the current stage.
 * These values intentionally have no completion state or action affordance.
 */
sealed interface BrewStageReferenceCuePresentation {
    val qualifier: StageTargetQualifier

    data class Time(
        val reference: StageTimeReference,
        override val qualifier: StageTargetQualifier,
        val minimumMillis: Long,
        val maximumMillis: Long,
    ) : BrewStageReferenceCuePresentation

    data class Mass(
        val role: QuantityRole,
        val reference: StageMassReference,
        override val qualifier: StageTargetQualifier,
        val minimumGrams: Double,
        val maximumGrams: Double,
    ) : BrewStageReferenceCuePresentation

    data class Temperature(
        override val qualifier: StageTargetQualifier,
        val minimumC: Double,
        val maximumC: Double,
    ) : BrewStageReferenceCuePresentation
}

sealed interface BrewStageCompletionPresentation {
    data object Manual : BrewStageCompletionPresentation

    /** Included defensively; normally the reducer consumes immediate stages before persistence. */
    data object Immediate : BrewStageCompletionPresentation

    data class Countdown(
        val targetElapsedMillis: Long,
        val remainingMillis: Long,
    ) : BrewStageCompletionPresentation

    data class ElapsedRange(
        val minimumElapsedMillis: Long,
        val maximumElapsedMillis: Long,
        val minimumRemainingMillis: Long,
        val maximumRemainingMillis: Long,
    ) : BrewStageCompletionPresentation

    data class ActualValue(
        val inputKind: StageActualInputKind,
        val targetGrams: Double,
        val recordedGrams: Double?,
        val remainingGrams: Double,
    ) : BrewStageCompletionPresentation

    data class ObservedEvent(
        val observationId: StageObservationId,
        val alreadyRecorded: Boolean,
    ) : BrewStageCompletionPresentation

    data class ExternalMarker(
        val markerId: StageMarkerId,
        val alreadyRecorded: Boolean,
    ) : BrewStageCompletionPresentation
}

enum class StageActualInputKind {
    ADDED_AMOUNT_GRAMS,
    CUMULATIVE_AMOUNT_GRAMS,
    BEVERAGE_YIELD_GRAMS,
}

data class BrewSessionActionAvailability(
    val canStart: Boolean,
    val canPause: Boolean,
    val canResume: Boolean,
    val canManualAdvance: Boolean,
    val canSkip: Boolean,
    val canCancel: Boolean,
    val canFinish: Boolean,
    /** True while an active stage can retain a measured value in its durable snapshot. */
    val canRecordActual: Boolean,
)

/**
 * Typed semantics for accessibility labels. The UI resolves these values to
 * localized text, preserving the exact same stage position, timer, and safety
 * information that is visible on screen.
 */
data class BrewSessionAccessibilityPresentation(
    val liveRegion: BrewSessionLiveRegion,
    val status: BrewSessionStatus,
    val stageProgress: BrewSessionStageProgressPresentation,
    val currentStageAction: BrewStageAction?,
    val currentStageContentId: StageContentId?,
    val totalActiveElapsedMillis: Long,
    val currentStageElapsedMillis: Long?,
    val countdownRemainingMillis: Long?,
    val safetyMessages: List<StageSafetyMessage>,
)

enum class BrewSessionLiveRegion {
    POLITE,
    ASSERTIVE,
}

/**
 * Pure boundary between durable session restoration and the active-brew UI.
 * [nowWallClockMillis] is optional so callers which are not actively ticking
 * can render the persisted elapsed values unchanged. When supplied for a
 * running session, it advances the display only; persistence remains owned by
 * the session coordinator and reducer.
 */
object ActiveBrewSessionPresentationMapper {

    fun map(
        restored: ActiveBrewSessionRestoreResult,
        nowWallClockMillis: Long? = null,
    ): ActiveBrewSessionPresentation = when (restored) {
        is ActiveBrewSessionRestoreResult.Restored -> map(restored.value, nowWallClockMillis)
        is ActiveBrewSessionRestoreResult.UnsupportedRecipe -> ActiveBrewSessionPresentation.Unavailable(
            sessionId = null,
            reason = ActiveBrewSessionUnavailableReason.UnsupportedRecipe(restored.schemaVersion),
        )

        is ActiveBrewSessionRestoreResult.InvalidRecipe -> ActiveBrewSessionPresentation.Unavailable(
            sessionId = null,
            reason = ActiveBrewSessionUnavailableReason.InvalidRecipe,
        )

        is ActiveBrewSessionRestoreResult.MissingExecutionContext -> {
            ActiveBrewSessionPresentation.Unavailable(
                sessionId = restored.sessionId,
                reason = ActiveBrewSessionUnavailableReason.MissingExecutionContext,
            )
        }

        is ActiveBrewSessionRestoreResult.UnsupportedStorage -> {
            ActiveBrewSessionPresentation.Unavailable(
                sessionId = null,
                reason = ActiveBrewSessionUnavailableReason.UnsupportedStorage(
                    document = restored.document,
                    schemaVersion = restored.schemaVersion,
                ),
            )
        }

        is ActiveBrewSessionRestoreResult.InvalidStorage -> ActiveBrewSessionPresentation.Unavailable(
            sessionId = null,
            reason = ActiveBrewSessionUnavailableReason.InvalidStorage(restored.document),
        )

        is ActiveBrewSessionRestoreResult.InconsistentEntity -> ActiveBrewSessionPresentation.Unavailable(
            sessionId = restored.sessionId,
            reason = ActiveBrewSessionUnavailableReason.InconsistentEntity,
        )
    }

    fun map(
        restored: RestoredActiveBrewSession,
        nowWallClockMillis: Long? = null,
    ): ActiveBrewSessionPresentation = map(restored.runtime, nowWallClockMillis)

    fun map(
        runtime: SessionRuntimeState,
        nowWallClockMillis: Long? = null,
    ): ActiveBrewSessionPresentation {
        runtimeIssue(runtime)?.let { issue ->
            return ActiveBrewSessionPresentation.Unavailable(
                sessionId = runtime.sessionId.value,
                reason = ActiveBrewSessionUnavailableReason.InvalidRuntime(issue),
            )
        }

        val currentStage = runtime.currentStage
        val currentProgress = runtime.currentProgress
        val stageProgress = BrewSessionStageProgressPresentation(
            completedStageCount = runtime.stageProgress.count { progress ->
                progress.status == StageRunStatus.COMPLETED
            },
            skippedStageCount = runtime.stageProgress.count { progress ->
                progress.status == StageRunStatus.SKIPPED
            },
            finishedStageCount = runtime.stageProgress.count { progress ->
                progress.status in FINISHED_STAGE_STATUSES
            },
            totalStageCount = runtime.stagePlan.stages.size,
            currentStageNumber = runtime.currentStageIndex?.plus(1),
        )
        val elapsedDelta = runningWallClockDelta(runtime, nowWallClockMillis)
        val totalElapsed = saturatingAdd(runtime.totalActiveElapsedMillis, elapsedDelta)
        val safetyMessages = currentStage?.definition?.safetyMessages?.toList().orEmpty()
        val presentedCurrentStage = currentStagePresentation(
            currentStage = currentStage,
            currentProgress = currentProgress,
            elapsedDelta = elapsedDelta,
            totalElapsed = totalElapsed,
            safetyMessages = safetyMessages,
        )
        val actions = actionAvailability(
            runtime = runtime,
            currentStage = presentedCurrentStage,
        )
        return ActiveBrewSessionPresentation.Available(
            sessionId = runtime.sessionId.value,
            status = runtime.status,
            totalActiveElapsedMillis = totalElapsed,
            stageProgress = stageProgress,
            currentStage = presentedCurrentStage,
            safetyMessages = safetyMessages,
            actions = actions,
            accessibility = BrewSessionAccessibilityPresentation(
                liveRegion = if (safetyMessages.any { message ->
                    message.severity == StageSafetySeverity.CRITICAL
                }) {
                    BrewSessionLiveRegion.ASSERTIVE
                } else {
                    BrewSessionLiveRegion.POLITE
                },
                status = runtime.status,
                stageProgress = stageProgress,
                currentStageAction = presentedCurrentStage?.action,
                currentStageContentId = presentedCurrentStage?.contentId,
                totalActiveElapsedMillis = totalElapsed,
                currentStageElapsedMillis = presentedCurrentStage?.elapsedActiveMillis,
                countdownRemainingMillis = presentedCurrentStage?.completion?.countdownRemainingMillis(),
                safetyMessages = safetyMessages,
            ),
        )
    }

    private fun currentStagePresentation(
        currentStage: CompiledBrewStage?,
        currentProgress: StageRuntimeProgress?,
        elapsedDelta: Long,
        totalElapsed: Long,
        safetyMessages: List<StageSafetyMessage>,
    ): CurrentBrewStagePresentation? {
        if (currentStage == null || currentProgress == null) return null
        val elapsed = saturatingAdd(currentProgress.elapsedActiveMillis, elapsedDelta)
        val constraint = currentStage.definition.advanceConstraint
        return CurrentBrewStagePresentation(
            stageInstanceId = currentStage.instanceId,
            action = currentStage.definition.action,
            contentId = currentStage.definition.contentId,
            instructionAssetId = currentStage.definition.instructionAssetId,
            requiresIllustration = currentStage.definition.requiresIllustration,
            runStatus = currentProgress.status,
            elapsedActiveMillis = elapsed,
            completion = completionPresentation(
                completionMode = currentStage.definition.completionMode,
                actuals = currentProgress.actuals,
                elapsedActiveMillis = elapsed,
            ),
            advanceConstraint = BrewStageAdvanceConstraintPresentation(
                stageRemainingMillis = constraint.notBeforeStageElapsedMillis
                    ?.let { boundary -> remaining(boundary, elapsed) }
                    ?: 0L,
                brewRemainingMillis = constraint.notBeforeBrewElapsedMillis
                    ?.let { boundary -> remaining(boundary, totalElapsed) }
                    ?: 0L,
            ),
            safetyMessages = safetyMessages,
            referenceCues = referenceCuePresentations(currentStage.definition.referenceTargets),
        )
    }

    private fun referenceCuePresentations(
        targets: StageReferenceTargets,
    ): List<BrewStageReferenceCuePresentation> = buildList {
        targets.timeTargets.forEach { target ->
            add(
                BrewStageReferenceCuePresentation.Time(
                    reference = target.reference,
                    qualifier = target.qualifier,
                    minimumMillis = target.minimumMillis,
                    maximumMillis = target.maximumMillis,
                ),
            )
        }
        targets.massTargets.forEach { target ->
            add(
                BrewStageReferenceCuePresentation.Mass(
                    role = target.role,
                    reference = target.reference,
                    qualifier = target.qualifier,
                    minimumGrams = target.minimumGrams,
                    maximumGrams = target.maximumGrams,
                ),
            )
        }
        targets.temperatureTarget?.let { target ->
            add(
                BrewStageReferenceCuePresentation.Temperature(
                    qualifier = target.qualifier,
                    minimumC = target.minimumC,
                    maximumC = target.maximumC,
                ),
            )
        }
    }

    private fun runtimeIssue(runtime: SessionRuntimeState): SessionRuntimePresentationIssue? = when {
        runtime.stagePlan.stages.isEmpty() -> SessionRuntimePresentationIssue.EMPTY_STAGE_PLAN
        runtime.stageProgress.size != runtime.stagePlan.stages.size -> {
            SessionRuntimePresentationIssue.STAGE_PROGRESS_COUNT_MISMATCH
        }

        runtime.totalActiveElapsedMillis < 0L -> {
            SessionRuntimePresentationIssue.NEGATIVE_TOTAL_ELAPSED_TIME
        }

        runtime.revision < 0L -> SessionRuntimePresentationIssue.NEGATIVE_REVISION
        runtime.stageProgress.any { progress -> progress.elapsedActiveMillis < 0L } -> {
            SessionRuntimePresentationIssue.NEGATIVE_STAGE_ELAPSED_TIME
        }

        runtime.currentStageIndex != null && runtime.currentStageIndex !in runtime.stagePlan.stages.indices -> {
            SessionRuntimePresentationIssue.INVALID_CURRENT_STAGE_INDEX
        }

        runtime.status == BrewSessionStatus.READY &&
            runtime.currentProgress?.status != StageRunStatus.PENDING -> {
            SessionRuntimePresentationIssue.READY_STAGE_NOT_PENDING
        }

        runtime.status in ACTIVE_SESSION_STATUSES &&
            runtime.currentProgress?.status != StageRunStatus.ACTIVE -> {
            SessionRuntimePresentationIssue.ACTIVE_SESSION_STAGE_NOT_ACTIVE
        }

        runtime.status == BrewSessionStatus.COMPLETED && runtime.currentStageIndex != null -> {
            SessionRuntimePresentationIssue.COMPLETED_SESSION_HAS_CURRENT_STAGE
        }

        else -> null
    }

    private fun runningWallClockDelta(
        runtime: SessionRuntimeState,
        nowWallClockMillis: Long?,
    ): Long {
        if (runtime.status != BrewSessionStatus.RUNNING || nowWallClockMillis == null) return 0L
        val anchor = runtime.activeClockAnchor ?: return 0L
        return (nowWallClockMillis - anchor.wallClockMillis).coerceAtLeast(0L)
    }

    private fun completionPresentation(
        completionMode: StageCompletionMode,
        actuals: StageActuals,
        elapsedActiveMillis: Long,
    ): BrewStageCompletionPresentation = when (completionMode) {
        StageCompletionMode.Manual -> BrewStageCompletionPresentation.Manual
        StageCompletionMode.Immediate -> BrewStageCompletionPresentation.Immediate
        is StageCompletionMode.Countdown -> BrewStageCompletionPresentation.Countdown(
            targetElapsedMillis = completionMode.durationMillis,
            remainingMillis = remaining(completionMode.durationMillis, elapsedActiveMillis),
        )

        is StageCompletionMode.ElapsedRange -> BrewStageCompletionPresentation.ElapsedRange(
            minimumElapsedMillis = completionMode.minimumMillis,
            maximumElapsedMillis = completionMode.maximumMillis,
            minimumRemainingMillis = remaining(completionMode.minimumMillis, elapsedActiveMillis),
            maximumRemainingMillis = remaining(completionMode.maximumMillis, elapsedActiveMillis),
        )

        is StageCompletionMode.AddedAmount -> actualValuePresentation(
            inputKind = StageActualInputKind.ADDED_AMOUNT_GRAMS,
            targetGrams = completionMode.targetGrams,
            recordedGrams = actuals.addedAmountGrams,
        )

        is StageCompletionMode.CumulativeAmount -> actualValuePresentation(
            inputKind = StageActualInputKind.CUMULATIVE_AMOUNT_GRAMS,
            targetGrams = completionMode.targetGrams,
            recordedGrams = actuals.cumulativeAmountGrams,
        )

        is StageCompletionMode.BeverageYield -> actualValuePresentation(
            inputKind = StageActualInputKind.BEVERAGE_YIELD_GRAMS,
            targetGrams = completionMode.targetGrams,
            recordedGrams = actuals.beverageYieldGrams,
        )

        is StageCompletionMode.ObservedEvent -> BrewStageCompletionPresentation.ObservedEvent(
            observationId = completionMode.observationId,
            alreadyRecorded = completionMode.observationId in actuals.observations,
        )

        is StageCompletionMode.ExternalMarker -> BrewStageCompletionPresentation.ExternalMarker(
            markerId = completionMode.markerId,
            alreadyRecorded = completionMode.markerId in actuals.markers,
        )
    }

    private fun actualValuePresentation(
        inputKind: StageActualInputKind,
        targetGrams: Double,
        recordedGrams: Double?,
    ): BrewStageCompletionPresentation.ActualValue = BrewStageCompletionPresentation.ActualValue(
        inputKind = inputKind,
        targetGrams = targetGrams,
        recordedGrams = recordedGrams,
        remainingGrams = (targetGrams - (recordedGrams ?: 0.0)).coerceAtLeast(0.0),
    )

    private fun actionAvailability(
        runtime: SessionRuntimeState,
        currentStage: CurrentBrewStagePresentation?,
    ): BrewSessionActionAvailability {
        val activeStage = currentStage?.runStatus == StageRunStatus.ACTIVE
        val canOperate = runtime.status in ACTIVE_SESSION_STATUSES && activeStage
        val canManualAdvance = canOperate &&
            currentStage.completion.allowsManualAdvance() &&
            currentStage.advanceConstraint.isSatisfied
        return BrewSessionActionAvailability(
            canStart = runtime.status == BrewSessionStatus.READY &&
                currentStage?.runStatus == StageRunStatus.PENDING,
            canPause = runtime.status == BrewSessionStatus.RUNNING && activeStage,
            canResume = runtime.status == BrewSessionStatus.PAUSED && activeStage,
            canManualAdvance = canManualAdvance,
            canSkip = canOperate && runtime.currentStage?.definition?.isSkippable == true,
            canCancel = canOperate,
            canFinish = canManualAdvance &&
                runtime.currentStageIndex == runtime.stagePlan.stages.lastIndex,
            canRecordActual = canOperate,
        )
    }

    private fun BrewStageCompletionPresentation.allowsManualAdvance(): Boolean = when (this) {
        BrewStageCompletionPresentation.Manual -> true
        is BrewStageCompletionPresentation.ElapsedRange -> minimumRemainingMillis == 0L
        else -> false
    }

    private fun BrewStageCompletionPresentation.countdownRemainingMillis(): Long? = when (this) {
        is BrewStageCompletionPresentation.Countdown -> remainingMillis
        is BrewStageCompletionPresentation.ElapsedRange -> maximumRemainingMillis
        else -> null
    }

    private fun remaining(target: Long, elapsed: Long): Long = when {
        elapsed >= target -> 0L
        else -> target - elapsed
    }

    private fun saturatingAdd(left: Long, right: Long): Long = when {
        right <= 0L -> left
        left > Long.MAX_VALUE - right -> Long.MAX_VALUE
        else -> left + right
    }

    private val ACTIVE_SESSION_STATUSES = setOf(BrewSessionStatus.RUNNING, BrewSessionStatus.PAUSED)
    private val FINISHED_STAGE_STATUSES = setOf(StageRunStatus.COMPLETED, StageRunStatus.SKIPPED)
}
