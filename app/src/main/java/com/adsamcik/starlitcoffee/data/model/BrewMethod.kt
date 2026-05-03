package com.adsamcik.starlitcoffee.data.model

import com.adsamcik.starlitcoffee.R

/**
 * Canonical brew method registry. Each enum constant captures the full
 * method profile (ratio, temp/time/bloom). The constructor parameter list
 * is naturally large because it IS the schema; bundling into a data class
 * would scatter related values across two declarations per constant.
 */
@Suppress("LongParameterList")
enum class BrewMethod(
    val displayName: String,
    val iconName: String,
    val defaultRatio: Float,
    val bloomMultiplier: Float,
    val defaultPulses: Int,
    val tempRangeLow: Int,
    val tempRangeHigh: Int,
    val timeTargetLow: Int,
    val timeTargetHigh: Int,
    val hasBloom: Boolean,
    val hasPulses: Boolean,
    val capacityMaxG: Int?,
    val defaultGrindDescriptor: GrindDescriptor,
    val bloomDurationSeconds: Int = 45,
    val absorptionRatio: Float = 2.0f,
) {
    PULSAR(
        displayName = "Pulsar",
        iconName = "coffee",
        defaultRatio = 17f,
        bloomMultiplier = 3f,
        defaultPulses = 5,
        tempRangeLow = 93,
        tempRangeHigh = 96,
        timeTargetLow = 210,
        timeTargetHigh = 270,
        hasBloom = true,
        hasPulses = true,
        capacityMaxG = 380,
        defaultGrindDescriptor = GrindDescriptor.MEDIUM_COARSE,
    ),
    V60(
        displayName = "V60",
        iconName = "filter_alt",
        defaultRatio = 16f,
        bloomMultiplier = 2.5f,
        defaultPulses = 4,
        tempRangeLow = 93,
        tempRangeHigh = 96,
        timeTargetLow = 150,
        timeTargetHigh = 210,
        hasBloom = true,
        hasPulses = true,
        capacityMaxG = null,
        defaultGrindDescriptor = GrindDescriptor.MEDIUM_FINE,
    ),
    FRENCH_PRESS(
        displayName = "French Press",
        iconName = "coffee_maker",
        defaultRatio = 15f,
        bloomMultiplier = 0f,
        defaultPulses = 0,
        tempRangeLow = 93,
        tempRangeHigh = 96,
        timeTargetLow = 240,
        timeTargetHigh = 240,
        hasBloom = false,
        hasPulses = false,
        capacityMaxG = null,
        defaultGrindDescriptor = GrindDescriptor.COARSE,
    ),
    AEROPRESS(
        displayName = "AeroPress",
        iconName = "compress",
        defaultRatio = 15f,
        bloomMultiplier = 0f,
        defaultPulses = 0,
        tempRangeLow = 80,
        tempRangeHigh = 90,
        timeTargetLow = 90,
        timeTargetHigh = 150,
        hasBloom = false,
        hasPulses = false,
        capacityMaxG = 250,
        defaultGrindDescriptor = GrindDescriptor.MEDIUM_FINE,
    ),
    ESPRESSO(
        displayName = "Espresso",
        iconName = "local_cafe",
        defaultRatio = 2f,
        bloomMultiplier = 0f,
        defaultPulses = 0,
        tempRangeLow = 90,
        tempRangeHigh = 94,
        timeTargetLow = 25,
        timeTargetHigh = 35,
        hasBloom = false,
        hasPulses = false,
        capacityMaxG = null,
        defaultGrindDescriptor = GrindDescriptor.VERY_FINE,
    ),
    MOKA_POT(
        displayName = "Moka Pot",
        iconName = "kettle",
        defaultRatio = 10f,
        bloomMultiplier = 0f,
        defaultPulses = 0,
        tempRangeLow = 0,
        tempRangeHigh = 0,
        timeTargetLow = 240,
        timeTargetHigh = 300,
        hasBloom = false,
        hasPulses = false,
        capacityMaxG = null,
        defaultGrindDescriptor = GrindDescriptor.FINE,
    ),
    COLD_BREW(
        displayName = "Cold Brew",
        iconName = "water_drop",
        defaultRatio = 8f,
        bloomMultiplier = 0f,
        defaultPulses = 0,
        tempRangeLow = 20,
        tempRangeHigh = 25,
        timeTargetLow = 43200,
        timeTargetHigh = 86400,
        hasBloom = false,
        hasPulses = false,
        capacityMaxG = null,
        defaultGrindDescriptor = GrindDescriptor.COARSE,
    ),
    ;

    val defaultRatioPresets: List<RatioPreset>
        get() {
            val base = defaultRatio.toInt()
            return listOf(
                RatioPreset(base - 1f, R.string.format_ratio_bright, base - 1),
                RatioPreset(base.toFloat(), R.string.format_ratio_balanced, base, isDefault = true),
                RatioPreset(base + 1f, R.string.format_ratio_rich, base + 1),
            )
        }
}
