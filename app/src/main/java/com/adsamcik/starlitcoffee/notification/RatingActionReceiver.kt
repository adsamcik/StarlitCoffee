package com.adsamcik.starlitcoffee.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate
import dev.tracebox.api.argument

private object RatingActionReceiverTraceboxTemplates {
    val IGNORING_QUICK_RATE_BROADCAST_WITH_INVALID_PAYLOAD = LogTemplate.of("Ignoring quick-rate broadcast with invalid payload: id={} rating={}")
    val FAILED_TO_APPLY_QUICK_RATING_FOR_BREW = LogTemplate.of("Failed to apply quick rating {} for brew {}")
    val BREW_LOG_NO_LONGER_EXISTS_SKIPPING_QUICK = LogTemplate.of("Brew log {} no longer exists — skipping quick rating")
}

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
        if (brewLogId <= 0L || ratingValue !in 1..4) {
            Tracebox.log.warn(
                RatingActionReceiverTraceboxTemplates.IGNORING_QUICK_RATE_BROADCAST_WITH_INVALID_PAYLOAD,
                argument(brewLogId),
                argument(ratingValue),
            )
            return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                if (applyRating(appContext, brewLogId, ratingValue.toFloat())) {
                    RatingReminderScheduler(appContext).cancelReminder(brewLogId)
                }
            } catch (error: Exception) {
                Tracebox.log.error(
                    error,
                    RatingActionReceiverTraceboxTemplates.FAILED_TO_APPLY_QUICK_RATING_FOR_BREW,
                    argument(ratingValue),
                    argument(brewLogId),
                )
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun applyRating(context: Context, brewLogId: Long, rating: Float): Boolean {
        val database = AppDatabase.getInstance(context)
        val repository = BrewLogRepository(
            database = database,
            brewLogDao = database.brewLogDao(),
            flavorTagDao = database.flavorTagDao(),
        )
        val existing = repository.getLogById(brewLogId) ?: run {
            Tracebox.log.warn(
                RatingActionReceiverTraceboxTemplates.BREW_LOG_NO_LONGER_EXISTS_SKIPPING_QUICK,
                argument(brewLogId),
            )
            return false
        }
        // Preserve any freeform notes the user may have already written; only
        // overwrite the rating itself.
        repository.updateRating(brewLogId, rating, existing.freeformNotes)
        return true
    }

    companion object {
        const val ACTION_QUICK_RATE = "com.adsamcik.starlitcoffee.action.QUICK_RATE"
        const val EXTRA_BREW_LOG_ID = "brew_log_id"
        const val EXTRA_RATING_VALUE = "rating_value"

        // Receiver-owned coroutine scope so it survives the brief `goAsync()`
        // window without leaking work into other components' lifecycles.
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
}
