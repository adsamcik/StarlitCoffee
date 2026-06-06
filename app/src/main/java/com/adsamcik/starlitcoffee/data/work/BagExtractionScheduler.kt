package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object BagExtractionScheduler {
    fun enqueue(
        context: Context,
        photoUrisCsv: String,
        knownValuesJson: String,
        runLlm: Boolean,
    ) {
        val request = OneTimeWorkRequestBuilder<BagExtractionWorker>()
            .setInputData(
                workDataOf(
                    BagExtractionWorker.KEY_PHOTO_URIS to photoUrisCsv,
                    BagExtractionWorker.KEY_KNOWN_VALUES to knownValuesJson,
                    BagExtractionWorker.KEY_RUN_LLM to runLlm,
                ),
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            BagExtractionWorker.UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BagExtractionWorker.UNIQUE_WORK)
    }

    fun workInfoFlow(context: Context): Flow<WorkInfo?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(BagExtractionWorker.UNIQUE_WORK)
            .map { it.firstOrNull() }

    /**
     * One-shot read of the latest unique-work [WorkInfo]. Reads from WorkManager's
     * persisted store, so it survives Activity/ViewModel recreation and process
     * death — used by the notification deep link to recover a completed result.
     */
    suspend fun currentWorkInfo(context: Context): WorkInfo? = workInfoFlow(context).first()
}
