package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId

/** Shared localized labels for every exact-brewing selection surface. */
@Composable
internal fun exactRecipeName(id: BuiltInRecipeId): String = stringResource(
    EXACT_RECIPE_NAME_RESOURCES[id.value] ?: R.string.label_exact_recipe_unavailable,
)

@Composable
internal fun localizedProfileName(profileId: BrewerProfileId, fallback: String): String = when (profileId.value) {
    "v60_02" -> stringResource(R.string.label_brewer_profile_v60_02)
    "v60_unspecified" -> stringResource(R.string.label_brewer_profile_v60)
    "manual_wave_185" -> stringResource(R.string.label_brewer_profile_wave_185)
    "manual_wedge_generic" -> stringResource(R.string.label_brewer_profile_wedge)
    "manual_thick_paper_carafe" -> stringResource(R.string.label_brewer_profile_thick_paper_carafe)
    "manual_conical_generic" -> stringResource(R.string.label_brewer_profile_conical)
    "clever_style" -> stringResource(R.string.label_brewer_profile_clever_style)
    "hario_switch" -> stringResource(R.string.label_brewer_profile_hario_switch)
    "cezve_generic" -> stringResource(R.string.label_brewer_profile_cezve_generic)
    "moccamaster_kbgv_select" -> stringResource(R.string.label_brewer_profile_moccamaster_kbgv_select)
    "automatic_batch_generic" -> stringResource(R.string.label_brewer_profile_automatic_batch_generic)
    "automatic_single_cup_generic" -> stringResource(R.string.label_brewer_profile_automatic_single_cup_generic)
    "vietnamese_phin" -> stringResource(R.string.label_brewer_profile_vietnamese_phin)
    "pulsar_standard" -> stringResource(R.string.label_brewer_profile_pulsar)
    else -> fallback
}

@Composable
internal fun localizedMethodFamilyName(profileId: BrewerProfileId, fallback: String): String = when (profileId.value) {
    "v60_02",
    "v60_unspecified",
    "manual_wave_185",
    "manual_wedge_generic",
    "manual_thick_paper_carafe",
    "manual_conical_generic",
    -> stringResource(R.string.label_brewer_profile_family_manual_gravity)
    "clever_style", "hario_switch" -> stringResource(R.string.label_brewer_profile_family_steep_and_release)
    "cezve_generic" -> stringResource(R.string.label_brewer_profile_family_heated_unfiltered)
    "moccamaster_kbgv_select", "automatic_batch_generic", "automatic_single_cup_generic" -> stringResource(
        R.string.label_brewer_profile_family_automatic_batch,
    )
    "vietnamese_phin" -> stringResource(
        R.string.label_brewer_profile_family_restricted_flow_gravity_concentrate,
    )
    "pulsar_standard" -> stringResource(
        R.string.label_brewer_profile_family_valve_controlled_no_bypass,
    )
    else -> fallback
}

private val EXACT_RECIPE_NAME_RESOURCES = mapOf(
    "v60_official_15_250" to R.string.recipe_p1_v60_official_15_250,
    "v60_rao_20_330" to R.string.recipe_p1_v60_rao_20_330,
    "v60_kasuya_4_6_20_300" to R.string.recipe_p1_v60_kasuya_4_6_20_300,
    "v60_kurasu_flash_16_150_70" to R.string.recipe_p1_v60_kurasu_flash_16_150_70,
    "wave185_ozone_25_400" to R.string.recipe_p1_wave185_ozone_25_400,
    "wedge_pulse_23_5_400" to R.string.recipe_p1_wedge_pulse_23_5_400,
    "chemex_42_700" to R.string.recipe_p1_chemex_42_700,
    "generic_conical_low_agitation_20_320" to R.string.recipe_p1_generic_conical_20_320,
    "clever_water_first_15_250" to R.string.recipe_p1_clever_water_first_15_250,
    "clever_coffee_first_15_250" to R.string.recipe_p1_clever_coffee_first_15_250,
    "switch_official_20_240" to R.string.recipe_p1_switch_official_20_240,
    "switch_ole_boen_hybrid_16_5_240" to R.string.recipe_p1_switch_hybrid_16_5_240,
    "switch_gravity_15_250" to R.string.recipe_p1_switch_gravity_15_250,
    "cezve_turkish_single_rise_6_65" to R.string.recipe_p1_cezve_single_rise_6_65,
    "cezve_bounded_repeated_rise_12_130" to R.string.recipe_p1_cezve_repeated_rise_12_130,
    "auto_batch_500_30" to R.string.recipe_p1_auto_batch_500_30,
    "auto_batch_1000_60" to R.string.recipe_p1_auto_batch_1000_60,
    "auto_cupone_20_300" to R.string.recipe_p1_auto_cupone_20_300,
    "phin_gravity_14_118" to R.string.recipe_p1_phin_gravity_14_118,
    "phin_screw_18_120" to R.string.recipe_p1_phin_screw_18_120,
)
