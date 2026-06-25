package com.adsamcik.starlitcoffee.scan.observability

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One LLM extraction-pass attempt, captured for observability.
 *
 * The bag-scan pipeline runs three LLM passes (text → vision → combine), each
 * retried a few times. Previously a failure surfaced only as a generic "AI
 * couldn't finish reading this label" banner, and the real reason (e.g.
 * `input_exceeds_context (...)`, a timeout, a parse error) lived only in a
 * transient logcat line that had usually rotated away by the time anyone
 * looked. This record persists the per-pass outcome — including the real error
 * message and a sample of what the model actually emitted — so a failure is
 * attributable after the fact, on any device, without a USB cable.
 */
@Serializable
data class LlmPassDiagnostic(
    val timestampMs: Long,
    /** TEXT, VISION, or COMBINE. */
    val pass: String,
    /** SUCCESS, TIMEOUT, ERROR, or UNAVAILABLE. */
    val status: String,
    val elapsedMs: Long,
    /** Total KV-cache budget requested for the session (input + output). */
    val maxTokens: Int,
    /** Characters of prompt sent (rough input-size proxy). */
    val promptCharLen: Int,
    /** Characters the model emitted (0 when it failed before generating). */
    val outputCharLen: Int,
    /** A leading slice of the model output — "what the LLM said" — or null. */
    val outputSample: String?,
    /** The real failure reason for non-SUCCESS passes (e.g. the wire message). */
    val errorMessage: String?,
) {
    enum class Status { SUCCESS, TIMEOUT, ERROR, UNAVAILABLE }
    enum class Pass { TEXT, VISION, COMBINE }

    companion object {
        /** Max characters of model output retained in [outputSample]. */
        const val OUTPUT_SAMPLE_LIMIT = 600
    }
}

/**
 * Sink the LLM provider records each pass into. Kept Context-free so the
 * provider stays unit-testable; the app wires a persistent implementation.
 */
fun interface LlmDiagnosticsRecorder {
    fun record(diagnostic: LlmPassDiagnostic)
}

/**
 * Persistent ring buffer of recent [LlmPassDiagnostic] records, backed by
 * SharedPreferences. Mirrors [ScanSessionRingBuffer] so the Scan Debug card and
 * [ScanBugReporter] can read/share/clear it with the same lifecycle.
 */
object ScanLlmDiagnosticsStore {

    private const val PREFS_NAME = "scan_llm_diagnostics"
    private const val KEY_PASSES = "passes"
    private const val MAX_RECORDS = 60

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun record(context: Context, diagnostic: LlmPassDiagnostic) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getAll(context).toMutableList()
        existing.add(0, diagnostic)
        val trimmed = existing.take(MAX_RECORDS)
        prefs.edit {
            putString(KEY_PASSES, json.encodeToString(trimmed))
        }
    }

    fun getAll(context: Context): List<LlmPassDiagnostic> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PASSES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LlmPassDiagnostic>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_PASSES) }
    }

    fun getForReport(context: Context, count: Int = 15): String {
        val passes = getAll(context).take(count)
        return if (passes.isEmpty()) {
            "No recent LLM extraction passes."
        } else {
            prettyJson.encodeToString(passes)
        }
    }
}

/**
 * [LlmDiagnosticsRecorder] that persists into [ScanLlmDiagnosticsStore].
 * Holds the application context only — safe to retain for the process lifetime.
 */
class PersistentLlmDiagnosticsRecorder(
    private val appContext: Context,
) : LlmDiagnosticsRecorder {
    override fun record(diagnostic: LlmPassDiagnostic) {
        ScanLlmDiagnosticsStore.record(appContext, diagnostic)
    }
}
