package com.adsamcik.starlitcoffee.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStageAlertNotifier
import com.adsamcik.starlitcoffee.data.brewing.session.SessionEffectDelivery
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertKind
import kotlinx.coroutines.flow.first

/**
 * Posts a compact, idempotent alert for a durable stage transition. It never
 * keeps a foreground service alive: timers remain persisted and WorkManager
 * only wakes the process to reconcile a deadline.
 */
class DurableBrewSessionStageNotifier(context: Context) : BrewSessionStageAlertNotifier {
    private val appContext = context.applicationContext
    private val preferences = UserPreferencesRepository(appContext)

    override suspend fun deliver(
        effect: PendingSessionEffect.StageAlert,
        session: ActiveBrewSession,
    ): SessionEffectDelivery {
        // Permission denial and an in-app visible transition are terminal
        // conditions, not failures that should leave a permanent outbox retry.
        if (!canPostNotifications() ||
            BrewSessionVisibilityRegistry.isVisible(effect.sessionId.value)
        ) return SessionEffectDelivery.Delivered
        val theme = vibrationTheme()
        NotificationChannels.ensureBrewChannels(appContext, theme)
        val isComplete = effect.kind == StageAlertKind.COMPLETED
        post(
            effect = effect,
            theme = theme,
            title = if (isComplete) {
                appContext.getString(R.string.notif_brew_target_reached_title)
            } else {
                appContext.getString(R.string.notif_brew_in_progress_title)
            },
            body = if (isComplete) {
                appContext.getString(R.string.msg_brew_notification_step_ready)
            } else {
                appContext.getString(R.string.msg_brew_notification_step_started)
            },
        )
        return SessionEffectDelivery.Delivered
    }

    @SuppressLint("MissingPermission")
    private fun post(
        effect: PendingSessionEffect.StageAlert,
        theme: BrewVibrationTheme,
        title: String,
        body: String,
    ) {
        val intent = MainActivity.buildBrewSessionIntent(appContext, effect.sessionId.value)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            effect.effectId.value.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(
            appContext,
            NotificationChannels.brewAlertsId(theme),
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(appContext).notify(effect.effectId.value.hashCode(), notification)
    }

    private suspend fun vibrationTheme(): BrewVibrationTheme = try {
        preferences.userPreferences.first().brewVibrationTheme
    } catch (_: Exception) {
        // A preference read must not make a durable stage transition fail.
        BrewVibrationTheme.CLASSIC
    }


    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
}
