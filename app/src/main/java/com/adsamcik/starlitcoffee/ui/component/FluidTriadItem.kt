package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget

@Immutable
data class FluidTriadItem(
    val target: CalculatorQuantityTarget,
    val label: String,
    val value: String,
    val spokenValue: String = value,
    val icon: ImageVector,
    val approximate: Boolean = false,
    val enabled: Boolean = true,
)
