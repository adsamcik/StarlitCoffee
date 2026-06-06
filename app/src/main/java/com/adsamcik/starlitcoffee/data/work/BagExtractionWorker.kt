package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
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
                .extract(photoUris, knownValues, runLlm)
                .copy(capturedPhotoUris = photosCsv)

            Result.success(workDataOf(KEY_RESULT_JSON to result.encodeToJson()))
        } catch (error: CancellationException) {
            throw error
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.e(TAG, "Bag extraction worker failed", error)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationChannels.ensureBagScanProgressChannel(applicationContext)
        val notification = NotificationCompat.Builder(
            applicationContext,
            NotificationChannels.BAG_SCAN_PROGRESS_ID,
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.notif_scan_progress_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(0, 0, true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
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
        const val UNIQUE_WORK = "bag_extraction"

        private const val TAG = "BagExtractionWorker"
        private const val NOTIFICATION_ID = 20_002
    }
}
