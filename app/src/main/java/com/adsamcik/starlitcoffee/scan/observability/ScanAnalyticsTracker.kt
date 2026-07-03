package com.adsamcik.starlitcoffee.scan.observability

import android.util.Log

/**
 * Lightweight analytics wrapper for scan pipeline events.
 *
 * Firebase Analytics is not currently a dependency, so all events are logged
 * to Logcat. When Firebase is added, replace the log calls with
 * FirebaseAnalytics.logEvent() and FirebaseCrashlytics custom keys.
 */
object ScanAnalyticsTracker {

    private const val TAG = "ScanAnalytics"

    fun trackScanStarted() {
        Log.d(TAG, "event=scan_started")
    }

    fun trackLlmFired(callNumber: Int, fieldsNeeded: Int) {
        Log.d(TAG, "event=llm_fired call_number=$callNumber fields_needed=$fieldsNeeded")
    }

    fun trackDraftShown(latencyMs: Long, fieldsResolved: Int) {
        Log.d(TAG, "event=draft_shown latency_ms=$latencyMs fields_resolved=$fieldsResolved")
    }

    fun trackScanCompleted(
        outcome: String,
        durationMs: Long,
        fieldsResolved: Int,
        fieldsTotal: Int,
    ) {
        Log.d(
            TAG,
            "event=scan_completed outcome=$outcome duration_ms=$durationMs " +
                "fields_resolved=$fieldsResolved fields_total=$fieldsTotal",
        )
    }

    fun trackUserEdited(fieldName: String) {
        Log.d(TAG, "event=user_edited field_name=$fieldName")
    }

    /**
     * Richer review signal than [trackUserEdited]: whether the user kept or
     * changed the model's proposed value for a field, and the model's stated
     * confidence. Feeds the on-device [ScanCorrectionLog] quality signal.
     */
    fun trackFieldReview(fieldName: String, wasEdited: Boolean, modelConfidence: String?) {
        Log.d(
            TAG,
            "event=field_review field_name=$fieldName was_edited=$wasEdited " +
                "model_confidence=${modelConfidence ?: "unknown"}",
        )
    }

    fun trackScanAbandoned(durationMs: Long, fieldsResolved: Int) {
        Log.d(TAG, "event=scan_abandoned duration_ms=$durationMs fields_resolved=$fieldsResolved")
    }

    fun trackScanError(error: String) {
        Log.e(TAG, "event=scan_error error_message=$error")
    }

    /**
     * Set Crashlytics custom keys for scan context.
     * Currently a no-op — activate when Firebase Crashlytics is added.
     */
    fun setCrashlyticsContext(
        deviceModel: String,
        appVersion: String,
        modelVersion: String,
    ) {
        Log.d(
            TAG,
            "crashlytics_context device=$deviceModel app=$appVersion model=$modelVersion",
        )
    }
}
