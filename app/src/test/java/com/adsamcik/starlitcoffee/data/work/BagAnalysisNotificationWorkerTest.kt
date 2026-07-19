package com.adsamcik.starlitcoffee.data.work

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BagAnalysisNotificationWorkerTest {

    @Test
    fun `running extraction leaves notification delivery for completion enqueue`() {
        assertEquals(
            NotificationWorkDisposition.FINISH,
            notificationWorkDisposition(WorkInfo.State.RUNNING),
        )
    }

    @Test
    fun `terminal extraction is delivered immediately`() {
        assertEquals(
            NotificationWorkDisposition.DELIVER,
            notificationWorkDisposition(WorkInfo.State.SUCCEEDED),
        )
    }

    @Test
    fun `terminal completion worker reconciles manifest cleanup`() {
        assertTrue(shouldReconcileTerminalWork(WorkInfo.State.SUCCEEDED))
        assertTrue(shouldReconcileTerminalWork(WorkInfo.State.FAILED))
        assertTrue(shouldReconcileTerminalWork(WorkInfo.State.CANCELLED))
        assertFalse(shouldReconcileTerminalWork(WorkInfo.State.RUNNING))
        assertFalse(shouldReconcileTerminalWork(WorkInfo.State.ENQUEUED))
    }
}
