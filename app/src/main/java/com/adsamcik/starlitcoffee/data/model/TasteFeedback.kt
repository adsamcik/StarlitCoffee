package com.adsamcik.starlitcoffee.data.model

enum class TasteFeedback(
    val displayName: String,
    val emoji: String,
) {
    TOO_SOUR("Too Sour / Weak", "🍋"),
    BALANCED("Balanced", "✅"),
    TOO_BITTER("Too Bitter / Harsh", "😖"),
    ASTRINGENT("Astringent / Dry", "😣"),
    CLOGGED("Clogged / Stalled", "🚫");

    fun getAdjustmentText(hasGrinder: Boolean, isPulsar: Boolean = false): String = when (this) {
        TOO_SOUR -> if (hasGrinder) {
            "Grind finer by 2–4 clicks"
        } else {
            "Try a finer grind setting"
        }
        BALANCED -> "No adjustment needed — save this recipe!"
        TOO_BITTER -> if (hasGrinder) {
            "Grind coarser by 2–4 clicks" + if (isPulsar) "\nTry opening the valve sooner" else ""
        } else {
            "Try a coarser grind setting" + if (isPulsar) "\nOr try cooler water" else ""
        }
        ASTRINGENT -> if (isPulsar) {
            "Increase dose to 20g+ for better bed depth\nReduce agitation during bloom\nKeep slurry ~1cm above bed"
        } else if (hasGrinder) {
            "Grind slightly coarser, reduce agitation"
        } else {
            "Grind slightly coarser, pour more gently"
        }
        CLOGGED -> if (isPulsar) {
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
}
