package com.adsamcik.starlitcoffee.data.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
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
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.ScanProgress
import com.adsamcik.starlitcoffee.util.ScanStage
import kotlinx.coroutines.CancellationException

class BagExtractionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private var latestProgress: ScanProgress? = null
    private var latestPreviewJson: String? = null
    private val foregroundNotificationId = foregroundNotificationId(id)

    override suspend fun doWork(): Result {
        val workId = id.toString()
        val manifestPath = inputData.getString(KEY_INPUT_MANIFEST)
        val fallbackReviewContext =
            decodeBagReviewContext(inputData.getString(KEY_REVIEW_CONTEXT_JSON))
        // WorkManager may restart this worker after the result file is durable but
        // before its terminal state is committed. Replaying the immutable result
        // keeps that window idempotent; reconciliation owns manifest deletion.
        return replayStoredBagExtractionResultOrRun(
            storedResult = BagExtractionResultStore.read(applicationContext, workId),
            fallbackReviewContext = fallbackReviewContext,
            onReplay = { replay -> completeTerminalResult(workId, replay) },
            runExtraction = {
                runExtraction(
                    workId = workId,
                    manifestPath = manifestPath,
                    fallbackReviewContext = fallbackReviewContext,
                )
            },
        )
    }

    private suspend fun runExtraction(
        workId: String,
        manifestPath: String?,
        fallbackReviewContext: BagReviewContext?,
    ): Result {
        var photosCsv = ""
        var reviewContext = fallbackReviewContext
        return try {
            val input = BagExtractionInputStore.read(
                applicationContext,
                checkNotNull(manifestPath) { "Bag extraction input manifest is missing" },
            )
            photosCsv = input.photoUrisCsv
            reviewContext = input.reviewContext ?: reviewContext
            val photoUris = photosCsv.split(",").map(String::trim).filter(String::isNotBlank)
            if (photoUris.isEmpty()) {
                val emptyResult = BagPhotoProcessingResult(capturedPhotoUris = photosCsv)
                val storedResult = BagExtractionResultStore.writeIfAbsent(
                    applicationContext,
                    workId,
                    successful = true,
                    resultJson = emptyResult.encodeToStoredJson(),
                    reviewContext = reviewContext,
                )
                return completeTerminalResult(
                    workId = workId,
                    replay = storedResult.toTerminalReplay(reviewContext),
                )
            }
            publishPreview(BagPhotoProcessingResult(capturedPhotoUris = photosCsv))

            // Real work ahead (OCR + on-device LLM passes, potentially minutes on
            // a slow device). Promote to a foreground service so the OS keeps this
            // process alive while the user is in another app — the LLM runs in the
            // Mindlayer process over a binder, and if our process is reclaimed the
            // binder drops and the whole pipeline restarts from scratch.
            enterForeground()

            val knownValues = decodeKnownFieldValues(input.knownValuesJson)
            val runLlm = inputData.getBoolean(KEY_RUN_LLM, true)
            val result = buildExtractor()
                .extract(
                    photoUris = photoUris,
                    knownFieldValues = knownValues,
                    runLlm = runLlm,
                    onProgress = ::publishProgress,
                    onPartialResult = ::publishPreview,
                )
                .copy(capturedPhotoUris = photosCsv)
            val storedResult = BagExtractionResultStore.writeIfAbsent(
                applicationContext,
                workId,
                successful = true,
                resultJson = result.encodeToStoredJson(),
                reviewContext = reviewContext,
            )
            completeTerminalResult(
                workId = workId,
                replay = storedResult.toTerminalReplay(reviewContext),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.e(TAG, "Bag extraction worker failed", error)
            val failureResult = BagPhotoProcessingResult(
                capturedPhotoUris = photosCsv,
                llmStatus = com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus.UNAVAILABLE,
            )
            try {
                val storedResult = BagExtractionResultStore.writeIfAbsent(
                    applicationContext,
                    workId,
                    successful = false,
                    resultJson = failureResult.encodeToStoredJson(),
                    reviewContext = reviewContext,
                )
                completeTerminalResult(
                    workId = workId,
                    replay = storedResult.toTerminalReplay(reviewContext),
                )
            } catch (@Suppress("TooGenericExceptionCaught") persistenceError: Exception) {
                Log.e(TAG, "Could not persist failed bag extraction result", persistenceError)
                Result.retry()
            }
        }
    }

    private fun completeTerminalResult(
        workId: String,
        replay: BagExtractionTerminalReplay,
    ): Result {
        BagExtractionScheduler.enqueueCompletionNotification(
            applicationContext,
            workId,
            waitForTerminal = true,
        )
        return if (replay.successful) {
            Result.success(replay.outputData)
        } else {
            Result.failure(replay.outputData)
        }
    }

    /**
     * Promote the worker to a foreground service for the duration of the
     * pipeline. Best-effort: if the platform refuses the promotion (e.g. a
     * background-start restriction on a corner-case launch path) we log it and
     * keep running as ordinary background work rather than failing the scan —
     * producing the result reliably matters more than the service guarantee.
     */
    private suspend fun enterForeground() {
        @Suppress("TooGenericExceptionCaught")
        try {
            setForeground(getForegroundInfo())
        } catch (error: Exception) {
            Log.w(
                TAG,
                "Could not promote bag extraction to a foreground service; " +
                    "continuing as background work",
                error,
            )
        }
    }

    /**
     * Mirror a pipeline [progress] snapshot to both observers: WorkManager's
     * progress data (read by the in-app analyzing screen via WorkInfo) and the
     * ongoing foreground-service notification. The worker runs as a foreground
     * service for the whole pipeline, so this low-priority notification is
     * visible throughout (not only once backgrounded); re-posting under the same
     * id updates the stage label and progress bar in place.
     */
    private fun publishProgress(progress: ScanProgress) {
        latestProgress = progress
        publishWorkProgress(progress)
        @Suppress("TooGenericExceptionCaught")
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(foregroundNotificationId, buildProgressNotification(progress))
        } catch (security: SecurityException) {
            Log.w(TAG, "Missing notification permission for scan progress update", security)
        } catch (error: Exception) {
            Log.w(TAG, "Failed to update scan progress notification", error)
        }
    }

    private fun publishPreview(result: BagPhotoProcessingResult) {
        latestPreviewJson = result.encodeForProgressJson()
        if (latestPreviewJson == null) {
            Log.w(TAG, "Bag analysis preview is too large for WorkManager progress data")
        }
        latestProgress?.let(::publishWorkProgress)
    }

    private fun publishWorkProgress(progress: ScanProgress) {
        val data = latestPreviewJson?.let { previewJson ->
            workDataOf(
                KEY_PROGRESS_STAGE to progress.stage.name,
                KEY_PROGRESS_INDEX to progress.stepIndex,
                KEY_PROGRESS_COUNT to progress.stepCount,
                KEY_PROGRESS_PREVIEW_JSON to previewJson,
            )
        } ?: workDataOf(
            KEY_PROGRESS_STAGE to progress.stage.name,
            KEY_PROGRESS_INDEX to progress.stepIndex,
            KEY_PROGRESS_COUNT to progress.stepCount,
        )
        setProgressAsync(data)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationChannels.ensureBagScanProgressChannel(applicationContext)
        return buildForegroundInfo(buildProgressNotification(progress = null))
    }

    /**
     * Wrap the progress [notification] in a [ForegroundInfo]. On API 29+ the
     * service is tagged `dataSync` (local on-device processing of user data);
     * Android 14+ rejects a typeless foreground service, and the matching
     * `FOREGROUND_SERVICE_DATA_SYNC` permission plus the `SystemForegroundService`
     * type merge are declared in the manifest. `shortService` is deliberately not
     * used — its hard ~3-minute cap conflicts with the long, device-dependent LLM
     * inference budget.
     */
    private fun buildForegroundInfo(notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                foregroundNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(foregroundNotificationId, notification)
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
        const val KEY_INPUT_MANIFEST = "input_manifest"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_GENERATION_ID = "generation_id"
        const val KEY_RUN_LLM = "run_llm"
        const val KEY_RESULT_JSON = "result_json"
        const val KEY_RESULT_STORED = "result_stored"
        const val KEY_REVIEW_CONTEXT_JSON = "review_context_json"
        const val KEY_NOTIFY_ON_COMPLETE = "notify_on_complete"
        const val KEY_PROGRESS_STAGE = "progress_stage"
        const val KEY_PROGRESS_INDEX = "progress_index"
        const val KEY_PROGRESS_COUNT = "progress_count"
        const val KEY_PROGRESS_PREVIEW_JSON = "progress_preview_json"
        private const val TAG = "BagExtractionWorker"
        private const val FOREGROUND_NOTIFICATION_ID_NAMESPACE = 0x20000000
        private const val FOREGROUND_NOTIFICATION_ID_MASK = 0x1FFFFFFF

        internal fun shouldNotifyOnCompletion(inputData: androidx.work.Data): Boolean =
            inputData.getBoolean(KEY_NOTIFY_ON_COMPLETE, false)

        internal fun foregroundNotificationId(workId: java.util.UUID): Int =
            (workId.hashCode() and FOREGROUND_NOTIFICATION_ID_MASK) or FOREGROUND_NOTIFICATION_ID_NAMESPACE
    }
}

