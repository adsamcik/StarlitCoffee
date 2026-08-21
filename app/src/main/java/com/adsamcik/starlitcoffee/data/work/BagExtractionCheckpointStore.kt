package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import com.adsamcik.starlitcoffee.util.AndroidDirectorySync
import com.adsamcik.starlitcoffee.util.AndroidFileSync
import com.adsamcik.starlitcoffee.util.BagPhotoProcessingResult
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import dev.tracebox.Tracebox

/** Latest deterministic scan result, retained only while its worker is active. */
object BagExtractionCheckpointStore {
    private const val CHECKPOINT_DIR = "bag_extraction_checkpoints"

    fun write(context: Context, workId: String, resultJson: String) = write(
        directory = File(context.noBackupFilesDir, CHECKPOINT_DIR),
        workId = workId,
        resultJson = resultJson,
    )

    @Synchronized
    internal fun write(
        directory: File,
        workId: String,
        resultJson: String,
        fileSync: FileSync = AndroidFileSync,
        directorySync: DirectorySync = AndroidDirectorySync,
        atomicReplace: (File, File) -> Unit = ::atomicReplace,
    ) {
        val destination = checkpointFile(directory, workId)
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        check(destination.parentFile?.exists() == true || destination.parentFile?.mkdirs() == true) {
            "Could not create bag extraction checkpoint directory"
        }
        try {
            requireSafeDestination(destination)
            require(!Files.isSymbolicLink(temporary.toPath())) {
                "Bag extraction checkpoint temporary file cannot be a symbolic link"
            }
            FileOutputStream(temporary).use { output ->
                output.write(resultJson.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            fileSync.sync(temporary)
            atomicReplace(temporary, destination)
            directorySync.sync(directory)
        } finally {
            temporary.delete()
        }
    }

    fun read(context: Context, workId: String): String? =
        read(File(context.noBackupFilesDir, CHECKPOINT_DIR), workId)

    internal fun read(directory: File, workId: String): String? {
        val file = checkpointFile(directory, workId).takeIf(::isSafeRegularFile) ?: return null
        return runCatching(file::readText)
            .onFailure { error -> Tracebox.log.error(error, "Failed to read bag extraction checkpoint") }
            .getOrNull()
    }

    fun delete(context: Context, workId: String) {
        check(delete(File(context.noBackupFilesDir, CHECKPOINT_DIR), workId)) {
            "Could not safely delete bag extraction checkpoint for $workId"
        }
    }

    internal fun delete(
        directory: File,
        workId: String,
        directorySync: DirectorySync = AndroidDirectorySync,
    ): Boolean {
        val file = checkpointFile(directory, workId)
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return true
        if (!isSafeRegularFile(file) || !file.delete()) return false
        directorySync.sync(directory)
        return true
    }

    private fun checkpointFile(directory: File, workId: String): File {
        val normalizedWorkId = UUID.fromString(workId).toString()
        return directory.toPath().toAbsolutePath().normalize()
            .resolve("checkpoint_$normalizedWorkId.json")
            .toFile()
    }

    private fun requireSafeDestination(destination: File) {
        if (!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        require(isSafeRegularFile(destination)) {
            "Bag extraction checkpoint destination must be a non-symlink regular file"
        }
    }

    private fun isSafeRegularFile(file: File): Boolean =
        !Files.isSymbolicLink(file.toPath()) &&
            Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun atomicReplace(temporary: File, destination: File) {
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

internal fun BagPhotoProcessingResult.hasDeterministicScanData(): Boolean =
    ocrPrefill != null ||
        fieldEvidence.isNotEmpty() ||
        photoAnalyses.isNotEmpty() ||
        detectedBarcode != null ||
        detectedQrUrl != null ||
        offLookupName != null ||
        offLookupRoaster != null
