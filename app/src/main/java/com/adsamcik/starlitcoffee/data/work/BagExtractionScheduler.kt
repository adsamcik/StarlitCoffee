package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.adsamcik.starlitcoffee.notification.AndroidBagAnalysisNotifier
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.BagPhotoReviewUris
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

internal enum class ManifestReconciliationState {
    PRESENT,
    EXPIRED,
    MISSING,
}

internal enum class PersistedWorkReconciliationAction {
    KEEP,
    RESCHEDULE,
    SURFACE_FAILURE,
    EXPIRE,
}

internal enum class TerminalWorkReconciliationAction {
    RETAIN,
    HANDLE_RESULT_AND_CLEAN,
    SURFACE_FAILURE_AND_CLEAN,
    DISCARD_CANCELLED,
}

internal fun persistedWorkReconciliationAction(
    workInfoPresent: Boolean,
    manifestState: ManifestReconciliationState,
): PersistedWorkReconciliationAction = when {
    workInfoPresent -> PersistedWorkReconciliationAction.KEEP
    manifestState == ManifestReconciliationState.PRESENT -> PersistedWorkReconciliationAction.RESCHEDULE
    manifestState == ManifestReconciliationState.EXPIRED -> PersistedWorkReconciliationAction.EXPIRE
    else -> PersistedWorkReconciliationAction.SURFACE_FAILURE
}

internal fun terminalWorkReconciliationAction(
    workState: WorkInfo.State?,
    durableResultPresent: Boolean,
): TerminalWorkReconciliationAction = when {
    workState == null || !workState.isFinished -> TerminalWorkReconciliationAction.RETAIN
    workState == WorkInfo.State.CANCELLED -> TerminalWorkReconciliationAction.DISCARD_CANCELLED
    durableResultPresent -> TerminalWorkReconciliationAction.HANDLE_RESULT_AND_CLEAN
    else -> TerminalWorkReconciliationAction.SURFACE_FAILURE_AND_CLEAN
}

internal fun performTerminalResultReconciliation(
    handleDurableResult: () -> Unit,
    deleteManifest: () -> Boolean,
    clearWorkState: () -> Unit,
): Boolean {
    handleDurableResult()
    if (!deleteManifest()) return false
    clearWorkState()
    return true
}

internal fun shouldActivateDurablyEnqueuedWork(
    pending: Boolean,
    manifestOwned: Boolean,
    generationIsLatest: Boolean,
): Boolean = pending && manifestOwned && generationIsLatest

internal fun expirableWorkIds(
    expiredResultWorkIds: Set<String>,
    expiredManifestWorkIds: Set<String>,
    protectedWorkIds: Set<String>,
): Set<String> =
    (expiredResultWorkIds + expiredManifestWorkIds) - protectedWorkIds

internal suspend fun performFailureAtomicEnqueue(
    persistPending: () -> Unit,
    writeManifest: () -> Unit,
    enqueueDurably: suspend () -> Unit,
    activate: suspend () -> Unit,
    rollbackFailedEnqueue: suspend () -> Unit,
) {
    try {
        persistPending()
        writeManifest()
        enqueueDurably()
    } catch (error: Exception) {
        rollbackFailedEnqueue()
        throw error
    }
    activate()
}

internal data class ExpiredWorkCleanupActions(
    val deleteManifest: () -> Unit,
    val deleteResult: () -> Unit,
    val clearMetadata: () -> Unit,
    val removeReviewIds: () -> Unit,
    val cancelNotification: () -> Unit,
)

internal fun performExpiredWorkCleanup(
    actions: ExpiredWorkCleanupActions,
    onFailure: (Exception) -> Unit = {},
) {
    listOf(
        actions.deleteManifest,
        actions.deleteResult,
        actions.clearMetadata,
        actions.removeReviewIds,
        actions.cancelNotification,
    ).forEach { action ->
        try {
            action()
        } catch (error: Exception) {
            onFailure(error)
        }
    }
}

object BagExtractionScheduler {
    suspend fun enqueue(
        context: Context,
        photoUrisCsv: String,
        knownValuesJson: String,
        runLlm: Boolean,
        sessionId: String,
        generationId: String,
        reviewContext: BagReviewContext? = BagReviewContext.addNew(),
        notifyOnCompletion: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val input = BagExtractionInput(
            photoUrisCsv = photoUrisCsv,
            knownValuesJson = knownValuesJson,
            runLlm = runLlm,
            sessionId = sessionId,
            generationId = generationId,
            reviewContext = reviewContext,
            notifyOnCompletion = notifyOnCompletion,
            createdAtMillis = System.currentTimeMillis(),
        )
        val workUuid = UUID.randomUUID()
        val workId = workUuid.toString()
        val manifestPath = BagExtractionInputStore.allocatePath(context, workId)
        val request = buildRequest(manifestPath, input, workUuid)
        withContext(NonCancellable) {
            performFailureAtomicEnqueue(
                persistPending = {
                    persistPendingEnqueue(context, workId, manifestPath, input)
                },
                writeManifest = {
                    BagExtractionInputStore.write(context, manifestPath, input)
                },
                enqueueDurably = {
                    enqueueDurably(context, request)
                },
                activate = {
                    activateDurablyEnqueuedWork(context, workId, manifestPath, input)
                },
                rollbackFailedEnqueue = {
                    rollbackFailedEnqueue(context, workId, manifestPath, input)
                },
            )
        }
        workId
    }

