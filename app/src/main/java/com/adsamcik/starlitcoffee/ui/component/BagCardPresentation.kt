package com.adsamcik.starlitcoffee.ui.component

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.util.FreshnessInsight
import com.adsamcik.starlitcoffee.util.FreshnessPhase
import java.util.Locale
import kotlin.math.roundToInt

private const val LOW_COFFEE_THRESHOLD_G = 30f

data class BagCardSummary(
    val statusLabel: String,
    val statusEmphasis: ChipEmphasis,
    val freshnessLabel: String,
    val freshnessEmphasis: ChipEmphasis,
    val freshnessSupportingText: String,
    val warningText: String?,
    val stockLabel: String,
    val stockSupportingText: String,
    val stockProgress: Float?,
    val stockEmphasis: ChipEmphasis,
    val primaryActionLabel: String,
)

@Suppress(
    // Builds the summary tuple from ~10 independent freshness / stock /
    // missing-data signals. Branches mirror signal count, not poor
    // decomposition; splitting per-signal would scatter the chip-priority
    // ordering rules.
    "CyclomaticComplexMethod",
)
fun buildBagCardSummary(
    bag: CoffeeBagEntity,
    freshness: FreshnessInsight,
): BagCardSummary {
    val currentWeight = bag.weightG
    val initialWeight = bag.initialWeightG ?: currentWeight
    val isMissingRoastDate = bag.roastDate == null
    val isMissingWeight = currentWeight == null
    val stockProgress = if (
        currentWeight != null &&
        initialWeight != null &&
        initialWeight > 0f
    ) {
        (currentWeight / initialWeight).coerceIn(0f, 1f)
    } else {
        null
    }
    val isLowStock = currentWeight != null && currentWeight in 0.01f..LOW_COFFEE_THRESHOLD_G

    val stockLabel = currentWeight?.let { "${formatWeight(it)}g left" } ?: "Weight unknown"
    val stockSupportingText = when {
        isMissingWeight -> "Add bag weight to track coffee."
        initialWeight == null || initialWeight <= 0f -> "Update the bag weight as you brew through it."
        currentWeight >= initialWeight - 0.5f -> "Full bag"
        else -> "${(stockProgress ?: 0f).times(100).roundToInt()}% of ${formatWeight(initialWeight)}g original"
    }

    val freshnessLabel = freshness.daysSinceRoast?.let { day ->
        "${freshness.phase.displayName} - day $day"
    } ?: "Needs roast date"

    val warningText = when {
        isMissingRoastDate && isMissingWeight ->
            "Add roast date and weight to unlock freshness and stock tracking."
        isMissingRoastDate -> "Roast date missing. Add it for freshness coaching."
        isMissingWeight -> "Add bag weight to track coffee."
        isLowStock -> "Low coffee. Plan one last brew or mark the bag finished."
        else -> null
    }

    val primaryActionLabel = when {
        isMissingRoastDate && isMissingWeight -> "Complete details"
        isMissingRoastDate -> "Add roast date"
        isMissingWeight -> "Add bag weight"
        isLowStock -> "Review bag"
        else -> "View details"
    }

    return BagCardSummary(
        statusLabel = bag.status.lowercase(Locale.getDefault()).replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        },
        statusEmphasis = statusEmphasis(bag.status),
        freshnessLabel = freshnessLabel,
        freshnessEmphasis = freshnessEmphasis(freshness.phase),
        freshnessSupportingText = freshness.headline,
        warningText = warningText,
        stockLabel = stockLabel,
        stockSupportingText = stockSupportingText,
        stockProgress = stockProgress,
        stockEmphasis = when {
            currentWeight == null -> ChipEmphasis.WARNING
            isLowStock -> ChipEmphasis.CRITICAL
            else -> ChipEmphasis.POSITIVE
        },
        primaryActionLabel = primaryActionLabel,
    )
}

private fun formatWeight(value: Float): String = String.format(Locale.US, "%.0f", value)

private fun freshnessEmphasis(phase: FreshnessPhase): ChipEmphasis = when (phase) {
    FreshnessPhase.PEAK -> ChipEmphasis.POSITIVE
    FreshnessPhase.DEGASSING,
    FreshnessPhase.UNKNOWN,
    -> ChipEmphasis.WARNING
    FreshnessPhase.MELLOWING -> ChipEmphasis.NEUTRAL
    FreshnessPhase.VINTAGE -> ChipEmphasis.CRITICAL
}

private fun statusEmphasis(status: String): ChipEmphasis = when (status) {
    "OPEN" -> ChipEmphasis.POSITIVE
    "FROZEN" -> ChipEmphasis.WARNING
    "FINISHED" -> ChipEmphasis.NEUTRAL
    else -> ChipEmphasis.NEUTRAL
}
