package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.ui.graphics.vector.ImageVector
import com.adsamcik.starlitcoffee.data.model.BrewMethod

fun iconForMethod(method: BrewMethod): ImageVector = when (method) {
    BrewMethod.PULSAR -> Icons.Filled.FilterDrama
    BrewMethod.V60 -> Icons.Filled.FilterList
    BrewMethod.FRENCH_PRESS -> Icons.Filled.Coffee
    BrewMethod.AEROPRESS -> Icons.Filled.Air
    BrewMethod.ESPRESSO -> Icons.Filled.LocalCafe
    BrewMethod.MOKA_POT -> Icons.Filled.OutdoorGrill
    BrewMethod.COLD_BREW -> Icons.Filled.AcUnit
}
