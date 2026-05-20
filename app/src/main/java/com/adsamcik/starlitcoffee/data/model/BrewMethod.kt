package com.adsamcik.starlitcoffee.data.model

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.R

data class DecafTimeAdjustmentPolicy(
    val secondsToSubtract: Int,
    val lowMinimumSeconds: Int,
    val highMinimumSeconds: Int,
) {
    fun lowTarget(baseSeconds: Int): Int =
        (baseSeconds - secondsToSubtract).coerceAtLeast(lowMinimumSeconds)

    fun highTarget(baseSeconds: Int): Int =
        (baseSeconds - secondsToSubtract).coerceAtLeast(highMinimumSeconds)

    companion object {
        val STANDARD = DecafTimeAdjustmentPolicy(
            secondsToSubtract = 30,
            lowMinimumSeconds = 120,
            highMinimumSeconds = 150,
        )
        val NONE = DecafTimeAdjustmentPolicy(
            secondsToSubtract = 0,
            lowMinimumSeconds = 0,
            highMinimumSeconds = 0,
        )
    }
}

data class RatioWarningRange(
    val strongBelow: Float,
    val weakAbove: Float,
) {
    companion object {
        val FILTER_BREW = RatioWarningRange(strongBelow = 10f, weakAbove = 20f)
    }
}

enum class BrewOutputSemantics {
    WATER_IN_MINUS_ABSORPTION,
    BEVERAGE_YIELD,
}

data class BrewStageGuidance(
    @param:StringRes val prepTipRes: Int,
    @param:StringRes val timerStartRes: Int? = null,
    @param:StringRes val timerActiveRes: Int? = null,
    @param:StringRes val timerReadyRes: Int? = null,
)

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
    val decafTimeAdjustmentPolicy: DecafTimeAdjustmentPolicy = DecafTimeAdjustmentPolicy.STANDARD,
    val ratioWarningRange: RatioWarningRange? = RatioWarningRange.FILTER_BREW,
    val outputSemantics: BrewOutputSemantics = BrewOutputSemantics.WATER_IN_MINUS_ABSORPTION,
    val stageGuidance: BrewStageGuidance,
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
        stageGuidance = BrewStageGuidance(
            prepTipRes = R.string.prep_tip_pulsar_paper,
        ),
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
        stageGuidance = BrewStageGuidance(
            prepTipRes = R.string.prep_tip_pour_over_paper,
        ),
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
        stageGuidance = BrewStageGuidance(
            prepTipRes = R.string.prep_tip_french_press,
            timerStartRes = R.string.instruction_french_press_start,
            timerActiveRes = R.string.instruction_french_press_steep,
            timerReadyRes = R.string.instruction_french_press_plunge,
        ),
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
        stageGuidance = BrewStageGuidance(
            prepTipRes = R.string.prep_tip_aeropress,
            timerStartRes = R.string.instruction_aeropress_start,
            timerActiveRes = R.string.instruction_aeropress_steep,
            timerReadyRes = R.string.instruction_aeropress_press,
        ),
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
        absorptionRatio = 0f,
        decafTimeAdjustmentPolicy = DecafTimeAdjustmentPolicy.NONE,
        ratioWarningRange = null,
        outputSemantics = BrewOutputSemantics.BEVERAGE_YIELD,
        stageGuidance = BrewStageGuidance(
            prepTipRes = R.string.prep_tip_espresso,
            timerStartRes = R.string.instruction_espresso_start,
            timerActiveRes = R.string.instruction_espresso_pull,
            timerReadyRes = R.string.instruction_espresso_stop,
        ),
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
        ratioWarningRange = RatioWarningRange(strongBelow = 8f, weakAbove = 12f),
        stageGuidance = BrewStageGuidance(
            prepTipRes = R.string.prep_tip_moka,
            timerStartRes = R.string.instruction_moka_start,
            timerActiveRes = R.string.instruction_moka_flow,
            timerReadyRes = R.string.instruction_moka_remove,
        ),
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
        ratioWarningRange = RatioWarningRange(strongBelow = 4f, weakAbove = 12f),
        stageGuidance = BrewStageGuidance(
            prepTipRes = R.string.prep_tip_cold_brew,
            timerStartRes = R.string.instruction_cold_brew_start,
            timerActiveRes = R.string.instruction_cold_brew_steep,
            timerReadyRes = R.string.instruction_cold_brew_filter,
        ),
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
