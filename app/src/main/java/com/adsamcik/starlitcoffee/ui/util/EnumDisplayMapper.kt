package com.adsamcik.starlitcoffee.ui.util

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import com.adsamcik.starlitcoffee.data.model.StrengthPreset
import com.adsamcik.starlitcoffee.data.model.FlavorDescriptor
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.data.model.BrewRating
import com.adsamcik.starlitcoffee.data.model.TasteIssue
import com.adsamcik.starlitcoffee.data.model.CoffeeBagStatus
import com.adsamcik.starlitcoffee.data.model.DecafProcess
import com.adsamcik.starlitcoffee.data.model.CoffeeRoastLevel
import com.adsamcik.starlitcoffee.data.model.CoffeeProcessType

// --- TasteFeedback ---

@StringRes
fun TasteFeedback.displayNameRes(): Int = when (this) {
    TasteFeedback.TOO_SOUR -> R.string.feedback_too_sour
    TasteFeedback.BALANCED -> R.string.feedback_balanced
    TasteFeedback.TOO_BITTER -> R.string.feedback_too_bitter
    TasteFeedback.ASTRINGENT -> R.string.feedback_astringent
    TasteFeedback.CLOGGED -> R.string.feedback_clogged
}

fun TasteFeedback.emoji(): String = when (this) {
    TasteFeedback.TOO_SOUR -> "🍋"
    TasteFeedback.BALANCED -> "✅"
    TasteFeedback.TOO_BITTER -> "😖"
    TasteFeedback.ASTRINGENT -> "😣"
    TasteFeedback.CLOGGED -> "🚫"
}

@StringRes
fun TasteFeedback.getDisplayAdjustmentTextRes(hasGrinder: Boolean, isPulsar: Boolean = false): Int = when (this) {
    TasteFeedback.TOO_SOUR -> if (hasGrinder) R.string.adjust_grind_finer else R.string.adjust_try_finer
    TasteFeedback.BALANCED -> R.string.adjust_no_change
    TasteFeedback.TOO_BITTER -> if (hasGrinder) {
        if (isPulsar) R.string.adjust_grind_coarser_valve else R.string.adjust_grind_coarser
    } else {
        if (isPulsar) R.string.adjust_try_coarser_water else R.string.adjust_try_coarser
    }
    TasteFeedback.ASTRINGENT -> if (isPulsar) R.string.adjust_astringent_pulsar
        else if (hasGrinder) R.string.adjust_astringent_grinder
        else R.string.adjust_astringent_no_grinder
    TasteFeedback.CLOGGED -> if (isPulsar) {
        if (hasGrinder) R.string.adjust_clogged_pulsar_grinder else R.string.adjust_clogged_pulsar_no_grinder
    } else if (hasGrinder) R.string.adjust_clogged_grinder
    else R.string.adjust_clogged_no_grinder
}

// --- InputMode ---

@StringRes
fun InputMode.displayNameRes(): Int = when (this) {
    InputMode.COFFEE_TO_WATER -> R.string.input_coffee_to_water
    InputMode.WATER_TO_COFFEE -> R.string.input_water_to_coffee
    InputMode.BREW_SIZE_TO_BOTH -> R.string.input_brew_to_both
    InputMode.CUP_SIZE_TO_BOTH -> R.string.input_cup_to_both
}

@StringRes
fun InputMode.shortLabelRes(): Int = when (this) {
    InputMode.COFFEE_TO_WATER -> R.string.input_coffee_short
    InputMode.WATER_TO_COFFEE -> R.string.input_water_short
    InputMode.BREW_SIZE_TO_BOTH -> R.string.input_brew_short
    InputMode.CUP_SIZE_TO_BOTH -> R.string.input_cup_short
}

@StringRes
fun InputMode.descriptionRes(): Int = when (this) {
    InputMode.COFFEE_TO_WATER -> R.string.input_coffee_desc
    InputMode.WATER_TO_COFFEE -> R.string.input_water_desc
    InputMode.BREW_SIZE_TO_BOTH -> R.string.input_brew_desc
    InputMode.CUP_SIZE_TO_BOTH -> R.string.input_cup_desc
}

