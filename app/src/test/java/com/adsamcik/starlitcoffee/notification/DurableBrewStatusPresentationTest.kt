package com.adsamcik.starlitcoffee.notification

import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionTestFixtures
import com.adsamcik.starlitcoffee.domain.brewing.session.ActiveClockAnchor
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAdvanceConstraint
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurableBrewStatusPresentationTest {
    @Test
    fun `countdown anchors to the real stage boundary without advancing the session`() {
        val runtime = runtime(StageCompletionMode.Countdown(45_000L))
        val before = requireNotNull(durableBrewStatusPresentation(runtime, 11_000L))
        assertEquals(DurableBrewStatusClock.COUNTDOWN, before.clock)
        assertEquals(46_000L, before.chronometerWallClockMillis)
        val reached = requireNotNull(durableBrewStatusPresentation(runtime, 46_000L))
        assertEquals(DurableBrewStatusClock.TIMING_REACHED, reached.clock)
        assertNull(reached.chronometerWallClockMillis)
        assertEquals(0L, runtime.totalActiveElapsedMillis)
        assertEquals(0, runtime.currentStageIndex)
    }

    @Test
    fun `many hour countdown uses the same physical origin`() {
        val duration = 14 * 60 * 60 * 1_000L
        val runtime = runtime(StageCompletionMode.Countdown(duration))
        assertEquals(1_000L + duration,
            durableBrewStatusPresentation(runtime, duration / 2)?.chronometerWallClockMillis)
    }

    @Test
    fun `source advance floor remains later than the completion timer`() {
        val runtime = runtime(StageCompletionMode.Countdown(10_000L),
            StageAdvanceConstraint(notBeforeBrewElapsedMillis = 30_000L))
        assertEquals(31_000L, durableBrewStatusPresentation(runtime, 16_000L)?.chronometerWallClockMillis)
    }

    @Test
    fun `range shares the minimum cue with the app strip without declaring completion`() {
        val runtime = runtime(StageCompletionMode.ElapsedRange(10_000L, 30_000L))
        assertEquals(11_000L, durableBrewStatusPresentation(runtime, 6_000L)?.chronometerWallClockMillis)
        assertEquals(DurableBrewStatusClock.TIMING_REACHED, durableBrewStatusPresentation(runtime, 16_000L)?.clock)
        assertEquals(BrewSessionStatus.RUNNING, runtime.status)
    }

    @Test
    fun `espresso yield and informational recipe time never create a countdown`() {
        val initial = runtime(StageCompletionMode.BeverageYield(36.0))
        val stage = initial.stagePlan.stages.single()
        val runtime = initial.copy(stagePlan = initial.stagePlan.copy(stages = listOf(stage.copy(
            definition = stage.definition.copy(referenceTargets = StageReferenceTargets(timeTargets = listOf(
                StageTimeTarget(reference = StageTimeReference.STAGE_DURATION, id = StageTargetId("shot_time"),
                    qualifier = StageTargetQualifier.STARTING_POINT, minimumMillis = 25_000L, maximumMillis = 35_000L),
            ))),
        ))))
        val presentation = requireNotNull(durableBrewStatusPresentation(runtime, 61_000L))
        assertEquals(DurableBrewStatusClock.ELAPSED, presentation.clock)
        assertEquals(1_000L, presentation.chronometerWallClockMillis)
        assertNull(runtime.currentProgress?.actuals?.beverageYieldGrams)
    }

    @Test
    fun `ready paused ended and damaged clocks have no running notification`() {
        val initial = runtime(StageCompletionMode.Manual)
        listOf(BrewSessionStatus.READY, BrewSessionStatus.PAUSED, BrewSessionStatus.COMPLETED,
            BrewSessionStatus.CANCELLED).forEach { status ->
            assertNull(durableBrewStatusPresentation(initial.copy(status = status), 6_000L))
        }
        assertNull(durableBrewStatusPresentation(initial.copy(stageProgress = emptyList()), 6_000L))
    }

    @Test
    fun `deadline saturates without overflowing into the past`() {
        val runtime = runtime(StageCompletionMode.Countdown(100_000L)).copy(
            activeClockAnchor = ActiveClockAnchor(null, Long.MAX_VALUE - 5L))
        assertEquals(Long.MAX_VALUE, durableBrewStatusPresentation(runtime, Long.MAX_VALUE - 1L)
            ?.chronometerWallClockMillis)
    }

    private fun runtime(mode: StageCompletionMode,
        constraint: StageAdvanceConstraint = StageAdvanceConstraint()): SessionRuntimeState {
        val initialPlan = ActiveBrewSessionTestFixtures.plan(mode, alertOnStart = false)
        val stage = initialPlan.stages.single()
        val plan = initialPlan.copy(stages = listOf(stage.copy(
            definition = stage.definition.copy(advanceConstraint = constraint))))
        return SessionReducer.reduce(SessionRuntimeState.create(SessionId("status-clock"), plan),
            SessionEvent.Start(), SessionClockReading(100L, 1_000L)).state
    }
}
