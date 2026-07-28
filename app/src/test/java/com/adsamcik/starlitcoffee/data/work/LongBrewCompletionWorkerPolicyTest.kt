package com.adsamcik.starlitcoffee.data.work

import androidx.work.workDataOf
import com.adsamcik.starlitcoffee.data.brewing.session.ScheduledBrewSessionDeadline
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongBrewCompletionWorkerPolicyTest {

    @Test
    fun `decoder accepts the exact versioned scheduler contract`() {
        val decoded = decodeLongBrewCompletionWork(validInput())

        val valid = decoded as LongBrewCompletionWorkInput.Valid
        assertEquals("session-42", valid.request.sessionId.value)
        assertEquals(StageId("steep"), valid.request.stageInstanceId.sourceStageId)
        assertEquals(2, valid.request.stageInstanceId.occurrence)
        assertEquals("session-42:steep_2:deadline", valid.request.scheduleToken)
        assertEquals(123_456L, valid.request.dueAtWallClockMillis)
        assertEquals("schedule-effect-42", valid.request.effectId.value)
    }

    @Test
    fun `decoder rejects an incompatible schema and inconsistent stage key`() {
        val futureSchema = workDataOf(
            LongSessionWork.KEY_SCHEMA_VERSION to LongSessionWork.INPUT_SCHEMA_VERSION + 1,
        )
        val inconsistentStage = workDataOf(
            LongSessionWork.KEY_SCHEMA_VERSION to LongSessionWork.INPUT_SCHEMA_VERSION,
            LongSessionWork.KEY_SESSION_ID to "session-42",
            LongSessionWork.KEY_STAGE_SOURCE_ID to "steep",
            LongSessionWork.KEY_STAGE_OCCURRENCE to 2,
            LongSessionWork.KEY_STAGE_INSTANCE_KEY to "steep_1",
            LongSessionWork.KEY_SCHEDULE_TOKEN to "session-42:steep_2:deadline",
            LongSessionWork.KEY_DUE_AT_WALL_CLOCK_MILLIS to 123_456L,
            LongSessionWork.KEY_EFFECT_ID to "schedule-effect-42",
        )

        assertTrue(decodeLongBrewCompletionWork(futureSchema) is LongBrewCompletionWorkInput.Invalid)
        assertTrue(decodeLongBrewCompletionWork(inconsistentStage) is LongBrewCompletionWorkInput.Invalid)
    }

    @Test
    fun `only exact running indexed metadata can reconcile a deadline`() {
        val request = (decodeLongBrewCompletionWork(validInput()) as LongBrewCompletionWorkInput.Valid).request
        val current = ScheduledBrewSessionDeadline(
            sessionId = request.sessionId.value,
            status = "RUNNING",
            stageInstanceKey = request.stageInstanceId.persistentKey,
            scheduleToken = request.scheduleToken,
            dueAtWallClockMillis = request.dueAtWallClockMillis,
        )

        assertEquals(DeadlineWorkDisposition.CURRENT, deadlineWorkDisposition(request, current))
        assertEquals(
            DeadlineWorkDisposition.STALE,
            deadlineWorkDisposition(request, current.copy(sessionId = "other-session")),
        )
        assertEquals(
            DeadlineWorkDisposition.STALE,
            deadlineWorkDisposition(request, current.copy(status = "PAUSED")),
        )
        assertEquals(
            DeadlineWorkDisposition.STALE,
            deadlineWorkDisposition(request, current.copy(stageInstanceKey = "steep_1")),
        )
        assertEquals(
            DeadlineWorkDisposition.STALE,
            deadlineWorkDisposition(request, current.copy(scheduleToken = "replaced-token")),
        )
        assertEquals(
            DeadlineWorkDisposition.STALE,
            deadlineWorkDisposition(request, current.copy(dueAtWallClockMillis = 123_457L)),
        )
        assertEquals(DeadlineWorkDisposition.STALE, deadlineWorkDisposition(request, null))
    }

    @Test
    fun `only SQLite and IO infrastructure causes are retryable`() {
        assertTrue(isRetryableLongBrewCompletionFailure(IOException("storage unavailable")))
        assertTrue(
            isRetryableLongBrewCompletionFailure(
                IllegalStateException("room wrapper", IOException("storage unavailable")),
            ),
        )
        assertFalse(isRetryableLongBrewCompletionFailure(IllegalArgumentException("bad document")))
    }

    private fun validInput() = LongSessionWork.inputData(
        sessionId = SessionId("session-42"),
        stageInstanceId = StageInstanceId(StageId("steep"), occurrence = 2),
        scheduleToken = "session-42:steep_2:deadline",
        dueAtWallClockMillis = 123_456L,
        effectId = SessionEffectId("schedule-effect-42"),
    )
}
