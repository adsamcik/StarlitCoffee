package com.adsamcik.starlitcoffee.scan.observability

import kotlinx.serialization.Serializable

/**
 * One field's model-vs-user outcome, captured when the user saves a scanned bag
 * from the review screen. This is a real-world (non-synthetic) quality signal:
 * [wasEdited] == true is a strong "the model got this wrong / not good enough"
 * signal; [wasEdited] == false is only a WEAK positive (the user may not have
 * reviewed a low-stakes free-text field at all — see the human-in-the-loop
 * literature on implicit-accept selection bias). Only fields the model actually
 * proposed are recorded, so this measures model quality rather than manual
 * additions of fields the model never saw.
 *
 * Captured on-device only, strictly behind the opt-in
 * `scanCorrectionLoggingEnabled` preference — nothing is uploaded.
 */
@Serializable
data class ScanFieldCorrection(
    val fieldName: String,
    val modelValue: String?,
    /** [com.adsamcik.starlitcoffee.util.BagFieldConfidence] name, or null if unknown. */
    val modelConfidence: String?,
    val finalValue: String?,
    val wasEdited: Boolean,
    val recordedAt: Long,
)
