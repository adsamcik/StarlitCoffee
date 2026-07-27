package com.adsamcik.starlitcoffee.data.work

import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkManagerLongSessionSchedulerTest {

    @Test
    fun `unique work names distinguish delimiter-like session and token values`() {
        val first = LongSessionWork.uniqueWorkName(
            sessionId = SessionId("session:alpha"),
            scheduleToken = "token/beta",
        )
        val second = LongSessionWork.uniqueWorkName(
            sessionId = SessionId("session"),
            scheduleToken = "alpha:token/beta",
        )

        assertNotEquals(first, second)
        assertTrue(first.startsWith(LongSessionWork.UNIQUE_WORK_PREFIX))
    }

    @Test
    fun `input data preserves the deadline worker contract`() {
        val input = LongSessionWork.inputData(
            sessionId = SessionId("session-42"),
            stageInstanceId = StageInstanceId(StageId("pour"), occurrence = 2),
            scheduleToken = "pour_2_deadline",
            dueAtWallClockMillis = 123_456L,
            effectId = SessionEffectId("effect-42"),
        )

        assertEquals(LongSessionWork.INPUT_SCHEMA_VERSION, input.getInt(LongSessionWork.KEY_SCHEMA_VERSION, -1))
        assertEquals("session-42", input.getString(LongSessionWork.KEY_SESSION_ID))
        assertEquals("pour", input.getString(LongSessionWork.KEY_STAGE_SOURCE_ID))
        assertEquals(2, input.getInt(LongSessionWork.KEY_STAGE_OCCURRENCE, -1))
        assertEquals("pour_2", input.getString(LongSessionWork.KEY_STAGE_INSTANCE_KEY))
        assertEquals("pour_2_deadline", input.getString(LongSessionWork.KEY_SCHEDULE_TOKEN))
        assertEquals(123_456L, input.getLong(LongSessionWork.KEY_DUE_AT_WALL_CLOCK_MILLIS, -1L))
        assertEquals("effect-42", input.getString(LongSessionWork.KEY_EFFECT_ID))
    }

    @Test
    fun `initial delay is zero for due and overdue work and saturates safely`() {
        assertEquals(0L, LongSessionWork.initialDelayMillis(100L, 100L))
        assertEquals(0L, LongSessionWork.initialDelayMillis(99L, 100L))
        assertEquals(250L, LongSessionWork.initialDelayMillis(350L, 100L))
        assertEquals(Long.MAX_VALUE, LongSessionWork.initialDelayMillis(Long.MAX_VALUE, -1L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank schedule tokens are rejected before they can share work`() {
        LongSessionWork.uniqueWorkName(SessionId("session-42"), " ")
    }
}
