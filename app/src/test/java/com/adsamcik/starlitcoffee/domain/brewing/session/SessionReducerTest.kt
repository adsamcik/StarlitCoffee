package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {

    @Test
    fun `countdown completes into next stage and persists an ordered outbox`() {
        val state = session(
            stage("bloom", StageCompletionMode.Countdown(30_000L), alertOnStart = true),
            stage("pour", StageCompletionMode.Manual),
        )

        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)
        assertTrue(started.effects.first() is SessionEffect.PersistRuntime)
        assertTrue(
            started.state.pendingEffects.any { it is PendingSessionEffect.ScheduleStageDeadline },
        )

        val elapsed = reduce(started.state, SessionEvent.Tick(), monotonic = 30_000L, wall = 31_000L)

        assertEquals(BrewSessionStatus.RUNNING, elapsed.state.status)
        assertEquals(1, elapsed.state.currentStageIndex)
        assertEquals(StageRunStatus.COMPLETED, elapsed.state.stageProgress[0].status)
        assertEquals(StageCompletionKind.AUTOMATIC, elapsed.state.stageProgress[0].completionKind)
        assertTrue(
            elapsed.state.pendingEffects.any {
                it is PendingSessionEffect.StageAlert && it.kind == StageAlertKind.COMPLETED
            },
        )
    }

    @Test
    fun `pause resume uses monotonic time and excludes paused time`() {
        val state = session(stage("pour", StageCompletionMode.Manual))
        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)
        val advanced = reduce(started.state, SessionEvent.Tick(), monotonic = 5_000L, wall = 6_000L)
        val paused = reduce(advanced.state, SessionEvent.Pause(), monotonic = 5_000L, wall = 6_000L)
        val resumed = reduce(paused.state, SessionEvent.Resume(), monotonic = 100_000L, wall = 7_000L)
        val completed = reduce(resumed.state, SessionEvent.Tick(), monotonic = 103_000L, wall = 10_000L)

        assertEquals(BrewSessionStatus.RUNNING, completed.state.status)
        assertEquals(8_000L, completed.state.totalActiveElapsedMillis)
        assertEquals(8_000L, completed.state.stageProgress.single().elapsedActiveMillis)
    }

    @Test
    fun `restore reconciles forward wall time and clamps a backward wall clock`() {
        val state = session(stage("steep", StageCompletionMode.Manual))
        val started = reduce(state, SessionEvent.Start(), monotonic = 100L, wall = 1_000L)

        val restored = reduce(started.state, SessionEvent.Restore(), monotonic = 5L, wall = 61_000L)
        assertEquals(60_000L, restored.state.totalActiveElapsedMillis)
        assertEquals(ClockReconciliationKind.RESTORE_WALL_CLOCK, restored.state.lastClockReconciliation?.kind)

        val backward = reduce(restored.state, SessionEvent.Reconcile(), monotonic = 6L, wall = 600L)
        assertEquals(60_000L, backward.state.totalActiveElapsedMillis)
        assertEquals(
            ClockReconciliationKind.WALL_CLOCK_BACKWARD_CLAMPED,
            backward.state.lastClockReconciliation?.kind,
        )
    }

    @Test
    fun `matching observed event completes the active observed-event stage`() {
        val state = session(
            stage(
                "wait_for_flow",
                StageCompletionMode.ObservedEvent(StageObservationId("first_drip")),
            ),
            stage("serve", StageCompletionMode.Manual),
        )
        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)
        val nonMatching = reduce(
            started.state,
            SessionEvent.RecordObservation(StageObservationId("last_drip")),
            monotonic = 1L,
            wall = 1_001L,
        )
        val matching = reduce(
            nonMatching.state,
            SessionEvent.RecordObservation(StageObservationId("first_drip")),
            monotonic = 2L,
            wall = 1_002L,
        )

        assertEquals(0, nonMatching.state.currentStageIndex)
        assertTrue(
            StageObservationId("last_drip") in
                nonMatching.state.stageProgress[0].actuals.observations,
        )
        assertEquals(1, matching.state.currentStageIndex)
        assertEquals(StageCompletionKind.OBSERVED, matching.state.stageProgress[0].completionKind)
    }

    @Test
    fun `manual advance cannot bypass a source minimum time`() {
        val state = session(
            stage(
                id = "bloom",
                completion = StageCompletionMode.Manual,
                advanceConstraint = StageAdvanceConstraint(notBeforeStageElapsedMillis = 30_000L),
            ),
            stage("pour", StageCompletionMode.Manual),
        )
        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)
        val early = reduce(started.state, SessionEvent.ManualAdvance(), monotonic = 29_999L, wall = 30_999L)
        val allowed = reduce(early.state, SessionEvent.ManualAdvance(), monotonic = 30_000L, wall = 31_000L)

        assertEquals(0, early.state.currentStageIndex)
        assertEquals(1, allowed.state.currentStageIndex)
        assertEquals(StageCompletionKind.MANUAL, allowed.state.stageProgress[0].completionKind)
    }

    @Test
    fun `early source observation is retained and completes when its boundary arrives`() {
        val observationId = StageObservationId("bloom_finished")
        val state = session(
            stage(
                id = "bloom",
                completion = StageCompletionMode.ObservedEvent(observationId),
                advanceConstraint = StageAdvanceConstraint(notBeforeBrewElapsedMillis = 30_000L),
            ),
            stage("pour", StageCompletionMode.Manual),
        )
        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)
        val observedEarly = reduce(
            started.state,
            SessionEvent.RecordObservation(observationId),
            monotonic = 20_000L,
            wall = 21_000L,
        )
        val boundary = reduce(observedEarly.state, SessionEvent.Tick(), monotonic = 30_000L, wall = 31_000L)

        assertEquals(0, observedEarly.state.currentStageIndex)
        assertTrue(observationId in observedEarly.state.stageProgress[0].actuals.observations)
        assertEquals(1, boundary.state.currentStageIndex)
        assertEquals(StageCompletionKind.OBSERVED, boundary.state.stageProgress[0].completionKind)
    }

    @Test
    fun `measurements reject unusable values and preserve ordered final effects`() {
        val state = session(stage("pour", StageCompletionMode.AddedAmount(10.0)))
        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)

        val invalid = reduce(
            started.state,
            SessionEvent.RecordActual(StageActualValue.AddedAmount(Double.NaN)),
            monotonic = 1L,
            wall = 1_001L,
        )
        assertTrue(invalid.wasIgnored)
        assertEquals(started.state, invalid.state)
        assertTrue(invalid.effects.isEmpty())

        val mismatched = reduce(
            started.state,
            SessionEvent.RecordActual(StageActualValue.BeverageYield(100.0)),
            monotonic = 2L,
            wall = 1_002L,
        )
        assertEquals(0, mismatched.state.currentStageIndex)
        assertEquals(100.0, mismatched.state.stageProgress[0].actuals.beverageYieldGrams)
        assertEquals(null, mismatched.state.stageProgress[0].actuals.addedAmountGrams)

        val belowTarget = reduce(
            mismatched.state,
            SessionEvent.RecordActual(StageActualValue.AddedAmount(9.9)),
            monotonic = 3L,
            wall = 1_003L,
        )
        assertEquals(0, belowTarget.state.currentStageIndex)
        assertEquals(null, belowTarget.state.stageProgress[0].completionKind)

        val completed = reduce(
            belowTarget.state,
            SessionEvent.RecordActual(StageActualValue.AddedAmount(10.0)),
            monotonic = 4L,
            wall = 1_004L,
        )

        assertEquals(BrewSessionStatus.COMPLETED, completed.state.status)
        assertEquals(StageCompletionKind.MEASURED, completed.state.stageProgress[0].completionKind)
        assertEquals(100.0, completed.state.stageProgress[0].actuals.beverageYieldGrams)
        assertEquals(10.0, completed.state.stageProgress[0].actuals.addedAmountGrams)
        assertEquals(3, completed.effects.size)
        assertTrue(completed.effects[0] is SessionEffect.PersistRuntime)
        assertTrue(completed.effects[1] is PendingSessionEffect.StageAlert)
        assertTrue(completed.effects[2] is PendingSessionEffect.FinalizeBrewLog)
    }

    @Test
    fun `matching marker can complete a paused stage without scheduling the next deadline`() {
        val markerId = StageMarkerId("release_complete")
        val state = session(
            stage("release", StageCompletionMode.ExternalMarker(markerId)),
            stage("drawdown", StageCompletionMode.Countdown(30_000L)),
        )
        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)
        val paused = reduce(started.state, SessionEvent.Pause(), monotonic = 0L, wall = 1_001L)

        val marked = reduce(
            paused.state,
            SessionEvent.RecordMarker(markerId),
            monotonic = 1L,
            wall = 1_002L,
        )

        assertEquals(BrewSessionStatus.PAUSED, marked.state.status)
        assertEquals(1, marked.state.currentStageIndex)
        assertEquals(StageCompletionKind.EXTERNAL_MARKER, marked.state.stageProgress[0].completionKind)
        assertTrue(markerId in marked.state.stageProgress[0].actuals.markers)
        assertEquals(StageRunStatus.ACTIVE, marked.state.stageProgress[1].status)
        assertEquals(null, marked.state.activeClockAnchor)
        assertFalse(
            marked.state.pendingEffects.any { it is PendingSessionEffect.ScheduleStageDeadline },
        )
    }

    @Test
    fun `finish creates one log effect and remains idempotent after acknowledgement`() {
        val state = session(stage("serve", StageCompletionMode.Manual))
        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)
        val finished = reduce(
            started.state,
            SessionEvent.Finish(SessionEventId("finish_once")),
            monotonic = 1L,
            wall = 1_001L,
        )
        val logEffect = finished.state.pendingEffects.filterIsInstance<PendingSessionEffect.FinalizeBrewLog>()
            .single()
        val acknowledged = reduce(
            finished.state,
            SessionEvent.AcknowledgeEffect(logEffect.effectId, SessionEventId("ack_log")),
            monotonic = 2L,
            wall = 1_002L,
        )
        val retriedFinish = reduce(
            acknowledged.state,
            SessionEvent.Finish(SessionEventId("finish_once")),
            monotonic = 3L,
            wall = 1_003L,
        )

        assertEquals(BrewSessionStatus.COMPLETED, finished.state.status)
        assertFalse(acknowledged.state.pendingEffects.any { it is PendingSessionEffect.FinalizeBrewLog })
        assertTrue(retriedFinish.wasIgnored)
        assertTrue(retriedFinish.effects.isEmpty())
    }

    @Test
    fun `restore replays the persisted pending outbox with the same effect id`() {
        val state = session(stage("prepare", StageCompletionMode.Manual, alertOnStart = true))
        val started = reduce(state, SessionEvent.Start(), monotonic = 0L, wall = 1_000L)
        val alert = started.state.pendingEffects.filterIsInstance<PendingSessionEffect.StageAlert>().single()

        val restored = reduce(started.state, SessionEvent.Restore(), monotonic = 0L, wall = 1_000L)
        val replayed = restored.effects.filterIsInstance<PendingSessionEffect.StageAlert>().single()

        assertEquals(alert.effectId, replayed.effectId)
        assertTrue(restored.effects.first() is SessionEffect.PersistRuntime)
    }

    private fun session(vararg definitions: BrewStageDefinition): SessionRuntimeState =
        SessionRuntimeState.create(
            sessionId = SessionId("session-1"),
            stagePlan = CompiledStagePlan(
                id = StagePlanId("test_plan"),
                version = 1,
                stages = definitions.mapIndexed { index, definition ->
                    CompiledBrewStage(StageInstanceId(definition.id, index + 1), definition)
                },
            ),
        )

    private fun stage(
        id: String,
        completion: StageCompletionMode,
        alertOnStart: Boolean = false,
        advanceConstraint: StageAdvanceConstraint = StageAdvanceConstraint(),
    ): BrewStageDefinition = BrewStageDefinition(
        id = StageId(id),
        action = BrewStageAction.CUSTOM,
        contentId = StageContentId("${id}_content"),
        completionMode = completion,
        advanceConstraint = advanceConstraint,
        alertPolicy = StageAlertPolicy(alertOnStart = alertOnStart),
    )

    private fun reduce(
        state: SessionRuntimeState,
        event: SessionEvent,
        monotonic: Long,
        wall: Long,
    ): SessionTransition = SessionReducer.reduce(
        state = state,
        event = event,
        now = SessionClockReading(monotonic, wall),
    )
}
