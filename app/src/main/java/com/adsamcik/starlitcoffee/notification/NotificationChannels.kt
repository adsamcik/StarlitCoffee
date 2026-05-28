package com.adsamcik.starlitcoffee.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.adsamcik.starlitcoffee.R

/**
 * Notification channel registry. Channels are created lazily on first use
 * because Android only requires registration before posting; creating an
 * existing channel is a no-op so calling [ensureRatingReminderChannel] from
 * every notification-posting code path is cheap and safe.
 */
internal object NotificationChannels {
    const val RATING_REMINDER_ID = "rating_reminder"

    fun ensureRatingReminderChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(RATING_REMINDER_ID) != null) return
        val channel = NotificationChannel(
            RATING_REMINDER_ID,
            context.getString(R.string.notif_channel_rating_reminder),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_rating_reminder_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
