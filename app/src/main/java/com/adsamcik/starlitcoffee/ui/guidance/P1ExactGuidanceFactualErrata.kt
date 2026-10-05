package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId

internal fun P1ExactRecipeGuidance.applyVerifiedFactualErrata(): P1ExactRecipeGuidance = when (recipeId) {
    CHEMEX_RECIPE_ID -> copy(
        recipeApproach = "App-authored six-cup 42 g / 700 g starting recipe with bonded paper.",
        evidenceStatus = "App-authored starting recipe",
        confidence = "medium; handling is sourced, recipe timing and taste are unvalidated",
        originalSourceOrProvenance =
            "CHEMEX FAQ supports filter fit, airflow and handling. The dose, pours, temperature and time " +
                "are app choices, not a verified CHEMEX or creator recipe.",
    )
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
        CHEMEX_RECIPE_ID -> applyChemexHandlingErrata()
        else -> this
    }
}

/**
 * Reviewed six-cup handling, 2026-10-04:
 * https://chemexcoffeemaker.com/pages/faq
 * https://bluebottlecoffee.com/us/eng/brew-guides/chemex
 * These sources do not validate the app's 42/700 quantities or time range.
 * The historical manifest, stage identities and executable targets are retained.
 * Production exact guidance remains gated to technically reviewed English.
 */
private fun P1ExactStageGuidance.applyChemexHandlingErrata(): P1ExactStageGuidance = when (sourceStageId) {
    "stage_02" -> {
        val instruction = "Rinse the paper and discard all rinse water. Add 42 g of ground coffee, " +
            "gently level the bed and zero the scale before the first brewing pour."
        val completion = "Paper is wet, rinse water is discarded, and 42 g of ground coffee is ready on a zeroed scale."
        copy(
            completionCriterion = completion,
            full = full.copy(imperativeInstruction = instruction, observableCompletionCue = completion),
            concise = concise.copy(currentAction = instruction, completionCue = completion),
            focused = focused.copy(actionLabel = instruction),
        )
    }
    "stage_06" -> {
        val completion = "Standing water above the bed is gone and continuous flow has become occasional drips."
        copy(
            completionCriterion = completion,
            observableSigns = completion,
            full = full.copy(
                conciseExplanation = "Observe the drainage; the recipe time range is a starting reference, not a completion signal.",
                observableCompletionCue = completion,
                optionalPracticalTip = "You do not need to wait for every last drip. Remove the wet filter carefully.",
                accessibleAltText = "Chemex with the spout air channel open, no standing water above the coffee bed, " +
                    "and occasional drips into the carafe.",
            ),
            concise = concise.copy(completionCue = completion),
        )
    }
    else -> this
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
private val CHEMEX_RECIPE_ID = BuiltInRecipeId("chemex_42_700")
private val DIGIT_TOKEN = Regex("\\d+")
