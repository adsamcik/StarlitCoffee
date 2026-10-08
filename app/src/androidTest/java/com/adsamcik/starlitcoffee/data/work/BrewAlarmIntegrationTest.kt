package com.adsamcik.starlitcoffee.data.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionEntityMapper
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionRestoreResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionRuntime
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.CalcUiState
import com.adsamcik.starlitcoffee.viewmodel.CalculatorBrewSessionStartFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses only newly owned UUID sessions; existing clocks, logs, beans and preferences stay intact. */
class BrewAlarmIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = ActiveBrewSessionRepository(AppDatabase.getInstance(context).activeBrewSessionDao())
    private val owned = mutableListOf<SessionId>()

    @After fun cleanOwnedSessions() = runBlocking {
        owned.forEach { id ->
            BrewSessionRuntime.create(context).coordinator.dispatch(id, SessionEvent.Cancel())
            context.getSystemService(NotificationManager::class.java).cancel("durable-brew-timer:${id.value}", 20_002)
            repository.delete(id.value)
        }
    }

    @Test fun preciseAlarmReconcilesRoomAndPostsOneCueWithoutEndingPhysicalWork() = runBlocking {
        assertTrue("Grant precise alarm access on the isolated emulator before this test", canSchedulePreciseBrewAlarms(context))
        val runtime = BrewSessionRuntime.create(context)
        val request = request()
        val started = runtime.coordinator.createOrResume(request) as BrewSessionOperationResult.Active
        val stage = started.session.runtime.currentStage!!.instanceId
        val configured = runtime.coordinator.dispatch(request.sessionId, SessionEvent.SetTimerTarget(stage, 5_000L))
            as BrewSessionOperationResult.Active
        val token = configured.session.runtime.userTimer!!.scheduleToken(request.sessionId)
        assertNotNull(pendingAlarm(request.sessionId, token))
        runtime.coordinator.dispatch(request.sessionId, SessionEvent.SetGuidancePaused(true))
        val timeout = SystemClock.elapsedRealtime() + 20_000L
        while (SystemClock.elapsedRealtime() < timeout && !restored(request.sessionId).runtime.userTimer!!.reached) delay(100L)
        val due = restored(request.sessionId)
        assertTrue(due.runtime.userTimer!!.reached)
        assertEquals(BrewSessionStatus.RUNNING, due.runtime.status)
        assertEquals(stage, due.runtime.currentStage!!.instanceId)
        assertNull(due.entity.completedLogId)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val notificationTimeout = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < notificationTimeout && notifications.activeNotifications
                .none { it.tag == "durable-brew-timer:${request.sessionId.value}" }) delay(100L)
        assertTrue(notifications.activeNotifications.any { it.tag == "durable-brew-timer:${request.sessionId.value}" })
    }

    @Test fun revisionReplacementAndCancellationWithdrawAlarmIdentities() = runBlocking {
        val runtime = BrewSessionRuntime.create(context)
        val request = request()
        val started = runtime.coordinator.createOrResume(request) as BrewSessionOperationResult.Active
        val stage = started.session.runtime.currentStage!!.instanceId
        val first = runtime.coordinator.dispatch(request.sessionId, SessionEvent.SetTimerTarget(stage, 60_000L))
            as BrewSessionOperationResult.Active
        val old = first.session.runtime.userTimer!!.scheduleToken(request.sessionId)
        val second = runtime.coordinator.dispatch(request.sessionId, SessionEvent.SetTimerTarget(stage, 120_000L))
            as BrewSessionOperationResult.Active
        val current = second.session.runtime.userTimer!!.scheduleToken(request.sessionId)
        assertNull(pendingAlarm(request.sessionId, old))
        assertNotNull(pendingAlarm(request.sessionId, current))
        runtime.coordinator.dispatch(request.sessionId, SessionEvent.Cancel())
        assertNull(pendingAlarm(request.sessionId, current))
        assertFalse(restored(request.sessionId).runtime.userTimer?.reached == true)
    }

    private fun request() = requireNotNull(CalculatorBrewSessionStartFactory().create(
        CalcUiState(brewMethod = BrewMethod.ESPRESSO, previewDoseG = 18f, previewWaterMl = 36f,
            previewBeverageG = 36f, ratio = 2f, hasValidExpression = true),
        BrewUiState(method = BrewMethod.ESPRESSO), null)).also { owned += it.sessionId }

    private suspend fun restored(id: SessionId) = (ActiveBrewSessionEntityMapper.restore(
        requireNotNull(repository.getSession(id.value))) as ActiveBrewSessionRestoreResult.Restored).value

    private fun pendingAlarm(id: SessionId, token: String): PendingIntent? = PendingIntent.getBroadcast(context, 0,
        Intent(context, BrewDeadlineReceiver::class.java).apply {
            action = ACTION_BREW_DEADLINE
            data = brewAlarmIdentity(id, token)
        }, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
}
