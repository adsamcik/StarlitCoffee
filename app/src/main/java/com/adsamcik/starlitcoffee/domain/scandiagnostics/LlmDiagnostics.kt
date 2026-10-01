package com.adsamcik.starlitcoffee.domain.scandiagnostics

import kotlinx.serialization.Serializable

/**
 * One LLM extraction-pass attempt, captured for observability.
 *
 * The bag-scan pipeline runs several LLM passes (translate → text → vision →
 * combine), each retried a few times. Previously a failure surfaced only as a
 * generic "AI couldn't finish reading this label" banner, and the real reason
 * (e.g. `input_exceeds_context (...)`, a timeout, a parse error) lived only in a
 * transient logcat line that had usually rotated away by the time anyone looked.
 * This record contains only typed outcomes, a numeric SDK failure code, and
 * timings/counts. Prompts, model output, and exception messages are never
 * diagnostic fields. Tracebox owns capture, retention, deletion, and export.
 *
 * Lives in a neutral, dependency-free `domain.*` package so both the scan
 * pipeline (`scan.*`) and the on-device LLM layer (`data.network.llm`) can
 * reference it without recreating a `scan <-> data.network.llm` package cycle.
 */
@Serializable
data class LlmPassDiagnostic(
    val timestampMs: Long,
    /** TRANSLATE, TEXT, VISION, COMBINE, or REFINE. */
    val pass: Pass,
    /** SUCCESS, TIMEOUT, ERROR, or UNAVAILABLE. */
    val status: Status,
    val elapsedMs: Long,
    /** Total KV-cache budget requested for the session (input + output). */
    val maxTokens: Int,
    /** Characters of prompt sent (rough input-size proxy). */
    val promptCharLen: Int,
    /** Characters the model emitted (0 when it failed before generating). */
    val outputCharLen: Int,
    /** Known Mindlayer wire error code; no exception text or arbitrary code name. */
    val errorCode: Int? = null,
) {
    enum class Status { SUCCESS, TIMEOUT, ERROR, UNAVAILABLE }
    enum class Pass { TRANSLATE, TEXT, VISION, COMBINE, REFINE }
}

/**
 * Sink the LLM provider records each pass into. Kept Context-free so the
 * provider stays unit-testable; the app wires a persistent implementation.
 */
fun interface LlmDiagnosticsRecorder {
    fun record(diagnostic: LlmPassDiagnostic)
}
