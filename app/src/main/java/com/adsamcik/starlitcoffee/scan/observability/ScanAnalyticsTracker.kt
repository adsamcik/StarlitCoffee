package com.adsamcik.starlitcoffee.scan.observability

import dev.tracebox.Tracebox


/**
 * Lightweight, privacy-classified Tracebox events for useful scan boundaries.
 * Values stay behind Tracebox's runtime level gate and strings are redacted by
 * default before they can reach durable storage or optional Logcat mirroring.
 */
object ScanAnalyticsTracker {

    fun trackScanStarted() {
        Tracebox.log.debug("event=scan_started")
    }

    fun trackLlmFired(callNumber: Int, fieldsNeeded: Int) {
        Tracebox.log.debug("event=llm_fired call_number={} fields_needed={}", callNumber, fieldsNeeded)
    }

    fun trackDraftShown(latencyMs: Long, fieldsResolved: Int) {
        Tracebox.log.debug("event=draft_shown latency_ms={} fields_resolved={}", latencyMs, fieldsResolved)
    }

    fun trackScanCompleted(
        outcome: String,
        durationMs: Long,
        fieldsResolved: Int,
        fieldsTotal: Int,
    ) {
        Tracebox.log.debug(
            "event=scan_completed outcome={} duration_ms={} fields_resolved={} fields_total={}",
            outcome,
            durationMs,
            fieldsResolved,
            fieldsTotal,
        )
    }

    fun trackUserEdited(fieldName: String) {
        Tracebox.log.debug("event=user_edited field_name={}", fieldName)
    }

    /**
     * Richer review signal than [trackUserEdited]: whether the user kept or
     * changed the model's proposed value for a field, and the model's stated
     * confidence. Feeds the on-device [ScanCorrectionLog] quality signal.
     */
    fun trackFieldReview(fieldName: String, wasEdited: Boolean, modelConfidence: String?) {
        Tracebox.log.debug(
            "event=field_review field_name={} was_edited={} model_confidence={}",
            fieldName,
            wasEdited,
            modelConfidence ?: "unknown",
        )
    }

    fun trackScanAbandoned(durationMs: Long, fieldsResolved: Int) {
        Tracebox.log.debug("event=scan_abandoned duration_ms={} fields_resolved={}", durationMs, fieldsResolved)
    }

    fun trackScanError(error: String) {
        Tracebox.log.error("event=scan_error error_message={}", error)
    }

}
