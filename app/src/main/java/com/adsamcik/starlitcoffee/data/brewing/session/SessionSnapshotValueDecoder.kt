package com.adsamcik.starlitcoffee.data.brewing.session

/** Shared fail-closed scalar decoding for the versioned session documents. */
internal object SessionSnapshotValueDecoder {

    inline fun <reified T : Enum<T>> enumValue(raw: String, label: String): T = try {
        enumValueOf(raw)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Unknown $label: $raw", error)
    }

    fun requiredPositive(value: Long?, label: String): Long {
        val resolved = requireNotNull(value) { "$label is required" }
        require(resolved > 0L) { "$label must be positive" }
        return resolved
    }

    fun requiredNonNegative(value: Long?, label: String): Long {
        val resolved = requireNotNull(value) { "$label is required" }
        require(resolved >= 0L) { "$label cannot be negative" }
        return resolved
    }

    fun requiredPositive(value: Double?, label: String): Double {
        val resolved = requireNotNull(value) { "$label is required" }
        require(resolved.isFinite() && resolved > 0.0) { "$label must be finite and positive" }
        return resolved
    }

    fun requireFiniteNonNegative(value: Double?, label: String) {
        if (value != null) {
            require(value.isFinite() && value >= 0.0) { "$label must be finite and non-negative" }
        }
    }

    fun requireNotBlank(value: String?, label: String): String =
        requireNotNull(value) { "$label is required" }.also { resolved ->
            require(resolved.isNotBlank()) { "$label cannot be blank" }
        }
}
