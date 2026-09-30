package com.adsamcik.starlitcoffee.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType

/** Presentation-only catalogue. Equipment identity never depends on Android resources. */
@JvmInline
value class EquipmentVisual(@param:DrawableRes val resource: Int) {
    companion object {
        fun method(method: BrewMethod): EquipmentVisual = EquipmentVisual(when (method) {
            BrewMethod.PULSAR -> R.drawable.equipment_pulsar
            BrewMethod.V60 -> R.drawable.equipment_v60
            BrewMethod.FRENCH_PRESS -> R.drawable.equipment_french_press
            BrewMethod.AEROPRESS -> R.drawable.equipment_aeropress
            BrewMethod.ESPRESSO -> R.drawable.equipment_espresso
            BrewMethod.MOKA_POT -> R.drawable.equipment_moka_pot
            BrewMethod.COLD_BREW -> R.drawable.equipment_cold_brew
            BrewMethod.CHEMEX -> R.drawable.equipment_chemex
        })

        fun filter(filter: FilterType?): EquipmentVisual? = when (filter) {
            FilterType.PAPER -> EquipmentVisual(R.drawable.equipment_filter_paper)
            FilterType.METAL_19K, FilterType.METAL_40K -> EquipmentVisual(R.drawable.equipment_filter_metal)
            null -> null
        }

        fun grinder(id: String?): EquipmentVisual? = when (id) {
            "1zpresso-zp6-special" -> EquipmentVisual(R.drawable.equipment_grinder_zp6)
            "comandante-c40" -> EquipmentVisual(R.drawable.equipment_grinder_c40)
            "fellow-ode-gen2" -> EquipmentVisual(R.drawable.equipment_grinder_ode)
            "baratza-encore-esp" -> EquipmentVisual(R.drawable.equipment_grinder_encore)
            "niche-zero" -> EquipmentVisual(R.drawable.equipment_grinder_niche)
            else -> null
        }
    }
}

/** Decorative beside a label; pass a description only when used without adjacent text. */
@Composable
fun EquipmentIcon(
    visual: EquipmentVisual?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    if (visual == null) {
        Icon(Icons.Default.RemoveCircleOutline, contentDescription, modifier, tint = tint)
    } else {
        Icon(painterResource(visual.resource), contentDescription, modifier, tint = tint)
    }
}

@Composable
fun EquipmentVisualBadge(visual: EquipmentVisual?, selected: Boolean = false, size: Dp = 48.dp) {
    Surface(shape = RoundedCornerShape(if (selected) 16.dp else 24.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            EquipmentIcon(visual, Modifier.size(size * 0.7f), tint = androidx.compose.material3.LocalContentColor.current)
        }
    }
}
