package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserBrewTimerTest {
    @Test
    fun `wall clock changes retain physical elapsed and replace the remaining deadline`() {
        val started = transition(initial(), SessionEvent.Start(), 0L)
        val timer = transition(started, timerEvent(started, 60_000L), 10_000L)
        val adjusted = SessionReducer.reduce(timer, SessionEvent.Reconcile(
            useMonotonicClock = true, rescheduleDeadline = true), SessionClockReading(20_000L, 900_000L)).state
        assertEquals(20_000L, adjusted.totalActiveElapsedMillis)
        assertEquals(940_000L, adjusted.userTimer!!.deadlineAtWallClockMillis)
        assertEquals(1_000L, adjusted.userTimer.originWallClockMillis)
        assertFalse(adjusted.userTimer.reached)
        assertTrue(adjusted.pendingEffects.any { it is PendingSessionEffect.CancelStageDeadline })
        assertEquals(940_000L, adjusted.pendingEffects.filterIsInstance<PendingSessionEffect.ScheduleTimerDeadline>()
            .single().dueAtWallClockMillis)
    }
    @Test
    fun `editing retains physical origin and invalidates the old alarm and cue`() {
        val started = transition(initial(), SessionEvent.Start(), 0L)
        val first = transition(started, timerEvent(started, 60_000L), 10_000L)
        assertEquals(61_000L, first.userTimer?.deadlineAtWallClockMillis)
        val reached = transition(first, SessionEvent.Tick(), 60_000L)
        assertTrue(reached.userTimer!!.reached)
        assertEquals(0, reached.currentStageIndex)
        val edited = transition(reached, timerEvent(reached, 120_000L), 70_000L)
        assertEquals(1_000L, edited.userTimer!!.originWallClockMillis)
        assertEquals(121_000L, edited.userTimer.deadlineAtWallClockMillis)
        assertEquals(50_000L, edited.userTimer.remainingMillis(70_000L))
        assertFalse(edited.pendingEffects.any { it is PendingSessionEffect.TimerAlert })
        assertEquals(2L, edited.userTimer.revision)
        assertEquals(70_000L, edited.totalActiveElapsedMillis)
    }

    @Test
    fun `guide pause leaves deadline fixed and an explicit reminder still fires once`() {
        val started = transition(initial(), SessionEvent.Start(), 0L)
        val timer = transition(started, timerEvent(started, 60_000L), 0L)
        val paused = transition(timer, SessionEvent.SetGuidancePaused(true), 10_000L)
        assertEquals(timer.userTimer, paused.userTimer)
        val due = transition(paused, SessionEvent.Restore(), 60_000L)
        assertEquals(BrewSessionStatus.RUNNING, due.status)
        assertEquals(0, due.currentStageIndex)
        assertEquals(1, due.pendingEffects.filterIsInstance<PendingSessionEffect.TimerAlert>().size)
        val alert = due.pendingEffects.filterIsInstance<PendingSessionEffect.TimerAlert>().single()
        val acknowledged = transition(due, SessionEvent.AcknowledgeEffect(alert.effectId), 60_000L)
        assertFalse(transition(acknowledged, SessionEvent.Tick(), 65_000L).pendingEffects
            .any { it is PendingSessionEffect.TimerAlert })
    }

    @Test
    fun `physical pause suspends the reminder and resume retains elapsed`() {
        val started = transition(initial(), SessionEvent.Start(), 0L)
        val timer = transition(started, timerEvent(started, 60_000L), 0L)
        val paused = transition(timer, SessionEvent.Pause(), 20_000L)
        assertNull(paused.userTimer?.deadlineAtWallClockMillis)
        assertTrue(paused.pendingEffects.any { it is PendingSessionEffect.CancelStageDeadline &&
            it.scheduleToken == timer.userTimer!!.scheduleToken(timer.sessionId) })
        val resumed = transition(paused, SessionEvent.Resume(), 100_000L)
        assertEquals(141_000L, resumed.userTimer?.deadlineAtWallClockMillis)
        assertEquals(20_000L, resumed.totalActiveElapsedMillis)
    }

    @Test
    fun `stage completion removes its reminder without completing the next stage`() {
        val started = transition(initial(), SessionEvent.Start(), 0L)
        val timer = transition(started, timerEvent(started, 60_000L), 0L)
        val next = transition(timer, SessionEvent.ManualAdvance(), 20_000L)
        assertEquals(1, next.currentStageIndex)
        assertNull(next.userTimer)
        assertTrue(next.pendingEffects.any { it is PendingSessionEffect.CancelStageDeadline })
        assertFalse(next.pendingEffects.any { it is PendingSessionEffect.ScheduleTimerDeadline })
        assertEquals(1L, next.timerRevision)
        val staleEdit = transition(next, timerEvent(timer, 120_000L), 25_000L)
        assertNull(staleEdit.userTimer)
        val newTimer = transition(staleEdit, timerEvent(staleEdit, 30_000L), 25_000L)
        assertEquals(2L, newTimer.userTimer?.revision)
    }

    @Test
    fun `removal cancellation and invalid durations never fabricate completion`() {
        val started = transition(initial(), SessionEvent.Start(), 0L)
        assertNull(transition(started, timerEvent(started, Long.MAX_VALUE), 0L).userTimer)
        val timer = transition(started, timerEvent(started, 60_000L), 0L)
        val removed = transition(timer, timerEvent(timer, null), 5_000L)
        assertNull(removed.userTimer)
        assertEquals(BrewSessionStatus.RUNNING, removed.status)
        val cancelled = transition(timer, SessionEvent.Cancel(), 5_000L)
        assertNull(cancelled.userTimer)
        assertFalse(cancelled.pendingEffects.any { it is PendingSessionEffect.TimerAlert })
    }

    private fun timerEvent(state: SessionRuntimeState, duration: Long?) =
        SessionEvent.SetTimerTarget(state.currentStage!!.instanceId, duration)

    private fun transition(state: SessionRuntimeState, event: SessionEvent, elapsed: Long) =
        SessionReducer.reduce(state, event, SessionClockReading(elapsed, 1_000L + elapsed)).state

    private fun initial(): SessionRuntimeState = SessionRuntimeState.create(SessionId("timer-test"),
        CompiledStagePlan(StagePlanId("manual_timer"), 1, listOf("steep", "filter").mapIndexed { index, id ->
            val definition = BrewStageDefinition(StageId(id), BrewStageAction.STEEP, StageContentId(id),
                completionMode = StageCompletionMode.Manual)
            CompiledBrewStage(StageInstanceId(definition.id, index + 1), definition)
        }))
}
