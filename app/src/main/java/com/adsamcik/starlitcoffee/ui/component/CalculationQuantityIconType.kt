package com.adsamcik.starlitcoffee.ui.component

import androidx.annotation.DrawableRes
import com.adsamcik.starlitcoffee.R

enum class CalculationQuantityIconType(
    @DrawableRes val drawableRes: Int,
) {
    COFFEE_DOSE(R.drawable.calculation_icon_coffee_dose),
    WATER_IN(R.drawable.calculation_icon_water_input),
    CUP_OUTPUT(R.drawable.calculation_icon_cup_output),
}
