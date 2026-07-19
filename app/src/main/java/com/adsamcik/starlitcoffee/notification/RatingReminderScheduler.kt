package com.adsamcik.starlitcoffee.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Schedules and cancels rating-reminder notifications for completed brews.
 * Extracted so tests can drop in a fake without dragging AlarmManager along.
 */
interface RatingReminders {
    fun scheduleReminder(brewLogId: Long, methodLabel: String?, delay: Duration = DEFAULT_DELAY)
    fun cancelReminder(brewLogId: Long)

    companion object {
        val DEFAULT_DELAY: Duration = 30.minutes
    }
}

/**
 * Default [RatingReminders] backed by [AlarmManager]. Inexact `set()` is
 * intentional — the reminder is a courtesy nudge, so battery-friendly
 * scheduling is preferred over wakeup precision (and it sidesteps the
 * SCHEDULE_EXACT_ALARM permission gate on Android 12+).
 *
 * Reminders are keyed by brew-log id so each brew gets at most one pending
 * alarm; rescheduling for the same id replaces the previous request.
 */
class RatingReminderScheduler(private val context: Context) : RatingReminders {

    override fun scheduleReminder(brewLogId: Long, methodLabel: String?, delay: Duration) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: run {
            Log.w(TAG, "AlarmManager unavailable — cannot schedule rating reminder")
            return
        }
        NotificationChannels.ensureRatingReminderChannel(context)
        val triggerAt = System.currentTimeMillis() + delay.inWholeMilliseconds
        val pendingIntent = buildPendingIntent(brewLogId, methodLabel)
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        Log.d(TAG, "Scheduled rating reminder for brew $brewLogId in $delay")
    }

    override fun cancelReminder(brewLogId: Long) {
        val pendingIntent = buildPendingIntent(brewLogId, methodLabel = null)
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent)
        NotificationManagerCompat.from(context)
            .cancel(RatingReminderReceiver.notificationIdFor(brewLogId))
    }

    private fun buildPendingIntent(
        brewLogId: Long,
        methodLabel: String?,
    ): PendingIntent {
        val intent = Intent(context, RatingReminderReceiver::class.java).apply {
            action = ACTION_RATING_REMINDER
            putExtra(EXTRA_BREW_LOG_ID, brewLogId)
            if (methodLabel != null) putExtra(EXTRA_METHOD_LABEL, methodLabel)
        }
        // requestCode keyed by brew-log id ensures distinct PendingIntents per
        // brew; FLAG_UPDATE_CURRENT lets a reschedule for the same brew win.
        return PendingIntent.getBroadcast(
            context,
            brewLogId.toRequestCode(),
            intent,
            ratingReminderPendingIntentFlags(),
        )
    }

    private fun Long.toRequestCode(): Int =
        (this and 0x7FFFFFFFL).toInt()

    companion object {
        private const val TAG = "RatingReminder"
        const val ACTION_RATING_REMINDER = "com.adsamcik.starlitcoffee.action.RATING_REMINDER"
        const val EXTRA_BREW_LOG_ID = "brew_log_id"
        const val EXTRA_METHOD_LABEL = "method_label"
    }
}

internal fun ratingReminderPendingIntentFlags(): Int =
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
