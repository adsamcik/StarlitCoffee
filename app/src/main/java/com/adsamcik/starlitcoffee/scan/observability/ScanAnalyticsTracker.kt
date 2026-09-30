package com.adsamcik.starlitcoffee.scan.observability

import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate
import dev.tracebox.api.argument


/**
 * Lightweight, privacy-classified Tracebox events for useful scan boundaries.
 * Values stay behind Tracebox's runtime level gate and strings are redacted by
 * default before they can reach durable storage or optional Logcat mirroring.
 */
object ScanAnalyticsTracker {

    fun trackScanStarted() {
        Tracebox.log.debug(LogTemplate.of("event=scan_started"))
    }

    fun trackLlmFired(callNumber: Int, fieldsNeeded: Int) {
        Tracebox.log.debug(
            LogTemplate.of("event=llm_fired call_number={} fields_needed={}"),
            argument(callNumber),
            argument(fieldsNeeded),
        )
    }

    fun trackDraftShown(latencyMs: Long, fieldsResolved: Int) {
        Tracebox.log.debug(
            LogTemplate.of("event=draft_shown latency_ms={} fields_resolved={}"),
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
            LogTemplate.of("event=scan_completed outcome={} duration_ms={} fields_resolved={} fields_total={}"),
            argument(outcome),
            argument(durationMs),
            argument(fieldsResolved),
            argument(fieldsTotal),
        )
    }

    fun trackUserEdited(fieldName: String) {
        Tracebox.log.debug(LogTemplate.of("event=user_edited field_name={}"), argument(fieldName))
    }

    /**
     * Richer review signal than [trackUserEdited]: whether the user kept or
     * changed the model's proposed value for a field, and the model's stated
     * confidence. Feeds the on-device [ScanCorrectionLog] quality signal.
     */
    fun trackFieldReview(fieldName: String, wasEdited: Boolean, modelConfidence: String?) {
        Tracebox.log.debug(
            LogTemplate.of("event=field_review field_name={} was_edited={} model_confidence={}"),
            argument(fieldName),
            argument(wasEdited),
            argument(modelConfidence ?: "unknown"),
        )
    }

    fun trackScanAbandoned(durationMs: Long, fieldsResolved: Int) {
        Tracebox.log.debug(
            LogTemplate.of("event=scan_abandoned duration_ms={} fields_resolved={}"),
            argument(durationMs),
            argument(fieldsResolved),
        )
    }

    fun trackScanError(error: String) {
        Tracebox.log.error(LogTemplate.of("event=scan_error error_message={}"), argument(error))
    }

}
