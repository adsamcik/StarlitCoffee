package com.adsamcik.starlitcoffee.notification

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BagAnalysisNotifierTest {

    @Test
    fun `notification tag is stable for a work result`() {
        val workId = "9f8b49ea-4407-4129-a34f-472f8654f875"

        assertEquals(notificationTagForWork(workId), notificationTagForWork(workId))
    }

    @Test
    fun `different work results use different notification tags`() {
        val first = notificationTagForWork("9f8b49ea-4407-4129-a34f-472f8654f875")
        val second = notificationTagForWork("22f1fb8c-d29c-4ae1-a6cf-b28e97f486a3")

        assertNotEquals(first, second)
        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
    }

    @Test
    fun `disabled or unavailable channel cannot deliver a notification`() {
        assertFalse(isNotificationChannelEnabled(null))
        assertFalse(isNotificationChannelEnabled(NotificationManager.IMPORTANCE_NONE))
    }

    @Test
    fun `enabled channel can deliver a notification`() {
        assertTrue(isNotificationChannelEnabled(NotificationManager.IMPORTANCE_DEFAULT))
    }
}
