package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.WeightParser

internal data class BagFormSnapshot(
    val name: String = "",
    val roaster: String = "",
    val originCountry: String = "",
    val originRegion: String = "",
    val farm: String = "",
    val altitude: String = "",
    val roastLevel: String = "",
    val variety: String = "",
    val processType: String = "",
    val tastingNotes: String = "",
    val barcode: String = "",
    val weight: String = "",
    val notes: String = "",
    val isDecaf: Boolean = false,
    val decafProcess: String? = null,
    val roastDateMillis: Long? = null,
    val expiryDateMillis: Long? = null,
)

internal fun shouldConfirmBagDismiss(
    isEditMode: Boolean,
    initial: BagFormSnapshot,
    current: BagFormSnapshot,
    hasCapturedPhotos: Boolean,
    hasTraceabilityData: Boolean,
): Boolean = if (isEditMode) {
    current != initial
} else {
    current != BagFormSnapshot() || hasCapturedPhotos || hasTraceabilityData
}

internal fun mergeBagFormEnrichment(
    current: BagFormSnapshot,
    previousEnrichment: BagFormSnapshot,
    incomingEnrichment: BagFormSnapshot,
): BagFormSnapshot = current.copy(
    name = mergeIfUnchanged(current.name, previousEnrichment.name, incomingEnrichment.name),
    roaster = mergeIfUnchanged(current.roaster, previousEnrichment.roaster, incomingEnrichment.roaster),
    originCountry = mergeIfUnchanged(
        current.originCountry,
        previousEnrichment.originCountry,
        incomingEnrichment.originCountry,
    ),
    originRegion = mergeIfUnchanged(
        current.originRegion,
        previousEnrichment.originRegion,
        incomingEnrichment.originRegion,
    ),
    farm = mergeIfUnchanged(current.farm, previousEnrichment.farm, incomingEnrichment.farm),
    altitude = mergeIfUnchanged(
        current.altitude,
        previousEnrichment.altitude,
        incomingEnrichment.altitude,
    ),
    roastLevel = mergeIfUnchanged(
        current.roastLevel,
        previousEnrichment.roastLevel,
        incomingEnrichment.roastLevel,
    ),
    variety = mergeIfUnchanged(current.variety, previousEnrichment.variety, incomingEnrichment.variety),
    processType = mergeIfUnchanged(
        current.processType,
        previousEnrichment.processType,
        incomingEnrichment.processType,
    ),
    tastingNotes = mergeIfUnchanged(
        current.tastingNotes,
        previousEnrichment.tastingNotes,
        incomingEnrichment.tastingNotes,
    ),
    barcode = mergeIfUnchanged(
        current.barcode,
        previousEnrichment.barcode,
        incomingEnrichment.barcode,
    ),
    weight = mergeIfUnchanged(current.weight, previousEnrichment.weight, incomingEnrichment.weight),
    isDecaf = mergeIfUnchanged(current.isDecaf, previousEnrichment.isDecaf, incomingEnrichment.isDecaf),
    roastDateMillis = mergeIfUnchanged(
        current.roastDateMillis,
        previousEnrichment.roastDateMillis,
        incomingEnrichment.roastDateMillis,
    ),
    expiryDateMillis = mergeIfUnchanged(
        current.expiryDateMillis,
        previousEnrichment.expiryDateMillis,
        incomingEnrichment.expiryDateMillis,
    ),
)

private fun <T> mergeIfUnchanged(current: T, previous: T, incoming: T): T =
    if (current == previous) incoming else current

internal fun shouldApplyBagResultToDraft(
    isDraftOpen: Boolean,
    currentSessionId: String,
    incomingSessionId: String,
): Boolean = !isDraftOpen || currentSessionId == incomingSessionId

internal data class BagWeightValidation(
    val valueGrams: Float?,
    val isValid: Boolean,
)

internal fun validateBagWeightInput(input: String): BagWeightValidation {
    if (input.isBlank()) return BagWeightValidation(valueGrams = null, isValid = true)
    val parsed = WeightParser.parseToGrams(input)?.takeIf { it > 0f }
    return BagWeightValidation(valueGrams = parsed, isValid = parsed != null)
}

internal fun applyValidatedBagWeight(
    bag: CoffeeBagEntity,
    validation: BagWeightValidation,
): CoffeeBagEntity? {
    if (!validation.isValid) return null
    return bag.copy(
        weightG = validation.valueGrams,
        initialWeightG = when {
            validation.valueGrams == null -> null
            bag.initialWeightG != null -> bag.initialWeightG
            else -> validation.valueGrams
        },
    )
}
