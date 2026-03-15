package com.adsamcik.starlitcoffee.audio

import java.io.File
import java.io.InputStream

/**
 * Reads Audacity label track files for ground truth event timing.
 * Format: TSV with columns: start_time\tend_time\tlabel
 *
 * Example:
 * ```
 * 0.000000	0.000000	ambient_baseline
 * 5.120000	5.120000	pour_start
 * 18.450000	18.450000	pour_stop
 * ```
 */
object LabelReader {

    data class Label(
        val startTimeS: Double,
        val endTimeS: Double,
        val name: String,
    ) {
        val startTimeMs: Long get() = (startTimeS * 1000).toLong()
        val endTimeMs: Long get() = (endTimeS * 1000).toLong()
        val isPointLabel: Boolean get() = startTimeS == endTimeS
    }

    /**
     * Reads labels from an Audacity label file.
     * Skips blank lines and comment lines starting with #.
     */
    fun read(input: InputStream): List<Label> {
        return input.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .map { line ->
                    val parts = line.split('\t')
                    require(parts.size >= 3) {
                        "Invalid label line (expected 3+ tab-separated fields): '$line'"
                    }
                    Label(
                        startTimeS = parts[0].toDouble(),
                        endTimeS = parts[1].toDouble(),
                        name = parts.subList(2, parts.size).joinToString("\t"),
                    )
                }
                .toList()
        }
    }

    fun read(file: File): List<Label> {
        return file.inputStream().buffered().use { read(it) }
    }

    fun readFromResource(resourcePath: String): List<Label> {
        val stream = LabelReader::class.java.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Resource not found: $resourcePath")
        return stream.use { read(it) }
    }
}
