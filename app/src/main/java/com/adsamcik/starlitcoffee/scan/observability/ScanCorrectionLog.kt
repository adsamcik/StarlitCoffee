package com.adsamcik.starlitcoffee.scan.observability

import android.content.Context
import androidx.core.content.edit
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.util.BagFieldConfidence
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * On-device, opt-in log of bag-scan corrections (what the model extracted vs.
 * what the user saved). Mirrors [ScanSessionRingBuffer]: a bounded ring buffer
 * in private [android.content.SharedPreferences]. Nothing leaves the device.
 *
 * The pure [buildCorrections] diff is the testable core; [record] adds the
 * opt-in gate and persistence. Keeping the opt-in check INSIDE [record] makes it
 * the single privacy choke point — a caller cannot accidentally persist when the
 * user has not opted in.
 */
object ScanCorrectionLog {

    private const val PREFS_NAME = "scan_field_corrections"
    private const val KEY_CORRECTIONS = "corrections"
    private const val MAX_RECORDS = 200

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val prettyJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Pure diff between the model's proposed values and the user's final saved
     * values. Only fields the model actually proposed (non-blank [modelValues])
     * are emitted. A field counts as edited when the normalized model value
     * differs from the normalized final value (case/whitespace-insensitive).
     */
    fun buildCorrections(
        modelValues: Map<String, String?>,
        finalValues: Map<String, String?>,
        confidence: Map<String, BagFieldConfidence> = emptyMap(),
        now: Long = System.currentTimeMillis(),
    ): List<ScanFieldCorrection> =
        modelValues.mapNotNull { (field, rawModel) ->
            val model = rawModel?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val finalValue = finalValues[field]?.trim()?.takeIf { it.isNotBlank() }
            ScanFieldCorrection(
                fieldName = field,
                modelValue = model,
                modelConfidence = confidence[field]?.name,
                finalValue = finalValue,
                wasEdited = !valuesEquivalent(model, finalValue),
                recordedAt = now,
            )
        }

    private fun valuesEquivalent(a: String?, b: String?): Boolean =
        a?.trim()?.lowercase() == b?.trim()?.lowercase()

    /**
     * Records [corrections] IFF the user has opted in
     * ([UserPreferencesRepository.userPreferences] -> scanCorrectionLoggingEnabled).
     * A no-op otherwise. Also surfaces each field to [ScanAnalyticsTracker].
     */
    suspend fun record(context: Context, corrections: List<ScanFieldCorrection>) {
        if (corrections.isEmpty()) return
        val enabled = UserPreferencesRepository(context.applicationContext)
            .userPreferences.first().scanCorrectionLoggingEnabled
        if (!enabled) return

        corrections.forEach {
            ScanAnalyticsTracker.trackFieldReview(it.fieldName, it.wasEdited, it.modelConfidence)
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getAll(appContext).toMutableList()
        existing.addAll(0, corrections)
        val trimmed = existing.take(MAX_RECORDS)
        prefs.edit { putString(KEY_CORRECTIONS, json.encodeToString(trimmed)) }
    }

    fun getAll(context: Context): List<ScanFieldCorrection> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CORRECTIONS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ScanFieldCorrection>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_CORRECTIONS) }
    }

    /** Human-readable dump for the diagnostics / bug-report surface. */
    fun getForReport(context: Context, count: Int = 50): String {
        val records = getAll(context).take(count)
        return if (records.isEmpty()) "No scan corrections recorded." else prettyJson.encodeToString(records)
    }
}