internal fun bagExtractionTerminalOutput(reviewContext: BagReviewContext?): androidx.work.Data =
    androidx.work.Data.Builder()
        .putBoolean(BagExtractionWorker.KEY_RESULT_STORED, true)
        .also { builder ->
            encodeBagReviewContext(reviewContext)?.let { encoded ->
                builder.putString(BagExtractionWorker.KEY_REVIEW_CONTEXT_JSON, encoded)
            }
        }
        .build()

internal data class BagExtractionTerminalReplay(
    val successful: Boolean,
    val outputData: androidx.work.Data,
)

internal fun StoredBagExtractionResult.toTerminalReplay(
    fallbackReviewContext: BagReviewContext?,
): BagExtractionTerminalReplay = BagExtractionTerminalReplay(
    successful = successful,
    outputData = bagExtractionTerminalOutput(reviewContext ?: fallbackReviewContext),
)

internal inline fun <T> replayStoredBagExtractionResultOrRun(
    storedResult: StoredBagExtractionResult?,
    fallbackReviewContext: BagReviewContext?,
    onReplay: (BagExtractionTerminalReplay) -> T,
    runExtraction: () -> T,
): T = storedResult
    ?.toTerminalReplay(fallbackReviewContext)
    ?.let(onReplay)
    ?: runExtraction()

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
