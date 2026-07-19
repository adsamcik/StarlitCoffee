package com.adsamcik.starlitcoffee.ui.util

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.adsamcik.starlitcoffee.R

// Map keys mirror the keys exposed via [availablePresetIcons] plus the
// historical "custom" alias that maps to the bowl glyph. Anything unknown
// falls back to the mug icon — same fallback the original when expression
// used. Lookup form drops cyclomatic complexity below the detekt threshold.
private val presetIconResources: Map<String, Int> = mapOf(
    "espresso" to R.drawable.vessel_icon_espresso,
    "cortado" to R.drawable.vessel_icon_cortado,
    "cappuccino" to R.drawable.vessel_icon_cappuccino,
    "mug" to R.drawable.vessel_icon_mug,
    "travel" to R.drawable.vessel_icon_travel,
    "takeaway" to R.drawable.vessel_icon_takeaway,
    "latte_glass" to R.drawable.vessel_icon_latte_glass,
    "carafe" to R.drawable.vessel_icon_carafe,
    "french_press" to R.drawable.vessel_icon_french_press,
    "kettle" to R.drawable.vessel_icon_kettle,
    "mason_jar" to R.drawable.vessel_icon_mason_jar,
    "bowl" to R.drawable.vessel_icon_bowl,
    "double_wall_espresso" to R.drawable.vessel_icon_double_wall_espresso,
    "double_wall_tumbler" to R.drawable.vessel_icon_double_wall_tumbler,
    "double_wall_mug" to R.drawable.vessel_icon_double_wall_mug,
    "tall_latte_glass" to R.drawable.vessel_icon_tall_latte_glass,
    "aroma_taster" to R.drawable.vessel_icon_aroma_taster,
    "spherical_latte" to R.drawable.vessel_icon_spherical_latte,
    "spouted_espresso_server" to R.drawable.vessel_icon_spouted_espresso_server,
    "dot_carafe" to R.drawable.vessel_icon_dot_carafe,
    "lid_server" to R.drawable.vessel_icon_lid_server,
    "beehive_server" to R.drawable.vessel_icon_beehive_server,
    "barista_server" to R.drawable.vessel_icon_barista_server,
    "angular_server" to R.drawable.vessel_icon_angular_server,
    "faceted_cortado" to R.drawable.vessel_icon_faceted_cortado,
    "ceramic_latte_bowl" to R.drawable.vessel_icon_ceramic_latte_bowl,
    "thermal_carafe" to R.drawable.vessel_icon_thermal_carafe,
    "double_wall_press" to R.drawable.vessel_icon_double_wall_press,
    "custom" to R.drawable.vessel_icon_bowl,
)

@DrawableRes
fun presetIconRes(iconName: String): Int =
    presetIconResources[iconName] ?: R.drawable.vessel_icon_mug

@Composable
fun PresetIcon(
    iconName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(id = presetIconRes(iconName)),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

val availablePresetIcons = listOf(
    "espresso",
    "cortado",
    "cappuccino",
    "mug",
    "travel",
    "takeaway",
    "latte_glass",
    "carafe",
    "french_press",
    "kettle",
    "mason_jar",
    "bowl",
    "double_wall_espresso",
    "double_wall_tumbler",
    "double_wall_mug",
    "tall_latte_glass",
    "aroma_taster",
    "spherical_latte",
    "spouted_espresso_server",
    "dot_carafe",
    "lid_server",
    "beehive_server",
    "barista_server",
    "angular_server",
    "faceted_cortado",
    "ceramic_latte_bowl",
    "thermal_carafe",
    "double_wall_press",
)

val presetColorPalette: List<String?> = listOf(
    null,
    "#8B4513",
    "#D2691E",
    "#CD853F",
    "#4682B4",
    "#2E8B57",
    "#9370DB",
    "#DC143C",
    "#FF8C00",
    "#708090",
)
