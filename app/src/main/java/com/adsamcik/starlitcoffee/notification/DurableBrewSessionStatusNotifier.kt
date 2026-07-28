package com.adsamcik.starlitcoffee.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStatusNotifier
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState

/**
 * The non-interruptive, ongoing timer shown while a durable brew continues
 * outside its live screen. Completion and stage-change alerts use their own
 * higher-priority channel; this notification is intentionally quiet.
 */
class DurableBrewSessionStatusNotifier(
    context: Context,
    private val nowWallClockMillis: () -> Long = System::currentTimeMillis,
    private val isForegroundVisible: (String) -> Boolean = BrewSessionVisibilityRegistry::isVisible,
) : BrewSessionStatusNotifier {
    private val appContext = context.applicationContext

    override fun publish(session: ActiveBrewSession) {
        val sessionId = session.runtime.sessionId
        if (!shouldPublishDurableBrewStatus(session.runtime.status, isForegroundVisible(sessionId.value))) {
            clear(sessionId)
            return
        }
        if (!canPostNotifications() || !areNotificationsEnabled()) {
            clear(sessionId)
            return
        }
        if (!isBrewStatusChannelEnabled()) return

        val now = nowWallClockMillis()
        val elapsedMillis = durableBrewStatusElapsedMillis(session.runtime, now)
        val body = appContext.getString(
            R.string.format_notif_brew_in_progress_body,
            formatDuration(elapsedMillis),
        )
        post(sessionId, now, elapsedMillis, body)
    }

    override fun clear(sessionId: SessionId) {
        runCatching {
            NotificationManagerCompat.from(appContext).cancel(
                durableBrewStatusNotificationTag(sessionId.value),
                STATUS_NOTIFICATION_ID,
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to cancel durable brew status notification", error)
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(
        sessionId: SessionId,
        nowWallClockMillis: Long,
        elapsedMillis: Long,
        body: String,
    ) {
        val intent = MainActivity.buildBrewSessionIntent(appContext, sessionId.value)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            sessionId.value.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, NotificationChannels.BREW_STATUS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.notif_brew_in_progress_title))
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setWhen((nowWallClockMillis - elapsedMillis).coerceAtLeast(0L))
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            NotificationManagerCompat.from(appContext).notify(
                durableBrewStatusNotificationTag(sessionId.value),
                STATUS_NOTIFICATION_ID,
                notification,
            )
        }.onFailure { error ->
            // Permission can be revoked after the preflight check. The durable
            // session stays intact and a later foreground/background cycle can retry.
            Log.w(TAG, "Unable to post durable brew status notification", error)
        }
    }

    private fun areNotificationsEnabled(): Boolean = runCatching {
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }.onFailure { error ->
        Log.w(TAG, "Unable to inspect notification availability", error)
    }.getOrDefault(false)

    private fun isBrewStatusChannelEnabled(): Boolean = runCatching {
        NotificationChannels.ensureBrewStatusChannel(appContext)
        val importance = appContext.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(NotificationChannels.BREW_STATUS_ID)
            ?.importance
        isNotificationChannelEnabled(importance)
    }.onFailure { error ->
        Log.w(TAG, "Unable to prepare durable brew status channel", error)
    }.getOrDefault(false)

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun formatDuration(elapsedMillis: Long): String {
        val totalSeconds = (elapsedMillis.coerceAtLeast(0L) / MILLIS_PER_SECOND)
        return "%d:%02d".format(totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)
    }

    private companion object {
        const val TAG = "DurableBrewStatus"
        const val STATUS_NOTIFICATION_ID = 20_001
        const val MILLIS_PER_SECOND = 1_000L
        const val SECONDS_PER_MINUTE = 60L
    }
}

internal fun shouldPublishDurableBrewStatus(
    status: BrewSessionStatus,
    foregroundVisible: Boolean,
): Boolean = status == BrewSessionStatus.RUNNING && !foregroundVisible

internal fun durableBrewStatusNotificationTag(sessionId: String): String = "durable-brew-status:$sessionId"

internal fun durableBrewStatusElapsedMillis(
    runtime: SessionRuntimeState,
    nowWallClockMillis: Long,
): Long {
    val persistedElapsed = runtime.totalActiveElapsedMillis.coerceAtLeast(0L)
    if (runtime.status != BrewSessionStatus.RUNNING) return persistedElapsed
    val anchor = runtime.activeClockAnchor ?: return persistedElapsed
    val activeDelta = (nowWallClockMillis - anchor.wallClockMillis).coerceAtLeast(0L)
    return if (Long.MAX_VALUE - persistedElapsed < activeDelta) Long.MAX_VALUE else persistedElapsed + activeDelta
}
