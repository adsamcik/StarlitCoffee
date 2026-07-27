package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveBrewSessionEntityMapperTest {

    @Test
    fun `versioned entity round trips all durable documents and retains acknowledged schedule metadata`() {
        val recipe = ActiveBrewSessionTestFixtures.recipe()
        val context = ActiveBrewSessionTestFixtures.executionContext()
        val started = SessionReducer.reduce(
            state = SessionRuntimeState.create(
                sessionId = SessionId("mapper-session"),
                stagePlan = ActiveBrewSessionTestFixtures.plan(
                    completionMode = StageCompletionMode.Countdown(30_000L),
                ),
            ),
            event = SessionEvent.Start(SessionEventId("start")),
            now = SessionClockReading(monotonicMillis = 100L, wallClockMillis = 1_000L),
        ).state
        val schedule = started.pendingEffects.filterIsInstance<PendingSessionEffect.ScheduleStageDeadline>().single()
        val entity = ActiveBrewSessionEntityMapper.create(
            recipe = recipe,
            runtime = started,
            executionContext = context,
            nowWallClockMillis = 1_000L,
        )

        val restored = ActiveBrewSessionEntityMapper.restore(entity) as ActiveBrewSessionRestoreResult.Restored

        assertEquals(recipe, restored.value.recipe)
        assertEquals(started, restored.value.runtime)
        assertEquals(context, restored.value.executionContext)
        assertEquals(schedule.dueAtWallClockMillis, entity.deadlineAtWallClockMillis)
        assertEquals(schedule.scheduleToken, entity.scheduledEventToken)

        val acknowledgedRuntime = SessionReducer.reduce(
            state = started,
            event = SessionEvent.AcknowledgeEffect(
                effectIdToAcknowledge = schedule.effectId,
                eventId = SessionEventId("ack_schedule"),
            ),
            now = SessionClockReading(monotonicMillis = 101L, wallClockMillis = 1_001L),
        ).state
        val afterAcknowledgement = ActiveBrewSessionEntityMapper.update(
            previous = entity,
            recipe = recipe,
            runtime = acknowledgedRuntime,
            executionContext = context,
            nowWallClockMillis = 1_001L,
        )
        val restoredAcknowledgement =
            ActiveBrewSessionEntityMapper.restore(afterAcknowledgement) as ActiveBrewSessionRestoreResult.Restored

        assertEquals(acknowledgedRuntime, restoredAcknowledgement.value.runtime)
        assertEquals(schedule.dueAtWallClockMillis, afterAcknowledgement.deadlineAtWallClockMillis)
        assertEquals(schedule.scheduleToken, afterAcknowledgement.scheduledEventToken)
    }

    @Test
    fun `unsupported or missing durable documents are surfaced without a replacement session`() {
        val initial = SessionRuntimeState.create(
            sessionId = SessionId("unavailable-session"),
            stagePlan = ActiveBrewSessionTestFixtures.plan(),
        )
        val entity = ActiveBrewSessionEntityMapper.create(
            recipe = ActiveBrewSessionTestFixtures.recipe(),
            runtime = initial,
            executionContext = ActiveBrewSessionTestFixtures.executionContext(),
            nowWallClockMillis = 2_000L,
        )

        val unsupported = ActiveBrewSessionEntityMapper.restore(
            entity.copy(recipeSnapshotJson = "{\"schemaVersion\":2}"),
        ) as ActiveBrewSessionRestoreResult.UnsupportedRecipe
        val missingContext = ActiveBrewSessionEntityMapper.restore(
            entity.copy(executionContextSchemaVersion = null, executionContextJson = null),
        ) as ActiveBrewSessionRestoreResult.MissingExecutionContext

        assertEquals(2, unsupported.schemaVersion)
        assertEquals("{\"schemaVersion\":2}", unsupported.rawJson)
        assertEquals("unavailable-session", missingContext.sessionId)
    }
}
