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
        assertEquals(1, matching.state.currentStageIndex)
        assertEquals(StageCompletionKind.OBSERVED, matching.state.stageProgress[0].completionKind)
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
    ): BrewStageDefinition = BrewStageDefinition(
        id = StageId(id),
        action = BrewStageAction.CUSTOM,
        contentId = StageContentId("${id}_content"),
        completionMode = completion,
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
