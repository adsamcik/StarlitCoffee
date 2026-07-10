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
 * This record persists the per-pass outcome — including the real error message
 * and a sample of what the model actually emitted — so a failure is attributable
 * after the fact, on any device, without a USB cable.
 *
 * Lives in a neutral, dependency-free `domain.*` package so both the scan
 * pipeline (`scan.*`) and the on-device LLM layer (`data.network.llm`) can
 * reference it without recreating a `scan <-> data.network.llm` package cycle.
 */
@Serializable
data class LlmPassDiagnostic(
    val timestampMs: Long,
    /** TRANSLATE, TEXT, VISION, COMBINE, or REFINE. */
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
    enum class Pass { TRANSLATE, TEXT, VISION, COMBINE, REFINE }

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
