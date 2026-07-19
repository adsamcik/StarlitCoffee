package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import com.adsamcik.starlitcoffee.notification.AndroidBagAnalysisNotifier
import kotlinx.coroutines.flow.first

class BagAnalysisNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val workId = inputData.getString(KEY_WORK_ID).orEmpty()
        if (workId.isBlank()) return Result.failure()

        val workInfoFlow = BagExtractionScheduler.workInfoFlow(applicationContext, workId)
        val workInfo = if (inputData.getBoolean(KEY_WAIT_FOR_TERMINAL, false)) {
            workInfoFlow.first { info -> info == null || info.state.isFinished }
        } else {
            workInfoFlow.first()
        }
        if (shouldReconcileTerminalWork(workInfo?.state)) {
            BagExtractionScheduler.reconcilePersistedState(applicationContext)
        }
        if (!BagExtractionScheduler.isCompletionNotificationRequested(applicationContext, workId) ||
            BagExtractionScheduler.isCompletionNotificationDelivered(applicationContext, workId)
        ) {
            return Result.success()
        }
        val storedResult = BagExtractionResultStore.read(applicationContext, workId)
        if (workInfo == null && storedResult == null) return Result.success()
        return deliverNotification(workInfo, storedResult, workId)
    }

    private fun deliverNotification(
        workInfo: WorkInfo?,
        storedResult: StoredBagExtractionResult?,
        workId: String,
    ): Result {
        if (storedResult == null &&
            workInfo?.let { notificationWorkDisposition(it.state) } != NotificationWorkDisposition.DELIVER
        ) {
            return Result.success()
        }
        if (!BagExtractionScheduler.claimCompletionNotificationDelivery(applicationContext, workId)) {
            return if (
                BagExtractionScheduler.isCompletionNotificationDelivered(applicationContext, workId)
            ) {
                Result.success()
            } else {
                Result.retry()
            }
        }
        val notifier = AndroidBagAnalysisNotifier(applicationContext)
        val successful = storedResult?.successful ?: (workInfo?.state == WorkInfo.State.SUCCEEDED)
        val reviewContext = storedResult?.reviewContext
            ?: BagExtractionScheduler.reviewContextForWork(applicationContext, workId)
            ?: decodeBagReviewContext(
                workInfo?.outputData?.getString(BagExtractionWorker.KEY_REVIEW_CONTEXT_JSON),
            )
        val resultJson = storedResult?.resultJson
            ?: workInfo?.outputData?.getString(BagExtractionWorker.KEY_RESULT_JSON)
        val posted = if (successful) {
            val displayName = resultJson?.let { json ->
                runCatching {
                    decodeBagExtractionResult(json).fieldEvidence["name"]?.value
                }.onFailure { error ->
                    Log.e(TAG, "Failed to decode completed bag analysis for notification", error)
                }.getOrNull()
            }
                notifier.notifyComplete(workId, displayName, reviewContext)
        } else {
            notifier.notifyFailed(workId, reviewContext)
        }

        if (posted) {
            BagExtractionScheduler.markCompletionNotificationDelivered(applicationContext, workId)
        } else {
            BagExtractionScheduler.releaseCompletionNotificationClaim(applicationContext, workId)
            BagReviewQueue.enqueue(applicationContext, workId, reviewContext)
        }
        return Result.success()
    }

    companion object {
        const val KEY_WORK_ID = "work_id"
        const val KEY_WAIT_FOR_TERMINAL = "wait_for_terminal"
        private const val TAG = "BagAnalysisNotification"
    }
}

internal enum class NotificationWorkDisposition {
    DELIVER,
    FINISH,
}

internal fun notificationWorkDisposition(state: WorkInfo.State): NotificationWorkDisposition =
    if (state.isFinished && state != WorkInfo.State.CANCELLED) {
        NotificationWorkDisposition.DELIVER
    } else {
        NotificationWorkDisposition.FINISH
    }

internal fun shouldReconcileTerminalWork(state: WorkInfo.State?): Boolean =
    state?.isFinished == true
