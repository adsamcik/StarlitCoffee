package com.adsamcik.starlitcoffee.ui.component

import android.content.Context
import android.util.Log
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.BagPhotoRect
import com.adsamcik.starlitcoffee.util.BagPhotoOwnership
import com.adsamcik.starlitcoffee.util.BagThumbnailWriter
import com.adsamcik.starlitcoffee.util.PendingBagPhotoDeletion
import com.adsamcik.starlitcoffee.util.ScanPhotoStorage
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal sealed interface ScannedBagSaveResult {
    data class Saved(val bagId: Long) : ScannedBagSaveResult
    data object PhotoCopyFailed : ScannedBagSaveResult
    data class Failed(val error: Exception) : ScannedBagSaveResult
}

private sealed interface PendingSaveRecovery {
    data class Owned(val bagId: Long) : PendingSaveRecovery
    data object Deleted : PendingSaveRecovery
    data class Deferred(val error: Exception? = null) : PendingSaveRecovery
}

internal suspend fun persistScannedBag(
    context: Context,
    brewViewModel: BrewViewModel,
    rawPhotoUris: String?,
    scanSessionId: String,
    thumbnailFocus: BagPhotoRect?,
    thumbnailTargetPx: Int,
    inputFactory: (photoUri: String?, photoUris: String?) -> BrewViewModel.CoffeeBagInput,
): ScannedBagSaveResult = withContext(NonCancellable + Dispatchers.IO) {
    var permanentUris: String? = null
    var thumbnailUri: String? = null
    try {
        brewViewModel.findScannedCoffeeBagId(scanSessionId)?.let { existingBagId ->
            withContext(Dispatchers.IO) {
                ScanPhotoStorage.deleteStagedCaptures(context, rawPhotoUris)
            }
            ScanPhotoStorage.clearPendingSave(context, scanSessionId)
            return@withContext ScannedBagSaveResult.Saved(existingBagId)
        }
        ScanPhotoStorage.markSavePending(context, scanSessionId)
        val storageKey = ScanPhotoStorage.storageKeyForSession(scanSessionId)
        permanentUris = rawPhotoUris?.let { uris ->
            ScanPhotoStorage.promoteStagedPhotosToPermanentStorage(context, uris, storageKey)
        }

        if (rawPhotoUris != null && permanentUris == null) {
            return@withContext when (
                val recovery = recoverPendingSave(
                    context = context,
                    scanSessionId = scanSessionId,
                    findOwnedBagId = { brewViewModel.findScannedCoffeeBagId(scanSessionId) },
                )
            ) {
                is PendingSaveRecovery.Owned -> ScannedBagSaveResult.Saved(recovery.bagId)
                PendingSaveRecovery.Deleted,
                is PendingSaveRecovery.Deferred -> ScannedBagSaveResult.PhotoCopyFailed
            }
        }

        val frontPhotoUri = permanentUris?.split(",")?.firstOrNull()
        thumbnailUri = if (frontPhotoUri != null && thumbnailFocus != null) {
            withContext(Dispatchers.IO) {
                BagThumbnailWriter.createFocusedThumbnail(
                    context = context,
                    sourceUri = frontPhotoUri,
                    focus = thumbnailFocus,
                    targetSizePx = thumbnailTargetPx,
                    storageKey = storageKey,
                )
            }
        } else {
            null
        }

        val insertResult = brewViewModel.addScannedCoffeeBagAndAwait(
            inputFactory(thumbnailUri ?: frontPhotoUri, permanentUris),
            scanSessionId = scanSessionId,
        )
        when (
            val recovery = recoverPendingSave(
                context = context,
                scanSessionId = scanSessionId,
                findOwnedBagId = { brewViewModel.findScannedCoffeeBagId(scanSessionId) },
            )
        ) {
            is PendingSaveRecovery.Owned -> {
                ScanPhotoStorage.deleteStagedCaptures(context, rawPhotoUris)
                ScannedBagSaveResult.Saved(recovery.bagId)
            }
            PendingSaveRecovery.Deleted -> ScannedBagSaveResult.Failed(
                IllegalStateException(
                    "Coffee bag ${insertResult.bagId} did not retain ownership of its promoted photos",
                ),
            )
            is PendingSaveRecovery.Deferred -> ScannedBagSaveResult.Failed(
                recovery.error ?: IllegalStateException("Could not verify saved coffee bag photo ownership"),
            )
        }
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        when (
            val recovery = recoverPendingSave(
                context = context,
                scanSessionId = scanSessionId,
                findOwnedBagId = { brewViewModel.findScannedCoffeeBagId(scanSessionId) },
            )
        ) {
            is PendingSaveRecovery.Owned -> {
                ScanPhotoStorage.deleteStagedCaptures(context, rawPhotoUris)
                ScannedBagSaveResult.Saved(recovery.bagId)
            }
            PendingSaveRecovery.Deleted -> ScannedBagSaveResult.Failed(error)
            is PendingSaveRecovery.Deferred -> {
                recovery.error?.let(error::addSuppressed)
                ScannedBagSaveResult.Failed(error)
            }
        }
    }
}

