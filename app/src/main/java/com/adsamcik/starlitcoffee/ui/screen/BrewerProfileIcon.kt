package com.adsamcik.starlitcoffee.ui.screen

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.ui.component.EquipmentVisual

/** Stable visual categories for exact brewers shown in the Learn selector. */
internal enum class BrewerProfileIconKind {
    CONE,
    FLAT_BOTTOM,
    WEDGE,
    CARAFE,
    IMMERSION,
    CEZVE,
    BATCH_MACHINE,
    SINGLE_CUP_MACHINE,
    PHIN,
    PULSAR,
}

/** Groups profiles under the user-facing method headings already localized by the app. */
internal enum class BrewerMethodGroup {
    MANUAL_GRAVITY,
    STEEP_AND_RELEASE,
    HEATED_UNFILTERED,
    AUTOMATIC,
    RESTRICTED_FLOW,
    VALVE_CONTROLLED,
}

internal fun brewerProfileIconKind(profileId: BrewerProfileId): BrewerProfileIconKind =
    when (profileId.value) {
        "v60_02", "v60_unspecified", "manual_conical_generic" -> BrewerProfileIconKind.CONE
        "manual_wave_185" -> BrewerProfileIconKind.FLAT_BOTTOM
        "manual_wedge_generic" -> BrewerProfileIconKind.WEDGE
        "manual_thick_paper_carafe", "chemex_unspecified" -> BrewerProfileIconKind.CARAFE
        "clever_style", "hario_switch" -> BrewerProfileIconKind.IMMERSION
        "cezve_generic" -> BrewerProfileIconKind.CEZVE
        "moccamaster_kbgv_select", "automatic_batch_generic" -> BrewerProfileIconKind.BATCH_MACHINE
        "automatic_single_cup_generic" -> BrewerProfileIconKind.SINGLE_CUP_MACHINE
        "vietnamese_phin" -> BrewerProfileIconKind.PHIN
        "pulsar_standard" -> BrewerProfileIconKind.PULSAR
        else -> BrewerProfileIconKind.CONE
    }

internal fun brewerMethodGroup(profileId: BrewerProfileId): BrewerMethodGroup =
    when (profileId.value) {
        "v60_02",
        "v60_unspecified",
        "manual_wave_185",
        "manual_wedge_generic",
        "manual_thick_paper_carafe",
        "manual_conical_generic",
        -> BrewerMethodGroup.MANUAL_GRAVITY
        "clever_style", "hario_switch" -> BrewerMethodGroup.STEEP_AND_RELEASE
        "cezve_generic" -> BrewerMethodGroup.HEATED_UNFILTERED
        "moccamaster_kbgv_select", "automatic_batch_generic", "automatic_single_cup_generic" ->
            BrewerMethodGroup.AUTOMATIC
        "vietnamese_phin" -> BrewerMethodGroup.RESTRICTED_FLOW
        "pulsar_standard" -> BrewerMethodGroup.VALVE_CONTROLLED
        else -> BrewerMethodGroup.MANUAL_GRAVITY
    }

/**
 * Recognition uses the same tintable family as the calculator. Instruction
 * scenes remain separately reviewed assets; an unknown profile gets a neutral cup.
 */
@DrawableRes
internal fun brewerProfileIconDrawable(profileId: BrewerProfileId): Int =
    EquipmentVisual.profile(profileId.value)?.resource ?: R.drawable.vessel_icon_mug

@Composable
internal fun BrewerProfileIcon(
    profileId: BrewerProfileId,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(brewerProfileIconDrawable(profileId)),
        contentDescription = null,
        modifier = modifier,
    )
}
