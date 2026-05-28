package com.adsamcik.starlitcoffee.notification

import android.Manifest
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
        if (!hasPostNotificationPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — cannot post rating reminder for $brewLogId")
            return
        }
        val methodLabel = intent.getStringExtra(RatingReminderScheduler.EXTRA_METHOD_LABEL)
        NotificationChannels.ensureRatingReminderChannel(context)
        postNotification(context, brewLogId, methodLabel)
    }

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
            pendingIntentFlags(mutable = false),
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
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
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
                pendingIntentFlags(mutable = false),
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

    private fun pendingIntentFlags(mutable: Boolean): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base or PendingIntent.FLAG_MUTABLE
        } else {
            base or PendingIntent.FLAG_IMMUTABLE
        }
    }

    private fun Long.toRequestCode(): Int = (this and 0x7FFFFFFFL).toInt()

    private fun notificationId(brewLogId: Long): Int =
        NOTIFICATION_ID_BASE + brewLogId.toRequestCode()

    private fun quickRateRequestCode(brewLogId: Long, ratingValue: Int): Int =
        ((brewLogId.toRequestCode() shl 3) or ratingValue) and 0x7FFFFFFF

    companion object {
        private const val TAG = "RatingReminderRecv"
        private const val NOTIFICATION_ID_BASE = 10_000

        // Maps custom view button ids to the rating value they record (1..5).
        private val EMOJI_BUTTONS: List<Pair<Int, Int>> = listOf(
            R.id.notif_rate_1 to 1,
            R.id.notif_rate_2 to 2,
            R.id.notif_rate_3 to 3,
            R.id.notif_rate_4 to 4,
            R.id.notif_rate_5 to 5,
        )

        fun notificationIdFor(brewLogId: Long): Int =
            NOTIFICATION_ID_BASE + ((brewLogId and 0x7FFFFFFFL).toInt())
    }
}
