package com.adsamcik.starlitcoffee.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertKind
import com.adsamcik.starlitcoffee.ui.util.localizedBrewMethodLabel
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.CalcUiState
import com.adsamcik.starlitcoffee.viewmodel.CalculatorBrewSessionStartFactory
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real OS posting/chronometer flags and PendingIntent identity; no brewing DB fixtures are inserted. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class DurableBrewStatusNotificationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val ownedSessions = mutableListOf<SessionId>()
    private val ownedAlerts = mutableListOf<Int>()
    private val now = System.currentTimeMillis()

    @Test
    fun allSeventeenReviewedMethodsPostTheirFrozenActionAndCustomDeadline() {
        val guides = com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary.decode(
            context.assets.open(com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary.ASSET_PATH)
                .bufferedReader().use { it.readText() })
        val notifier = notifier()
        val intents = mutableSetOf<android.app.PendingIntent>()
        guides.forEach { guide ->
            val request = com.adsamcik.starlitcoffee.viewmodel.ReviewedGuideStartFactory.create(guide, BrewUiState(), null, 21.3)
            ownedSessions += request.sessionId
            var runtime = SessionRuntimeState.create(request.sessionId, request.stagePlan).copy(physicalClock = request.physicalClock)
            val reading = SessionClockReading(1_000L, now)
            runtime = SessionReducer.reduce(runtime, SessionEvent.Start(), reading).state
            repeat(guide.steps.indexOfFirst { it.id == guide.originStepId }) {
                runtime = SessionReducer.reduce(runtime, SessionEvent.ManualAdvance(), reading).state
            }
            runtime = SessionReducer.reduce(runtime, SessionEvent.Start(), reading).state
            val stage = requireNotNull(runtime.currentStage).instanceId
            runtime = SessionReducer.reduce(runtime, SessionEvent.SetTimerTarget(stage, 600_000L), reading).state
            assertTrue(guide.id, runtime.hasPhysicalClockStarted)
            val session = ActiveBrewSession(request.recipe, runtime, request.executionContext)
            notifier.publish(session)
            val notification = posted(runtime.sessionId).notification
            assertEquals(guide.methodName, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
            assertEquals(now + 600_000L, notification.`when`)
            assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertTrue(requireNotNull(notification.contentIntent).let(intents::add))
            assertEquals(BrewSessionStatus.RUNNING, runtime.status)
            assertEquals(stage, runtime.currentStage?.instanceId)
            notifier.clear(runtime.sessionId)
        }
        assertEquals(17, intents.size)
    }

    @Before
    fun requirePostingPermission() {
        assertEquals("The isolated test runner must grant notification access before this class",
            PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
    }

    @After
    fun restoreOwnedState() {
        ownedSessions.forEach(DurableBrewSessionStatusNotifier(context)::clear)
        ownedSessions.forEach { id ->
            listOf("durable-brew-cue:${id.value}", "durable-brew-timer:${id.value}").forEach { tag ->
                manager.cancel(tag, 20_002)
            }
            val receipts = context.getSharedPreferences("durable_brew_alert_receipts", 0)
            val prefix = "${id.value.length}:${id.value}:"
            val editor = receipts.edit()
            receipts.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
            editor.commit()
        }
        ownedAlerts.forEach(manager::cancel)
    }

    @Test
    fun explicitReminderSurvivesGuidePauseAndDismissalDoesNotReplayOrCompleteBrew() = runBlocking<Unit> {
        val original = session(BrewMethod.ESPRESSO)
        val stage = requireNotNull(original.runtime.currentStage).instanceId
        val timerSet = SessionReducer.reduce(original.runtime, SessionEvent.SetTimerTarget(stage, 1_000L),
            SessionClockReading(0L, now)).state
        val due = SessionReducer.reduce(timerSet.copy(isGuidancePaused = true), SessionEvent.Tick(),
            SessionClockReading(1_000L, now + 1_000L)).state
        val effect = due.pendingEffects.filterIsInstance<PendingSessionEffect.TimerAlert>().single()
        val notifier = DurableBrewSessionStageNotifier(context)
        val session = original.copy(runtime = due)
        notifier.deliverTimer(effect, session)
        val tag = "durable-brew-timer:${due.sessionId.value}"
        awaitAlert(tag, expected = true)
        assertEquals(BrewSessionStatus.RUNNING, due.status)
        assertEquals(stage, due.currentStage?.instanceId)
        manager.cancel(tag, 20_002)
        awaitAlert(tag, expected = false)
        // A new adapter after process recreation sees the persisted at-most-once delivery receipt.
        DurableBrewSessionStageNotifier(context).deliverTimer(effect, session)
        awaitAlert(tag, expected = false)
        val edited = SessionReducer.reduce(due, SessionEvent.SetTimerTarget(stage, 5_000L),
            SessionClockReading(1_000L, now + 1_000L)).state
        notifier.deliverTimer(effect, original.copy(runtime = edited))
        awaitAlert(tag, expected = false)
    }

    @Test
    fun postedCueIsWithdrawnOnGuidePauseAndTimerCueOnRevisionChange() = runBlocking<Unit> {
        val session = session(BrewMethod.ESPRESSO)
        val stage = requireNotNull(session.runtime.currentStage).instanceId
        val notifier = DurableBrewSessionStageNotifier(context)
        notifier.deliver(PendingSessionEffect.StageAlert(SessionEffectId("native-cue-${UUID.randomUUID()}"),
            session.runtime.sessionId, stage, StageAlertKind.STARTED), session)
        val cueTag = "durable-brew-cue:${session.runtime.sessionId.value}"
        awaitAlert(cueTag, true)
        notifier.synchronize(session.copy(runtime = session.runtime.copy(isGuidancePaused = true)))
        awaitAlert(cueTag, false)
        val timer = com.adsamcik.starlitcoffee.domain.brewing.session.UserBrewTimer(
            stage, 1L, 1_000L, 0L, now, null, reached = true)
        val due = session.copy(runtime = session.runtime.copy(userTimer = timer, timerRevision = 1L))
        notifier.deliverTimer(PendingSessionEffect.TimerAlert(SessionEffectId("native-timer-${UUID.randomUUID()}"),
            session.runtime.sessionId, stage, 1L), due)
        val timerTag = "durable-brew-timer:${session.runtime.sessionId.value}"
        awaitAlert(timerTag, true)
        notifier.synchronize(due.copy(runtime = due.runtime.copy(userTimer = timer.copy(revision = 2L,
            reached = false, deadlineAtWallClockMillis = now + 1_000L), timerRevision = 2L)))
        awaitAlert(timerTag, false)
    }

    private fun awaitAlert(tag: String, expected: Boolean) {
        repeat(50) {
            if (manager.activeNotifications.any { it.tag == tag } == expected) return
            Thread.sleep(100L)
        }
        error("Alert visibility for $tag did not become $expected")
    }

    @Test
    fun guidePauseSilencesStageCueWhileOsChronometerKeepsItsOriginalDeadline() = runBlocking<Unit> {
        val session = session(BrewMethod.V60).let { it.copy(runtime = it.runtime.copy(isGuidancePaused = true)) }
        val effect = PendingSessionEffect.StageAlert(
            effectId = SessionEffectId("native-guide-pause-${UUID.randomUUID()}"),
            sessionId = session.runtime.sessionId,
            stageInstanceId = requireNotNull(session.runtime.currentStage).instanceId,
            kind = StageAlertKind.COMPLETED,
        )
        val alertId = effect.effectId.value.hashCode()
        ownedAlerts += alertId
        DurableBrewSessionStageNotifier(context).deliver(effect, session)
        notifier().publish(session)
        val status = posted(session.runtime.sessionId).notification
        assertEquals(now + 45_000L, status.`when`)
        assertTrue(status.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(manager.activeNotifications.any { it.id == alertId })
        assertEquals(BrewSessionStatus.RUNNING, session.runtime.status)
    }

    @Test
    fun allCalculatorMethodsPostTheirOwnQuietActionAndClock() {
        val notifier = notifier()
        BrewMethod.entries.forEach { method ->
            val session = session(method)
            notifier.publish(session)
            val notification = posted(session.runtime.sessionId).notification
            assertEquals(context.localizedBrewMethodLabel(method.name),
                notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
            assertEquals(method.hasBloom || method == BrewMethod.COLD_BREW,
                notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
            when {
                method.hasBloom -> assertEquals(now + 45_000L, notification.`when`)
                method == BrewMethod.COLD_BREW -> assertEquals(now + 50_400_000L, notification.`when`)
                else -> assertEquals(now, notification.`when`)
            }
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
            assertEquals(NotificationChannels.BREW_STATUS_ID, notification.channelId)
            assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains(" · "))
            notifier.clear(session.runtime.sessionId)
        }
    }

    @Test
    fun collidingRequestCodesStillHaveDifferentNotificationAndTapIdentities() {
        val prefix = "native-status-${UUID.randomUUID()}-"
        val first = session(BrewMethod.ESPRESSO, prefix + "Aa")
        val second = session(BrewMethod.ESPRESSO, prefix + "BB")
        assertEquals(first.runtime.sessionId.value.hashCode(), second.runtime.sessionId.value.hashCode())
        val notifier = notifier()
        notifier.publish(first)
        notifier.publish(second)
        assertNotEquals(posted(first.runtime.sessionId).notification.contentIntent,
            posted(second.runtime.sessionId).notification.contentIntent)
        val firstIntent = MainActivity.buildBrewSessionIntent(context, first.runtime.sessionId.value)
        val secondIntent = MainActivity.buildBrewSessionIntent(context, second.runtime.sessionId.value)
        assertFalse(firstIntent.filterEquals(secondIntent))
        assertEquals(first.runtime.sessionId.value, firstIntent.data?.lastPathSegment)
        assertEquals(first.runtime.sessionId.value, firstIntent.getStringExtra(MainActivity.EXTRA_BREW_SESSION_ID))
        notifier.clear(first.runtime.sessionId)
        awaitAbsent(first.runtime.sessionId)
        assertTrue(posted(second.runtime.sessionId).notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun focusedEndedAndReleaseGatedBrewsWithdrawOnlyTheirOwnStatus() {
        val hidden = mutableSetOf<String>()
        val notifier = DurableBrewSessionStatusNotifier(context, { now }, hidden::contains)
        val first = session(BrewMethod.V60)
        val second = session(BrewMethod.COLD_BREW)
        notifier.publish(first)
        notifier.publish(second)
        hidden += first.runtime.sessionId.value
        notifier.publish(first)
        awaitAbsent(first.runtime.sessionId)
        assertFalse(isPosted(first.runtime.sessionId))
        assertTrue(isPosted(second.runtime.sessionId))
        notifier.publish(second.copy(recipe = second.recipe.copy(
            builtInRecipeId = "v60_kasuya_4_6_20_300", brewerProfileId = "v60_02")))
        awaitAbsent(second.runtime.sessionId)
        assertFalse(isPosted(second.runtime.sessionId))
        hidden.clear()
        notifier.publish(first)
        notifier.publish(first.copy(runtime = first.runtime.copy(status = BrewSessionStatus.CANCELLED)))
        awaitAbsent(first.runtime.sessionId)
        assertFalse(isPosted(first.runtime.sessionId))
    }

    private fun notifier() = DurableBrewSessionStatusNotifier(context, { now }, { false })

    private fun posted(id: SessionId): android.service.notification.StatusBarNotification {
        repeat(50) {
            manager.activeNotifications.singleOrNull {
                it.tag == durableBrewStatusNotificationTag(id.value)
            }?.let { notification -> return notification }
            Thread.sleep(100L)
        }
        error("The OS did not post ${id.value}")
    }

    private fun awaitAbsent(id: SessionId) {
        repeat(50) {
            if (!isPosted(id)) return
            Thread.sleep(100L)
        }
        error("The OS did not withdraw ${id.value}")
    }

    private fun isPosted(id: SessionId) = manager.activeNotifications.any {
        it.tag == durableBrewStatusNotificationTag(id.value)
    }

    private fun session(method: BrewMethod, id: String = "native-status-${UUID.randomUUID()}"): ActiveBrewSession {
        val request = requireNotNull(CalculatorBrewSessionStartFactory().create(
            CalcUiState(brewMethod = method, tokens = listOf(CalcToken.Number("20")), previewDoseG = 20f,
                previewWaterMl = 20f * method.defaultRatio, ratio = method.defaultRatio,
                hasValidExpression = true),
            BrewUiState(method = method, effectiveBloomDurationSeconds = 45), null))
        val sessionId = SessionId(id)
        ownedSessions += sessionId
        val runtime = SessionReducer.reduce(SessionRuntimeState.create(sessionId, request.stagePlan),
            SessionEvent.Start(), SessionClockReading(1_000L, now)).state
        return ActiveBrewSession(request.recipe, runtime, request.executionContext)
    }
}
