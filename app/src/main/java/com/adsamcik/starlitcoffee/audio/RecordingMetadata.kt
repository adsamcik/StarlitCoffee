package com.adsamcik.starlitcoffee.audio

import android.os.Build
import java.io.File

/**
 * Metadata for a brew recording session, written as a JSON sidecar alongside WAV files.
 *
 * Captures device info, brew parameters, and ambient conditions per the validation
 * strategy recording protocol. Used for:
 * - Grouping recordings by device/environment for threshold analysis
 * - Reproducing brew conditions when debugging detection issues
 * - Building device-specific calibration profiles
 *
 * File naming: `brew_{timestamp}_meta.json`
 */
data class RecordingMetadata(
    val phoneModel: String,
    val phoneManufacturer: String,
    val androidSdkVersion: Int,
    val brewTimestamp: Long,
    val method: String,
    val filterType: String,
    val doseG: Float,
    val waterG: Float,
    val ratio: Float,
    val grinderId: String?,
    val grinderSetting: String?,
    val placement: String,
    val environment: String,
    val ambientRmsDb: Float,
    val sampleRate: Int,
    val notes: String,
) {
    /**
     * Writes this metadata as a JSON file in the given directory.
     * Uses manual JSON construction to avoid adding a serialization dependency.
     */
    fun writeToFile(directory: File) {
        directory.mkdirs()
        val file = File(directory, "brew_${brewTimestamp}_meta.json")
        val json = buildString {
            appendLine("{")
            appendJsonField("phone_model", phoneModel)
            appendJsonField("phone_manufacturer", phoneManufacturer)
            appendJsonField("android_sdk_version", androidSdkVersion)
            appendJsonField("brew_timestamp", brewTimestamp)
            appendJsonField("method", method)
            appendJsonField("filter_type", filterType)
            appendJsonField("dose_g", doseG)
            appendJsonField("water_g", waterG)
            appendJsonField("ratio", ratio)
            appendJsonField("grinder_id", grinderId)
            appendJsonField("grinder_setting", grinderSetting)
            appendJsonField("placement", placement)
            appendJsonField("environment", environment)
            appendJsonField("ambient_rms_db", ambientRmsDb)
            appendJsonField("sample_rate", sampleRate)
            appendJsonFieldLast("notes", notes)
            appendLine("}")
        }
        file.writeText(json)
    }

    companion object {
        /**
         * Creates metadata from the current device and brew state.
         * Call at recording start, after ambient calibration.
         */
        fun fromCurrentSession(
            brewTimestamp: Long,
            method: String,
            filterType: String,
            doseG: Float,
            waterG: Float,
            ratio: Float,
            grinderId: String? = null,
            grinderSetting: String? = null,
            placement: String = "unknown",
            environment: String = "unknown",
            ambientRmsDb: Float = -96f,
            sampleRate: Int = 44100,
            notes: String = "",
        ): RecordingMetadata = RecordingMetadata(
            phoneModel = Build.MODEL,
            phoneManufacturer = Build.MANUFACTURER,
            androidSdkVersion = Build.VERSION.SDK_INT,
            brewTimestamp = brewTimestamp,
            method = method,
            filterType = filterType,
            doseG = doseG,
            waterG = waterG,
            ratio = ratio,
            grinderId = grinderId,
            grinderSetting = grinderSetting,
            placement = placement,
            environment = environment,
            ambientRmsDb = ambientRmsDb,
            sampleRate = sampleRate,
            notes = notes,
        )
    }
}

// Manual JSON helpers to avoid kotlinx.serialization dependency
private fun StringBuilder.appendJsonField(key: String, value: String?) {
    append("  \"$key\": ")
    if (value == null) append("null") else append("\"${escapeJson(value)}\"")
    appendLine(",")
}

private fun StringBuilder.appendJsonField(key: String, value: Int) {
    appendLine("  \"$key\": $value,")
}

private fun StringBuilder.appendJsonField(key: String, value: Long) {
    appendLine("  \"$key\": $value,")
}

private fun StringBuilder.appendJsonField(key: String, value: Float) {
    appendLine("  \"$key\": ${"%.2f".format(value)},")
}

private fun StringBuilder.appendJsonFieldLast(key: String, value: String) {
    append("  \"$key\": \"${escapeJson(value)}\"")
    appendLine()
}

private fun escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
