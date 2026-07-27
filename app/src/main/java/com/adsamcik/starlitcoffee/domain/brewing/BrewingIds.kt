package com.adsamcik.starlitcoffee.domain.brewing

private val stableIdPattern = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")

private fun requireStableId(value: String) {
    require(value.matches(stableIdPattern)) {
        "Stable IDs must use lower_snake_case: $value"
    }
}

@JvmInline
value class MethodFamilyId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class BrewerProfileId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class FilterProfileId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class AccessoryProfileId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class BasketProfileId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class RecipeVariantId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class StagePlanId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class StageId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class StageContentId(val value: String) {
    init {
        requireStableId(value)
    }
}

@JvmInline
value class InstructionAssetId(val value: String) {
    init {
        requireStableId(value)
    }
}

/**
 * A persisted catalogue reference must retain raw values that a newer version
 * of the app introduced. The app can then show an unavailable configuration
 * instead of pretending that it knows how to brew it.
 */
sealed interface CatalogResolution<out T> {
    data class Known<T>(val value: T) : CatalogResolution<T>

    data class Unknown(val rawId: String) : CatalogResolution<Nothing>
}
