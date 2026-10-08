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
    /** Final validated outcome, including a valid response with no usable fields. */
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
    /** Closed app diagnosis for failures that have no Mindlayer wire code. */
    val failureReason: FailureReason? = null,
    /** Scan correlation contains validated numeric keys, never a label or image path. */
    val sessionKey: Long? = null,
    val generationKey: Long? = null,
    val workKey: Long? = null,
    val photoCount: Int? = null,
    val mode: ScanDiagnosticMode? = null,
    /** Known coarse SDK readiness cause; arbitrary service reason strings are excluded. */
    val readinessCode: ReadinessCode? = null,
) {
    enum class Status { SUCCESS, NO_RESULT, TIMEOUT, ERROR, UNAVAILABLE }
    enum class Pass { TRANSLATE, TEXT, VISION, COMBINE, REFINE }
    enum class FailureReason {
        INVALID_RESPONSE,
        CONNECTION_UNAVAILABLE,
        AUTHORIZATION_REQUIRED,
        MODEL_SETUP_REQUIRED,
        MODEL_IN_PROGRESS,
        MODEL_FAILED,
        TIMEOUT,
        INFERENCE_FAILED,
    }
    enum class ReadinessCode {
        MODEL_MISSING,
        LOW_MEMORY,
        INTEGRITY_MISMATCH,
        BACKEND_UNAVAILABLE,
        NATIVE_ERROR,
        OLD_SERVICE,
    }
}

/**
 * Sink the LLM provider records each pass into. Kept Context-free so the
 * provider stays unit-testable; the app wires the policy-controlled Tracebox sink.
 */
fun interface LlmDiagnosticsRecorder {
    fun record(diagnostic: LlmPassDiagnostic)
}
