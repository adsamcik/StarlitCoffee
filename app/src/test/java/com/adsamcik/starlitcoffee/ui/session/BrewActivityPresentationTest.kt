package com.adsamcik.starlitcoffee.ui.session

import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionEntityMapper
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionRestoreResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewLogPresentationContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.session.RestoredActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.SessionExecutionContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAdvanceConstraint
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewActivityPresentationTest {
    @Test
    fun `restored deadline continues across routes without advancing persisted state`() {
        val session = session(StageCompletionMode.Countdown(30_000L))
        val snapshot = session.runtime
        val before = requireNotNull(BrewActivityPresentationMapper.map(session, 11_000L))
        val reached = requireNotNull(BrewActivityPresentationMapper.map(session, 41_000L))
        assertEquals(20_000L, before.timeMillis)
        assertEquals(BrewActivityState.TIME_LEFT, before.state)
        assertEquals(BrewActivityState.TIMING_REACHED, reached.state)
        assertTrue(reached.needsAttention)
        assertEquals(snapshot, session.runtime)
        assertEquals(0, session.runtime.currentStageIndex)
    }

    @Test
    fun `elapsed range exposes minimum boundary without claiming physical completion`() {
        val session = session(StageCompletionMode.ElapsedRange(10_000L, 30_000L))
        assertEquals(BrewActivityState.TIME_LEFT, BrewActivityPresentationMapper.map(session, 6_000L)?.state)
        assertEquals(BrewActivityState.TIMING_REACHED, BrewActivityPresentationMapper.map(session, 16_000L)?.state)
        assertEquals(0, session.runtime.currentStageIndex)
    }

    @Test
    fun `yield target and informational times do not invent a countdown`() {
        val session = session(
            StageCompletionMode.BeverageYield(36.0),
            references = StageReferenceTargets(timeTargets = listOf(StageTimeTarget(
                reference = StageTimeReference.STAGE_DURATION,
                id = StageTargetId("extraction_time"),
                qualifier = StageTargetQualifier.APPROXIMATE,
                minimumMillis = 25_000L,
                maximumMillis = 35_000L,
            ))),
        )
        val presentation = requireNotNull(BrewActivityPresentationMapper.map(session, 61_000L))
        assertEquals(BrewActivityState.ELAPSED, presentation.state)
        assertEquals(60_000L, presentation.timeMillis)
        assertFalse(presentation.needsAttention)
    }

    @Test
    fun `source advance floor cannot be bypassed by an earlier completion timer`() {
        val session = session(
            StageCompletionMode.Countdown(10_000L),
            constraint = StageAdvanceConstraint(notBeforeBrewElapsedMillis = 30_000L),
        )
        val presentation = requireNotNull(BrewActivityPresentationMapper.map(session, 16_000L))
        assertEquals(BrewActivityState.TIME_LEFT, presentation.state)
        assertEquals(15_000L, presentation.timeMillis)
    }

    @Test
    fun `manual stage has elapsed time without invented ready target`() {
        val session = session(StageCompletionMode.Manual)
        val presentation = requireNotNull(BrewActivityPresentationMapper.map(session, 50_401_000L))
        assertEquals(50_400_000L, presentation.timeMillis)
        assertEquals(BrewActivityState.ELAPSED, presentation.state)
    }

    @Test
    fun `pause freezes legacy clock and does not pretend to be a running deadline`() {
        val initial = session(StageCompletionMode.Countdown(30_000L))
        val paused = withRuntime(initial, SessionReducer.reduce(
            initial.runtime, SessionEvent.Pause(), SessionClockReading(6_000L, 6_000L),
        ).state)
        val presentation = requireNotNull(BrewActivityPresentationMapper.map(paused, 99_000L))
        assertEquals(BrewActivityState.PAUSED, presentation.state)
        assertEquals(5_000L, presentation.timeMillis)
    }

    @Test
    fun `completed unlogged brew remains reachable but saved and cancelled brews disappear`() {
        val initial = session(StageCompletionMode.Manual)
        val completed = withRuntime(initial, SessionReducer.reduce(
            initial.runtime, SessionEvent.Finish(), SessionClockReading(6_000L, 6_000L),
        ).state)
        assertEquals(BrewActivityState.COMPLETED, BrewActivityPresentationMapper.map(completed, 99_000L)?.state)
        assertNull(BrewActivityPresentationMapper.map(completed.copy(
            entity = completed.entity.copy(completedLogId = 42L),
        ), 99_000L))
        val cancelled = withRuntime(initial, SessionReducer.reduce(
            initial.runtime, SessionEvent.Cancel(), SessionClockReading(6_000L, 6_000L),
        ).state)
        assertNull(BrewActivityPresentationMapper.map(cancelled, 99_000L))
    }

    @Test
    fun `malformed runtime is not represented as a believable timer`() {
        val initial = session(StageCompletionMode.Manual)
        assertNull(BrewActivityPresentationMapper.map(initial.copy(
            runtime = initial.runtime.copy(stageProgress = emptyList()),
        ), 10_000L))
    }

    @Test
    fun `attention precedes running sessions and focus hides only exact identity`() {
        val running = BrewActivityPresentation("one", "Chemex", BrewStageAction.POUR, BrewActivityState.ELAPSED, 10L)
        val reached = running.copy(sessionId = "two", state = BrewActivityState.TIMING_REACHED)
        assertEquals(listOf(reached, running), BrewActivityPresentationMapper.visible(listOf(running, reached), null))
        assertEquals(listOf(running), BrewActivityPresentationMapper.visible(listOf(running, reached), "two"))
        assertTrue(BrewActivityPresentationMapper.visible(listOf(running), "one").isEmpty())
        assertTrue(BrewActivityPresentationMapper.visible(emptyList(), null).isEmpty())
    }

    private fun session(
        completion: StageCompletionMode,
        references: StageReferenceTargets = StageReferenceTargets(),
        constraint: StageAdvanceConstraint = StageAdvanceConstraint(),
    ): RestoredActiveBrewSession {
        val stageId = StageId("brew")
        val plan = CompiledStagePlan(StagePlanId("activity_test"), version = 1, stages = listOf(CompiledBrewStage(
            StageInstanceId(stageId, 1),
            BrewStageDefinition(
                id = stageId,
                action = BrewStageAction.STEEP,
                contentId = StageContentId("brew_instruction"),
                completionMode = completion,
                referenceTargets = references,
                advanceConstraint = constraint,
            ),
        )))
        val runtime = SessionReducer.reduce(
            SessionRuntimeState.create(SessionId("activity-test"), plan),
            SessionEvent.Start(), SessionClockReading(1_000L, 1_000L),
        ).state
        val recipe = BrewRecipeSnapshotV1(
            methodFamilyId = "manual_gravity", brewerProfileId = "v60_02",
            equipment = EquipmentConfigurationSnapshotV1("v60_02"),
            quantities = BrewQuantitiesSnapshotV1(20.0, brewWaterInputG = 300.0),
            ratioDefinition = RatioDefinitionSnapshotV1("BREW_WATER_INPUT", "DRY_COFFEE_DOSE"),
            ratioValue = 15.0,
            outputModel = OutputModelSnapshotV1(kind = "BREW_WATER_MINUS_RETENTION"),
        )
        val context = SessionExecutionContextSnapshotV1(logPresentation = BrewLogPresentationContextSnapshotV1(
            methodLabel = "My Chemex", doseG = 20.0, waterG = 300.0, ratio = 15.0,
        ))
        return (ActiveBrewSessionEntityMapper.restore(ActiveBrewSessionEntityMapper.create(
            recipe, runtime, context, 1_000L,
        )) as ActiveBrewSessionRestoreResult.Restored).value
    }

    private fun withRuntime(session: RestoredActiveBrewSession, runtime: SessionRuntimeState): RestoredActiveBrewSession =
        session.copy(
            runtime = runtime,
            entity = ActiveBrewSessionEntityMapper.create(session.recipe, runtime, session.executionContext, 6_000L),
        )
}
