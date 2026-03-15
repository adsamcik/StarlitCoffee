package com.adsamcik.starlitcoffee.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adsamcik.starlitcoffee.MainActivity
import com.adsamcik.starlitcoffee.R
import java.util.concurrent.TimeUnit

class RatingReminderWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val logId = inputData.getLong(KEY_LOG_ID, -1L)
        if (logId == -1L) return Result.failure()

        ensureChannel(applicationContext)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RATE_BREW_ID, logId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            logId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("How was your coffee? ☕")
            .setContentText("Tap to rate your brew while it's fresh in your memory.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_BASE + logId.toInt(), notification)

        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "brew_rating_reminder"
        private const val NOTIFICATION_ID_BASE = 2000
        const val KEY_LOG_ID = "log_id"
        const val EXTRA_RATE_BREW_ID = "rate_brew_id"
        private const val DELAY_MINUTES = 15L

        fun schedule(context: Context, logId: Long) {
            val work = OneTimeWorkRequestBuilder<RatingReminderWorker>()
                .setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_LOG_ID to logId))
                .addTag("rating_reminder_$logId")
                .build()
            WorkManager.getInstance(context).enqueue(work)
        }

        fun cancel(context: Context, logId: Long) {
            WorkManager.getInstance(context)
                .cancelAllWorkByTag("rating_reminder_$logId")
        }

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Brew Rating Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminds you to rate your brew after drinking"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
