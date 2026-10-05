package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.session.SessionRuntimeSnapshotMapperV1
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.domain.brewing.session.*
import org.junit.Assert.*
import org.junit.Test

class ColdBrewStartFactoryTest {
    @Test
    fun `guided preparation has no extraction clock and steep waits for its actual start`() {
        var state = start(timerOnly = false)
        state = transition(state, SessionEvent.Tick(), 60_000L)
        assertEquals(0L, state.totalActiveElapsedMillis)
        assertFalse(state.hasPhysicalClockStarted)
        state = transition(state, SessionEvent.ManualAdvance(), 60_000L)
        state = transition(state, SessionEvent.ManualAdvance(), 120_000L)
        assertEquals(BrewStageAction.STEEP, state.currentStage!!.definition.action)
        assertEquals(state, transition(state, SessionEvent.ManualAdvance(), 130_000L))
        state = transition(state, SessionEvent.Start(), 180_000L)
        assertEquals(EPOCH + 180_000L, state.startedAtWallClockMillis)
        assertEquals(EPOCH + 180_000L + ColdBrewStartFactory.DEFAULT_DURATION_MILLIS,
            state.userTimer!!.deadlineAtWallClockMillis)
        val due = transition(state, SessionEvent.Restore(), 180_000L + ColdBrewStartFactory.DEFAULT_DURATION_MILLIS)
        assertEquals(BrewStageAction.STEEP, due.currentStage!!.definition.action)
        assertTrue(due.userTimer!!.reached)
        val filter = transition(due, SessionEvent.ManualAdvance(), 180_000L + ColdBrewStartFactory.DEFAULT_DURATION_MILLIS)
        val later = transition(filter, SessionEvent.Tick(), 190_000L + ColdBrewStartFactory.DEFAULT_DURATION_MILLIS)
        assertEquals(ColdBrewStartFactory.DEFAULT_DURATION_MILLIS, later.totalActiveElapsedMillis)
        assertEquals(10_000L, later.currentProgress!!.elapsedActiveMillis)
    }

    @Test
    fun `earlier start retains elapsed and reminder expiry never ends the timer or logs coffee`() {
        val state = start(timerOnly = true, origin = EPOCH - 3_600_000L)
        assertEquals(3_600_000L, state.totalActiveElapsedMillis)
        assertEquals(EPOCH + 13L * 3_600_000L, state.userTimer!!.deadlineAtWallClockMillis)
        val due = transition(state, SessionEvent.Restore(), 13L * 3_600_000L)
        assertEquals(BrewSessionStatus.RUNNING, due.status)
        assertEquals(1, due.pendingEffects.filterIsInstance<PendingSessionEffect.TimerAlert>().size)
        val ended = transition(due, SessionEvent.Finish(), 13L * 3_600_000L)
        assertEquals(BrewSessionStatus.COMPLETED, ended.status)
        assertFalse(ended.pendingEffects.any { it is PendingSessionEffect.FinalizeBrewLog })
        assertTrue(ended.pendingEffects.any { it is PendingSessionEffect.CancelSessionWork })
    }

    @Test
    fun `unknown start persists without a fabricated alarm and accepts a real origin later`() {
        var state = start(timerOnly = true, originKnown = false)
        state = transition(state, SessionEvent.Restore(), 2L * 86_400_000L)
        assertNull(state.startedAtWallClockMillis)
        assertNull(state.userTimer!!.deadlineAtWallClockMillis)
        assertFalse(state.userTimer!!.reached)
        assertFalse(state.pendingEffects.any { it is PendingSessionEffect.TimerAlert })
        val snapshot = SessionRuntimeSnapshotMapperV1.toSnapshot(state)
        val restored = SessionRuntimeSnapshotMapperV1.toDomain(snapshot, state.stagePlan)
        assertEquals(state, restored)
        val identified = transition(restored, SessionEvent.EstablishClockOrigin(
            restored.currentStage!!.instanceId, EPOCH + 2L * 86_400_000L - 3_600_000L), 2L * 86_400_000L)
        assertTrue(identified.isPhysicalOriginKnown)
        assertEquals(3_600_000L, identified.totalActiveElapsedMillis)
        assertEquals(2L, identified.userTimer!!.revision)
        assertEquals(EPOCH + 2L * 86_400_000L + 13L * 3_600_000L,
            identified.userTimer.deadlineAtWallClockMillis)
    }

    @Test
    fun `frozen quantities coffee identity and clock boundaries survive storage`() {
        val request = request(false)
        assertEquals(100.0, request.recipe.quantities.dryCoffeeDoseG, 0.0)
        assertEquals(800.0, request.recipe.quantities.brewWaterInputG!!, 0.0)
        assertEquals(91L, request.executionContext.coffeeBagId)
        assertNull(request.recipe.temperatureC)
        val state = start(false)
        val restored = SessionRuntimeSnapshotMapperV1.toDomain(SessionRuntimeSnapshotMapperV1.toSnapshot(state), state.stagePlan)
        assertEquals(state.physicalClock, restored.physicalClock)
    }

    @Test
    fun `future physical origins cannot start a clock`() {
        val request = request(true, origin = EPOCH + 1L)
        val state = SessionRuntimeState.create(request.sessionId, request.stagePlan).copy(physicalClock = request.physicalClock)
        assertEquals(state, transition(state, SessionEvent.Start(), 0L))
    }

    private fun request(timerOnly: Boolean, originKnown: Boolean = true, origin: Long? = null) =
        ColdBrewStartFactory.create(requireNotNull(CalculatorBrewSessionStartFactory().create(
            CalcUiState(brewMethod = BrewMethod.COLD_BREW, previewDoseG = 100f, previewWaterMl = 800f,
                previewBeverageG = 600f, ratio = 8f, hasValidExpression = true),
            BrewUiState(method = BrewMethod.COLD_BREW), 91L)), timerOnly, originKnown = originKnown,
            originWallClockMillis = origin)

    private fun start(timerOnly: Boolean, originKnown: Boolean = true, origin: Long? = null): SessionRuntimeState {
        val request = request(timerOnly, originKnown, origin)
        return transition(SessionRuntimeState.create(request.sessionId, request.stagePlan)
            .copy(physicalClock = request.physicalClock), SessionEvent.Start(), 0L)
    }

    private fun transition(state: SessionRuntimeState, event: SessionEvent, elapsed: Long) =
        SessionReducer.reduce(state, event, SessionClockReading(elapsed, EPOCH + elapsed)).state

    private companion object { const val EPOCH = 1_000_000_000L }
}
