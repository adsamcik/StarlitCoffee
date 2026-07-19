package com.adsamcik.starlitcoffee.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the scheduled alarm broadcast and posts the rating-reminder
 * notification. The notification body is a custom [RemoteViews] layout with
 * five tappable emoji buttons so the user can rate the brew (1–5) without
 * leaving the shade; the body itself opens [MainActivity] deep-linked to the
 * brew log detail.
 */
class RatingReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RatingReminderScheduler.ACTION_RATING_REMINDER) return
        val brewLogId = intent.getLongExtra(RatingReminderScheduler.EXTRA_BREW_LOG_ID, -1L)
        if (brewLogId <= 0L) {
            Log.w(TAG, "Reminder fired without a valid brew log id — skipping")
            return
        }
        val appContext = context.applicationContext
        val methodLabel = intent.getStringExtra(RatingReminderScheduler.EXTRA_METHOD_LABEL)
        val pending = goAsync()
        scope.launch {
            try {
                if (!hasPostNotificationPermission(appContext)) {
                    Log.w(TAG, "POST_NOTIFICATIONS not granted — cannot post rating reminder for $brewLogId")
                    return@launch
                }
                NotificationChannels.ensureRatingReminderChannel(appContext)
                val log = loadBrewLog(appContext, brewLogId)
                if (!shouldPostRatingReminder(log)) {
                    RatingReminderScheduler(appContext).cancelReminder(brewLogId)
                    return@launch
                }
                postNotification(appContext, brewLogId, methodLabel)
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e(TAG, "Failed to validate rating reminder for $brewLogId", error)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun loadBrewLog(context: Context, brewLogId: Long): BrewLogEntity? {
        val database = AppDatabase.getInstance(context)
        return BrewLogRepository(
            database = database,
            brewLogDao = database.brewLogDao(),
            flavorTagDao = database.flavorTagDao(),
        ).getLogById(brewLogId)
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(context: Context, brewLogId: Long, methodLabel: String?) {
        val title = if (!methodLabel.isNullOrBlank()) {
            context.getString(R.string.format_notif_rating_title, methodLabel)
        } else {
            context.getString(R.string.notif_rating_title)
        }
        val body = context.getString(R.string.notif_rating_body)

        val contentIntent = MainActivity.buildBrewLogDetailIntent(context, brewLogId)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            brewLogId.toRequestCode(),
            contentIntent,
            immutablePendingIntentFlags(),
        )

        val customView = buildRatingRemoteViews(context, brewLogId, title, body)

        val notification = NotificationCompat.Builder(context, NotificationChannels.RATING_REMINDER_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(customView)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(RATING_NOTIFICATION_TIMEOUT_MILLIS)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            // Permission is checked by hasPostNotificationPermission(context) at the
            // top of onReceive; runCatching also catches the SecurityException that
            // would be thrown if the user revoked POST_NOTIFICATIONS between the
            // permission check and this call. The @SuppressLint above silences
            // lint's MissingPermission false positive (it doesn't reason across
            // helper boundaries).
            NotificationManagerCompat.from(context).notify(notificationId(brewLogId), notification)
        }.onFailure { error ->
            Log.e(TAG, "Failed to post rating reminder for $brewLogId", error)
        }
    }

    private fun buildRatingRemoteViews(
        context: Context,
        brewLogId: Long,
        title: String,
        body: String,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_rating_reminder)
        views.setTextViewText(R.id.notif_title, title)
        views.setTextViewText(R.id.notif_body, body)
        EMOJI_BUTTONS.forEach { (viewId, ratingValue) ->
            val intent = Intent(context, RatingActionReceiver::class.java).apply {
                action = RatingActionReceiver.ACTION_QUICK_RATE
                putExtra(RatingActionReceiver.EXTRA_BREW_LOG_ID, brewLogId)
                putExtra(RatingActionReceiver.EXTRA_RATING_VALUE, ratingValue)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                quickRateRequestCode(brewLogId, ratingValue),
                intent,
                immutablePendingIntentFlags(),
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
        return views
    }

    private fun hasPostNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun immutablePendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun Long.toRequestCode(): Int = (this and 0x7FFFFFFFL).toInt()

    private fun notificationId(brewLogId: Long): Int =
        NOTIFICATION_ID_BASE + brewLogId.toRequestCode()

    private fun quickRateRequestCode(brewLogId: Long, ratingValue: Int): Int =
        ((brewLogId.toRequestCode() shl 3) or ratingValue) and 0x7FFFFFFF

    companion object {
        private const val TAG = "RatingReminderRecv"
        private const val NOTIFICATION_ID_BASE = 10_000
        internal const val RATING_NOTIFICATION_TIMEOUT_MILLIS = 24L * 60L * 60L * 1_000L
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Maps custom view button ids to the rating value they record (1..4 =
        // the BrewRating tier score: 1 Bad, 2 Meh, 3 Good, 4 Awesome).
        private val EMOJI_BUTTONS: List<Pair<Int, Int>> = listOf(
            R.id.notif_rate_1 to 1,
            R.id.notif_rate_2 to 2,
            R.id.notif_rate_3 to 3,
            R.id.notif_rate_4 to 4,
        )

        fun notificationIdFor(brewLogId: Long): Int =
            NOTIFICATION_ID_BASE + ((brewLogId and 0x7FFFFFFFL).toInt())
    }
}

internal fun shouldPostRatingReminder(log: BrewLogEntity?): Boolean =
    log != null && log.rating == null
