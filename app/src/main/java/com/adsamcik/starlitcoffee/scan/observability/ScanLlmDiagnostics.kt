package com.adsamcik.starlitcoffee.scan.observability

import android.content.Context
import androidx.core.content.edit
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmDiagnosticsRecorder
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    fun clear(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PASSES)
            .commit()

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
