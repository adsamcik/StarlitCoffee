package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import com.adsamcik.starlitcoffee.util.AndroidDirectorySync
import com.adsamcik.starlitcoffee.util.AndroidFileSync
import com.adsamcik.starlitcoffee.util.DirectorySync
import com.adsamcik.starlitcoffee.util.FileSync
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
internal data class BagExtractionInput(
    val photoUrisCsv: String,
    val knownValuesJson: String,
    val runLlm: Boolean = true,
    val sessionId: String = "",
    val generationId: String = "",
    val reviewContext: BagReviewContext? = null,
    val notifyOnCompletion: Boolean = false,
    val createdAtMillis: Long = 0L,
)

internal object BagExtractionInputStore {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    fun allocatePath(context: Context, token: String = UUID.randomUUID().toString()): String =
        allocatePath(inputDirectory(context), token)

    fun write(context: Context, path: String, input: BagExtractionInput): String =
        writeToPath(inputDirectory(context), path, input)

    fun read(context: Context, path: String): BagExtractionInput =
        readFromDirectory(inputDirectory(context), path)

    fun delete(context: Context, path: String?) {
        if (path == null) return
        deleteFromDirectory(inputDirectory(context), path)
    }

    internal fun writeToDirectory(
        directory: File,
        input: BagExtractionInput,
        token: String = UUID.randomUUID().toString(),
        fileSync: FileSync = AndroidFileSync,
        directorySync: DirectorySync = AndroidDirectorySync,
        atomicReplace: (File, File) -> Unit = ::atomicReplace,
    ): String = writeToPath(
        directory = directory,
        path = allocatePath(directory, token),
        input = input,
        fileSync = fileSync,
        directorySync = directorySync,
        atomicReplace = atomicReplace,
    )

    internal fun allocatePath(directory: File, token: String): String {
        check(directory.exists() || directory.mkdirs()) {
            "Could not create bag extraction input directory"
        }
        require(token.matches(Regex("[A-Za-z0-9._-]+"))) {
            "Bag extraction input token contains unsupported characters"
        }
        return File(directory, "input_$token.json").absolutePath
    }

    internal fun writeToPath(
        directory: File,
        path: String,
        input: BagExtractionInput,
        fileSync: FileSync = AndroidFileSync,
        directorySync: DirectorySync = AndroidDirectorySync,
        atomicReplace: (File, File) -> Unit = ::atomicReplace,
    ): String {
        check(directory.exists() || directory.mkdirs()) {
            "Could not create bag extraction input directory"
        }
        val destination = resolveManifestPath(directory, path)
        requireSafeDestination(destination)
        val temporary = File(directory, ".${destination.name}.tmp")
        try {
            require(!Files.isSymbolicLink(temporary.toPath())) {
                "Bag extraction input temporary file cannot be a symbolic link"
            }
            FileOutputStream(temporary).use { output ->
                output.write(json.encodeToString(input).toByteArray(Charsets.UTF_8))
                output.flush()
            }
            fileSync.sync(temporary)
            atomicReplace(temporary, destination)
            directorySync.sync(directory)
            return destination.absolutePath
        } finally {
            temporary.delete()
        }
    }

    internal fun readFromDirectory(directory: File, path: String): BagExtractionInput {
        val manifest = resolveExistingManifest(directory, path)
        return json.decodeFromString(manifest.readText(Charsets.UTF_8))
    }

    internal fun deleteFromDirectory(
        directory: File,
        path: String,
        directorySync: DirectorySync = AndroidDirectorySync,
    ) {
        val manifest = runCatching { resolveManifestPath(directory, path) }.getOrNull() ?: return
        if (!Files.exists(manifest.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (directory.isDirectory) {
                directorySync.sync(directory)
            }
            return
        }
        requireSafeExistingManifest(manifest)
        check(manifest.delete()) { "Could not delete bag extraction input manifest" }
        directorySync.sync(directory)
    }

    fun deleteExpired(
        context: Context,
        protectedPaths: Collection<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        deleteExpiredFromDirectory(
            directory = inputDirectory(context),
            protectedPaths = protectedPaths,
            cutoffMillis = nowMillis - INPUT_RETENTION_MILLIS,
        )
    }

    fun isExpired(
        context: Context,
        path: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val directory = inputDirectory(context)
        val manifest = resolveExistingManifest(directory, path)
        val input = readFromDirectory(directory, path)
        val createdAtMillis = input.createdAtMillis.takeIf { it > 0L }
            ?: Files.getLastModifiedTime(manifest.toPath(), LinkOption.NOFOLLOW_LINKS).toMillis()
        return nowMillis - createdAtMillis >= INPUT_RETENTION_MILLIS
    }

    private fun inputDirectory(context: Context): File =
        File(context.noBackupFilesDir, INPUT_DIRECTORY)

    private fun resolveManifestPath(directory: File, path: String): File {
        val lexicalDirectory = directory.toPath().toAbsolutePath().normalize()
        val lexicalManifest = File(path).toPath().toAbsolutePath().normalize()
        require(
            lexicalManifest.parent == lexicalDirectory &&
                INPUT_FILE_NAME.matches(lexicalManifest.fileName.toString()),
        ) {
            "Bag extraction input manifest is outside the managed directory"
        }
        return lexicalManifest.toFile()
    }

    private fun resolveExistingManifest(directory: File, path: String): File =
        resolveManifestPath(directory, path).also(::requireSafeExistingManifest)

    private fun requireSafeDestination(destination: File) {
        if (!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        requireSafeExistingManifest(destination)
    }

    private fun requireSafeExistingManifest(manifest: File) {
        require(!Files.isSymbolicLink(manifest.toPath())) {
            "Bag extraction input manifest cannot be a symbolic link"
        }
        require(Files.isRegularFile(manifest.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "Bag extraction input manifest must be a regular file"
        }
    }

    internal fun deleteExpiredFromDirectory(
        directory: File,
        protectedPaths: Collection<String>,
        cutoffMillis: Long,
        directorySync: DirectorySync = AndroidDirectorySync,
    ) {
        val protectedManifests = protectedPaths
            .mapNotNull { path -> runCatching { resolveManifestPath(directory, path) }.getOrNull() }
            .toSet()
        val expiredManifests = directory.listFiles()
            ?.filter { manifest ->
                INPUT_FILE_NAME.matches(manifest.name) &&
                    !Files.isSymbolicLink(manifest.toPath()) &&
                    Files.isRegularFile(manifest.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    Files.getLastModifiedTime(
                        manifest.toPath(),
                        LinkOption.NOFOLLOW_LINKS,
                    ).toMillis() < cutoffMillis &&
                    manifest.toPath().toAbsolutePath().normalize().toFile() !in protectedManifests
            }
            .orEmpty()
        var deletedAny = false
        var failure: IOException? = null
        expiredManifests.forEach { manifest ->
            if (manifest.delete()) {
                deletedAny = true
            } else {
                val deleteFailure = IOException(
                    "Could not delete expired bag extraction input manifest ${manifest.path}",
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

    private fun atomicReplace(temporary: File, destination: File) {
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private val INPUT_FILE_NAME = Regex("""input_[A-Za-z0-9._-]+\.json""")
    private const val INPUT_DIRECTORY = "bag_extraction_inputs"
    private const val INPUT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
}