// --- StrengthPreset ---

@StringRes
fun StrengthPreset.displayNameRes(): Int = when (this) {
    StrengthPreset.LIGHT -> R.string.strength_light
    StrengthPreset.BALANCED -> R.string.strength_balanced
    StrengthPreset.STRONG -> R.string.strength_strong
}

// --- FlavorDescriptor ---

@StringRes
fun FlavorDescriptor.displayNameRes(): Int = when (this) {
    FlavorDescriptor.FRUITY -> R.string.flavor_fruity
    FlavorDescriptor.CHOCOLATE -> R.string.flavor_chocolate
    FlavorDescriptor.NUTTY -> R.string.flavor_nutty
    FlavorDescriptor.FLORAL -> R.string.flavor_floral
    FlavorDescriptor.BRIGHT -> R.string.flavor_bright
    FlavorDescriptor.SMOOTH -> R.string.flavor_smooth
    FlavorDescriptor.BITTER -> R.string.flavor_bitter
    FlavorDescriptor.SWEET -> R.string.flavor_sweet
    FlavorDescriptor.EARTHY -> R.string.flavor_earthy
    FlavorDescriptor.SPICY -> R.string.flavor_spicy
    FlavorDescriptor.CITRUS -> R.string.flavor_citrus
    FlavorDescriptor.BERRY -> R.string.flavor_berry
    FlavorDescriptor.CARAMEL -> R.string.flavor_caramel
    FlavorDescriptor.WINE -> R.string.flavor_wine
    FlavorDescriptor.CLEAN -> R.string.flavor_clean
}

// --- GrindDescriptor ---

@StringRes
fun GrindDescriptor.displayNameRes(): Int = when (this) {
    GrindDescriptor.VERY_FINE -> R.string.grind_very_fine
    GrindDescriptor.FINE -> R.string.grind_fine
    GrindDescriptor.MEDIUM_FINE -> R.string.grind_medium_fine
    GrindDescriptor.MEDIUM -> R.string.grind_medium
    GrindDescriptor.MEDIUM_COARSE -> R.string.grind_medium_coarse
    GrindDescriptor.COARSE -> R.string.grind_coarse
}

// --- BrewRating ---

@StringRes
fun BrewRating.labelRes(): Int = when (this) {
    BrewRating.BAD -> R.string.rating_bad
    BrewRating.MEH -> R.string.rating_meh
    BrewRating.GOOD -> R.string.rating_good
    BrewRating.AWESOME -> R.string.rating_awesome
}

/** Content description for the tappable rating face (e.g. "Rate as good"). */
@StringRes
fun BrewRating.selectContentDescriptionRes(): Int = when (this) {
    BrewRating.BAD -> R.string.cd_rate_bad
    BrewRating.MEH -> R.string.cd_rate_meh
    BrewRating.GOOD -> R.string.cd_rate_good
    BrewRating.AWESOME -> R.string.cd_rate_awesome
}

// --- TasteIssue ---

@StringRes
fun TasteIssue.labelRes(): Int = when (this) {
    TasteIssue.TOO_BITTER -> R.string.taste_issue_too_bitter
    TasteIssue.TOO_SOUR -> R.string.taste_issue_too_sour
    TasteIssue.TOO_WEAK -> R.string.taste_issue_too_weak
    TasteIssue.TOO_STRONG -> R.string.taste_issue_too_strong
}

@StringRes
fun TasteIssue.suggestionRes(): Int = when (this) {
    TasteIssue.TOO_BITTER -> R.string.taste_issue_bitter_suggestion
    TasteIssue.TOO_SOUR -> R.string.taste_issue_sour_suggestion
    TasteIssue.TOO_WEAK -> R.string.taste_issue_weak_suggestion
    TasteIssue.TOO_STRONG -> R.string.taste_issue_strong_suggestion
}

