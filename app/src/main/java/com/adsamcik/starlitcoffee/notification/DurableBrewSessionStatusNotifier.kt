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
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStatusNotifier
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInInstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInP1ExactGuidanceLoader
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInP1ExactTerminologyLoader
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactRecipeReleaseGate
import com.adsamcik.starlitcoffee.ui.guidance.shouldGateSession
import com.adsamcik.starlitcoffee.data.brewing.guides.contentId
import com.adsamcik.starlitcoffee.data.brewing.guides.title
import com.adsamcik.starlitcoffee.ui.util.labelResource
import com.adsamcik.starlitcoffee.ui.util.localizedBrewMethodLabel
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate

private object DurableBrewSessionStatusNotifierTraceboxTemplates {
    val UNABLE_TO_CANCEL_DURABLE_BREW_STATUS_NOTIFICATION = LogTemplate.of("Unable to cancel durable brew status notification")
    val UNABLE_TO_POST_DURABLE_BREW_STATUS_NOTIFICATION = LogTemplate.of("Unable to post durable brew status notification")
    val UNABLE_TO_INSPECT_NOTIFICATION_AVAILABILITY = LogTemplate.of("Unable to inspect notification availability")
    val UNABLE_TO_PREPARE_DURABLE_BREW_STATUS_CHANNEL = LogTemplate.of("Unable to prepare durable brew status channel")
}

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
    private val releaseGate by lazy {
        P1ExactRecipeReleaseGate(
            guidanceLoadResult = BuiltInP1ExactGuidanceLoader.getInstance(appContext),
            instructionAssets = BuiltInInstructionAssetCatalog.catalog,
            terminologyLoadResult = BuiltInP1ExactTerminologyLoader.getInstance(appContext),
        )
    }

    override fun publish(session: ActiveBrewSession) {
        val sessionId = session.runtime.sessionId
        if (!shouldPublishDurableBrewStatus(session.runtime.status, isForegroundVisible(sessionId.value))) {
            clear(sessionId)
            return
        }
        if (releaseGate.shouldGateSession(session.recipe, session.runtime.stagePlan.stages.map { it.definition })) {
            clear(sessionId)
            return
        }
        if (!canPostNotifications() || !areNotificationsEnabled() || !isBrewStatusChannelEnabled()) {
            clear(sessionId)
            return
        }

        val presentation = durableBrewStatusPresentation(session.runtime, nowWallClockMillis())
        if (presentation == null) {
            clear(sessionId)
            return
        }
        post(session, presentation)
    }

    override fun clear(sessionId: SessionId) {
        runCatching {
            NotificationManagerCompat.from(appContext).cancel(
                durableBrewStatusNotificationTag(sessionId.value),
                STATUS_NOTIFICATION_ID,
            )
        }.onFailure { error ->
            Tracebox.log.error(error, DurableBrewSessionStatusNotifierTraceboxTemplates.UNABLE_TO_CANCEL_DURABLE_BREW_STATUS_NOTIFICATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(
        session: ActiveBrewSession,
        presentation: DurableBrewStatusPresentation,
    ) {
        val sessionId = session.runtime.sessionId
        val intent = MainActivity.buildBrewSessionIntent(appContext, sessionId.value)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            sessionId.value.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, NotificationChannels.BREW_STATUS_ID)
            .withStarlitSmallIcon()
            .setContentTitle(appContext.localizedBrewMethodLabel(session.executionContext.logPresentation.methodLabel))
            .setContentText(listOf(session.recipe.reviewedGuide?.let { guide ->
                guide.steps.firstOrNull { guide.contentId(it) == session.runtime.currentStage?.definition?.contentId }?.title()
            } ?: appContext.getString(presentation.action.labelResource()),
                appContext.getString(when (presentation.clock) {
                    DurableBrewStatusClock.ELAPSED -> R.string.label_elapsed
                    DurableBrewStatusClock.COUNTDOWN -> R.string.label_remaining
                    DurableBrewStatusClock.TIMING_REACHED -> R.string.label_brew_activity_timing_reached
                    DurableBrewStatusClock.UNKNOWN_START -> R.string.label_brew_start_unknown
                    DurableBrewStatusClock.EXTRACTION_ENDED -> R.string.label_brew_extraction_ended
                })).joinToString(" · "))
            .setContentIntent(pendingIntent)
            .setWhen(presentation.chronometerWallClockMillis ?: 0L)
            .setShowWhen(presentation.chronometerWallClockMillis != null)
            .setUsesChronometer(presentation.chronometerWallClockMillis != null)
            .setChronometerCountDown(presentation.clock == DurableBrewStatusClock.COUNTDOWN)
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
            Tracebox.log.error(error, DurableBrewSessionStatusNotifierTraceboxTemplates.UNABLE_TO_POST_DURABLE_BREW_STATUS_NOTIFICATION)
        }
    }

    private fun areNotificationsEnabled(): Boolean = runCatching {
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }.onFailure { error ->
        Tracebox.log.error(error, DurableBrewSessionStatusNotifierTraceboxTemplates.UNABLE_TO_INSPECT_NOTIFICATION_AVAILABILITY)
    }.getOrDefault(false)

    private fun isBrewStatusChannelEnabled(): Boolean = runCatching {
        NotificationChannels.ensureBrewStatusChannel(appContext)
        val importance = appContext.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(NotificationChannels.BREW_STATUS_ID)
            ?.importance
        isNotificationChannelEnabled(importance)
    }.onFailure { error ->
        Tracebox.log.error(error, DurableBrewSessionStatusNotifierTraceboxTemplates.UNABLE_TO_PREPARE_DURABLE_BREW_STATUS_CHANNEL)
    }.getOrDefault(false)

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val STATUS_NOTIFICATION_ID = 20_001
    }
}

internal fun shouldPublishDurableBrewStatus(
    status: BrewSessionStatus,
    foregroundVisible: Boolean,
): Boolean = status == BrewSessionStatus.RUNNING && !foregroundVisible

internal fun durableBrewStatusNotificationTag(sessionId: String): String = "durable-brew-status:$sessionId"
