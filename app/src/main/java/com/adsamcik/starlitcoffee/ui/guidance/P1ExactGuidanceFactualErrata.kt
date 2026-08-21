package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId

internal fun P1ExactRecipeGuidance.applyVerifiedFactualErrata(): P1ExactRecipeGuidance = when (recipeId) {
    CEZVE_SINGLE_RISE_RECIPE_ID -> copy(
        recipeApproach =
            "App-authored conservative adaptation: mix before low heat and stop at the first " +
                "controlled foam rise; do not return the coffee to heat.",
        evidenceStatus = "App-authored safety adaptation",
        originalSourceOrProvenance =
            "Adapted from SRC-MEHMET-EFENDI and SRC-UNESCO-TURKISH; this is not the complete Mehmet Efendi return-to-heat procedure.",
    )
    CEZVE_REPEATED_RISE_RECIPE_ID -> copy(
        recipeApproach = "App-authored bounded two-rise adaptation informed by traditional cezve sources; stop before rolling boil.",
        evidenceStatus = "App-authored bounded adaptation",
    )
    else -> this
}

/**
 * Corrections verified after the immutable 2026-07-27 evidence projection was produced.
 *
 * The Hoffmann Clever technique steeps to 2:00, breaks the crust, settles for another
 * 30 seconds, and starts drawdown at 2:30. The original video describes a drawdown of
 * roughly one minute. Applying digit-only substitutions preserves reviewed/localized
 * sentence structure while preventing the stale 2:00 release from reaching any locale.
 *
 * Primary source: https://www.youtube.com/watch?v=RpOdennxP24
 * Cross-check: https://crema-coffee.com/pages/clever-dripper-brewing-guide
 */
internal fun P1ExactStageGuidance.applyVerifiedFactualErrata(): P1ExactStageGuidance {
    return when (recipeId) {
        CLEVER_WATER_FIRST_RECIPE_ID -> applyCleverWaterFirstErrata()
        CUP_ONE_RECIPE_ID -> applyCupOneSafetyErrata()
        else -> this
    }
}

private fun P1ExactStageGuidance.applyCleverWaterFirstErrata(): P1ExactStageGuidance =
    when (sourceStageId) {
        "stage_04" -> copy(
            targetDurationOrRange = targetDurationOrRange.replaceDigitTokens(
                "2",
                "00",
                "2",
                "30",
            ),
            concise = concise.copy(
                currentTarget = concise.currentTarget.replaceDigitTokens(
                    "2",
                    "00",
                    "2",
                    "30",
                ),
            ),
        )

        "stage_05" -> {
            val correctedCompletion = completionCriterion.replaceDigitTokens("3", "30")
            copy(
                startTimeOrPrecedingCondition = startTimeOrPrecedingCondition
                    .replaceDigitTokens("2", "30"),
                targetDurationOrRange = targetDurationOrRange.replaceDigitTokens("60"),
                completionCriterion = correctedCompletion,
                full = full.copy(
                    conciseExplanation = full.conciseExplanation.replaceDigitTokens("3", "30"),
                    observableCompletionCue = correctedCompletion,
                    accessibleAltText = full.accessibleAltText.replaceDigitTokens("3", "30"),
                ),
                concise = concise.copy(
                    currentTarget = concise.currentTarget.replaceDigitTokens("60"),
                    completionCue = correctedCompletion,
                ),
            )
        }

        else -> this
    }

private fun P1ExactStageGuidance.applyCupOneSafetyErrata(): P1ExactStageGuidance {
    val correctedWarning = when (sourceStageId) {
        "stage_01" ->
            "Switch off and unplug the brewer before clearing the outlet. A blocked outlet can overflow and cause scalding."
        "stage_05" ->
            "Wait until flow has stopped. The outlet arm and coffee are hot; keep hands clear and remove the mug carefully."
        "stage_06" ->
            "Switch off, unplug, and let the outlet cool before brushing it. Never submerge the brewer."
        else -> return this
    }
    return copy(
        full = full.copy(warning = correctedWarning),
        concise = concise.copy(essentialWarning = correctedWarning),
    )
}

private fun String.replaceDigitTokens(vararg replacements: String): String {
    val matches = DIGIT_TOKEN.findAll(this).toList()
    require(matches.size == replacements.size) {
        "Factual erratum expected ${replacements.size} numeric tokens but found ${matches.size}"
    }
    return matches.indices.reversed().fold(this) { corrected, index ->
        corrected.replaceRange(matches[index].range, replacements[index])
    }
}

private val CLEVER_WATER_FIRST_RECIPE_ID = BuiltInRecipeId("clever_water_first_15_250")
private val CEZVE_SINGLE_RISE_RECIPE_ID = BuiltInRecipeId("cezve_turkish_single_rise_6_65")
private val CEZVE_REPEATED_RISE_RECIPE_ID = BuiltInRecipeId("cezve_bounded_repeated_rise_12_130")
private val CUP_ONE_RECIPE_ID = BuiltInRecipeId("auto_cupone_20_300")
private val DIGIT_TOKEN = Regex("\\d+")
