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
        /** Accepted recognition family; these glyphs are not brewing instructions. */
        val methodKeys: List<String> = listOf(
            "pulsar", "v60", "chemex", "kalita-wave", "wedge", "melitta", "clever", "hario-switch",
            "aeropress", "french-press", "espresso", "moka-pot", "cold-brew", "turkish", "phin",
            "automatic-drip", "siphon", "percolator",
        )

        fun methodKey(key: String): EquipmentVisual? = when (key) {
            "pulsar" -> R.drawable.equipment_pulsar
            "v60", "v60_02" -> R.drawable.equipment_v60
            "chemex" -> R.drawable.equipment_chemex
            "kalita-wave", "wave_185" -> R.drawable.equipment_kalita_wave
            "wedge" -> R.drawable.equipment_wedge
            "melitta" -> R.drawable.equipment_melitta
            "clever" -> R.drawable.equipment_clever
            "hario-switch" -> R.drawable.equipment_hario_switch
            "aeropress" -> R.drawable.equipment_aeropress
            "french-press" -> R.drawable.equipment_french_press
            "espresso" -> R.drawable.equipment_espresso
            "moka-pot" -> R.drawable.equipment_moka_pot
            "cold-brew", "cold" -> R.drawable.equipment_cold_brew
            "turkish" -> R.drawable.equipment_turkish
            "phin" -> R.drawable.equipment_phin
            "automatic-drip" -> R.drawable.equipment_automatic_drip
            "siphon" -> R.drawable.equipment_siphon
            "percolator" -> R.drawable.equipment_percolator
            else -> null
        }?.let(::EquipmentVisual)

        @Suppress("CyclomaticComplexMethod") // Explicit recognition aliases retain exact approved assets.
        fun profile(profileId: String): EquipmentVisual? = when (profileId) {
            "pulsar_standard" -> methodKey("pulsar")
            "v60_01", "v60_02", "v60_03", "v60_unspecified", "manual_conical_generic" -> methodKey("v60")
            "manual_wave_155", "manual_wave_185" -> methodKey("kalita-wave")
            "manual_wedge_generic" -> methodKey("wedge")
            "manual_thick_paper_carafe", "chemex_unspecified" -> methodKey("chemex")
            "clever_style", "valve_release_generic" -> methodKey("clever")
            "hario_switch" -> methodKey("hario-switch")
            "french_press_generic" -> methodKey("french-press")
            "aeropress_standard", "aeropress_xl" -> methodKey("aeropress")
            "espresso_pump_generic" -> methodKey("espresso")
            "moka_generic_unspecified" -> methodKey("moka-pot")
            "cold_immersion_generic" -> methodKey("cold-brew")
            "cezve_generic" -> methodKey("turkish")
            "vietnamese_phin" -> methodKey("phin")
            "hario_technica_tcar3" -> methodKey("siphon")
            "presto_02822" -> methodKey("percolator")
            "moccamaster_kbgv_select", "automatic_batch_generic" -> methodKey("automatic-drip")
            // A cup conveys the method without inventing a carafe, pump or lever variant.
            "automatic_single_cup_generic" -> EquipmentVisual(R.drawable.vessel_icon_mug)
            "espresso_lever_generic", "espresso_portable_generic" -> EquipmentVisual(R.drawable.vessel_icon_espresso)
            else -> null
        }

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
