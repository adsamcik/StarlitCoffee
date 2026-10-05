package com.adsamcik.starlitcoffee.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStageAlertNotifier
import com.adsamcik.starlitcoffee.data.brewing.session.SessionEffectDelivery
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertKind
import com.adsamcik.starlitcoffee.ui.util.labelResource
import com.adsamcik.starlitcoffee.ui.util.localizedBrewMethodLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** One actionable cue per session and kind; dismissing it never ends a physical brew. */
class DurableBrewSessionStageNotifier(context: Context) : BrewSessionStageAlertNotifier {
    private val appContext = context.applicationContext
    private val preferences = UserPreferencesRepository(appContext)
    private val notificationBoundaryStore = BrewSessionNotificationBoundaryStore(appContext)
    private val receipts = appContext.getSharedPreferences("durable_brew_alert_receipts", Context.MODE_PRIVATE)

    override suspend fun deliver(
        effect: PendingSessionEffect.StageAlert,
        session: ActiveBrewSession,
    ): SessionEffectDelivery {
        if (session.runtime.isGuidancePaused) return SessionEffectDelivery.Delivered
        if (!shouldPublishDurableBrewStageAlert(
                effect = effect,
                runtime = session.runtime,
                backgroundedAtWallClockMillis = notificationBoundaryStore
                    .backgroundedAtWallClockMillis(effect.sessionId.value),
            )
        ) return SessionEffectDelivery.Delivered
        return deliverCue(session, effect.effectId.value, timerRevision = null,
            body = appContext.getString(if (effect.kind == StageAlertKind.COMPLETED) {
                R.string.msg_brew_notification_step_ready
            } else R.string.msg_brew_notification_step_started))
    }

    override suspend fun deliverTimer(
        effect: PendingSessionEffect.TimerAlert,
        session: ActiveBrewSession,
    ): SessionEffectDelivery {
        val timer = session.runtime.userTimer
        if (timer?.reached != true || timer.revision != effect.timerRevision ||
            timer.stageInstanceId != effect.stageInstanceId
        ) return SessionEffectDelivery.Delivered
        return deliverCue(session, effect.effectId.value, effect.timerRevision,
            appContext.getString(R.string.msg_brew_reminder_reached))
    }

    override fun synchronize(session: ActiveBrewSession) {
        val runtime = session.runtime
        val manager = appContext.getSystemService(NotificationManager::class.java)
        for (notification in manager.activeNotifications) {
            if (notification.tag !in listOf(alertTag(runtime.sessionId, false), alertTag(runtime.sessionId, true))) continue
            val extras = notification.notification.extras
            val timerRevision = extras.getLong(EXTRA_TIMER_REVISION, NO_TIMER_REVISION)
            val isTimer = timerRevision != NO_TIMER_REVISION
            val relevant = runtime.status == BrewSessionStatus.RUNNING &&
                extras.getString(EXTRA_STAGE_KEY) == runtime.currentStage?.instanceId?.persistentKey &&
                if (isTimer) runtime.userTimer?.revision == timerRevision && runtime.userTimer.reached
                else !runtime.isGuidancePaused
            if (!relevant) manager.cancel(notification.tag, ALERT_NOTIFICATION_ID)
        }
        if (runtime.status in setOf(BrewSessionStatus.COMPLETED, BrewSessionStatus.CANCELLED)) {
            val prefix = receiptPrefix(runtime.sessionId)
            val keys = receipts.all.keys.filter { it.startsWith(prefix) }
            receipts.edit { keys.forEach { remove(it) } }
        }
    }

    // The Boolean commit result is required before posting; KTX edit cannot report a failed write.
    @SuppressLint("UseKtx")
    private suspend fun deliverCue(
        session: ActiveBrewSession,
        receiptKey: String,
        timerRevision: Long?,
        body: String,
    ): SessionEffectDelivery {
        val runtime = session.runtime
        val suppressForeground = timerRevision == null && BrewSessionVisibilityRegistry.isVisible(runtime.sessionId.value)
        if (runtime.status != BrewSessionStatus.RUNNING || !canPostNotifications() || suppressForeground) {
            return SessionEffectDelivery.Delivered
        }
        val theme = vibrationTheme()
        NotificationChannels.ensureBrewChannels(appContext, theme)
        val channel = appContext.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(NotificationChannels.brewAlertsId(theme))
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled() ||
            channel?.importance == NotificationManager.IMPORTANCE_NONE
        ) return SessionEffectDelivery.Delivered
        // Persist an at-most-once delivery attempt before posting. A crash cannot replay a dismissed cue.
        // The physical clock and reached state remain visible even if posting is interrupted.
        val scopedReceiptKey = receiptPrefix(runtime.sessionId) + receiptKey
        synchronized(RECEIPT_LOCK) {
            if (receipts.contains(scopedReceiptKey)) return SessionEffectDelivery.Delivered
            if (!receipts.edit().putBoolean(scopedReceiptKey, true).commit()) {
                return SessionEffectDelivery.Deferred("Unable to persist alert delivery receipt")
            }
        }
        post(session, theme, timerRevision, body)
        return SessionEffectDelivery.Delivered
    }

    @SuppressLint("MissingPermission")
    private fun post(session: ActiveBrewSession, theme: BrewVibrationTheme, timerRevision: Long?, body: String) {
        val runtime = session.runtime
        val intent = MainActivity.buildBrewSessionIntent(appContext, runtime.sessionId.value)
        val pendingIntent = PendingIntent.getActivity(appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = runtime.currentStage?.definition?.action?.let { appContext.getString(it.labelResource()) }
        val text = listOfNotNull(action, body).joinToString(" · ")
        val extras = Bundle().apply {
            putString(EXTRA_STAGE_KEY, runtime.currentStage?.instanceId?.persistentKey)
            putLong(EXTRA_TIMER_REVISION, timerRevision ?: NO_TIMER_REVISION)
        }
        val notification = NotificationCompat.Builder(appContext, NotificationChannels.brewAlertsId(theme))
            .withStarlitSmallIcon()
            .setContentTitle(appContext.localizedBrewMethodLabel(session.executionContext.logPresentation.methodLabel))
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addExtras(extras).build()
        try {
            NotificationManagerCompat.from(appContext).notify(alertTag(runtime.sessionId, timerRevision != null),
                ALERT_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Revocation between preflight and delivery leaves the persisted brew intact.
        }
    }

    private suspend fun vibrationTheme(): BrewVibrationTheme = try {
        preferences.userPreferences.first().brewVibrationTheme
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        BrewVibrationTheme.CLASSIC
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        val RECEIPT_LOCK = Any()
        const val ALERT_NOTIFICATION_ID = 20_002
        const val EXTRA_STAGE_KEY = "brew_stage_key"
        const val EXTRA_TIMER_REVISION = "brew_timer_revision"
        const val NO_TIMER_REVISION = -1L
        fun alertTag(sessionId: SessionId, timer: Boolean): String =
            "durable-brew-${if (timer) "timer" else "cue"}:${sessionId.value}"
        fun receiptPrefix(sessionId: SessionId): String = "${sessionId.value.length}:${sessionId.value}:"
    }
}
