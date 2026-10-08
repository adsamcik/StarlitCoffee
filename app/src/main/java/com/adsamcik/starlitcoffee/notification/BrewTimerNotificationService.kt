package com.adsamcik.starlitcoffee.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionEntityMapper
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionRestoreResult
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Display-only owner. Durable alarms, physical clocks and stage transitions stay in the runtime. */
class BrewTimerNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = linkedMapOf<String, Job>()
    private val notifications = linkedMapOf<String, Notification>()
    private var primarySession: String? = null
    private var stopping = false
    private var recovering = false
    private var displayWallClockMillis = System.currentTimeMillis()
    private val repository by lazy { ActiveBrewSessionRepository(AppDatabase.getInstance(this).activeBrewSessionDao()) }
    private val notifier by lazy {
        DurableBrewSessionStatusNotifier(this, nowWallClockMillis = { displayWallClockMillis }, notificationDelivery = { id, notification ->
            track(id.value, notification)
        }, notificationCleared = { id -> removeSession(id.value) })
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_SESSION)?.takeIf(String::isNotBlank)
        val initial = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_NOTIFICATION, Notification::class.java) }
        if (id == null || initial == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        stopping = false
        val shouldRecover = flags and START_FLAG_REDELIVERY != 0 && !recovering
        if (shouldRecover) recovering = true
        try {
            // Promote synchronously, before Room restoration or any suspension.
            if (primarySession == null) primarySession = id
            post(id, initial)
            track(id, initial)
        } catch (error: Exception) {
            Tracebox.log.error(error, REFRESH_FAILED)
            val fallback = IntentCompat.getParcelableExtra(intent, EXTRA_FALLBACK, Notification::class.java)
            if (shouldRecover) recovering = false
            removeSession(id)
            if (fallback != null) postFallback(this, id, fallback)
            return START_NOT_STICKY
        }
        if (shouldRecover) {
            // Redelivery restores all running sessions, not just the last start intent.
            scope.launch {
                try {
                    repository.getRecoverableSessions().forEach { entity ->
                        val restored = ActiveBrewSessionEntityMapper.restore(entity) as? ActiveBrewSessionRestoreResult.Restored
                        restored?.value?.let { publishSession(ActiveBrewSession(it.recipe, it.runtime, it.executionContext)) }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Tracebox.log.error(error, REFRESH_FAILED)
                } finally {
                    recovering = false
                    stopIfIdle()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun track(id: String, notification: Notification) {
        if (BrewSessionVisibilityRegistry.isVisible(id)) {
            removeSession(id)
            return
        }
        post(id, notification)
        if (jobs.containsKey(id)) return
        jobs[id] = scope.launch {
            try {
                repository.observeSession(id).collectLatest { entity ->
                    val restored = entity?.let(ActiveBrewSessionEntityMapper::restore) as? ActiveBrewSessionRestoreResult.Restored
                    val session = restored?.value?.let { ActiveBrewSession(it.recipe, it.runtime, it.executionContext) }
                    if (session == null) {
                        removeSession(id)
                        return@collectLatest
                    }
                    while (true) {
                        val presentation = publishSession(session)
                        if (presentation?.chronometerWallClockMillis == null) awaitCancellation()
                        delay(brewNotificationRefreshDelay(
                            presentation.timeMillis, presentation.clock == DurableBrewStatusClock.COUNTDOWN,
                        ))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Tracebox.log.error(error, REFRESH_FAILED)
                removeSession(id)
            }
        }
    }

    /** Publishing and scheduling share one instant, so crossing zero cannot freeze a stale 1s view. */
    private fun publishSession(session: ActiveBrewSession): DurableBrewStatusPresentation? {
        displayWallClockMillis = System.currentTimeMillis()
        notifier.publish(session)
        return durableBrewStatusPresentation(session.runtime, displayWallClockMillis)
    }

    @SuppressLint("MissingPermission")
    private fun post(id: String, notification: Notification) {
        if (primarySession == null) primarySession = id
        notifications[id] = notification
        val manager = NotificationManagerCompat.from(this)
        if (primarySession == id) {
            manager.cancel(durableBrewStatusNotificationTag(id), NOTIFICATION_ID)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } else {
            manager.notify(durableBrewStatusNotificationTag(id), NOTIFICATION_ID, notification)
        }
    }

    private fun removeSession(id: String) {
        jobs.remove(id)?.cancel()
        notifications.remove(id)
        NotificationManagerCompat.from(this).cancel(durableBrewStatusNotificationTag(id), NOTIFICATION_ID)
        if (primarySession == id) {
            primarySession = notifications.keys.firstOrNull()
            primarySession?.let { post(it, requireNotNull(notifications[it])) }
        }
        stopIfIdle()
    }

    private fun stopIfIdle() {
        if (notifications.isEmpty() && !recovering) {
            stopping = true
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notifications.keys.forEach { NotificationManagerCompat.from(this).cancel(durableBrewStatusNotificationTag(it), NOTIFICATION_ID) }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 20_001
        private const val EXTRA_SESSION = "brew_session"
        private const val EXTRA_NOTIFICATION = "brew_notification"
        private const val EXTRA_FALLBACK = "brew_fallback"
        private val REFRESH_FAILED = LogTemplate.of("Brew timer notification refresh failed")
        private val START_FAILED = LogTemplate.of("Brew timer notification service could not start")
        @Volatile private var instance: BrewTimerNotificationService? = null
        private val mainHandler = Handler(Looper.getMainLooper())

        internal fun show(context: Context, id: String, notification: Notification, fallback: Notification): Boolean {
            if (Looper.myLooper() == Looper.getMainLooper()) return showOnMain(context, id, notification, fallback)
            // The check and delivery must survive cancellation of a departing service's scope.
            return mainHandler.post {
                if (!showOnMain(context, id, notification, fallback)) postFallback(context, id, fallback)
            }
        }

        private fun showOnMain(context: Context, id: String, notification: Notification, fallback: Notification): Boolean {
            val running = instance?.takeUnless { it.stopping }
            if (running != null) {
                return runCatching {
                    running.track(id, notification)
                    true
                }.onFailure {
                    Tracebox.log.error(it, REFRESH_FAILED)
                    running.removeSession(id)
                }.getOrDefault(false)
            }
            return runCatching {
                ContextCompat.startForegroundService(context, Intent(context, BrewTimerNotificationService::class.java).apply {
                    putExtra(EXTRA_SESSION, id)
                    putExtra(EXTRA_NOTIFICATION, notification)
                    putExtra(EXTRA_FALLBACK, fallback)
                })
                true
            }.onFailure { Tracebox.log.error(it, START_FAILED) }.getOrDefault(false)
        }

        internal fun remove(id: String) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                instance?.removeSession(id)
            } else {
                mainHandler.post { instance?.removeSession(id) }
            }
        }

        @SuppressLint("MissingPermission")
        private fun postFallback(context: Context, id: String, notification: Notification) {
            runCatching { NotificationManagerCompat.from(context).notify(durableBrewStatusNotificationTag(id), NOTIFICATION_ID, notification) }
                .onFailure { Tracebox.log.error(it, REFRESH_FAILED) }
        }
    }
}
