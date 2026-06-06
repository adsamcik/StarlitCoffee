package com.adsamcik.starlitcoffee.notification

import android.Manifest
import android.annotation.SuppressLint
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
    fun notifyComplete(displayName: String?)
}

/** Default no-op used in unit tests and when no platform context is available. */
object NoOpBagAnalysisNotifier : BagAnalysisNotifier {
    override fun notifyComplete(displayName: String?) = Unit
}

/**
 * Real notifier. Tapping the notification opens [MainActivity] deep-linked to
 * the analyzed coffee-bag form (the result is held by the process-scoped
 * BrewViewModel, so the intent carries only a flag — see [DeepLinkBus]).
 */
class AndroidBagAnalysisNotifier(
    private val context: Context,
) : BagAnalysisNotifier {

    @SuppressLint("MissingPermission")
    override fun notifyComplete(displayName: String?) {
        if (!hasPostNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — cannot post bag analysis notification")
            return
        }
        NotificationChannels.ensureBagAnalysisChannel(context)

        val title = context.getString(R.string.notif_bag_analysis_title)
        val body = if (!displayName.isNullOrBlank()) {
            context.getString(R.string.format_notif_bag_analysis_body, displayName)
        } else {
            context.getString(R.string.notif_bag_analysis_body)
        }

        val contentIntent = MainActivity.buildBagAnalysisIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
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
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            // Permission is checked above; runCatching also absorbs the
            // SecurityException that would be thrown if the user revoked
            // POST_NOTIFICATIONS between the check and this call.
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.onFailure { error ->
            Log.e(TAG, "Failed to post bag analysis notification", error)
        }
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
        private const val NOTIFICATION_ID = 20_001
        private const val REQUEST_CODE = 20_001
    }
}
