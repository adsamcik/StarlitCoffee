package com.adsamcik.starlitcoffee.notification

import com.adsamcik.starlitcoffee.domain.brewing.session.ActiveClockAnchor
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionTestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableBrewSessionStatusNotifierTest {

    @Test
    fun `only a background running session is eligible for quiet status`() {
        assertTrue(shouldPublishDurableBrewStatus(BrewSessionStatus.RUNNING, foregroundVisible = false))
        assertFalse(shouldPublishDurableBrewStatus(BrewSessionStatus.RUNNING, foregroundVisible = true))
        assertFalse(shouldPublishDurableBrewStatus(BrewSessionStatus.PAUSED, foregroundVisible = false))
        assertFalse(shouldPublishDurableBrewStatus(BrewSessionStatus.COMPLETED, foregroundVisible = false))
        assertFalse(shouldPublishDurableBrewStatus(BrewSessionStatus.CANCELLED, foregroundVisible = false))
    }

    @Test
    fun `status tag is stable per durable session`() {
        val first = SessionId("status-1")
        val second = SessionId("status-2")

        assertEquals(
            durableBrewStatusNotificationTag(first.value),
            durableBrewStatusNotificationTag(first.value),
        )
        assertNotEquals(
            durableBrewStatusNotificationTag(first.value),
            durableBrewStatusNotificationTag(second.value),
        )
    }

    @Test
    fun `status elapsed time includes only active wall-clock progress`() {
        val runtime = SessionRuntimeState.create(
            sessionId = SessionId("elapsed"),
            stagePlan = ActiveBrewSessionTestFixtures.plan(
                completionMode = StageCompletionMode.Manual,
                alertOnStart = false,
            ),
        ).copy(
            status = BrewSessionStatus.RUNNING,
            totalActiveElapsedMillis = 5_000L,
            activeClockAnchor = ActiveClockAnchor(monotonicMillis = null, wallClockMillis = 10_000L),
        )

        assertEquals(8_500L, durableBrewStatusElapsedMillis(runtime, nowWallClockMillis = 13_500L))
        assertEquals(5_000L, durableBrewStatusElapsedMillis(runtime, nowWallClockMillis = 9_000L))
    }
}
