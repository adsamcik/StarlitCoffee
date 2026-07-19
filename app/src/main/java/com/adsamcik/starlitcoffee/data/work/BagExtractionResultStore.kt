package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import android.util.Log
import com.adsamcik.starlitcoffee.util.AndroidDirectorySync
import com.adsamcik.starlitcoffee.util.AndroidFileSync
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
data class StoredBagExtractionResult(
    val successful: Boolean,
    val resultJson: String,
    val createdAtMillis: Long,
    val reviewContext: BagReviewContext? = null,
)

object BagExtractionResultStore {
    private const val TAG = "BagExtractionResult"
    private const val RESULT_DIR = "bag_extraction_results"
    private const val RESULT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun write(
        context: Context,
        workId: String,
        successful: Boolean,
        resultJson: String,
        reviewContext: BagReviewContext? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) = write(
        directory = File(context.noBackupFilesDir, RESULT_DIR),
        workId = workId,
        successful = successful,
        resultJson = resultJson,
        reviewContext = reviewContext,
        nowMillis = nowMillis,
    )

    fun writeIfAbsent(
        context: Context,
        workId: String,
        successful: Boolean,
        resultJson: String,
        reviewContext: BagReviewContext? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): StoredBagExtractionResult = writeIfAbsent(
        directory = File(context.noBackupFilesDir, RESULT_DIR),
        workId = workId,
        successful = successful,
        resultJson = resultJson,
        reviewContext = reviewContext,
        nowMillis = nowMillis,
    )

    @Synchronized
    internal fun writeIfAbsent(
        directory: File,
        workId: String,
        successful: Boolean,
        resultJson: String,
        reviewContext: BagReviewContext? = null,
        nowMillis: Long = System.currentTimeMillis(),
        fileSync: FileSync = AndroidFileSync,
        directorySync: DirectorySync = AndroidDirectorySync,
        atomicReplace: (File, File) -> Unit = ::atomicReplace,
    ): StoredBagExtractionResult {
        read(directory, workId)?.let { return it }
        val stored = StoredBagExtractionResult(
            successful = successful,
            resultJson = resultJson,
            createdAtMillis = nowMillis,
            reviewContext = reviewContext,
        )
        write(
            directory = directory,
            workId = workId,
            successful = stored.successful,
            resultJson = stored.resultJson,
            reviewContext = stored.reviewContext,
            nowMillis = stored.createdAtMillis,
            fileSync = fileSync,
            directorySync = directorySync,
            atomicReplace = atomicReplace,
        )
        return stored
    }

    internal fun write(
        directory: File,
        workId: String,
        successful: Boolean,
        resultJson: String,
        reviewContext: BagReviewContext? = null,
        nowMillis: Long = System.currentTimeMillis(),
        fileSync: FileSync = AndroidFileSync,
        directorySync: DirectorySync = AndroidDirectorySync,
        atomicReplace: (File, File) -> Unit = ::atomicReplace,
    ) {
        val destination = resultFile(directory, workId)
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        check(destination.parentFile?.exists() == true || destination.parentFile?.mkdirs() == true) {
            "Could not create bag extraction result directory"
        }
        try {
            requireSafeDestination(destination)
            require(!Files.isSymbolicLink(temporary.toPath())) {
                "Bag extraction result temporary file cannot be a symbolic link"
            }
            FileOutputStream(temporary).use { output ->
                output.write(
                    json.encodeToString(
                        StoredBagExtractionResult(
                            successful = successful,
                            resultJson = resultJson,
                            createdAtMillis = nowMillis,
                            reviewContext = reviewContext,
                        ),
                    ).toByteArray(Charsets.UTF_8),
                )
                output.flush()
            }
            fileSync.sync(temporary)
            atomicReplace(temporary, destination)
            directorySync.sync(directory)
        } finally {
            temporary.delete()
        }
    }

    fun read(context: Context, workId: String): StoredBagExtractionResult? {
        return read(File(context.noBackupFilesDir, RESULT_DIR), workId)
    }

    internal fun read(directory: File, workId: String): StoredBagExtractionResult? {
        val file = safeExistingResultFile(directory, workId) ?: return null
        return runCatching {
            json.decodeFromString<StoredBagExtractionResult>(file.readText())
        }.onFailure { error ->
            Log.e(TAG, "Failed to read stored bag extraction result", error)
        }.getOrNull()
    }

    fun delete(context: Context, workId: String) {
        check(delete(File(context.noBackupFilesDir, RESULT_DIR), workId)) {
            "Could not safely delete stored bag extraction result for $workId"
        }
    }

