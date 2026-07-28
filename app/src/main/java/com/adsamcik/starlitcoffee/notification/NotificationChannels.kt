package com.adsamcik.starlitcoffee.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewVibrationTheme

/**
 * Notification channel registry. Channels are created lazily on first use
 * because Android only requires registration before posting; creating an
 * existing channel is a no-op so calling [ensureRatingReminderChannel] from
 * every notification-posting code path is cheap and safe.
 */
internal object NotificationChannels {
    const val RATING_REMINDER_ID = "rating_reminder"
    const val BAG_ANALYSIS_ID = "bag_analysis"
    const val BAG_SCAN_PROGRESS_ID = "bag_scan_progress"
    const val BREW_STATUS_ID = "brew_status"

    fun brewAlertsId(theme: BrewVibrationTheme): String =
        "brew_alerts_${theme.name.lowercase()}"

    fun ensureBrewStatusChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(BREW_STATUS_ID) != null) return
        NotificationChannel(
            BREW_STATUS_ID,
            context.getString(R.string.notif_channel_brew_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_brew_status_desc)
            setShowBadge(false)
        }.also(manager::createNotificationChannel)
    }

    fun ensureBrewChannels(context: Context, theme: BrewVibrationTheme) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        ensureBrewStatusChannel(context)
        val alertsId = brewAlertsId(theme)
        if (manager.getNotificationChannel(alertsId) == null) {
            NotificationChannel(
                alertsId,
                context.getString(R.string.notif_channel_brew_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_brew_alerts_desc)
                enableVibration(true)
                vibrationPattern = theme.alertChannelPattern()
            }.also(manager::createNotificationChannel)
        }
    }

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

    fun ensureBagAnalysisChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(BAG_ANALYSIS_ID) != null) return
        val channel = NotificationChannel(
            BAG_ANALYSIS_ID,
            context.getString(R.string.notif_channel_bag_analysis),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_bag_analysis_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun ensureBagScanProgressChannel(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(BAG_SCAN_PROGRESS_ID) != null) return
        val channel = NotificationChannel(
            BAG_SCAN_PROGRESS_ID,
            context.getString(R.string.notif_scan_progress_title),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
