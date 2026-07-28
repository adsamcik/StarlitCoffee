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
        require(value.logPresentation.ratio.isFinite() && value.logPresentation.ratio > 0.0) {
            "Log ratio must be finite and positive"
        }
        return value
    }
}
