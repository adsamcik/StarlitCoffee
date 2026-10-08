package com.adsamcik.starlitcoffee.data.work

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionRuntime
import kotlinx.coroutines.CancellationException

class BrewClockRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        BrewSessionRuntime.create(applicationContext).reconcileRecoverableSessions(
            useMonotonicClock = inputData.getBoolean(KEY_MONOTONIC, false))
        Result.success()
    } catch (error: CancellationException) { throw error
    } catch (_: Exception) { Result.retry() }
}

/** Android clears alarms at boot and on precise-alarm revocation; Room remains authoritative. */
class BrewClockRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> enqueueBrewClockRecovery(context, useMonotonicClock = false)
            Intent.ACTION_TIME_CHANGED -> enqueueBrewClockRecovery(context, useMonotonicClock = true)
        }
    }
}

fun enqueueBrewClockRecovery(context: Context, useMonotonicClock: Boolean = false) {
    // Separate names ensure a clock-change request cannot be replaced by an unrelated permission refresh.
    WorkManager.getInstance(context).enqueueUniqueWork("brew-clock-recovery:$useMonotonicClock",
        ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<BrewClockRecoveryWorker>()
            .setInputData(workDataOf(KEY_MONOTONIC to useMonotonicClock)).build())
}

private const val KEY_MONOTONIC = "reconcile_monotonic_clock"
