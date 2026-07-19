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
import com.adsamcik.starlitcoffee.data.work.BagReviewContext

/**
 * Posts the "bag analysis complete" notification after the user sent the AI
 * bag-label extraction to the background. Modelled as an interface so the
 * [com.adsamcik.starlitcoffee.viewmodel.BrewViewModel] can be unit-tested with
 * [NoOpBagAnalysisNotifier]; the factory wires the real [AndroidBagAnalysisNotifier].
 */
interface BagAnalysisNotifier {
    /**
     * Notifies the user that AI extraction finished. [displayName] is the
     * resolved bag name when known, used to make the body specific; pass null
     * for a generic message.
     */
    fun notifyComplete(
        workId: String,
        displayName: String?,
        reviewContext: BagReviewContext? = null,
    ): Boolean

    /** Notifies the user that the background extraction failed. */
    fun notifyFailed(workId: String, reviewContext: BagReviewContext? = null): Boolean
}

/** Default no-op used in unit tests and when no platform context is available. */
object NoOpBagAnalysisNotifier : BagAnalysisNotifier {
    override fun notifyComplete(
        workId: String,
        displayName: String?,
        reviewContext: BagReviewContext?,
    ) = false

    override fun notifyFailed(workId: String, reviewContext: BagReviewContext?) = false
}

/**
 * Real notifier. Tapping the notification opens [MainActivity] deep-linked to
 * the analyzed coffee-bag form (the result is held by the process-scoped
 * BrewViewModel, so the intent carries only a flag — see [DeepLinkBus]).
 */
class AndroidBagAnalysisNotifier(
    private val context: Context,
) : BagAnalysisNotifier {

    override fun notifyComplete(
        workId: String,
        displayName: String?,
        reviewContext: BagReviewContext?,
    ): Boolean {
        val body = if (!displayName.isNullOrBlank()) {
            context.getString(R.string.format_notif_bag_analysis_body, displayName)
        } else {
            context.getString(R.string.notif_bag_analysis_body)
        }
        return postNotification(
            workId = workId,
            title = context.getString(R.string.notif_bag_analysis_title),
            body = body,
            reviewContext = reviewContext,
        )
    }

    override fun notifyFailed(
        workId: String,
        reviewContext: BagReviewContext?,
    ): Boolean = postNotification(
        workId = workId,
        title = context.getString(R.string.notif_bag_analysis_failed_title),
        body = context.getString(R.string.notif_bag_analysis_failed_body),
        reviewContext = reviewContext,
    )

    @SuppressLint("MissingPermission")
    private fun postNotification(
        workId: String,
        title: String,
        body: String,
        reviewContext: BagReviewContext?,
    ): Boolean {
        if (!hasPostNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — cannot post bag analysis notification")
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled — cannot post bag analysis notification")
            return false
        }
        NotificationChannels.ensureBagAnalysisChannel(context)
        val channelImportance = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(NotificationChannels.BAG_ANALYSIS_ID)
            ?.importance
        if (!isNotificationChannelEnabled(channelImportance)) {
            Log.w(TAG, "Bag analysis notification channel is disabled")
            return false
        }

        val contentIntent = MainActivity.buildBagAnalysisIntent(context, workId, reviewContext)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_REQUEST_CODE,
            contentIntent,
            pendingIntentFlags(),
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.BAG_ANALYSIS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return runCatching {
            // Permission is checked above; runCatching also absorbs the
            // SecurityException that would be thrown if the user revoked
            // POST_NOTIFICATIONS between the check and this call.
            NotificationManagerCompat.from(context).notify(
                notificationTagForWork(workId),
                NOTIFICATION_ID,
                notification,
            )
            true
        }.onFailure { error ->
            Log.e(TAG, "Failed to post bag analysis notification", error)
        }.getOrDefault(false)
    }

    private fun hasPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntentFlags(): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return base or PendingIntent.FLAG_IMMUTABLE
    }

    companion object {
        private const val TAG = "BagAnalysisNotifier"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_REQUEST_CODE = 0
        private const val NOTIFICATION_TIMEOUT_MS = 6L * 24L * 60L * 60L * 1_000L

        fun cancel(context: Context, workId: String) {
            NotificationManagerCompat.from(context)
                .cancel(notificationTagForWork(workId), NOTIFICATION_ID)
        }
    }
}

internal fun notificationTagForWork(workId: String): String = "bag-analysis:$workId"

internal fun isNotificationChannelEnabled(importance: Int?): Boolean =
    importance != null && importance != NotificationManager.IMPORTANCE_NONE
