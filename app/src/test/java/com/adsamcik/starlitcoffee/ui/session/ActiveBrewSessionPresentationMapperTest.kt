package com.adsamcik.starlitcoffee.ui.session

import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionEntityMapper
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionRestoreResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewLogPresentationContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.session.SessionExecutionContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageActualValue
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveBrewSessionPresentationMapperTest {

    @Test
    fun `restored countdown session exposes current stage timers safety and semantic accessibility`() {
        val plan = plan(
            stage(
                id = "bloom",
                action = BrewStageAction.BLOOM,
                contentId = "bloom_instruction",
                completion = StageCompletionMode.Countdown(30_000L),
                isSkippable = true,
                safety = listOf(
                    StageSafetyMessage("warm_vessel", StageSafetySeverity.ADVICE),
                    StageSafetyMessage("hot_liquid", StageSafetySeverity.WARNING),
                    StageSafetyMessage("burn_risk", StageSafetySeverity.CRITICAL),
                ),
            ),
            stage(
                id = "pour",
                action = BrewStageAction.POUR,
                contentId = "pour_instruction",
                completion = StageCompletionMode.Manual,
            ),
        )
        val running = startedRuntime(plan, "countdown-session", startedAt = 1_000L)
        val entity = ActiveBrewSessionEntityMapper.create(
            recipe = recipe(),
            runtime = running,
            executionContext = executionContext(),
            nowWallClockMillis = 1_000L,
        )

        val presentation = ActiveBrewSessionPresentationMapper.map(
            restored = ActiveBrewSessionEntityMapper.restore(entity),
            nowWallClockMillis = 11_000L,
        ) as ActiveBrewSessionPresentation.Available
        val stage = presentation.currentStage!!
        val countdown = stage.completion as BrewStageCompletionPresentation.Countdown

        assertEquals("countdown-session", presentation.sessionId)
        assertEquals(BrewSessionStatus.RUNNING, presentation.status)
        assertEquals(10_000L, presentation.totalActiveElapsedMillis)
        assertEquals(0, presentation.stageProgress.completedStageCount)
        assertEquals(0, presentation.stageProgress.skippedStageCount)
        assertEquals(0, presentation.stageProgress.finishedStageCount)
        assertEquals(2, presentation.stageProgress.totalStageCount)
        assertEquals(1, presentation.stageProgress.currentStageNumber)
        assertEquals(StageId("bloom"), stage.stageId)
        assertEquals(BrewStageAction.BLOOM, stage.action)
        assertEquals(StageContentId("bloom_instruction"), stage.contentId)
        assertEquals(10_000L, stage.elapsedActiveMillis)
        assertEquals(30_000L, countdown.targetElapsedMillis)
        assertEquals(20_000L, countdown.remainingMillis)
        assertEquals(
            listOf("warm_vessel", "hot_liquid", "burn_risk"),
            presentation.safetyMessages.map(StageSafetyMessage::code),
        )
        assertEquals(presentation.safetyMessages, stage.safetyMessages)
        assertEquals(presentation.safetyMessages, presentation.accessibility.safetyMessages)
        assertEquals(BrewSessionLiveRegion.ASSERTIVE, presentation.accessibility.liveRegion)
        assertEquals(20_000L, presentation.accessibility.countdownRemainingMillis)
        assertTrue(presentation.actions.canPause)
        assertFalse(presentation.actions.canResume)
        assertFalse(presentation.actions.canManualAdvance)
        assertTrue(presentation.actions.canSkip)
        assertTrue(presentation.actions.canCancel)
        assertFalse(presentation.actions.canFinish)
        assertTrue(presentation.actions.canRecordActual)
    }

    @Test
    fun `action flags follow status and the stage completion rule`() {
        val plan = plan(
            stage(
                id = "drawdown",
                action = BrewStageAction.POUR,
                contentId = "drawdown_instruction",
                completion = StageCompletionMode.ElapsedRange(
                    minimumMillis = 5_000L,
                    maximumMillis = 20_000L,
                ),
                isSkippable = true,
            ),
        )
        val initial = SessionRuntimeState.create(SessionId("action-session"), plan)

        val ready = ActiveBrewSessionPresentationMapper.map(initial)
            as ActiveBrewSessionPresentation.Available
        assertTrue(ready.actions.canStart)
        assertFalse(ready.actions.canPause)
        assertFalse(ready.actions.canResume)
        assertFalse(ready.actions.canManualAdvance)
        assertFalse(ready.actions.canSkip)
        assertFalse(ready.actions.canCancel)
        assertFalse(ready.actions.canFinish)
        assertFalse(ready.actions.canRecordActual)

        val running = startedRuntime(plan, "action-session", startedAt = 1_000L)
        val active = ActiveBrewSessionPresentationMapper.map(
            runtime = running,
            nowWallClockMillis = 7_000L,
        ) as ActiveBrewSessionPresentation.Available
        val elapsedRange = active.currentStage!!.completion as BrewStageCompletionPresentation.ElapsedRange

        assertEquals(0L, elapsedRange.minimumRemainingMillis)
        assertEquals(14_000L, elapsedRange.maximumRemainingMillis)
        assertTrue(active.actions.canPause)
        assertTrue(active.actions.canManualAdvance)
        assertTrue(active.actions.canSkip)
        assertTrue(active.actions.canCancel)
        assertTrue(active.actions.canFinish)
        assertTrue(active.actions.canRecordActual)

        val paused = SessionReducer.reduce(
            state = running,
            event = SessionEvent.Pause(),
            now = SessionClockReading(monotonicMillis = 6_000L, wallClockMillis = 7_000L),
        ).state
        val pausedPresentation = ActiveBrewSessionPresentationMapper.map(paused)
            as ActiveBrewSessionPresentation.Available

        assertTrue(pausedPresentation.actions.canResume)
        assertFalse(pausedPresentation.actions.canPause)
        assertTrue(pausedPresentation.actions.canManualAdvance)
        assertTrue(pausedPresentation.actions.canFinish)
    }

    @Test
    fun `actual target keeps its durable recorded and remaining amounts`() {
        val plan = plan(
            stage(
                id = "pour",
                action = BrewStageAction.POUR,
                contentId = "pour_instruction",
                completion = StageCompletionMode.AddedAmount(100.0),
            ),
        )
        val running = startedRuntime(plan, "actual-session", startedAt = 1_000L)
        val recorded = SessionReducer.reduce(
            state = running,
            event = SessionEvent.RecordActual(StageActualValue.AddedAmount(40.0)),
            now = SessionClockReading(monotonicMillis = 0L, wallClockMillis = 1_000L),
        ).state

        val presentation = ActiveBrewSessionPresentationMapper.map(recorded)
            as ActiveBrewSessionPresentation.Available
        val actual = presentation.currentStage!!.completion as BrewStageCompletionPresentation.ActualValue

        assertEquals(StageActualInputKind.ADDED_AMOUNT_GRAMS, actual.inputKind)
        assertEquals(100.0, actual.targetGrams, 0.0)
        assertEquals(40.0, actual.recordedGrams!!, 0.0)
        assertEquals(60.0, actual.remainingGrams, 0.0)
        assertTrue(presentation.actions.canRecordActual)
        assertFalse(presentation.actions.canManualAdvance)
    }

    @Test
    fun `unknown or malformed session data stays unavailable without a fallback`() {
        val unsupported = ActiveBrewSessionPresentationMapper.map(
            ActiveBrewSessionRestoreResult.UnsupportedRecipe(
                schemaVersion = 2,
                rawJson = "{\"schemaVersion\":2}",
            ),
        ) as ActiveBrewSessionPresentation.Unavailable

        assertNull(unsupported.sessionId)
        assertEquals(
            ActiveBrewSessionUnavailableReason.UnsupportedRecipe(2),
            unsupported.reason,
        )

        val malformed = SessionRuntimeState.create(
            sessionId = SessionId("malformed-session"),
            stagePlan = plan(
                stage(
                    id = "brew",
                    action = BrewStageAction.POUR,
                    contentId = "brew_instruction",
                    completion = StageCompletionMode.Manual,
                ),
            ),
        ).copy(
            stageProgress = emptyList(),
        )
        val malformedPresentation = ActiveBrewSessionPresentationMapper.map(malformed)
            as ActiveBrewSessionPresentation.Unavailable

        assertEquals("malformed-session", malformedPresentation.sessionId)
        assertEquals(
            ActiveBrewSessionUnavailableReason.InvalidRuntime(
                SessionRuntimePresentationIssue.STAGE_PROGRESS_COUNT_MISMATCH,
            ),
            malformedPresentation.reason,
        )
    }

    private fun startedRuntime(
        plan: CompiledStagePlan,
        sessionId: String,
        startedAt: Long,
    ): SessionRuntimeState = SessionReducer.reduce(
        state = SessionRuntimeState.create(SessionId(sessionId), plan),
        event = SessionEvent.Start(),
        now = SessionClockReading(monotonicMillis = 0L, wallClockMillis = startedAt),
    ).state

    private fun plan(vararg stages: CompiledBrewStage): CompiledStagePlan = CompiledStagePlan(
        id = StagePlanId("presentation_test"),
        version = 1,
        stages = stages.toList(),
    )

    private fun stage(
        id: String,
        action: BrewStageAction,
        contentId: String,
        completion: StageCompletionMode,
        isSkippable: Boolean = false,
        safety: List<StageSafetyMessage> = emptyList(),
    ): CompiledBrewStage {
        val stageId = StageId(id)
        return CompiledBrewStage(
            instanceId = StageInstanceId(stageId, 1),
            definition = BrewStageDefinition(
                id = stageId,
                action = action,
                contentId = StageContentId(contentId),
                safetyMessages = safety,
                completionMode = completion,
                isSkippable = isSkippable,
            ),
        )
    }

    private fun recipe(): BrewRecipeSnapshotV1 = BrewRecipeSnapshotV1(
        methodFamilyId = "manual_gravity",
        brewerProfileId = "v60_02",
        equipment = EquipmentConfigurationSnapshotV1(brewerProfileId = "v60_02"),
        quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 20.0, brewWaterInputG = 300.0),
        ratioDefinition = RatioDefinitionSnapshotV1(
            numerator = "BREW_WATER_INPUT",
            denominator = "DRY_COFFEE_DOSE",
        ),
        ratioValue = 15.0,
        outputModel = OutputModelSnapshotV1(kind = "BREW_WATER_MINUS_RETENTION"),
    )

    private fun executionContext(): SessionExecutionContextSnapshotV1 = SessionExecutionContextSnapshotV1(
        logPresentation = BrewLogPresentationContextSnapshotV1(
            methodLabel = "V60 02",
            doseG = 20.0,
            waterG = 300.0,
            ratio = 15.0,
        ),
    )
}