internal suspend fun persistRescannedBagUpdate(
    context: Context,
    brewViewModel: BrewViewModel,
    originalBag: CoffeeBagEntity,
    updatedBag: CoffeeBagEntity,
    rawPhotoUris: String?,
    scanSessionId: String,
    thumbnailFocus: BagPhotoRect?,
    thumbnailTargetPx: Int,
): ScannedBagSaveResult = withContext(NonCancellable + Dispatchers.IO) {
    if (rawPhotoUris.isNullOrBlank()) {
        return@withContext runCatching {
            check(brewViewModel.updateCoffeeBagAndWait(updatedBag)) {
                "Coffee bag repository is unavailable"
            }
            ScannedBagSaveResult.Saved(updatedBag.id)
        }.getOrElse { error ->
            ScannedBagSaveResult.Failed(error as? Exception ?: IllegalStateException(error))
        }
    }

    var replacementCleanup: PendingBagPhotoDeletion? = null
    try {
        ScanPhotoStorage.markSavePending(context, scanSessionId)
        val storageKey = ScanPhotoStorage.storageKeyForSession(scanSessionId)
        val permanentUris = ScanPhotoStorage.promoteStagedPhotosToPermanentStorage(
            context = context,
            stagedUris = rawPhotoUris,
            storageKey = storageKey,
        )
        if (permanentUris == null) {
            return@withContext when (
                val recovery = recoverPendingSave(
                    context = context,
                    scanSessionId = scanSessionId,
                    findOwnedBagId = {
                        brewViewModel.findCoffeeBagById(updatedBag.id)
                            ?.takeIf { it.scanSessionId == scanSessionId }
                            ?.id
                    },
                )
            ) {
                is PendingSaveRecovery.Owned -> ScannedBagSaveResult.Saved(recovery.bagId)
                PendingSaveRecovery.Deleted,
                is PendingSaveRecovery.Deferred -> ScannedBagSaveResult.PhotoCopyFailed
            }
        }

        val frontPhotoUri = permanentUris.split(",").firstOrNull()
        val thumbnailUri = if (frontPhotoUri != null && thumbnailFocus != null) {
            BagThumbnailWriter.createFocusedThumbnail(
                context = context,
                sourceUri = frontPhotoUri,
                focus = thumbnailFocus,
                targetSizePx = thumbnailTargetPx,
                storageKey = storageKey,
            )
        } else {
            null
        }
        val replacement = applyRescannedPhotoMapping(
            updatedBag = updatedBag,
            permanentUris = permanentUris,
            thumbnailUri = thumbnailUri,
            scanSessionId = scanSessionId,
        )
        replacementCleanup = replacedBagPhotoCleanup(
            originalBag = originalBag,
            replacementSessionId = scanSessionId,
        )
        replacementCleanup?.let { deletion ->
            ScanPhotoStorage.markBagPhotoDeletionPending(context, deletion)
        }
        check(brewViewModel.updateCoffeeBagAndWait(replacement)) {
            "Coffee bag repository is unavailable"
        }
        when (
            val recovery = recoverPendingSave(
                context = context,
                scanSessionId = scanSessionId,
                findOwnedBagId = {
                    brewViewModel.findCoffeeBagById(updatedBag.id)
                        ?.takeIf { it.scanSessionId == scanSessionId }
                        ?.id
                },
            )
        ) {
            is PendingSaveRecovery.Owned -> {
                ScanPhotoStorage.deleteStagedCaptures(context, rawPhotoUris)
                cleanupReplacedBagPhotos(context, brewViewModel, replacementCleanup)
                ScannedBagSaveResult.Saved(recovery.bagId)
            }
            PendingSaveRecovery.Deleted -> {
                cleanupReplacedBagPhotos(context, brewViewModel, replacementCleanup)
                ScannedBagSaveResult.Failed(
                    IllegalStateException("Updated coffee bag did not retain ownership of its promoted photos"),
                )
            }
            is PendingSaveRecovery.Deferred -> {
                cleanupReplacedBagPhotos(context, brewViewModel, replacementCleanup)
                ScannedBagSaveResult.Failed(
                    recovery.error ?: IllegalStateException(
                        "Could not verify updated coffee bag photo ownership",
                    ),
                )
            }
        }
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        val recovery = recoverPendingSave(
            context = context,
            scanSessionId = scanSessionId,
            findOwnedBagId = {
                brewViewModel.findCoffeeBagById(updatedBag.id)
                    ?.takeIf { it.scanSessionId == scanSessionId }
                    ?.id
            },
        )
        cleanupReplacedBagPhotos(context, brewViewModel, replacementCleanup)
        when (recovery) {
            is PendingSaveRecovery.Owned -> {
                ScanPhotoStorage.deleteStagedCaptures(context, rawPhotoUris)
                ScannedBagSaveResult.Saved(recovery.bagId)
            }
            PendingSaveRecovery.Deleted -> ScannedBagSaveResult.Failed(error)
            is PendingSaveRecovery.Deferred -> {
                recovery.error?.let(error::addSuppressed)
                ScannedBagSaveResult.Failed(error)
            }
        }
    }
}

