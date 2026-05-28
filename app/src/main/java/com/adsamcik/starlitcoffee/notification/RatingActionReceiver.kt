package com.adsamcik.starlitcoffee.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles taps on the emoji rating buttons inside the rating-reminder
 * notification. Writes the chosen rating directly to the [BrewLogEntity] via
 * [BrewLogRepository] and dismisses the notification.
 *
 * Uses `goAsync()` so the broadcast receiver is allowed enough time to finish
 * the database update before the runtime tears the process down.
 */
class RatingActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUICK_RATE) return
        val brewLogId = intent.getLongExtra(EXTRA_BREW_LOG_ID, -1L)
        val ratingValue = intent.getIntExtra(EXTRA_RATING_VALUE, -1)
        if (brewLogId <= 0L || ratingValue !in 1..5) {
            Log.w(TAG, "Ignoring quick-rate broadcast with invalid payload: id=$brewLogId rating=$ratingValue")
            return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                applyRating(appContext, brewLogId, ratingValue.toFloat())
                NotificationManagerCompat.from(appContext)
                    .cancel(RatingReminderReceiver.notificationIdFor(brewLogId))
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.e(TAG, "Failed to apply quick rating $ratingValue for brew $brewLogId", error)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun applyRating(context: Context, brewLogId: Long, rating: Float) {
        val database = AppDatabase.getInstance(context)
        val repository = BrewLogRepository(
            database = database,
            brewLogDao = database.brewLogDao(),
            flavorTagDao = database.flavorTagDao(),
        )
        val existing = repository.getLogById(brewLogId) ?: run {
            Log.w(TAG, "Brew log $brewLogId no longer exists — skipping quick rating")
            return
        }
        // Preserve any freeform notes the user may have already written; only
        // overwrite the rating itself.
        repository.updateRating(brewLogId, rating, existing.freeformNotes)
    }

    companion object {
        private const val TAG = "RatingActionRecv"
        const val ACTION_QUICK_RATE = "com.adsamcik.starlitcoffee.action.QUICK_RATE"
        const val EXTRA_BREW_LOG_ID = "brew_log_id"
        const val EXTRA_RATING_VALUE = "rating_value"

        // Receiver-owned coroutine scope so it survives the brief `goAsync()`
        // window without leaking work into other components' lifecycles.
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}
