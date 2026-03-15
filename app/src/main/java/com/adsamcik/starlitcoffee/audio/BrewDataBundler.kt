package com.adsamcik.starlitcoffee.audio

import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Bundles all brew session files (WAV, JSONL, metadata, labels, shadow log)
 * into a single zip archive for easy sharing and offline analysis.
 *
 * Output zip structure:
 * ```
 * brew_1720000000000.zip
 * ├── brew_1720000000000_meta.json
 * ├── brew_1720000000000_phase_0_bloom.wav
 * ├── brew_1720000000000_phase_0_bloom.jsonl
 * ├── brew_1720000000000_phase_1_main_pour.wav
 * ├── brew_1720000000000_phase_1_main_pour.jsonl
 * ├── brew_1720000000000_user_labels.txt
 * ├── brew_1720000000000_shadow.jsonl
 * └── manifest.txt
 * ```
 *
 * The manifest.txt lists all included files and basic session info
 * for quick orientation when opening the zip in a desktop tool.
 */
object BrewDataBundler {

    private const val TAG = "BrewDataBundler"
    private const val BUFFER_SIZE = 8192

    /**
     * Result of a bundle operation.
     */
    data class BundleResult(
        val zipFile: File,
        val fileCount: Int,
        val totalSizeBytes: Long,
        val errors: List<String>,
    ) {
        val success: Boolean get() = errors.isEmpty()
    }

    /**
     * Bundles all files for a brew session into a zip archive.
     *
     * Finds all files in [sessionDirectory] matching the [brewTimestamp] prefix
     * and compresses them into a single zip file.
     *
     * @param sessionDirectory directory containing session files (e.g. .../brew_audio/)
     * @param brewTimestamp the session timestamp (used as filename prefix filter)
     * @param outputDirectory where to write the zip (defaults to sessionDirectory)
     * @return BundleResult with zip path, file count, and any errors
     */
    fun bundle(
        sessionDirectory: File,
        brewTimestamp: String,
        outputDirectory: File = sessionDirectory,
    ): BundleResult {
        val errors = mutableListOf<String>()

        if (!sessionDirectory.isDirectory) {
            return BundleResult(File(""), 0, 0, listOf("Session directory does not exist: $sessionDirectory"))
        }

        // Find all files matching this brew timestamp
        val prefix = "brew_$brewTimestamp"
        val sessionFiles = sessionDirectory.listFiles { file ->
            file.isFile && file.name.startsWith(prefix)
        }?.sortedBy { it.name } ?: emptyList()

        if (sessionFiles.isEmpty()) {
            return BundleResult(File(""), 0, 0, listOf("No files found with prefix: $prefix"))
        }

        // Create zip file
        outputDirectory.mkdirs()
        val zipFile = File(outputDirectory, "${prefix}.zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                var fileCount = 0

                // Add each session file
                for (file in sessionFiles) {
                    try {
                        addFileToZip(zos, file)
                        fileCount++
                    } catch (e: Exception) {
                        errors.add("Failed to add ${file.name}: ${e.message}")
                        Log.w(TAG, "Failed to zip file: ${file.name}", e)
                    }
                }

                // Write manifest as final entry
                val manifest = buildManifest(brewTimestamp, sessionFiles, errors)
                val manifestEntry = ZipEntry("manifest.txt")
                zos.putNextEntry(manifestEntry)
                zos.write(manifest.toByteArray())
                zos.closeEntry()
            }
        } catch (e: Exception) {
            errors.add("Failed to create zip: ${e.message}")
            Log.e(TAG, "Failed to create brew data bundle", e)
            return BundleResult(zipFile, 0, 0, errors)
        }

        return BundleResult(
            zipFile = zipFile,
            fileCount = sessionFiles.size,
            totalSizeBytes = zipFile.length(),
            errors = errors,
        )
    }

    /**
     * Finds the most recent brew timestamp in the session directory.
     * Useful for "export last brew" functionality.
     */
    fun findLatestBrewTimestamp(sessionDirectory: File): String? {
        if (!sessionDirectory.isDirectory) return null
        return sessionDirectory.listFiles { file ->
            file.isFile && file.name.startsWith("brew_") && file.name.contains("_meta.json")
        }
            ?.maxByOrNull { it.lastModified() }
            ?.name
            ?.removePrefix("brew_")
            ?.removeSuffix("_meta.json")
    }

    /**
     * Bundles the most recent brew session.
     */
    fun bundleLatest(sessionDirectory: File): BundleResult {
        val timestamp = findLatestBrewTimestamp(sessionDirectory)
            ?: return BundleResult(File(""), 0, 0, listOf("No brew sessions found"))
        return bundle(sessionDirectory, timestamp)
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File) {
        val entry = ZipEntry(file.name)
        entry.time = file.lastModified()
        zos.putNextEntry(entry)

        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                zos.write(buffer, 0, bytesRead)
            }
        }
        zos.closeEntry()
    }

    private fun buildManifest(
        brewTimestamp: String,
        files: List<File>,
        errors: List<String>,
    ): String = buildString {
        appendLine("Starlit Coffee Brew Data Bundle")
        appendLine("================================")
        appendLine("Brew timestamp: $brewTimestamp")
        appendLine("Bundle created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        appendLine("Files: ${files.size}")
        appendLine()

        appendLine("Contents:")
        for (file in files) {
            val sizeKb = file.length() / 1024.0
            appendLine("  ${file.name} (%.1f KB)".format(sizeKb))
        }
        appendLine()

        appendLine("File types:")
        val wavCount = files.count { it.extension == "wav" }
        val jsonlCount = files.count { it.extension == "jsonl" }
        val jsonCount = files.count { it.extension == "json" }
        val txtCount = files.count { it.extension == "txt" }
        appendLine("  WAV recordings: $wavCount")
        appendLine("  JSONL flight data: $jsonlCount")
        appendLine("  JSON metadata: $jsonCount")
        appendLine("  Label files: $txtCount")
        appendLine()

        appendLine("Notes:")
        appendLine("  - WAV files are 16-bit mono PCM at 44100 Hz")
        appendLine("  - JSONL files contain per-250ms pipeline snapshots")
        appendLine("  - User labels are approximate (±2-3s accuracy)")
        appendLine("  - Load .txt label files in Audacity: File > Import > Labels")

        if (errors.isNotEmpty()) {
            appendLine()
            appendLine("Errors during bundling:")
            errors.forEach { appendLine("  ⚠ $it") }
        }
    }
}