    fun cancel(
        context: Context,
        workId: String? = activeWorkId(context),
        cancelWork: Boolean = true,
    ) {
        workId?.takeIf { cancelWork }?.let { id ->
            runCatching { UUID.fromString(id) }
                .getOrNull()
                ?.let { WorkManager.getInstance(context).cancelWorkById(it) }
        }
        val manifestCleaned = workId?.let { id -> cleanupInputManifest(context, id) } ?: true
        workId?.takeIf { manifestCleaned }?.let {
            clearWorkState(
                context = context,
                workId = it,
                preserveLatestGeneration = !cancelWork,
            )
        }
        if (cancelWork && workId != null) {
            BagExtractionResultStore.delete(context, workId)
            BagReviewQueue.removeEverywhere(context, workId)
            AndroidBagAnalysisNotifier.cancel(context, workId)
            forgetLegacyWorkSession(context, workId)
        }
    }

    fun cancelSession(context: Context, sessionId: String) {
        val workId = workIdForSession(context, sessionId)
        if (workId != null) {
            cancel(context, workId)
        } else {
            DeliveryState.preferences(context)
                .edit()
                .remove(DeliveryState.sessionGenerationKey(sessionId))
                .apply()
        }
    }

    /** Persists a completion notification request made after extraction has started. */
    fun requestCompletionNotification(context: Context, workId: String) {
        synchronized(deliveryStateLock) {
            val preferences = DeliveryState.preferences(context)
            val delivered = preferences.getBoolean(
                DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId),
                false,
            )
            preferences.edit()
                .putBoolean(DeliveryState.notificationKey(KEY_NOTIFY_ON_COMPLETE, workId), true)
                .putBoolean(
                    DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId),
                    delivered,
                )
                .commit()
        }
        enqueueCompletionNotification(context, workId, waitForTerminal = false)
    }

    fun isCompletionNotificationRequested(context: Context, workId: String): Boolean =
        DeliveryState.preferences(context)
            .getBoolean(DeliveryState.notificationKey(KEY_NOTIFY_ON_COMPLETE, workId), false)

    fun isCompletionNotificationDelivered(context: Context, workId: String): Boolean =
        DeliveryState.preferences(context)
            .getBoolean(DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId), false)

    fun markCompletionNotificationDelivered(context: Context, workId: String) {
        synchronized(deliveryStateLock) {
            DeliveryState.preferences(context)
                .edit()
                .putBoolean(DeliveryState.notificationKey(KEY_NOTIFY_ON_COMPLETE, workId), true)
                .putBoolean(DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId), true)
                .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_CLAIMED_AT, workId))
                .commit()
        }
    }

    fun claimCompletionNotificationDelivery(
        context: Context,
        workId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(deliveryStateLock) {
        val preferences = DeliveryState.preferences(context)
        if (preferences.getBoolean(DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId), false)) {
            return@synchronized false
        }
        val claimKey = DeliveryState.notificationKey(KEY_NOTIFICATION_CLAIMED_AT, workId)
        val claimedAt = preferences.getLong(claimKey, 0L)
        if (claimedAt > 0L && nowMillis - claimedAt < NOTIFICATION_CLAIM_TIMEOUT_MS) {
            return@synchronized false
        }
        preferences.edit().putLong(claimKey, nowMillis).commit()
    }

    fun releaseCompletionNotificationClaim(context: Context, workId: String) {
        synchronized(deliveryStateLock) {
            DeliveryState.preferences(context)
                .edit()
                .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_CLAIMED_AT, workId))
                .commit()
        }
    }

    /** Clears the persisted background-delivery state after its review is opened. */
    fun consumeCompletionNotificationRequest(context: Context, workId: String) {
        DeliveryState.preferences(context)
            .edit()
            .remove(DeliveryState.notificationKey(KEY_NOTIFY_ON_COMPLETE, workId))
            .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId))
            .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_CLAIMED_AT, workId))
            .apply()
        AndroidBagAnalysisNotifier.cancel(context, workId)
        BagExtractionResultStore.delete(context, workId)
        clearAllWorkMetadata(context, workId)
    }

    fun activeWorkId(context: Context): String? =
        DeliveryState.preferences(context).getString(KEY_ACTIVE_WORK_ID, null)

    fun workIdForSession(context: Context, sessionId: String): String? =
        DeliveryState.preferences(context).getString(DeliveryState.sessionWorkKey(sessionId), null)

    fun sessionIdForWork(context: Context, workId: String): String? =
        DeliveryState.preferences(context).getString(DeliveryState.workSessionKey(workId), null)

    fun generationIdForWork(context: Context, workId: String): String? =
        DeliveryState.preferences(context).getString(DeliveryState.workGenerationKey(workId), null)

    fun reviewContextForWork(context: Context, workId: String): BagReviewContext? =
        decodeBagReviewContext(
            DeliveryState.preferences(context)
                .getString(DeliveryState.workReviewContextKey(workId), null),
        )

    fun latestGenerationId(context: Context, sessionId: String): String? =
        DeliveryState.preferences(context)
            .getString(DeliveryState.sessionGenerationKey(sessionId), null)

    fun rememberLatestGeneration(context: Context, sessionId: String, generationId: String) {
        check(
            DeliveryState.preferences(context)
                .edit()
                .putString(DeliveryState.sessionGenerationKey(sessionId), generationId)
                .commit(),
        ) { "Could not persist latest bag extraction generation" }
    }

    fun invalidateSessionGeneration(context: Context, sessionId: String) {
        DeliveryState.preferences(context)
            .edit()
            .remove(DeliveryState.sessionGenerationKey(sessionId))
            .apply()
    }

    fun isLatestGeneration(context: Context, sessionId: String, generationId: String): Boolean =
        latestGenerationId(context, sessionId) == generationId

    fun workInfoFlow(context: Context, workId: String): Flow<WorkInfo?> {
        val id = workId.takeIf(::isValidWorkId)?.let(UUID::fromString)
        return if (id != null) {
            WorkManager.getInstance(context).getWorkInfoByIdFlow(id)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }

    fun isValidWorkId(workId: String): Boolean =
        runCatching { UUID.fromString(workId).toString().equals(workId, ignoreCase = true) }
            .getOrDefault(false)

    internal fun buildInputData(
        manifestPath: String,
        runLlm: Boolean,
        sessionId: String,
        generationId: String,
        notifyOnCompletion: Boolean,
        reviewContext: BagReviewContext? = null,
    ): Data = Data.Builder()
        .putString(BagExtractionWorker.KEY_INPUT_MANIFEST, manifestPath)
        .putBoolean(BagExtractionWorker.KEY_RUN_LLM, runLlm)
        .putString(BagExtractionWorker.KEY_SESSION_ID, sessionId)
        .putString(BagExtractionWorker.KEY_GENERATION_ID, generationId)
        .putBoolean(BagExtractionWorker.KEY_NOTIFY_ON_COMPLETE, notifyOnCompletion)
        .also { builder ->
            encodeBagReviewContext(reviewContext)?.let { encoded ->
                builder.putString(BagExtractionWorker.KEY_REVIEW_CONTEXT_JSON, encoded)
            }
        }
        .build()

    private fun buildRequest(
        manifestPath: String,
        input: BagExtractionInput,
        workId: UUID = UUID.randomUUID(),
    ) = OneTimeWorkRequestBuilder<BagExtractionWorker>()
        .setId(workId)
        .setInputData(
            buildInputData(
                manifestPath = manifestPath,
                runLlm = input.runLlm,
                sessionId = input.sessionId,
                generationId = input.generationId,
                notifyOnCompletion = input.notifyOnCompletion,
                reviewContext = input.reviewContext,
            ),
        )
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .addTag("$SESSION_TAG_PREFIX${input.sessionId}")
        .addTag("$GENERATION_TAG_PREFIX${input.generationId}")
        .addTag(WORK_TAG)
        .keepResultsForAtLeast(RESULT_RETENTION_DAYS, TimeUnit.DAYS)
        .build()

    private fun persistPendingEnqueue(
        context: Context,
        workId: String,
        manifestPath: String,
        input: BagExtractionInput,
    ) {
        val preferences = DeliveryState.preferences(context)
        val editor = preferences.edit()
            .putBoolean(DeliveryState.pendingEnqueueKey(workId), true)
            .putString(DeliveryState.inputManifestKey(workId), manifestPath)
            .putString(DeliveryState.sessionWorkKey(input.sessionId), workId)
            .putString(DeliveryState.workSessionKey(workId), input.sessionId)
            .putString(DeliveryState.workGenerationKey(workId), input.generationId)
            .putBoolean(
                DeliveryState.notificationKey(KEY_NOTIFY_ON_COMPLETE, workId),
                input.notifyOnCompletion,
            )
            .putBoolean(DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId), false)
            .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_CLAIMED_AT, workId))
        encodeBagReviewContext(input.reviewContext)?.let { encoded ->
            editor.putString(DeliveryState.workReviewContextKey(workId), encoded)
        }
        check(editor.commit()) { "Could not persist pending bag extraction enqueue" }
    }

    private suspend fun enqueueDurably(
        context: Context,
        request: androidx.work.OneTimeWorkRequest,
    ) {
        withContext(NonCancellable) {
            WorkManager.getInstance(context).enqueue(request).await()
        }
    }

    private suspend fun activateDurablyEnqueuedWork(
        context: Context,
        workId: String,
        manifestPath: String,
        input: BagExtractionInput,
    ) {
        val preferences = DeliveryState.preferences(context)
        val stillPending = preferences.getBoolean(DeliveryState.pendingEnqueueKey(workId), false)
        val manifestStillOwned =
            preferences.getString(DeliveryState.inputManifestKey(workId), null) == manifestPath
        val stillLatest =
            preferences.getString(DeliveryState.sessionGenerationKey(input.sessionId), null) ==
                input.generationId
        if (!shouldActivateDurablyEnqueuedWork(stillPending, manifestStillOwned, stillLatest)) {
            withContext(NonCancellable) {
                WorkManager.getInstance(context).cancelWorkById(UUID.fromString(workId)).await()
            }
            rollbackFailedEnqueue(context, workId, manifestPath, input)
            throw CancellationException("Bag extraction enqueue was superseded before activation")
        }
        if (!preferences.edit()
                .remove(DeliveryState.pendingEnqueueKey(workId))
                .putString(KEY_ACTIVE_WORK_ID, workId)
                .commit()
        ) {
            Log.w(TAG, "Durable enqueue succeeded but active state promotion did not commit")
        }
    }

    private suspend fun rollbackFailedEnqueue(
        context: Context,
        workId: String,
        manifestPath: String,
        input: BagExtractionInput,
    ) {
        val failedGenerationWasLatest =
            latestGenerationId(context, input.sessionId) == input.generationId
        runCatching {
            withContext(NonCancellable) {
                WorkManager.getInstance(context)
                    .cancelWorkById(UUID.fromString(workId))
                    .await()
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not cancel failed extraction enqueue", error)
        }
        runCatching { BagExtractionInputStore.delete(context, manifestPath) }
            .onFailure { error -> Log.w(TAG, "Could not roll back failed extraction manifest", error) }
        clearWorkState(context, workId, preserveLatestGeneration = false)
        if (failedGenerationWasLatest) {
            rememberLatestGeneration(context, input.sessionId, input.generationId)
        }
    }

    internal fun cleanupInputManifest(
        context: Context,
        workId: String,
        fallbackPath: String? = null,
    ): Boolean {
        val preferences = DeliveryState.preferences(context)
        val manifestKey = DeliveryState.inputManifestKey(workId)
        val manifestPath = preferences.getString(manifestKey, null) ?: fallbackPath
        return try {
            BagExtractionInputStore.delete(context, manifestPath)
            if (!preferences.edit().remove(manifestKey).commit()) {
                Log.w(TAG, "Could not clear bag extraction input manifest metadata")
                false
            } else {
                true
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not delete bag extraction input manifest", error)
            false
        }
    }

    fun protectedStagedPhotoUris(context: Context): Set<String> {
        val inputs = activeInputManifestPaths(context).mapNotNull { path ->
            runCatching { BagExtractionInputStore.read(context, path) }
                .onFailure { error ->
                    Log.w(TAG, "Could not read active bag extraction input manifest", error)
                }
                .getOrNull()
        }
        val resultPhotoUris = pendingResultWorkIds(context).mapNotNull { workId ->
            BagExtractionResultStore.read(context, workId)?.resultJson?.let { resultJson ->
                runCatching { decodeBagExtractionResult(resultJson).capturedPhotoUris }
                    .onFailure { error ->
                        Log.w(TAG, "Could not decode pending bag extraction result", error)
                    }
                    .getOrNull()
            }
        }
        return collectProtectedStagedPhotoUris(inputs, resultPhotoUris)
    }

    suspend fun cleanupExpiredStorageState(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        reconcilePersistedState(context, nowMillis)
    }

    internal fun collectProtectedStagedPhotoUris(
        inputs: Collection<BagExtractionInput>,
        resultPhotoUris: Collection<String?>,
    ): Set<String> = buildSet {
        inputs.forEach { input -> addAll(BagPhotoReviewUris.parse(input.photoUrisCsv)) }
        resultPhotoUris.forEach { uris -> addAll(BagPhotoReviewUris.parse(uris)) }
    }

    suspend fun reconcilePersistedState(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<String> = reconciliationMutex.withLock {
        val preferences = DeliveryState.preferences(context)
        val protectedReviewWorkIds = protectedReviewWorkIds(context)
        val protectedResultWorkIds = protectedResultWorkIds(context)
        val protectedExpiryWorkIds = protectedReviewWorkIds + protectedResultWorkIds
        val protectedStagedUris = protectedStagedPhotoUris(context)
        val workIds = persistedManifestWorkIds(preferences) +
            pendingEnqueueWorkIds(preferences) +
            listOfNotNull(activeWorkId(context)?.takeIf(::isValidWorkId))
        workIds.forEach { workId ->
            reconcilePersistedWork(context, workId, nowMillis)
        }
        sweepExpiredPersistedState(context, nowMillis, protectedExpiryWorkIds)
        protectedStagedUris
    }

    suspend fun migrateIncompatibleActiveWork(context: Context) {
        reconcilePersistedState(context)
    }

    private suspend fun reconcilePersistedWork(
        context: Context,
        workId: String,
        nowMillis: Long,
    ) {
        val preferences = DeliveryState.preferences(context)
        val manifestPath = preferences.getString(DeliveryState.inputManifestKey(workId), null)
        val input = manifestPath?.let { path ->
            runCatching { BagExtractionInputStore.read(context, path) }
                .onFailure { error ->
                    Log.w(TAG, "Could not read persisted bag extraction manifest", error)
                }
                .getOrNull()
        }
        val manifestState = when {
            input == null -> ManifestReconciliationState.MISSING
            runCatching { BagExtractionInputStore.isExpired(context, requireNotNull(manifestPath), nowMillis) }
                .getOrDefault(true) -> ManifestReconciliationState.EXPIRED
            else -> ManifestReconciliationState.PRESENT
        }
        val workInfo = workInfoFlow(context, workId).first()
        val storedResult = BagExtractionResultStore.read(context, workId)
        when (terminalWorkReconciliationAction(workInfo?.state, storedResult != null)) {
            TerminalWorkReconciliationAction.RETAIN -> Unit
            TerminalWorkReconciliationAction.HANDLE_RESULT_AND_CLEAN -> {
                reconcileStoredTerminalResult(
                    context = context,
                    workId = workId,
                    storedResult = requireNotNull(storedResult),
                    input = input,
                    manifestPath = manifestPath,
                )
                return
            }
            TerminalWorkReconciliationAction.SURFACE_FAILURE_AND_CLEAN -> {
                surfaceRecoverableWorkFailure(
                    context = context,
                    workId = workId,
                    input = input?.let { resolvePersistedInput(context, workId, it) },
                    manifestPath = manifestPath,
                )
                return
            }
            TerminalWorkReconciliationAction.DISCARD_CANCELLED -> {
                discardCancelledWorkState(context, workId, manifestPath)
                return
            }
        }
        when (persistedWorkReconciliationAction(workInfo != null, manifestState)) {
            PersistedWorkReconciliationAction.KEEP -> {
                val pendingEnqueue =
                    preferences.getBoolean(DeliveryState.pendingEnqueueKey(workId), false)
                if (pendingEnqueue) {
                    preferences.edit()
                        .remove(DeliveryState.pendingEnqueueKey(workId))
                        .putString(KEY_ACTIVE_WORK_ID, workId)
                        .apply()
                }
            }
            PersistedWorkReconciliationAction.RESCHEDULE -> {
                val resolvedInput = resolvePersistedInput(context, workId, requireNotNull(input))
                try {
                    enqueueDurably(
                        context,
                        buildRequest(
                            manifestPath = requireNotNull(manifestPath),
                            input = resolvedInput,
                            workId = UUID.fromString(workId),
                        ),
                    )
                    val editor = preferences.edit()
                        .remove(DeliveryState.pendingEnqueueKey(workId))
                    if (activeWorkId(context) == workId ||
                        preferences.getBoolean(DeliveryState.pendingEnqueueKey(workId), false)
                    ) {
                        editor.putString(KEY_ACTIVE_WORK_ID, workId)
                    }
                    check(editor.commit()) {
                        "Could not finalize reconciled bag extraction enqueue"
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(TAG, "Could not reschedule missing WorkManager extraction", error)
                    surfaceRecoverableWorkFailure(context, workId, resolvedInput, manifestPath)
                }
            }
            PersistedWorkReconciliationAction.SURFACE_FAILURE -> {
                surfaceRecoverableWorkFailure(
                    context = context,
                    workId = workId,
                    input = input?.let { resolvePersistedInput(context, workId, it) },
                    manifestPath = manifestPath,
                )
            }
            PersistedWorkReconciliationAction.EXPIRE -> expireWorkState(context, workId)
        }
    }

    private fun resolvePersistedInput(
        context: Context,
        workId: String,
        input: BagExtractionInput,
    ): BagExtractionInput = input.copy(
        sessionId = input.sessionId.ifBlank { sessionIdForWork(context, workId) ?: workId },
        generationId = input.generationId.ifBlank { generationIdForWork(context, workId) ?: workId },
        reviewContext = input.reviewContext ?: reviewContextForWork(context, workId),
        notifyOnCompletion = input.notifyOnCompletion ||
            isCompletionNotificationRequested(context, workId),
    )

    private fun reconcileStoredTerminalResult(
        context: Context,
        workId: String,
        storedResult: StoredBagExtractionResult,
        input: BagExtractionInput?,
        manifestPath: String?,
    ) {
        performTerminalResultReconciliation(
            handleDurableResult = {
                routeStoredTerminalResult(
                    context = context,
                    workId = workId,
                    storedResult = storedResult,
                    notifyOnCompletion = input?.notifyOnCompletion == true,
                    fallbackReviewContext = input?.reviewContext,
                )
            },
            deleteManifest = {
                cleanupInputManifest(context, workId, fallbackPath = manifestPath)
            },
            clearWorkState = {
                clearWorkState(context, workId, preserveLatestGeneration = true)
            },
        )
    }

    private fun routeStoredTerminalResult(
        context: Context,
        workId: String,
        storedResult: StoredBagExtractionResult,
        notifyOnCompletion: Boolean,
        fallbackReviewContext: BagReviewContext?,
    ) {
        val reviewContext = storedResult.reviewContext
            ?: fallbackReviewContext
            ?: reviewContextForWork(context, workId)
        if (notifyOnCompletion || isCompletionNotificationRequested(context, workId)) {
            requestCompletionNotification(context, workId)
        } else {
            BagReviewQueue.retainForeground(context, workId, reviewContext)
        }
    }

    private fun discardCancelledWorkState(
        context: Context,
        workId: String,
        manifestPath: String?,
    ) {
        if (!cleanupInputManifest(context, workId, fallbackPath = manifestPath)) return
        performExpiredWorkCleanup(
            actions = ExpiredWorkCleanupActions(
                deleteManifest = {},
                deleteResult = { BagExtractionResultStore.delete(context, workId) },
                clearMetadata = { clearAllWorkMetadata(context, workId) },
                removeReviewIds = { BagReviewQueue.removeEverywhere(context, workId) },
                cancelNotification = { AndroidBagAnalysisNotifier.cancel(context, workId) },
            ),
            onFailure = { error ->
                Log.w(TAG, "Could not fully discard cancelled bag extraction $workId", error)
            },
        )
    }

    private fun surfaceRecoverableWorkFailure(
        context: Context,
        workId: String,
        input: BagExtractionInput?,
        manifestPath: String?,
    ) {
        val sessionId = input?.sessionId?.takeIf(String::isNotBlank)
            ?: sessionIdForWork(context, workId)
            ?: workId
        val generationId = input?.generationId?.takeIf(String::isNotBlank)
            ?: generationIdForWork(context, workId)
            ?: workId
        val reviewContext = input?.reviewContext ?: reviewContextForWork(context, workId)
        val preferences = DeliveryState.preferences(context)
        val editor = preferences.edit()
            .putString(DeliveryState.workSessionKey(workId), sessionId)
            .putString(DeliveryState.workGenerationKey(workId), generationId)
        encodeBagReviewContext(reviewContext)?.let { encoded ->
            editor.putString(DeliveryState.workReviewContextKey(workId), encoded)
        }
        editor.apply()
        val storedResult = BagExtractionResultStore.writeIfAbsent(
            context = context,
            workId = workId,
            successful = false,
            resultJson = BagPhotoProcessingResult(
                capturedPhotoUris = input?.photoUrisCsv,
                llmStatus = LlmEnrichmentStatus.UNAVAILABLE,
            ).encodeToStoredJson(),
            reviewContext = reviewContext,
        )
        performTerminalResultReconciliation(
            handleDurableResult = {
                routeStoredTerminalResult(
                    context = context,
                    workId = workId,
                    storedResult = storedResult,
                    notifyOnCompletion = input?.notifyOnCompletion == true,
                    fallbackReviewContext = reviewContext,
                )
            },
            deleteManifest = {
                cleanupInputManifest(context, workId, fallbackPath = manifestPath)
            },
            clearWorkState = {
                clearWorkState(context, workId, preserveLatestGeneration = true)
            },
        )
    }

    private suspend fun sweepExpiredPersistedState(
        context: Context,
        nowMillis: Long,
        protectedWorkIds: Set<String>,
    ) {
        val resultWorkIds = BagExtractionResultStore.expiredWorkIds(context, nowMillis)
        val manifestWorkIds = persistedManifestWorkIds(DeliveryState.preferences(context))
        val expiredManifestWorkIds = manifestWorkIds.filterTo(mutableSetOf()) { workId ->
            val manifestPath = DeliveryState.preferences(context)
                .getString(DeliveryState.inputManifestKey(workId), null)
                ?: return@filterTo false
            val expired = runCatching {
                BagExtractionInputStore.isExpired(context, manifestPath, nowMillis)
            }.getOrDefault(true)
            if (!expired) return@filterTo false
            val workInfo = workInfoFlow(context, workId).first()
            workInfo == null || workInfo.state.isFinished
        }
        expirableWorkIds(
            expiredResultWorkIds = resultWorkIds,
            expiredManifestWorkIds = expiredManifestWorkIds,
            protectedWorkIds = protectedWorkIds,
        ).forEach { workId ->
            expireWorkState(context, workId)
        }
        BagExtractionInputStore.deleteExpired(
            context = context,
            protectedPaths = activeInputManifestPaths(context),
            nowMillis = nowMillis,
        )
        BagExtractionResultStore.deleteExpired(
            context = context,
            protectedWorkIds = pendingResultWorkIds(context) + protectedWorkIds,
            nowMillis = nowMillis,
        )
    }

    private fun expireWorkState(context: Context, workId: String) {
        runCatching { UUID.fromString(workId) }
            .getOrNull()
            ?.let { WorkManager.getInstance(context).cancelWorkById(it) }
        performExpiredWorkCleanup(
            actions = ExpiredWorkCleanupActions(
                deleteManifest = { cleanupInputManifest(context, workId) },
                deleteResult = { BagExtractionResultStore.delete(context, workId) },
                clearMetadata = { clearAllWorkMetadata(context, workId) },
                removeReviewIds = { BagReviewQueue.removeEverywhere(context, workId) },
                cancelNotification = { AndroidBagAnalysisNotifier.cancel(context, workId) },
            ),
            onFailure = { error ->
                Log.w(TAG, "Could not fully expire bag extraction $workId", error)
            },
        )
    }

    fun enqueueCompletionNotification(
        context: Context,
        workId: String,
        waitForTerminal: Boolean,
    ) {
        val request = OneTimeWorkRequestBuilder<BagAnalysisNotificationWorker>()
            .setInputData(
                workDataOf(
                    BagAnalysisNotificationWorker.KEY_WORK_ID to workId,
                    BagAnalysisNotificationWorker.KEY_WAIT_FOR_TERMINAL to waitForTerminal,
                ),
            )
            .setInitialDelay(NOTIFICATION_INITIAL_DELAY_MS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "bag_analysis_notification_$workId",
            if (waitForTerminal) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private object DeliveryState {
        fun preferences(context: Context) =
            context.getSharedPreferences(DELIVERY_PREFERENCES, Context.MODE_PRIVATE)

        fun notificationKey(prefix: String, workId: String) = "$prefix:$workId"

        fun inputManifestKey(workId: String) = "$KEY_INPUT_MANIFEST:$workId"
        fun pendingEnqueueKey(workId: String) = "$KEY_PENDING_ENQUEUE:$workId"
        fun sessionWorkKey(sessionId: String) = "$KEY_SESSION_WORK:$sessionId"
        fun sessionGenerationKey(sessionId: String) = "$KEY_SESSION_GENERATION:$sessionId"
        fun workSessionKey(workId: String) = "$KEY_WORK_SESSION:$workId"
        fun workGenerationKey(workId: String) = "$KEY_WORK_GENERATION:$workId"
        fun workReviewContextKey(workId: String) = "$KEY_WORK_REVIEW_CONTEXT:$workId"
    }

    private fun persistedManifestWorkIds(preferences: android.content.SharedPreferences): Set<String> =
        preferences.all.keys
            .asSequence()
            .filter { key -> key.startsWith("$KEY_INPUT_MANIFEST:") }
            .map { key -> key.removePrefix("$KEY_INPUT_MANIFEST:") }
            .filter(::isValidWorkId)
            .toSet()

    private fun pendingEnqueueWorkIds(preferences: android.content.SharedPreferences): Set<String> =
        preferences.all
            .filter { (key, value) ->
                key.startsWith("$KEY_PENDING_ENQUEUE:") && value == true
            }
            .keys
            .map { key -> key.removePrefix("$KEY_PENDING_ENQUEUE:") }
            .filter(::isValidWorkId)
            .toSet()

    private fun activeInputManifestPaths(context: Context): Set<String> =
        DeliveryState.preferences(context)
            .all
            .filterKeys { key -> key.startsWith("$KEY_INPUT_MANIFEST:") }
            .values
            .filterIsInstance<String>()
            .toSet()

    private fun pendingResultWorkIds(context: Context): Set<String> = buildSet {
        activeWorkId(context)?.takeIf(::isValidWorkId)?.let(::add)
        addAll(BagReviewQueue.list(context).filter(::isValidWorkId))
        addAll(BagReviewQueue.retainedForeground(context).filter(::isValidWorkId))
        DeliveryState.preferences(context)
            .all
            .keys
            .asSequence()
            .filter { key -> key.startsWith("$KEY_WORK_SESSION:") }
            .map { key -> key.removePrefix("$KEY_WORK_SESSION:") }
            .filter(::isValidWorkId)
            .forEach(::add)
    }

    private fun protectedReviewWorkIds(context: Context): Set<String> =
        (
            BagReviewQueue.list(context) +
                BagReviewQueue.retainedForeground(context)
            )
            .filter(::isValidWorkId)
            .toSet()

    private fun protectedResultWorkIds(context: Context): Set<String> =
        pendingResultWorkIds(context)
            .filterTo(mutableSetOf()) { workId ->
                BagExtractionResultStore.read(context, workId) != null
            }

    private fun clearWorkState(
        context: Context,
        workId: String?,
        preserveLatestGeneration: Boolean,
    ) {
        if (workId == null) return
        val preferences = DeliveryState.preferences(context)
        val sessionId = preferences.getString(DeliveryState.workSessionKey(workId), null)
        val workGenerationId = preferences.getString(DeliveryState.workGenerationKey(workId), null)
        val editor = preferences.edit()
            .remove(DeliveryState.inputManifestKey(workId))
            .remove(DeliveryState.pendingEnqueueKey(workId))
        if (!preserveLatestGeneration) {
            editor
                .remove(DeliveryState.workSessionKey(workId))
                .remove(DeliveryState.workGenerationKey(workId))
                .remove(DeliveryState.workReviewContextKey(workId))
                .remove(DeliveryState.notificationKey(KEY_NOTIFY_ON_COMPLETE, workId))
                .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId))
                .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_CLAIMED_AT, workId))
        }
        if (preferences.getString(KEY_ACTIVE_WORK_ID, null) == workId) {
            editor.remove(KEY_ACTIVE_WORK_ID)
        }
        if (sessionId != null &&
            preferences.getString(DeliveryState.sessionWorkKey(sessionId), null) == workId
        ) {
            editor.remove(DeliveryState.sessionWorkKey(sessionId))
            if (!preserveLatestGeneration &&
                preferences.getString(DeliveryState.sessionGenerationKey(sessionId), null) ==
                workGenerationId
            ) {
                editor.remove(DeliveryState.sessionGenerationKey(sessionId))
            }
        }
        editor.apply()
    }

    private fun clearAllWorkMetadata(context: Context, workId: String) {
        val preferences = DeliveryState.preferences(context)
        val sessionId = preferences.getString(DeliveryState.workSessionKey(workId), null)
        val generationId = preferences.getString(DeliveryState.workGenerationKey(workId), null)
        val mappedSessions = preferences.all
            .filter { (key, value) ->
                key.startsWith("$KEY_SESSION_WORK:") && value == workId
            }
            .keys
            .map { key -> key.removePrefix("$KEY_SESSION_WORK:") }
            .toSet()
        val editor = preferences.edit()
            .remove(DeliveryState.inputManifestKey(workId))
            .remove(DeliveryState.pendingEnqueueKey(workId))
            .remove(DeliveryState.workSessionKey(workId))
            .remove(DeliveryState.workGenerationKey(workId))
            .remove(DeliveryState.workReviewContextKey(workId))
            .remove(DeliveryState.notificationKey(KEY_NOTIFY_ON_COMPLETE, workId))
            .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_DELIVERED, workId))
            .remove(DeliveryState.notificationKey(KEY_NOTIFICATION_CLAIMED_AT, workId))
        if (preferences.getString(KEY_ACTIVE_WORK_ID, null) == workId) {
            editor.remove(KEY_ACTIVE_WORK_ID)
        }
        (mappedSessions + listOfNotNull(sessionId)).forEach { mappedSessionId ->
            if (preferences.getString(DeliveryState.sessionWorkKey(mappedSessionId), null) == workId) {
                editor.remove(DeliveryState.sessionWorkKey(mappedSessionId))
                if (generationId == null ||
                    preferences.getString(DeliveryState.sessionGenerationKey(mappedSessionId), null) ==
                    generationId
                ) {
                    editor.remove(DeliveryState.sessionGenerationKey(mappedSessionId))
                }
            }
        }
        if (!editor.commit()) {
            Log.w(TAG, "Could not clear expired extraction metadata for $workId")
        }
        forgetLegacyWorkSession(context, workId)
    }

    private fun forgetLegacyWorkSession(context: Context, workId: String) {
        context.getSharedPreferences(LEGACY_BAG_SCAN_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove("$LEGACY_WORK_SESSION_PREFIX$workId")
            .apply()
    }

    private const val DELIVERY_PREFERENCES = "bag_extraction_delivery"
    private const val KEY_ACTIVE_WORK_ID = "active_work_id"
    private const val KEY_NOTIFY_ON_COMPLETE = "notify_on_complete"
    private const val KEY_NOTIFICATION_DELIVERED = "notification_delivered"
    private const val KEY_NOTIFICATION_CLAIMED_AT = "notification_claimed_at"
    private const val KEY_INPUT_MANIFEST = "input_manifest"
    private const val KEY_PENDING_ENQUEUE = "pending_enqueue"
    private const val KEY_SESSION_WORK = "session_work"
    private const val KEY_SESSION_GENERATION = "session_generation"
    private const val KEY_WORK_SESSION = "work_session"
    private const val KEY_WORK_GENERATION = "work_generation"
    private const val KEY_WORK_REVIEW_CONTEXT = "work_review_context"
    private const val LEGACY_BAG_SCAN_PREFERENCES = "bag_scan"
    private const val LEGACY_WORK_SESSION_PREFIX = "workSession:"
    private const val RESULT_RETENTION_DAYS = 7L
    private const val NOTIFICATION_INITIAL_DELAY_MS = 500L
    private const val NOTIFICATION_CLAIM_TIMEOUT_MS = 2L * 60L * 1_000L
    private const val TAG = "BagExtractionScheduler"
    private val deliveryStateLock = Any()
    private val reconciliationMutex = Mutex()
    const val SESSION_TAG_PREFIX = "bag_session:"
    const val GENERATION_TAG_PREFIX = "bag_generation:"
    const val WORK_TAG = "bag_extraction"
}
