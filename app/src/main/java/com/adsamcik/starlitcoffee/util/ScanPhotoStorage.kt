package com.adsamcik.starlitcoffee.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class PendingBagPhotoDeletion(
    val deletionId: String,
    val bagId: Long? = null,
    val sessionId: String? = null,
    val photoUrisCsv: String? = null,
)

internal data class BagPhotoOwnership(
    val bagId: Long,
    val sessionId: String?,
    val photoUrisCsv: String?,
)

internal data class BagPhotoCleanupPlan(
    val sessionId: String?,
    val photoUrisCsv: String?,
    val protectedPhotoUris: Set<String>,
    val canClearJournal: Boolean = true,
)

internal suspend fun commitDeletionWithDeferredCleanup(
    markCleanupPending: () -> Unit,
    deleteRecord: suspend () -> Unit,
    cleanupPhotos: suspend () -> Boolean,
    clearCleanupPending: () -> Unit,
    onCleanupFailure: (Exception?) -> Unit = {},
): Boolean {
    markCleanupPending()
    try {
        deleteRecord()
    } catch (error: Exception) {
        var cleanupError: Exception? = null
        val reconciled = try {
            cleanupPhotos()
        } catch (reconciliationError: Exception) {
            cleanupError = reconciliationError
            false
        }
        if (reconciled) {
            clearCleanupPending()
        } else {
            onCleanupFailure(cleanupError)
        }
        cleanupError?.let(error::addSuppressed)
        throw error
    }
    var cleanupError: Exception? = null
    val cleanupSucceeded = try {
        cleanupPhotos()
    } catch (error: Exception) {
        cleanupError = error
        false
    }
    if (cleanupSucceeded) {
        clearCleanupPending()
    } else {
        onCleanupFailure(cleanupError)
    }
    return true
}

/**
 * Filesystem helpers for the guided bag-scan flow: writing freshly captured
 * camera frames to durable no-backup staging, and promoting kept photos to permanent storage
 * when a bag is saved.
 *
 * Staged captures use the `file://` scheme so the existing OCR pipeline
 * ([com.adsamcik.starlitcoffee.viewmodel.BrewViewModel] `decodeBagPhotoBitmap`)
 * can decode them via `contentResolver.openInputStream`.
 */
object ScanPhotoStorage {
    private const val TAG = "ScanPhotoStorage"
    private const val STAGING_DIR = "bag_scan_captures"
    private const val PERMANENT_DIR = "bag_photos"
    private const val SAVE_JOURNAL_PREFERENCES = "bag_photo_save_journal"
    private const val KEY_PENDING_SAVE_SESSIONS = "pending_save_sessions"
    private const val KEY_PENDING_DELETE_PREFIX = "pending_delete:"
    private const val STAGING_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    private const val MAX_IMPORTED_PHOTO_BYTES = 32L * 1024L * 1024L
    private val saveJournalLock = Any()
    private val journalJson = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Write JPEG [bytes] to durable no-backup staging and return its `file://` URI
     * string, or null on failure.
     */
    fun writeCaptureToCache(context: Context, bytes: ByteArray): String? = try {
        val file = writeCaptureBytesToStorage(
            storageDir = stagingDirectory(context),
            fileName = "capture_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg",
            bytes = bytes,
        )
        file.toUri().toString()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to write capture to cache", e)
        null
    }

