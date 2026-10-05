package com.adsamcik.starlitcoffee.data.brewing.session

/** Validation boundary for the independently stored V1 execution context. */
internal object SessionExecutionContextSnapshotValidatorV1 {

    fun validate(value: SessionExecutionContextSnapshotV1): SessionExecutionContextSnapshotV1 {
        require(value.schemaVersion == SessionExecutionContextSnapshotV1.SCHEMA_VERSION) {
            "Unsupported execution-context snapshot schema: ${value.schemaVersion}"
        }
        require(value.logPresentation.methodLabel.isNotBlank()) {
            "Log method label cannot be blank"
        }
        SessionSnapshotValueDecoder.requireFiniteNonNegative(
            value.logPresentation.doseG,
            "Log dose",
        )
        SessionSnapshotValueDecoder.requireFiniteNonNegative(
            value.logPresentation.waterG,
            "Log water",
        )
        val presentation = value.logPresentation
        require(presentation.ratio.isFinite() && if (presentation.hasMassRatio) {
            presentation.ratio > 0.0
        } else presentation.ratio == 0.0) {
            "Log ratio must be positive when known, or explicitly absent"
        }
        return value
    }
}
