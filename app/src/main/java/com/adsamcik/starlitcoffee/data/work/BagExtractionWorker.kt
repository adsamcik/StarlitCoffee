package com.adsamcik.starlitcoffee.data.work

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.network.OpenFoodFactsClient
import com.adsamcik.starlitcoffee.data.network.SafeQrLinkMetadataExplorer
import com.adsamcik.starlitcoffee.data.network.llm.LlmResultCache
import com.adsamcik.starlitcoffee.data.network.llm.StubLlmInferenceProvider
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.notification.NotificationChannels
import com.adsamcik.starlitcoffee.scan.BagPhotoExtractor
import com.adsamcik.starlitcoffee.util.ScanProgress
import com.adsamcik.starlitcoffee.util.ScanStage
import kotlinx.coroutines.CancellationException

class BagExtractionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val photosCsv = inputData.getString(KEY_PHOTO_URIS).orEmpty()
            val photoUris = photosCsv.split(",").map(String::trim).filter(String::isNotBlank)
            if (photoUris.isEmpty()) return Result.success()

            val knownValues = decodeKnownFieldValues(inputData.getString(KEY_KNOWN_VALUES))
            val runLlm = inputData.getBoolean(KEY_RUN_LLM, true)
            val result = buildExtractor()
                .extract(photoUris, knownValues, runLlm) { progress -> publishProgress(progress) }
                .copy(capturedPhotoUris = photosCsv)

            Result.success(workDataOf(KEY_RESULT_JSON to result.encodeToJson()))
        } catch (error: CancellationException) {
            throw error
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.e(TAG, "Bag extraction worker failed", error)
            Result.failure()
        }
    }

    /**
     * Mirror a pipeline [progress] snapshot to both observers: WorkManager's
     * progress data (read by the in-app analyzing screen via WorkInfo) and the
     * ongoing foreground notification (visible once the user backgrounds the
     * scan). The notification re-post reuses the same id so it updates in place.
     */
    private fun publishProgress(progress: ScanProgress) {
        setProgressAsync(
            workDataOf(
                KEY_PROGRESS_STAGE to progress.stage.name,
                KEY_PROGRESS_INDEX to progress.stepIndex,
                KEY_PROGRESS_COUNT to progress.stepCount,
            ),
        )
        @Suppress("TooGenericExceptionCaught")
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, buildProgressNotification(progress))
        } catch (security: SecurityException) {
            Log.w(TAG, "Missing notification permission for scan progress update", security)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to update scan progress notification", error)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationChannels.ensureBagScanProgressChannel(applicationContext)
        return ForegroundInfo(NOTIFICATION_ID, buildProgressNotification(progress = null))
    }

    private fun buildProgressNotification(progress: ScanProgress?): Notification {
        NotificationChannels.ensureBagScanProgressChannel(applicationContext)
        val builder = NotificationCompat.Builder(
            applicationContext,
            NotificationChannels.BAG_SCAN_PROGRESS_ID,
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.notif_scan_progress_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (progress == null || progress.stepCount <= 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setContentText(applicationContext.getString(progress.stage.notificationLabelRes()))
            builder.setProgress(progress.stepCount, progress.stepIndex, false)
        }
        return builder.build()
    }

    private fun buildExtractor(): BagPhotoExtractor {
        val db = AppDatabase.getInstance(applicationContext)
        val app = applicationContext as? StarlitCoffeeApp
        return BagPhotoExtractor(
            appContext = applicationContext,
            coffeeBagRepository = CoffeeBagRepository(db.coffeeBagDao()),
            qrLinkMetadataExplorer = SafeQrLinkMetadataExplorer(),
            llmProvider = app?.llmProvider ?: StubLlmInferenceProvider(),
            ocrService = app?.ocrService,
            userBarcodeStemDao = db.userBarcodeStemDao(),
            openFoodFactsLookup = { OpenFoodFactsClient.lookupBarcode(it) },
            llmCache = LlmResultCache(),
        )
    }

    companion object {
        const val KEY_PHOTO_URIS = "photo_uris"
        const val KEY_RUN_LLM = "run_llm"
        const val KEY_KNOWN_VALUES = "known_values"
        const val KEY_RESULT_JSON = "result_json"
        const val KEY_PROGRESS_STAGE = "progress_stage"
        const val KEY_PROGRESS_INDEX = "progress_index"
        const val KEY_PROGRESS_COUNT = "progress_count"
        const val UNIQUE_WORK = "bag_extraction"

        private const val TAG = "BagExtractionWorker"
        private const val NOTIFICATION_ID = 20_002
    }
}

/**
 * Notification body label for a pipeline stage. Kept here (worker layer) so the
 * [ScanStage] enum stays UI-resource free; the in-app analyzing screen has its
 * own richer mapping.
 */
private fun ScanStage.notificationLabelRes(): Int = when (this) {
    ScanStage.OCR -> R.string.scan_stage_ocr
    ScanStage.BARCODE_LOOKUP -> R.string.scan_stage_barcode
    ScanStage.LLM_EXTRACT -> R.string.scan_stage_llm
    ScanStage.LABEL_CROP -> R.string.scan_stage_crop
    ScanStage.VISION -> R.string.scan_stage_vision
    ScanStage.COMBINING -> R.string.scan_stage_combine
    ScanStage.FINALIZING -> R.string.scan_stage_finalizing
}