// --- CoffeeBagStatus ---

@StringRes
fun CoffeeBagStatus.displayNameRes(): Int = when (this) {
    CoffeeBagStatus.SEALED -> R.string.bag_status_sealed
    CoffeeBagStatus.OPEN -> R.string.bag_status_open
    CoffeeBagStatus.FROZEN -> R.string.bag_status_frozen
    CoffeeBagStatus.FINISHED -> R.string.bag_status_finished
}

// --- DecafProcess ---

@StringRes
fun DecafProcess.displayNameRes(): Int = when (this) {
    DecafProcess.UNKNOWN -> R.string.decaf_unknown
    DecafProcess.SWISS_WATER -> R.string.decaf_swiss_water
    DecafProcess.MOUNTAIN_WATER -> R.string.decaf_mountain_water
    DecafProcess.CO2_SUPERCRITICAL -> R.string.decaf_co2
    DecafProcess.EA_SUGARCANE -> R.string.decaf_ea_sugarcane
    DecafProcess.MC_DIRECT -> R.string.decaf_mc_direct
    DecafProcess.EA_DIRECT -> R.string.decaf_ea_direct
}

@StringRes
fun DecafProcess.shortLabelRes(): Int = when (this) {
    DecafProcess.UNKNOWN -> R.string.decaf_short_unknown
    DecafProcess.SWISS_WATER -> R.string.decaf_short_swiss_water
    DecafProcess.MOUNTAIN_WATER -> R.string.decaf_short_mountain_water
    DecafProcess.CO2_SUPERCRITICAL -> R.string.decaf_short_co2
    DecafProcess.EA_SUGARCANE -> R.string.decaf_short_ea_sugarcane
    DecafProcess.MC_DIRECT -> R.string.decaf_short_mc_direct
    DecafProcess.EA_DIRECT -> R.string.decaf_short_ea_direct
}

// --- CoffeeRoastLevel.Known ---

@StringRes
fun CoffeeRoastLevel.Known.displayNameRes(): Int = when (this) {
    CoffeeRoastLevel.Known.LIGHT -> R.string.roast_light
    CoffeeRoastLevel.Known.MEDIUM_LIGHT -> R.string.roast_medium_light
    CoffeeRoastLevel.Known.MEDIUM -> R.string.roast_medium
    CoffeeRoastLevel.Known.MEDIUM_DARK -> R.string.roast_medium_dark
    CoffeeRoastLevel.Known.DARK -> R.string.roast_dark
    CoffeeRoastLevel.Known.FILTER -> R.string.roast_filter
    CoffeeRoastLevel.Known.ESPRESSO -> R.string.roast_espresso
    CoffeeRoastLevel.Known.OMNIROAST -> R.string.roast_omniroast
    CoffeeRoastLevel.Known.CINNAMON -> R.string.roast_cinnamon
}

// --- CoffeeProcessType.Known ---

@StringRes
fun CoffeeProcessType.Known.displayNameRes(): Int = when (this) {
    CoffeeProcessType.Known.WASHED -> R.string.process_washed
    CoffeeProcessType.Known.NATURAL -> R.string.process_natural
    CoffeeProcessType.Known.HONEY -> R.string.process_honey
    CoffeeProcessType.Known.ANAEROBIC -> R.string.process_anaerobic
    CoffeeProcessType.Known.CARBONIC_MACERATION -> R.string.process_carbonic_maceration
    CoffeeProcessType.Known.SEMI_WASHED -> R.string.process_semi_washed
    CoffeeProcessType.Known.WET_HULLED -> R.string.process_wet_hulled
    CoffeeProcessType.Known.PULPED_NATURAL -> R.string.process_pulped_natural
    CoffeeProcessType.Known.DOUBLE_FERMENTED -> R.string.process_double_fermented
    CoffeeProcessType.Known.THERMAL_SHOCK -> R.string.process_thermal_shock
}
