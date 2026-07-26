package com.adsamcik.starlitcoffee.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewTimingMode
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState

/** Keeps an active brew useful when its timer is no longer on screen. */
interface BrewSessionNotifier {
    fun onBrewStateChanged(state: BrewUiState)
}

object NoOpBrewSessionNotifier : BrewSessionNotifier {
    override fun onBrewStateChanged(state: BrewUiState) = Unit
}

/**
 * Quietly exposes the active timer in the shade. Interruptive alerts are
 * reserved for bloom completion and the recipe's target time so useful signals
 * never get lost among minute-by-minute notifications.
 */
class AndroidBrewSessionNotifier(context: Context) : BrewSessionNotifier, DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    @Volatile private var vibrationTheme = BrewVibrationTheme.CLASSIC
    private var appInForeground = true
    private var latestState: BrewUiState? = null
    private var bloomCompletionAlerted = false
    private var targetTimeAlerted = false
    private var lastStatusElapsedSeconds = -1

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            UserPreferencesRepository(appContext).userPreferences.collect { preferences ->
                vibrationTheme = preferences.brewVibrationTheme
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        appInForeground = true
        cancelStatus()
    }

    override fun onStop(owner: LifecycleOwner) {
        appInForeground = false
        latestState?.let(::publishForBackground)
    }

    override fun onBrewStateChanged(state: BrewUiState) {
        latestState = state
        if (!isActiveTimer(state)) {
            clearSession()
            return
        }
        if (!appInForeground) publishForBackground(state)
    }

    private fun publishForBackground(state: BrewUiState) {
        if (!isActiveTimer(state) || !canPostNotifications()) return
        val selectedTheme = vibrationTheme
        NotificationChannels.ensureBrewChannels(appContext, selectedTheme)
        if (state.elapsedSeconds != lastStatusElapsedSeconds) {
            postStatus(state)
            lastStatusElapsedSeconds = state.elapsedSeconds
        }
        if (state.bloomFinished && state.bloomMarkedAtSeconds != null && !bloomCompletionAlerted) {
            bloomCompletionAlerted = true
            postAlert(
                notificationId = BLOOM_COMPLETE_NOTIFICATION_ID,
                title = appContext.getString(R.string.notif_brew_bloom_complete_title),
                message = appContext.getString(R.string.notif_brew_bloom_complete_body),
                theme = selectedTheme,
            )
        }
        if (state.timeTargetHighS > 0 &&
            state.elapsedSeconds >= state.timeTargetHighS &&
            !targetTimeAlerted
        ) {
            targetTimeAlerted = true
            postAlert(
                notificationId = TARGET_TIME_NOTIFICATION_ID,
                title = appContext.getString(R.string.notif_brew_target_reached_title),
                message = appContext.getString(
                    R.string.format_notif_brew_target_reached_body,
                    formatDuration(state.timeTargetHighS),
                ),
                theme = selectedTheme,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun postStatus(state: BrewUiState) {
        val notification = NotificationCompat.Builder(appContext, NotificationChannels.BREW_STATUS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.notif_brew_in_progress_title))
            .setContentText(
                appContext.getString(
                    R.string.format_notif_brew_in_progress_body,
                    formatDuration(state.elapsedSeconds),
                ),
            )
            .setContentIntent(openBrewPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(appContext).notify(STATUS_NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun postAlert(
        notificationId: Int,
        title: String,
        message: String,
        theme: BrewVibrationTheme,
    ) {
        val notification = NotificationCompat.Builder(
            appContext,
            NotificationChannels.brewAlertsId(theme),
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openBrewPendingIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(appContext).notify(notificationId, notification)
    }

    private fun openBrewPendingIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        OPEN_BREW_REQUEST_CODE,
        MainActivity.buildOpenBrewIntent(appContext),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun clearSession() {
        cancelStatus()
        bloomCompletionAlerted = false
        targetTimeAlerted = false
        lastStatusElapsedSeconds = -1
    }

    private fun cancelStatus() {
        appContext.getSystemService(NotificationManager::class.java)?.cancel(STATUS_NOTIFICATION_ID)
        lastStatusElapsedSeconds = -1
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun isActiveTimer(state: BrewUiState): Boolean =
        state.timerRunning && state.method.timingMode == BrewTimingMode.ACTIVE_TIMER

    private fun formatDuration(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        return "%d:%02d".format(safeSeconds / 60, safeSeconds % 60)
    }

    private companion object {
        const val STATUS_NOTIFICATION_ID = 20_001
        const val BLOOM_COMPLETE_NOTIFICATION_ID = 20_002
        const val TARGET_TIME_NOTIFICATION_ID = 20_003
        const val OPEN_BREW_REQUEST_CODE = 20_004
    }
}
