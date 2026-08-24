package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.runtime.Immutable
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget

@Immutable
data class FluidTriadItem(
    val target: CalculatorQuantityTarget,
    val label: String,
    val value: String,
    val spokenValue: String = value,
    val icon: CalculationQuantityIconType,
    val approximate: Boolean = false,
    val enabled: Boolean = true,
)