    @VisibleForTesting
    internal fun writeCaptureBytesToStorage(
        storageDir: File,
        fileName: String,
        bytes: ByteArray,
        fileSync: FileSync = AndroidFileSync,
        atomicReplace: (File, File) -> Unit = ::atomicReplace,
        directorySync: DirectorySync = AndroidDirectorySync,
    ): File {
        require(bytes.size.toLong() <= MAX_IMPORTED_PHOTO_BYTES) {
            "Captured photo exceeds the import limit"
        }
        requireOwnedStorageDirectory(storageDir)
        val destination = requireOwnedDirectChild(storageDir, fileName)
        requireSafeReplacementDestination(destination)
        val temporary = requireOwnedDirectChild(
            storageDir,
            ".${destination.name}.${UUID.randomUUID()}.tmp",
        )
        check(temporary.createNewFile()) { "Could not create capture temporary file" }
        var failure: Exception? = null
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
            }
            replaceFileDurably(
                temporary = temporary,
                destination = destination,
                fileSync = fileSync,
                atomicReplace = atomicReplace,
                directorySync = directorySync,
            )
            return destination
        } catch (error: Exception) {
            failure = error
            throw error
        } finally {
            if (temporary.exists() && temporary.delete()) {
                runCatching { directorySync.sync(storageDir) }
                    .onFailure { cleanupError ->
                        failure?.addSuppressed(cleanupError)
                            ?: Log.w(TAG, "Could not durably remove capture temporary file", cleanupError)
                    }
            }
        }
    }

    /**
     * Imports temporary picker URIs into app-owned no-backup staging before long-running work
     * begins. Picker grants can expire while a WorkManager extraction is queued or
     * running, whereas the returned `file://` URIs remain readable for the scan.
     *
     * Returns null after deleting every copied file if any source cannot be read,
     * so a front/back scan never proceeds with an accidental partial selection.
     */
    fun copyPhotosToCache(context: Context, sourceUris: List<String>): List<String>? =
        copyPhotoStreamsToCache(
            cacheDir = File(context.noBackupFilesDir, STAGING_DIR),
            sourceUris = sourceUris,
            openInputStream = { uriStr -> context.contentResolver.openInputStream(uriStr.toUri()) },
        )?.map { it.toUri().toString() }

    @VisibleForTesting
    internal fun copyPhotoStreamsToCache(
        cacheDir: File,
        sourceUris: List<String>,
        openInputStream: (String) -> InputStream?,
        directorySync: DirectorySync = AndroidDirectorySync,
    ): List<File>? = copyPhotoStreamsToStorage(
        storageDir = cacheDir,
        sourceUris = sourceUris,
        openInputStream = openInputStream,
        directorySync = directorySync,
        fileNameForIndex = { index ->
            "import_${System.currentTimeMillis()}_${index}_${UUID.randomUUID()}.image"
        },
    )

    /** Copies test streams together and removes only files created by a failed batch. */
    @VisibleForTesting
    internal fun copyPhotoStreamsToPermanentStorage(
        permanentDir: File,
        sourceUris: List<String>,
        openInputStream: (String) -> InputStream?,
        directorySync: DirectorySync = AndroidDirectorySync,
    ): List<File>? = copyPhotoStreamsToStorage(
        storageDir = permanentDir,
        sourceUris = sourceUris,
        openInputStream = openInputStream,
        directorySync = directorySync,
        fileNameForIndex = { index ->
            "bag_${System.currentTimeMillis()}_${index}_${UUID.randomUUID()}.webp"
        },
    )

    private fun copyPhotoStreamsToStorage(
        storageDir: File,
        sourceUris: List<String>,
        openInputStream: (String) -> InputStream?,
        directorySync: DirectorySync,
        fileNameForIndex: (Int) -> String,
    ): List<File>? {
        val copiedFiles = mutableListOf<File>()
        return try {
            check(storageDir.exists() || storageDir.mkdirs()) { "Could not create photo storage directory" }
            sourceUris.forEachIndexed { index, uriStr ->
                val input = openInputStream(uriStr)
                    ?: throw IOException("Could not open selected photo")
                copiedFiles += copyStreamToStorage(
                    storageDir = storageDir,
                    fileName = fileNameForIndex(index),
                    input = input,
                    directorySync = directorySync,
                )
            }
            copiedFiles
        } catch (error: Exception) {
            copiedFiles.forEach { it.delete() }
            if (copiedFiles.isNotEmpty()) {
                runCatching { directorySync.sync(storageDir) }
                    .onFailure(error::addSuppressed)
            }
            Log.w(TAG, "Failed to copy scan photos", error)
            null
        }
    }

    private fun copyStreamToStorage(
        storageDir: File,
        fileName: String,
        input: InputStream,
        directorySync: DirectorySync,
    ): File {
        val destination = File(storageDir, fileName)
        val temporary = File(storageDir, ".$fileName.tmp")
        try {
            input.use { source ->
                FileOutputStream(temporary).use { destinationStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (true) {
                        val bytesRead = source.read(buffer)
                        if (bytesRead < 0) break
                        copiedBytes += bytesRead
                        if (copiedBytes > MAX_IMPORTED_PHOTO_BYTES) {
                            throw IOException("Selected photo exceeds the import limit")
                        }
                        destinationStream.write(buffer, 0, bytesRead)
                    }
                    destinationStream.flush()
                    destinationStream.fd.sync()
                }
            }
            replaceFileDurably(temporary, destination, directorySync = directorySync)
            return destination
        } finally {
            temporary.delete()
        }
    }

    /** Deletes one app-owned staged capture, rejecting content URIs and paths outside staging. */
    fun deleteStagedCapture(context: Context, uriString: String): Boolean {
        val deleted = deleteOwnedPhoto(
            ownedDirectory = stagingDirectory(context),
            uriString = uriString,
        )
        if (!deleted) {
            Log.w(TAG, "Rejected or could not delete staged capture")
        }
        return deleted
    }

    /** Deletes an unsaved scan's temporary captures once the user saves or discards it. */
    fun deleteStagedCaptures(context: Context, captureUris: String?): Boolean =
        BagPhotoReviewUris.parse(captureUris)
            .map { deleteStagedCapture(context, it) }
            .all { it }

    @VisibleForTesting
    internal fun deleteOwnedPhoto(ownedDirectory: File, uriString: String): Boolean {
        val file = resolveOwnedPhoto(ownedDirectory, uriString) ?: return false
        return !file.exists() || file.delete()
    }

    fun deleteExpiredStagedCaptures(
        context: Context,
        protectedStagedUris: Collection<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        deleteStagedCapturesOlderThan(
            stagingDir = stagingDirectory(context),
            cutoffMillis = nowMillis - STAGING_RETENTION_MILLIS,
            protectedStagedUris = protectedStagedUris,
        )
    }

    @VisibleForTesting
    internal fun deleteStagedCapturesOlderThan(
        stagingDir: File,
        cutoffMillis: Long,
        protectedStagedUris: Collection<String> = emptySet(),
    ) {
        val protectedFiles = protectedStagedUris
            .mapNotNull { resolveOwnedPhoto(stagingDir, it) }
            .toSet()
        stagingDir.listFiles()
            ?.filter { file ->
                isOwnedRegularFile(stagingDir, file) &&
                    file.lastModified() < cutoffMillis &&
                    file.canonicalFile !in protectedFiles
            }
            ?.forEach { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Could not remove expired staged capture ${file.path}")
                }
            }
    }

    /**
     * Optimizes app-owned staged photos into permanent WebP storage (`filesDir/bag_photos/`).
     * EXIF orientation is applied and the longest edge is bounded to the resolution
     * retained for OCR and vision re-analysis.
     *
     * Returns every permanent `file://` URI. Failed batches remove newly created
     * destinations while preserving any previous valid replacement target.
     */
    fun promoteStagedPhotosToPermanentStorage(
        context: Context,
        stagedUris: String,
        storageKey: String,
    ): String? {
        val bagPhotosDir = permanentDirectory(context).apply { mkdirs() }
        val sourceUris = BagPhotoReviewUris.parse(stagedUris)
        if (sourceUris.isEmpty()) return null

        val newlyCreatedFiles = mutableListOf<File>()
        return try {
            check(bagPhotosDir.exists() || bagPhotosDir.mkdirs()) {
                "Could not create permanent photo directory"
            }
            sourceUris.forEachIndexed { index, uriStr ->
                val sourceFile = resolveOwnedPhoto(stagingDirectory(context), uriStr)
                    ?.takeIf { file -> file.isFile }
                    ?: throw IOException("Permanent photo source is not an app-owned staged file")
                val bitmap = decodeBoundedPhoto(
                    filePath = sourceFile.path,
                    maxLongEdgePx = PhotoStoragePolicy.AI_MAX_LONG_EDGE_PX,
                ) ?: throw IOException("Could not decode selected photo")
                val destination = File(
                    bagPhotosDir,
                    permanentPhotoFileName(storageKey, index),
                )
                if (!destination.exists()) {
                    newlyCreatedFiles += destination
                }
                writeOptimizedWebp(
                    source = bitmap,
                    destination = destination,
                    maxLongEdgePx = PhotoStoragePolicy.AI_MAX_LONG_EDGE_PX,
                    quality = PhotoStoragePolicy.WEBP_QUALITY,
                )
            }

            sourceUris.indices.joinToString(",") { index ->
                File(bagPhotosDir, permanentPhotoFileName(storageKey, index)).toUri().toString()
            }
        } catch (error: Exception) {
            deletePermanentFilesDurably(
                permanentDir = bagPhotosDir,
                files = newlyCreatedFiles,
                directorySync = AndroidDirectorySync,
                deleteFile = File::delete,
            )
            Log.w(TAG, "Failed to optimize scan photos", error)
            null
        } catch (error: OutOfMemoryError) {
            deletePermanentFilesDurably(
                permanentDir = bagPhotosDir,
                files = newlyCreatedFiles,
                directorySync = AndroidDirectorySync,
                deleteFile = File::delete,
            )
            Log.e(TAG, "Insufficient memory to optimize scan photos", error)
            null
        }
    }

    /**
     * Compatibility wrapper. Sources are still strictly validated as app-owned staged files.
     */
    fun copyPhotosToPermanentStorage(
        context: Context,
        cacheUris: String,
        storageKey: String,
    ): String? = promoteStagedPhotosToPermanentStorage(
        context = context,
        stagedUris = cacheUris,
        storageKey = storageKey,
    )

    fun storageKeyForSession(sessionId: String): String =
        UUID.nameUUIDFromBytes(sessionId.encodeToByteArray()).toString()

    internal fun permanentPhotoFileName(storageKey: String, index: Int): String =
        "bag_${storageKey}_$index.webp"

    internal fun focusedThumbnailFileName(storageKey: String): String =
        "thumb_$storageKey.webp"

    fun markSavePending(context: Context, sessionId: String) {
        synchronized(saveJournalLock) {
            val preferences = context.getSharedPreferences(SAVE_JOURNAL_PREFERENCES, Context.MODE_PRIVATE)
            val pending = preferences.getStringSet(KEY_PENDING_SAVE_SESSIONS, emptySet())
                ?.toSet()
                .orEmpty() + sessionId
            check(
                preferences
                .edit()
                .putStringSet(KEY_PENDING_SAVE_SESSIONS, pending)
                .commit(),
            ) { "Could not persist scan-photo save journal" }
        }
    }

    fun clearPendingSave(context: Context, sessionId: String): Boolean =
        synchronized(saveJournalLock) {
            val preferences = context.getSharedPreferences(SAVE_JOURNAL_PREFERENCES, Context.MODE_PRIVATE)
            val pending = preferences.getStringSet(KEY_PENDING_SAVE_SESSIONS, emptySet())
                ?.toSet()
                .orEmpty() - sessionId
            val cleared = preferences.edit()
                .putStringSet(KEY_PENDING_SAVE_SESSIONS, pending)
                .commit()
            if (!cleared) {
                Log.w(TAG, "Could not clear scan-photo save journal for $sessionId")
            }
            cleared
        }

    fun pendingSaveSessions(context: Context): Set<String> =
        synchronized(saveJournalLock) {
            context.getSharedPreferences(SAVE_JOURNAL_PREFERENCES, Context.MODE_PRIVATE)
                .getStringSet(KEY_PENDING_SAVE_SESSIONS, emptySet())
                ?.toSet()
                .orEmpty()
        }

    internal fun markBagPhotoDeletionPending(
        context: Context,
        deletion: PendingBagPhotoDeletion,
    ) {
        synchronized(saveJournalLock) {
            check(
                context.getSharedPreferences(SAVE_JOURNAL_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putString(
                        "$KEY_PENDING_DELETE_PREFIX${deletion.deletionId}",
                        encodePendingBagPhotoDeletion(deletion),
                    )
                    .commit(),
            ) { "Could not persist deleted-bag photo cleanup journal" }
        }
    }

    fun clearPendingBagPhotoDeletion(context: Context, deletionId: String): Boolean =
        synchronized(saveJournalLock) {
            val cleared = context.getSharedPreferences(SAVE_JOURNAL_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .remove("$KEY_PENDING_DELETE_PREFIX$deletionId")
                    .commit()
            if (!cleared) {
                Log.w(TAG, "Could not clear deleted-bag photo cleanup journal for $deletionId")
            }
            cleared
        }

    internal fun pendingBagPhotoDeletions(context: Context): List<PendingBagPhotoDeletion> =
        synchronized(saveJournalLock) {
            context.getSharedPreferences(SAVE_JOURNAL_PREFERENCES, Context.MODE_PRIVATE)
                .all
                .filterKeys { key -> key.startsWith(KEY_PENDING_DELETE_PREFIX) }
                .values
                .filterIsInstance<String>()
                .mapNotNull(::decodePendingBagPhotoDeletion)
        }

    internal suspend fun reconcilePendingBagPhotoDeletions(
        context: Context,
        findCurrentOwnership: suspend (Long) -> BagPhotoOwnership?,
    ) {
        pendingBagPhotoDeletions(context).forEach { deletion ->
            val bagId = deletion.bagId ?: bagIdFromDeletionId(deletion.deletionId)
            if (bagId == null) {
                Log.w(TAG, "Cannot safely reconcile bag-photo cleanup without a bag id")
                return@forEach
            }
            val currentOwnership = runCatching { findCurrentOwnership(bagId) }
                .onFailure { error ->
                    Log.w(TAG, "Could not verify current bag-photo ownership; cleanup will retry", error)
                }
                .getOrElse { return@forEach }
            if (deleteUnreferencedPermanentBagPhotos(context, deletion, currentOwnership)) {
                clearPendingBagPhotoDeletion(context, deletion.deletionId)
            }
        }
    }

    @VisibleForTesting
    internal fun bagIdFromDeletionId(deletionId: String): Long? =
        deletionId.toLongOrNull()
            ?: deletionId
                .takeIf { it.startsWith("replace:") }
                ?.removePrefix("replace:")
                ?.substringBefore(':')
                ?.toLongOrNull()

    @VisibleForTesting
    internal fun cleanupPlan(
        deletion: PendingBagPhotoDeletion,
        currentOwnership: BagPhotoOwnership?,
    ): BagPhotoCleanupPlan {
        val protectedUris = BagPhotoReviewUris.parse(currentOwnership?.photoUrisCsv).toSet()
        val expectedBagId = deletion.bagId ?: bagIdFromDeletionId(deletion.deletionId)
        if (currentOwnership != null && currentOwnership.bagId != expectedBagId) {
            return BagPhotoCleanupPlan(
                sessionId = null,
                photoUrisCsv = null,
                protectedPhotoUris = protectedUris,
                canClearJournal = false,
            )
        }
        val candidateUris = BagPhotoReviewUris.parse(deletion.photoUrisCsv)
            .filterNot { it in protectedUris }
        return BagPhotoCleanupPlan(
            sessionId = deletion.sessionId?.takeUnless { it == currentOwnership?.sessionId },
            photoUrisCsv = candidateUris.joinToString(",").takeIf(String::isNotBlank),
            protectedPhotoUris = protectedUris,
        )
    }

    internal fun deleteUnreferencedPermanentBagPhotos(
        context: Context,
        deletion: PendingBagPhotoDeletion,
        currentOwnership: BagPhotoOwnership?,
    ): Boolean {
        val plan = cleanupPlan(deletion, currentOwnership)
        if (!plan.canClearJournal) {
            Log.w(TAG, "Bag-photo ownership did not match the pending cleanup journal")
            return false
        }
        return deletePermanentBagPhotos(
            context = context,
            sessionId = plan.sessionId,
            photoUrisCsv = plan.photoUrisCsv,
            protectedPhotoUris = plan.protectedPhotoUris,
        )
    }

    @VisibleForTesting
    internal fun encodePendingBagPhotoDeletion(deletion: PendingBagPhotoDeletion): String =
        journalJson.encodeToString(PendingBagPhotoDeletion.serializer(), deletion)

    @VisibleForTesting
    internal fun decodePendingBagPhotoDeletion(value: String): PendingBagPhotoDeletion? =
        runCatching {
            journalJson.decodeFromString(PendingBagPhotoDeletion.serializer(), value)
        }.getOrNull()

    fun recoverInterruptedPermanentReplacements(context: Context) {
        recoverInterruptedReplacements(permanentDirectory(context))
    }

    @VisibleForTesting
    internal fun recoverInterruptedReplacements(
        permanentDir: File,
        directorySync: DirectorySync = AndroidDirectorySync,
    ) {
        permanentDir.listFiles()
            ?.filter { backup ->
                isOwnedRegularFile(permanentDir, backup) &&
                    backup.name.startsWith(".") &&
                    backup.name.endsWith(".backup")
            }
            ?.forEach { backup ->
                val destinationName = backup.name
                    .removePrefix(".")
                    .removeSuffix(".backup")
                if (!destinationName.startsWith("bag_") &&
                    !destinationName.startsWith("thumb_")
                ) {
                    return@forEach
                }
                val destination = File(permanentDir, destinationName)
                runCatching { recoverFallbackBackup(destination, directorySync) }
                    .onFailure { error ->
                        Log.w(TAG, "Could not recover interrupted photo replacement", error)
                    }
            }
    }

    fun deletePermanentPhotosForSession(context: Context, sessionId: String): Boolean {
        return deletePermanentPhotosForSession(
            permanentDir = permanentDirectory(context),
            sessionId = sessionId,
            protectedFiles = emptySet(),
            directorySync = AndroidDirectorySync,
        )
    }

    @VisibleForTesting
    internal fun deletePermanentPhotosForSession(
        permanentDir: File,
        sessionId: String,
        protectedFiles: Set<File> = emptySet(),
        directorySync: DirectorySync = AndroidDirectorySync,
        deleteFile: (File) -> Boolean = File::delete,
    ): Boolean {
        val storageKey = storageKeyForSession(sessionId)
        val files = permanentFiles(permanentDir) ?: return !permanentDir.exists()
        val candidates = files
            .filter { file ->
                isOwnedRegularFile(permanentDir, file) &&
                    isSessionOwnedPermanentPhotoName(file.name, storageKey) &&
                    file.canonicalFile !in protectedFiles
            }
            .toSet()
        return deletePermanentFilesDurably(
            permanentDir = permanentDir,
            files = candidates,
            directorySync = directorySync,
            deleteFile = deleteFile,
        )
    }

    fun deletePermanentBagPhotos(
        context: Context,
        sessionId: String?,
        photoUrisCsv: String?,
        protectedPhotoUris: Collection<String> = emptySet(),
    ): Boolean = deletePermanentBagPhotos(
        permanentDir = permanentDirectory(context),
        sessionId = sessionId,
        photoUrisCsv = photoUrisCsv,
        protectedPhotoUris = protectedPhotoUris,
        directorySync = AndroidDirectorySync,
    )

    @VisibleForTesting
    internal fun deletePermanentBagPhotos(
        permanentDir: File,
        sessionId: String?,
        photoUrisCsv: String?,
        protectedPhotoUris: Collection<String> = emptySet(),
        directorySync: DirectorySync = AndroidDirectorySync,
        deleteFile: (File) -> Boolean = File::delete,
    ): Boolean {
        val protectedFiles = protectedPhotoUris
            .mapNotNull { uriString -> resolvePermanentPhoto(permanentDir, uriString) }
            .map(File::getCanonicalFile)
            .toSet()
        val referencedFiles = photoUrisCsv
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { uriString -> resolvePermanentPhoto(permanentDir, uriString) }
            .map(File::getCanonicalFile)
            .filterNot { it in protectedFiles }
            .toSet()

        val sessionFiles = sessionId?.let { id ->
            val storageKey = storageKeyForSession(id)
            val files = permanentFiles(permanentDir) ?: return !permanentDir.exists()
            files.filter { file ->
                isOwnedRegularFile(permanentDir, file) &&
                    isSessionOwnedPermanentPhotoName(file.name, storageKey) &&
                    file.canonicalFile !in protectedFiles
            }
        }.orEmpty()
        return deletePermanentFilesDurably(
            permanentDir = permanentDir,
            files = sessionFiles + referencedFiles,
            directorySync = directorySync,
            deleteFile = deleteFile,
        )
    }

    private fun permanentFiles(permanentDir: File): List<File>? {
        if (!permanentDir.exists()) return emptyList()
        if (!permanentDir.isDirectory) {
            Log.w(TAG, "Permanent photo path is not a directory: ${permanentDir.path}")
            return null
        }
        return permanentDir.listFiles()?.toList().also { files ->
            if (files == null) {
                Log.w(TAG, "Could not enumerate permanent photo directory ${permanentDir.path}")
            }
        }
    }

    private fun deletePermanentFilesDurably(
        permanentDir: File,
        files: Collection<File>,
        directorySync: DirectorySync,
        deleteFile: (File) -> Boolean,
    ): Boolean {
        var deletedAll = true
        files.toSet().forEach { file ->
            if (!file.exists()) return@forEach
            if (!deleteFile(file)) {
                deletedAll = false
                Log.w(TAG, "Could not remove permanent bag photo ${file.path}")
            }
        }
        if (permanentDir.exists()) {
            try {
                directorySync.sync(permanentDir)
            } catch (error: Exception) {
                Log.w(TAG, "Could not durably sync permanent photo deletion", error)
                deletedAll = false
            }
        }
        return deletedAll
    }

    internal fun isSessionOwnedPermanentPhotoName(fileName: String, storageKey: String): Boolean =
        fileName.startsWith("bag_${storageKey}_") ||
            fileName.startsWith(".bag_${storageKey}_") ||
            fileName == focusedThumbnailFileName(storageKey) ||
            fileName.startsWith(".${focusedThumbnailFileName(storageKey)}.")

    internal fun resolvePermanentPhoto(context: Context, uriString: String): File? =
        resolvePermanentPhoto(permanentDirectory(context), uriString)

    private fun resolvePermanentPhoto(permanentDir: File, uriString: String): File? =
        resolveOwnedPhoto(permanentDir, uriString)
            ?.takeIf { file ->
                file.name.startsWith("bag_") || file.name.startsWith("thumb_")
            }

    private fun decodeBoundedPhoto(filePath: String, maxLongEdgePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(File(filePath))
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val target = PhotoStoragePolicy.scaledDimensions(
                    width = info.size.width,
                    height = info.size.height,
                    maxLongEdgePx = maxLongEdgePx,
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(target.width, target.height)
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        val sourceLongEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (sourceLongEdge <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoStoragePolicy.boundedDecodeSampleSize(
                sourceLongEdgePx = sourceLongEdge,
                maxLongEdgePx = maxLongEdgePx,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(filePath, options) ?: return null
        return ImagePreprocessor.applyExifRotation(decoded, filePath).also { oriented ->
            if (oriented !== decoded && !decoded.isRecycled) decoded.recycle()
        }
    }

    internal fun writeOptimizedWebp(
        source: Bitmap,
        destination: File,
        maxLongEdgePx: Int,
        quality: Int,
    ) {
        val temporary = File(
            destination.parentFile,
            ".${destination.name}.${UUID.randomUUID()}.tmp",
        )
        var resized: Bitmap? = null
        try {
            check(temporary.createNewFile()) { "Could not create optimized WebP temporary file" }
            val dimensions = PhotoStoragePolicy.scaledDimensions(
                width = source.width,
                height = source.height,
                maxLongEdgePx = maxLongEdgePx,
            )
            resized = if (source.width == dimensions.width && source.height == dimensions.height) {
                source
            } else {
                Bitmap.createScaledBitmap(source, dimensions.width, dimensions.height, true)
            }
            FileOutputStream(temporary).use { output ->
                check(requireNotNull(resized).compress(webpCompressFormat(), quality, output)) {
                    "Could not encode optimized WebP photo"
                }
                output.flush()
                output.fd.sync()
            }
            replaceFileDurably(temporary, destination)
        } finally {
            temporary.delete()
            resized?.takeIf { it !== source && !it.isRecycled }?.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    @VisibleForTesting
    internal fun replaceFileDurably(
        temporary: File,
        destination: File,
        fileSync: FileSync = AndroidFileSync,
        atomicReplace: (File, File) -> Unit = ::atomicReplace,
        directorySync: DirectorySync = AndroidDirectorySync,
    ) {
        val parentDirectory = requireNotNull(destination.canonicalFile.parentFile)
        require(temporary.canonicalFile.parentFile == parentDirectory) {
            "Temporary and destination files must share a directory"
        }
        require(
            !Files.isSymbolicLink(temporary.toPath()) &&
                Files.isRegularFile(temporary.toPath(), LinkOption.NOFOLLOW_LINKS),
        ) {
            "Replacement temporary must be a non-symlink regular file"
        }
        requireSafeReplacementDestination(destination)
        recoverFallbackBackup(destination, directorySync, atomicReplace)
        fileSync.sync(temporary)

        val backup = File(parentDirectory, ".${destination.name}.backup")
        val hadDestination = Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)
        if (hadDestination) {
            Files.createLink(
                backup.toPath(),
                destination.toPath(),
            )
        }

        try {
            atomicReplace(temporary, destination)
            directorySync.sync(parentDirectory)
        } catch (error: Exception) {
            if (!temporary.exists() && destination.exists()) {
                restoreAfterReplacementFailure(
                    temporary = temporary,
                    destination = destination,
                    backup = backup,
                    hadDestination = hadDestination,
                    fileSync = fileSync,
                    atomicReplace = atomicReplace,
                    directorySync = directorySync,
                    originalFailure = error,
                )
            }
            throw error
        }

        if (backup.exists()) {
            if (!backup.delete()) {
                Log.w(TAG, "Could not remove replaced photo rollback link ${backup.path}")
            } else {
                runCatching { directorySync.sync(parentDirectory) }
                    .onFailure { error ->
                        Log.w(TAG, "Could not durably sync photo rollback-link cleanup", error)
                    }
            }
        }
    }

    private fun recoverFallbackBackup(
        destination: File,
        directorySync: DirectorySync,
        atomicReplace: (File, File) -> Unit = ::atomicReplace,
    ) {
        val backup = File(destination.parentFile, ".${destination.name}.backup")
        if (!Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        require(
            !Files.isSymbolicLink(backup.toPath()) &&
                Files.isRegularFile(backup.toPath(), LinkOption.NOFOLLOW_LINKS),
        ) {
            "Replacement backup must be a non-symlink regular file"
        }
        if (Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            requireSafeReplacementDestination(destination)
            check(backup.delete()) { "Could not remove stale photo backup" }
        } else {
            atomicReplace(backup, destination)
        }
        directorySync.sync(requireNotNull(destination.parentFile))
    }

    private fun atomicReplace(temporary: File, destination: File) {
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun restoreAfterReplacementFailure(
        temporary: File,
        destination: File,
        backup: File,
        hadDestination: Boolean,
        fileSync: FileSync,
        atomicReplace: (File, File) -> Unit,
        directorySync: DirectorySync,
        originalFailure: Exception,
    ) {
        runCatching {
            Files.copy(
                destination.toPath(),
                temporary.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            fileSync.sync(temporary)
            if (hadDestination) {
                check(backup.exists()) { "Previous photo destination is unavailable for rollback" }
                atomicReplace(backup, destination)
            } else {
                Files.move(
                    destination.toPath(),
                    temporary.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            directorySync.sync(requireNotNull(destination.parentFile))
        }.onFailure { rollbackFailure ->
            originalFailure.addSuppressed(rollbackFailure)
        }
    }

    private fun requireSafeReplacementDestination(destination: File) {
        if (!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        require(
            !Files.isSymbolicLink(destination.toPath()) &&
                Files.isRegularFile(destination.toPath(), LinkOption.NOFOLLOW_LINKS),
        ) {
            "Replacement destination must be a non-symlink regular file"
        }
    }

    private fun requireOwnedStorageDirectory(storageDir: File) {
        if (!storageDir.exists()) {
            check(storageDir.mkdirs()) { "Could not create photo storage directory" }
        }
        require(
            !Files.isSymbolicLink(storageDir.toPath()) &&
                Files.isDirectory(storageDir.toPath(), LinkOption.NOFOLLOW_LINKS),
        ) {
            "Photo storage root must be a non-symlink directory"
        }
    }

    private fun requireOwnedDirectChild(ownedDirectory: File, fileName: String): File {
        require(fileName.isNotBlank()) { "Owned photo file name must not be blank" }
        val ownedPath = ownedDirectory.absoluteFile.toPath().normalize()
        val candidatePath = ownedPath.resolve(fileName).normalize()
        require(candidatePath.parent == ownedPath) {
            "Owned photo destination must be a direct child of its storage root"
        }
        return candidatePath.toFile()
    }

    @VisibleForTesting
    internal fun resolveOwnedPhoto(ownedDirectory: File, uriString: String): File? =
        runCatching {
            val uri = URI(uriString)
            if (!uri.scheme.equals("file", ignoreCase = true) ||
                !uri.rawAuthority.isNullOrEmpty() ||
                uri.rawQuery != null ||
                uri.rawFragment != null
            ) {
                return@runCatching null
            }
            val decodedPath = uri.path ?: return@runCatching null
            if (decodedPath.replace('\\', '/').split('/').any { it == "." || it == ".." }) {
                return@runCatching null
            }

            val absoluteDirectory = ownedDirectory.absoluteFile
            val candidate = File(uri).absoluteFile
            if (candidate.parentFile != absoluteDirectory) {
                return@runCatching null
            }
            if (Files.isSymbolicLink(candidate.toPath())) {
                return@runCatching null
            }

            val canonicalDirectory = ownedDirectory.canonicalFile
            candidate.canonicalFile.takeIf { file ->
                file.parentFile == canonicalDirectory
            }
        }.getOrNull()

    private fun isOwnedRegularFile(ownedDirectory: File, file: File): Boolean =
        resolveOwnedPhoto(ownedDirectory, file.toURI().toString())?.isFile == true

    private fun stagingDirectory(context: Context): File =
        File(context.noBackupFilesDir, STAGING_DIR)

    private fun permanentDirectory(context: Context): File =
        File(context.filesDir, PERMANENT_DIR)

    @Suppress("DEPRECATION")
    private fun webpCompressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
}
