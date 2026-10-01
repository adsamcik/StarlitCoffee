package com.adsamcik.starlitcoffee.scan.observability

import android.content.Context
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmDiagnosticsRecorder
import com.adsamcik.starlitcoffee.domain.scandiagnostics.LlmPassDiagnostic
import dev.tracebox.Tracebox
import dev.tracebox.api.LogLevel
import dev.tracebox.api.LogTemplate
import dev.tracebox.api.TraceboxLogger
import dev.tracebox.api.argument

/** Retired raw-sample storage. Never read or write it; retry deletion on each startup. */
object LegacyLlmDiagnosticsStore {
    internal const val PREFS_NAME = "scan_llm_diagnostics"

    fun clear(context: Context): Boolean = try {
        // Deletes the whole preferences file and its backup, including unknown legacy keys.
        context.deleteSharedPreferences(PREFS_NAME)
    } catch (_: Exception) {
        false
    }
}

/** One policy-controlled sink for AI diagnostics, containing no raw text or throwable. */
class TraceboxLlmDiagnosticsRecorder(
    private val logger: TraceboxLogger = Tracebox.log,
) : LlmDiagnosticsRecorder {
    override fun record(diagnostic: LlmPassDiagnostic) {
        val level = if (diagnostic.status == LlmPassDiagnostic.Status.SUCCESS) LogLevel.INFO else LogLevel.WARN
        logger.log(
            level,
            LogTemplate.of("AI pass={} status={} elapsed_ms={} max_tokens={} prompt_chars={} output_chars={} error_code={}"),
            argument(diagnostic.pass),
            argument(diagnostic.status),
            argument(diagnostic.elapsedMs),
            argument(diagnostic.maxTokens),
            argument(diagnostic.promptCharLen),
            argument(diagnostic.outputCharLen),
            argument(diagnostic.errorCode),
        )
    }
}
