package com.adsamcik.starlitcoffee.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.ui.graphics.vector.ImageVector

fun presetIcon(iconName: String): ImageVector {
    return when (iconName) {
        "espresso" -> Icons.Filled.LocalCafe
        "cortado" -> Icons.Filled.Coffee
        "cappuccino" -> Icons.Filled.EmojiFoodBeverage
        "mug" -> Icons.Filled.FreeBreakfast
        "travel" -> Icons.Filled.Luggage
        else -> Icons.Filled.LocalCafe
    }
}

val availablePresetIcons = listOf(
    "espresso" to "Espresso",
    "cortado" to "Cortado",
    "cappuccino" to "Cappuccino",
    "mug" to "Mug",
    "travel" to "Travel",
    "custom" to "Cup",
)

val presetColorPalette: List<Pair<String?, String>> = listOf(
    null to "Default",
    "#8B4513" to "Brown",
    "#D2691E" to "Chocolate",
    "#CD853F" to "Peru",
    "#4682B4" to "Steel Blue",
    "#2E8B57" to "Sea Green",
    "#9370DB" to "Purple",
    "#DC143C" to "Crimson",
    "#FF8C00" to "Orange",
    "#708090" to "Slate",
)
