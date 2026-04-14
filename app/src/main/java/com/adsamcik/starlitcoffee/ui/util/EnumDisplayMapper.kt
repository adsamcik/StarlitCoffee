package com.adsamcik.starlitcoffee.ui.util

import androidx.annotation.StringRes
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.TasteFeedback

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

// Legacy - used in non-composable context
fun TasteFeedback.displayName(): String = when (this) {
    TasteFeedback.TOO_SOUR -> "Too Sour / Weak"
    TasteFeedback.BALANCED -> "Balanced"
    TasteFeedback.TOO_BITTER -> "Too Bitter / Harsh"
    TasteFeedback.ASTRINGENT -> "Astringent / Dry"
    TasteFeedback.CLOGGED -> "Clogged / Stalled"
}

// Legacy - used in non-composable context
fun TasteFeedback.getDisplayAdjustmentText(hasGrinder: Boolean, isPulsar: Boolean = false): String = when (this) {
    TasteFeedback.TOO_SOUR -> if (hasGrinder) {
        "Grind finer by 2–4 clicks"
    } else {
        "Try a finer grind setting"
    }
    TasteFeedback.BALANCED -> "No adjustment needed — save this recipe!"
    TasteFeedback.TOO_BITTER -> if (hasGrinder) {
        "Grind coarser by 2–4 clicks" + if (isPulsar) "\nTry opening the valve sooner" else ""
    } else {
        "Try a coarser grind setting" + if (isPulsar) "\nOr try cooler water" else ""
    }
    TasteFeedback.ASTRINGENT -> if (isPulsar) {
        "Increase dose to 20g+ for better bed depth\nReduce agitation during bloom\nKeep slurry ~1cm above bed"
    } else if (hasGrinder) {
        "Grind slightly coarser, reduce agitation"
    } else {
        "Grind slightly coarser, pour more gently"
    }
    TasteFeedback.CLOGGED -> if (isPulsar) {
        if (hasGrinder) {
            "Grind much coarser by 3–5 clicks\nReduce agitation (gentle swirl only)\nCheck if coffee is too fresh — rest 7+ days"
        } else {
            "Grind much coarser\nReduce agitation\nEnsure gentle pours through dispersion cap"
        }
    } else if (hasGrinder) {
        "Grind much coarser by 3–5 clicks"
    } else {
        "Try a much coarser grind setting"
    }
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

// Legacy - used in non-composable context
fun InputMode.displayName(): String = when (this) {
    InputMode.COFFEE_TO_WATER -> "Coffee → Water"
    InputMode.WATER_TO_COFFEE -> "Water → Coffee"
    InputMode.BREW_SIZE_TO_BOTH -> "Brew Size → Both"
    InputMode.CUP_SIZE_TO_BOTH -> "Cup Size → Both"
}

// Legacy - used in non-composable context
fun InputMode.shortLabel(): String = when (this) {
    InputMode.COFFEE_TO_WATER -> "Coffee"
    InputMode.WATER_TO_COFFEE -> "Water"
    InputMode.BREW_SIZE_TO_BOTH -> "Brew"
    InputMode.CUP_SIZE_TO_BOTH -> "Cup"
}

// Legacy - used in non-composable context
fun InputMode.description(): String = when (this) {
    InputMode.COFFEE_TO_WATER -> "Enter coffee dose, calculate water"
    InputMode.WATER_TO_COFFEE -> "Enter water amount, calculate coffee"
    InputMode.BREW_SIZE_TO_BOTH -> "Enter target brew size, calculate coffee and water accounting for absorption"
    InputMode.CUP_SIZE_TO_BOTH -> "Enter cup size, calculate coffee and water"
}
