package com.adsamcik.starlitcoffee.ui.util

import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.PhaseType
import com.adsamcik.starlitcoffee.data.model.TasteFeedback

// --- TasteFeedback ---

fun TasteFeedback.displayName(): String = when (this) {
    TasteFeedback.TOO_SOUR -> "Too Sour / Weak"
    TasteFeedback.BALANCED -> "Balanced"
    TasteFeedback.TOO_BITTER -> "Too Bitter / Harsh"
    TasteFeedback.ASTRINGENT -> "Astringent / Dry"
    TasteFeedback.CLOGGED -> "Clogged / Stalled"
}

fun TasteFeedback.emoji(): String = when (this) {
    TasteFeedback.TOO_SOUR -> "🍋"
    TasteFeedback.BALANCED -> "✅"
    TasteFeedback.TOO_BITTER -> "😖"
    TasteFeedback.ASTRINGENT -> "😣"
    TasteFeedback.CLOGGED -> "🚫"
}

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

// --- PhaseType ---

fun PhaseType.displayName(): String = when (this) {
    PhaseType.BLOOM -> "Bloom"
    PhaseType.POUR -> "Pour"
    PhaseType.DRAIN_AND_REFILL -> "Drain & Refill"
    PhaseType.DRAWDOWN -> "Drawdown"
}

fun PhaseType.emoji(): String = when (this) {
    PhaseType.BLOOM -> "🌱"
    PhaseType.POUR -> "💧"
    PhaseType.DRAIN_AND_REFILL -> "🔄"
    PhaseType.DRAWDOWN -> "⏬"
}

// --- InputMode ---

fun InputMode.displayName(): String = when (this) {
    InputMode.COFFEE_TO_WATER -> "Coffee → Water"
    InputMode.WATER_TO_COFFEE -> "Water → Coffee"
    InputMode.BREW_SIZE_TO_BOTH -> "Brew Size → Both"
    InputMode.CUP_SIZE_TO_BOTH -> "Cup Size → Both"
}

fun InputMode.shortLabel(): String = when (this) {
    InputMode.COFFEE_TO_WATER -> "Coffee"
    InputMode.WATER_TO_COFFEE -> "Water"
    InputMode.BREW_SIZE_TO_BOTH -> "Brew"
    InputMode.CUP_SIZE_TO_BOTH -> "Cup"
}

fun InputMode.description(): String = when (this) {
    InputMode.COFFEE_TO_WATER -> "Enter coffee dose, calculate water"
    InputMode.WATER_TO_COFFEE -> "Enter water amount, calculate coffee"
    InputMode.BREW_SIZE_TO_BOTH -> "Enter target brew size, calculate coffee and water accounting for absorption"
    InputMode.CUP_SIZE_TO_BOTH -> "Enter cup size, calculate coffee and water"
}
