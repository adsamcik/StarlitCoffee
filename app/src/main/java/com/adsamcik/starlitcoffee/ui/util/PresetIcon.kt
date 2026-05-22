package com.adsamcik.starlitcoffee.ui.util

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.adsamcik.starlitcoffee.R

@DrawableRes
fun presetIconRes(iconName: String): Int {
    return when (iconName) {
        "espresso" -> R.drawable.vessel_icon_espresso
        "cortado" -> R.drawable.vessel_icon_cortado
        "cappuccino" -> R.drawable.vessel_icon_cappuccino
        "mug" -> R.drawable.vessel_icon_mug
        "travel" -> R.drawable.vessel_icon_travel
        "takeaway" -> R.drawable.vessel_icon_takeaway
        "latte_glass" -> R.drawable.vessel_icon_latte_glass
        "carafe" -> R.drawable.vessel_icon_carafe
        "french_press" -> R.drawable.vessel_icon_french_press
        "kettle" -> R.drawable.vessel_icon_kettle
        "mason_jar" -> R.drawable.vessel_icon_mason_jar
        "bowl" -> R.drawable.vessel_icon_bowl
        "double_wall_espresso" -> R.drawable.vessel_icon_double_wall_espresso
        "double_wall_tumbler" -> R.drawable.vessel_icon_double_wall_tumbler
        "double_wall_mug" -> R.drawable.vessel_icon_double_wall_mug
        "tall_latte_glass" -> R.drawable.vessel_icon_tall_latte_glass
        "aroma_taster" -> R.drawable.vessel_icon_aroma_taster
        "spherical_latte" -> R.drawable.vessel_icon_spherical_latte
        "spouted_espresso_server" -> R.drawable.vessel_icon_spouted_espresso_server
        "dot_carafe" -> R.drawable.vessel_icon_dot_carafe
        "lid_server" -> R.drawable.vessel_icon_lid_server
        "beehive_server" -> R.drawable.vessel_icon_beehive_server
        "barista_server" -> R.drawable.vessel_icon_barista_server
        "angular_server" -> R.drawable.vessel_icon_angular_server
        "faceted_cortado" -> R.drawable.vessel_icon_faceted_cortado
        "ceramic_latte_bowl" -> R.drawable.vessel_icon_ceramic_latte_bowl
        "thermal_carafe" -> R.drawable.vessel_icon_thermal_carafe
        "double_wall_press" -> R.drawable.vessel_icon_double_wall_press
        "custom" -> R.drawable.vessel_icon_bowl
        else -> R.drawable.vessel_icon_mug
    }
}

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
    "espresso" to "Espresso",
    "cortado" to "Cortado",
    "cappuccino" to "Cappuccino",
    "mug" to "Mug",
    "travel" to "Travel",
    "takeaway" to "Takeaway",
    "latte_glass" to "Latte Glass",
    "carafe" to "Carafe",
    "french_press" to "French Press",
    "kettle" to "Kettle",
    "mason_jar" to "Mason Jar",
    "bowl" to "Bowl",
    "double_wall_espresso" to "Double Wall Espresso",
    "double_wall_tumbler" to "Double Wall Tumbler",
    "double_wall_mug" to "Double Wall Mug",
    "tall_latte_glass" to "Tall Latte Glass",
    "aroma_taster" to "Aroma Taster",
    "spherical_latte" to "Spherical Latte",
    "spouted_espresso_server" to "Spouted Espresso",
    "dot_carafe" to "Dot Carafe",
    "lid_server" to "Lid Server",
    "beehive_server" to "Beehive Server",
    "barista_server" to "Barista Server",
    "angular_server" to "Angular Server",
    "faceted_cortado" to "Faceted Cortado",
    "ceramic_latte_bowl" to "Ceramic Latte Bowl",
    "thermal_carafe" to "Thermal Carafe",
    "double_wall_press" to "Double Wall Press",
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
