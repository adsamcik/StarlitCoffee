package com.adsamcik.starlitcoffee.ui.util

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction

/** Shared localized action names for the live screen, app activity and OS notifications. */
@StringRes
internal fun BrewStageAction.labelResource(): Int = when (this) {
    BrewStageAction.PREPARE -> R.string.action_brew_prepare
    BrewStageAction.RINSE -> R.string.action_brew_rinse
    BrewStageAction.ADD_COFFEE -> R.string.action_brew_add_coffee
    BrewStageAction.ADD_WATER -> R.string.action_brew_add_water
    BrewStageAction.BLOOM -> R.string.action_brew_bloom
    BrewStageAction.POUR -> R.string.action_brew_pour
    BrewStageAction.AGITATE -> R.string.action_brew_agitate
    BrewStageAction.STEEP -> R.string.action_brew_steep
    BrewStageAction.RELEASE -> R.string.action_brew_release
    BrewStageAction.PRESS -> R.string.action_brew_press
    BrewStageAction.HEAT -> R.string.action_brew_heat
    BrewStageAction.OBSERVE -> R.string.action_brew_observe
    BrewStageAction.FILTER -> R.string.action_brew_filter
    BrewStageAction.SERVE -> R.string.action_brew_serve
    BrewStageAction.CLEAN_UP -> R.string.action_brew_clean_up
    BrewStageAction.CUSTOM -> R.string.action_brew_custom
}