    internal fun delete(
        directory: File,
        workId: String,
        directorySync: DirectorySync = AndroidDirectorySync,
    ): Boolean {
        val file = resultFile(directory, workId)
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (directory.isDirectory) {
                directorySync.sync(directory)
            }
            return true
        }
        if (!isSafeRegularFile(file)) return false
        if (!file.delete()) return false
        directorySync.sync(directory)
        return true
    }

    fun deleteExpired(
        context: Context,
        protectedWorkIds: Collection<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        deleteExpired(
            directory = File(context.noBackupFilesDir, RESULT_DIR),
            protectedWorkIds = protectedWorkIds,
            nowMillis = nowMillis,
        )
    }

    fun expiredWorkIds(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Set<String> = expiredWorkIds(
        directory = File(context.noBackupFilesDir, RESULT_DIR),
        nowMillis = nowMillis,
    )

    internal fun expiredWorkIds(
        directory: File,
        nowMillis: Long,
    ): Set<String> = directory
        .listFiles()
        ?.mapNotNull { file ->
            val workId = workIdFromResultFileName(file.name) ?: return@mapNotNull null
            if (!isSafeRegularFile(file)) return@mapNotNull null
            val createdAtMillis = runCatching {
                json.decodeFromString<StoredBagExtractionResult>(file.readText()).createdAtMillis
            }.getOrNull()?.takeIf { it > 0L }
                ?: Files.getLastModifiedTime(file.toPath(), LinkOption.NOFOLLOW_LINKS).toMillis()
            workId.takeIf { nowMillis - createdAtMillis >= RESULT_RETENTION_MILLIS }
        }
        ?.toSet()
        .orEmpty()

    internal fun deleteExpired(
        directory: File,
        protectedWorkIds: Collection<String>,
        nowMillis: Long,
        directorySync: DirectorySync = AndroidDirectorySync,
    ) {
        val protectedFiles = protectedWorkIds
            .mapNotNull { workId -> runCatching { resultFile(directory, workId) }.getOrNull() }
            .toSet()
        val expiredFiles = directory
            .listFiles()
            ?.filter { file ->
                workIdFromResultFileName(file.name) != null &&
                    isSafeRegularFile(file) &&
                    file.toPath().toAbsolutePath().normalize().toFile() !in protectedFiles &&
                    nowMillis - Files.getLastModifiedTime(
                        file.toPath(),
                        LinkOption.NOFOLLOW_LINKS,
                    ).toMillis() >= RESULT_RETENTION_MILLIS
            }
            .orEmpty()
        var deletedAny = false
        var failure: IOException? = null
        expiredFiles.forEach { file ->
            if (file.delete()) {
                deletedAny = true
            } else {
                val deleteFailure = IOException(
                    "Could not delete expired bag extraction result ${file.path}",
                )
                if (failure == null) {
                    failure = deleteFailure
                } else {
                    failure.addSuppressed(deleteFailure)
                }
            }
        }
        if (deletedAny) {
            try {
                directorySync.sync(directory)
            } catch (syncFailure: IOException) {
                if (failure == null) {
                    failure = syncFailure
                } else {
                    failure.addSuppressed(syncFailure)
                }
            }
        }
        failure?.let { throw it }
    }

    private fun resultFile(directory: File, workId: String): File {
        val normalizedWorkId = UUID.fromString(workId).toString()
        val lexicalDirectory = directory.toPath().toAbsolutePath().normalize()
        return lexicalDirectory.resolve("result_$normalizedWorkId.json").toFile()
    }

    private fun safeExistingResultFile(directory: File, workId: String): File? {
        val file = resultFile(directory, workId)
        return file.takeIf(::isSafeRegularFile)
    }

    private fun requireSafeDestination(destination: File) {
        if (!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        require(isSafeRegularFile(destination)) {
            "Bag extraction result destination must be a non-symlink regular file"
        }
    }

    private fun isSafeRegularFile(file: File): Boolean =
        !Files.isSymbolicLink(file.toPath()) &&
            Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun workIdFromResultFileName(fileName: String): String? {
        val rawWorkId = fileName
            .takeIf { name -> name.startsWith("result_") && name.endsWith(".json") }
            ?.removePrefix("result_")
            ?.removeSuffix(".json")
            ?: return null
        val normalizedWorkId = runCatching { UUID.fromString(rawWorkId).toString() }.getOrNull()
            ?: return null
        return normalizedWorkId.takeIf { fileName == "result_$normalizedWorkId.json" }
    }

    private fun atomicReplace(temporary: File, destination: File) {
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