private suspend fun recoverPendingSave(
    context: Context,
    scanSessionId: String,
    findOwnedBagId: suspend () -> Long?,
): PendingSaveRecovery {
    val ownership = runCatching { findOwnedBagId() }
    ownership.exceptionOrNull()?.let { error ->
        Log.w(
            "ScannedBagSaveResult",
            "Could not verify Room ownership for pending scan-photo save; journal retained",
            error,
        )
        return PendingSaveRecovery.Deferred(error as? Exception ?: IllegalStateException(error))
    }
    ownership.getOrNull()?.let { bagId ->
        ScanPhotoStorage.clearPendingSave(context, scanSessionId)
        return PendingSaveRecovery.Owned(bagId)
    }

    val deletion = runCatching {
        ScanPhotoStorage.deletePermanentPhotosForSession(context, scanSessionId)
    }
    if (deletion.getOrDefault(false)) {
        ScanPhotoStorage.clearPendingSave(context, scanSessionId)
        return PendingSaveRecovery.Deleted
    }
    val error = deletion.exceptionOrNull()
    Log.w(
        "ScannedBagSaveResult",
        "Could not durably remove unowned scan photos; save journal retained for retry",
        error,
    )
    return PendingSaveRecovery.Deferred(error as? Exception)
}

internal fun applyRescannedPhotoMapping(
    updatedBag: CoffeeBagEntity,
    permanentUris: String,
    thumbnailUri: String?,
    scanSessionId: String,
): CoffeeBagEntity = updatedBag.copy(
    photoUri = thumbnailUri ?: permanentUris.split(",").firstOrNull(),
    photoUris = permanentUris,
    scanSessionId = scanSessionId,
)

private fun replacedBagPhotoCleanup(
    originalBag: CoffeeBagEntity,
    replacementSessionId: String,
): PendingBagPhotoDeletion? {
    val deletion = PendingBagPhotoDeletion(
        deletionId = "replace:${originalBag.id}:$replacementSessionId",
        bagId = originalBag.id,
        sessionId = originalBag.scanSessionId?.takeUnless { it == replacementSessionId },
        photoUrisCsv = listOfNotNull(originalBag.photoUris, originalBag.photoUri)
            .joinToString(",")
            .takeIf(String::isNotBlank),
    )
    return deletion.takeIf { it.sessionId != null || it.photoUrisCsv != null }
}

private suspend fun cleanupReplacedBagPhotos(
    context: Context,
    brewViewModel: BrewViewModel,
    deletion: PendingBagPhotoDeletion?,
) {
    deletion ?: return
    runCatching {
        val currentOwnership = brewViewModel.findCoffeeBagById(requireNotNull(deletion.bagId))
            ?.let { bag ->
                BagPhotoOwnership(
                    bagId = bag.id,
                    sessionId = bag.scanSessionId,
                    photoUrisCsv = listOfNotNull(bag.photoUris, bag.photoUri)
                        .joinToString(",")
                        .takeIf(String::isNotBlank),
                )
            }
        if (ScanPhotoStorage.deleteUnreferencedPermanentBagPhotos(
                context = context,
                deletion = deletion,
                currentOwnership = currentOwnership,
            )
        ) {
            ScanPhotoStorage.clearPendingBagPhotoDeletion(context, deletion.deletionId)
        }
    }.onFailure { error ->
        Log.w("ScannedBagSaveResult", "Old bag-photo cleanup deferred; journal retained", error)
    }
}
