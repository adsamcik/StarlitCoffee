package com.adsamcik.starlitcoffee.scan.observability

import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate
import dev.tracebox.api.argument
import com.adsamcik.starlitcoffee.domain.scandiagnostics.ScanLifecycleDiagnostic

private object ScanAnalyticsTrackerTraceboxTemplates {
    val EVENT_SCAN_STARTED = LogTemplate.of("event=scan_started")
    val EVENT_LLM_FIRED_CALL_NUMBER_FIELDS_NEEDED = LogTemplate.of("event=llm_fired call_number={} fields_needed={}")
    val EVENT_DRAFT_SHOWN_LATENCY_MS_FIELDS_RESOLVED = LogTemplate.of("event=draft_shown latency_ms={} fields_resolved={}")
    val EVENT_SCAN_COMPLETED_OUTCOME_DURATION_MS_FIELDS = LogTemplate.of("event=scan_completed outcome={} duration_ms={} fields_resolved={} fields_total={}")
    val EVENT_USER_EDITED_FIELD_NAME = LogTemplate.of("event=user_edited field_name={}")
    val EVENT_FIELD_REVIEW_FIELD_NAME_WAS_EDITED = LogTemplate.of("event=field_review field_name={} was_edited={} model_confidence={}")
    val EVENT_SCAN_ABANDONED_DURATION_MS_FIELDS_RESOLVED = LogTemplate.of("event=scan_abandoned duration_ms={} fields_resolved={}")
    val EVENT_SCAN_ERROR_ERROR_MESSAGE = LogTemplate.of("event=scan_error error_message={}")
}

/**
 * Lightweight, privacy-classified Tracebox events for useful scan boundaries.
 * Values stay behind Tracebox's runtime level gate and strings are redacted by
 * default before they can reach durable storage or optional Logcat mirroring.
 */
object ScanAnalyticsTracker {

    fun trackLifecycle(diagnostic: ScanLifecycleDiagnostic) {
        TraceboxScanDiagnosticsRecorder().record(diagnostic)
    }

    fun trackScanStarted() {
        Tracebox.log.debug(ScanAnalyticsTrackerTraceboxTemplates.EVENT_SCAN_STARTED)
    }

    fun trackLlmFired(callNumber: Int, fieldsNeeded: Int) {
        Tracebox.log.debug(
            ScanAnalyticsTrackerTraceboxTemplates.EVENT_LLM_FIRED_CALL_NUMBER_FIELDS_NEEDED,
            argument(callNumber),
            argument(fieldsNeeded),
        )
    }

    fun trackDraftShown(latencyMs: Long, fieldsResolved: Int) {
        Tracebox.log.debug(
            ScanAnalyticsTrackerTraceboxTemplates.EVENT_DRAFT_SHOWN_LATENCY_MS_FIELDS_RESOLVED,
            argument(latencyMs),
            argument(fieldsResolved),
        )
    }

    fun trackScanCompleted(
        outcome: String,
        durationMs: Long,
        fieldsResolved: Int,
        fieldsTotal: Int,
    ) {
        Tracebox.log.debug(
            ScanAnalyticsTrackerTraceboxTemplates.EVENT_SCAN_COMPLETED_OUTCOME_DURATION_MS_FIELDS,
            argument(outcome),
            argument(durationMs),
            argument(fieldsResolved),
            argument(fieldsTotal),
        )
    }

    fun trackUserEdited(fieldName: String) {
        Tracebox.log.debug(ScanAnalyticsTrackerTraceboxTemplates.EVENT_USER_EDITED_FIELD_NAME, argument(fieldName))
    }

    /**
     * Richer review signal than [trackUserEdited]: whether the user kept or
     * changed the model's proposed value for a field, and the model's stated
     * confidence. Feeds the on-device [ScanCorrectionLog] quality signal.
     */
    fun trackFieldReview(fieldName: String, wasEdited: Boolean, modelConfidence: String?) {
        Tracebox.log.debug(
            ScanAnalyticsTrackerTraceboxTemplates.EVENT_FIELD_REVIEW_FIELD_NAME_WAS_EDITED,
            argument(fieldName),
            argument(wasEdited),
            argument(modelConfidence ?: "unknown"),
        )
    }

    fun trackScanAbandoned(durationMs: Long, fieldsResolved: Int) {
        Tracebox.log.debug(
            ScanAnalyticsTrackerTraceboxTemplates.EVENT_SCAN_ABANDONED_DURATION_MS_FIELDS_RESOLVED,
            argument(durationMs),
            argument(fieldsResolved),
        )
    }

    fun trackScanError(error: String) {
        Tracebox.log.error(ScanAnalyticsTrackerTraceboxTemplates.EVENT_SCAN_ERROR_ERROR_MESSAGE, argument(error))
    }

}
