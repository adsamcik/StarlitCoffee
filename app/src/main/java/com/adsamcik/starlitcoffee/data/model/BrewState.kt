package com.adsamcik.starlitcoffee.data.model

data class BrewState(
    val method: BrewMethod,
    val inputMode: InputMode,
    val amount: Float,
    val strengthPreset: StrengthPreset,
    val customRatio: Float? = null,
    val tempC: Int? = null,
    val bloomMultiplier: Float? = null,
    val pulseCount: Int? = null,
    val filterType: FilterType? = null,
    val grinderId: String? = null,
    val calibrationStyle: CalibrationStyle? = null,
) {
    val effectiveRatio: Float
        get() = customRatio ?: (method.defaultRatio + strengthPreset.ratioOffset)

    val coffeeG: Float
        get() = when (inputMode) {
            InputMode.COFFEE_TO_WATER -> amount
            InputMode.WATER_TO_COFFEE -> amount / effectiveRatio
            InputMode.BREW_SIZE_TO_BOTH -> {
                val divisor = effectiveRatio - 2.0f
                if (divisor > 0f) amount / divisor else amount / effectiveRatio
            }
            InputMode.CUP_SIZE_TO_BOTH -> amount / effectiveRatio
        }

    val waterG: Float
        get() = when (inputMode) {
            InputMode.COFFEE_TO_WATER -> amount * effectiveRatio
            InputMode.WATER_TO_COFFEE -> amount
            InputMode.BREW_SIZE_TO_BOTH -> {
                val divisor = effectiveRatio - 2.0f
                if (divisor > 0f) (amount / divisor) * effectiveRatio else amount
            }
            InputMode.CUP_SIZE_TO_BOTH -> amount
        }

    val bloomG: Float
        get() {
            if (!method.hasBloom) return 0f
            val multiplier = bloomMultiplier ?: method.bloomMultiplier
            return coffeeG * multiplier
        }

    val remainingWaterG: Float
        get() = waterG - bloomG

    val effectivePulseCount: Int
        get() {
            if (!method.hasPulses) return 0
            return pulseCount ?: method.defaultPulses
        }

    val pulseSizeG: Float
        get() {
            val pulses = effectivePulseCount
            if (pulses <= 0) return 0f
            return remainingWaterG / pulses
        }

    val timeTargetLowS: Int
        get() = method.timeTargetLow

    val timeTargetHighS: Int
        get() = method.timeTargetHigh
}
