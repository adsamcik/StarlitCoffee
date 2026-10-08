package com.adsamcik.starlitcoffee.domain.scandiagnostics

enum class ScanLifecycleEvent {
    QUEUED, STARTED, STAGE_STARTED, PARTIAL_RESULTS, COMPLETED, RESULT_REPLAYED,
    RETRY_REQUESTED, REVIEW_OPENED, BACKGROUNDED, SAVED, DISCARDED, CANCELLED, ENQUEUE_FAILED,
}

enum class ScanDiagnosticOutcome { COMPLETE, PARTIAL, NO_RESULT, ERROR, CANCELLED }

enum class ScanDiagnosticFailure {
    ENQUEUE_FAILED, WORKER_FAILED, NO_PHOTOS, TIMED_OUT, AUTHORIZATION_REQUIRED,
    MODEL_SETUP_REQUIRED, RECOGNITION_UNAVAILABLE, INFERENCE_FAILED,
}

/** Bounded numeric/enum summary; Tracebox is the sole retention and export owner. */
data class ScanLifecycleDiagnostic(
    val context: ScanDiagnosticContext,
    val event: ScanLifecycleEvent,
    val elapsedMs: Long = 0L,
    val queueMs: Long = 0L,
    val fieldsResolved: Int = 0,
    val stage: ScanDiagnosticStage? = null,
    val stagesObserved: Int = 0,
    val stagesPlanned: Int = 0,
    val runAttempt: Int = 0,
    val aiRequested: Boolean? = null,
    val outcome: ScanDiagnosticOutcome? = null,
    val failure: ScanDiagnosticFailure? = null,
)

enum class ScanDiagnosticStage { OCR, BARCODE_LOOKUP, LLM_EXTRACT, LABEL_CROP, VISION, COMBINING, FINALIZING }
