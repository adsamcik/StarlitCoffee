package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import com.adsamcik.starlitcoffee.data.brewing.guides.action
import com.adsamcik.starlitcoffee.data.brewing.guides.contentId
import com.adsamcik.starlitcoffee.data.brewing.guides.targetsFor
import com.adsamcik.starlitcoffee.data.brewing.guides.title
import com.adsamcik.starlitcoffee.data.brewing.guides.familyId
import com.adsamcik.starlitcoffee.data.brewing.guides.text
import kotlinx.serialization.json.jsonObject
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.StageId

/** Reviewed English text is independent of mechanical instruction-scene approval. */
fun ReviewedMethodGuide.guidanceCatalog(): BuiltInGuidanceCatalog = BuiltInGuidanceCatalog(
    steps.map { step ->
        val warning = safetyForStep(step.id).joinToString("\n").takeIf(String::isNotBlank)
        val alt = "$methodName equipment. ${step.instruction}"
        val authored = GuidancePresentationLevel.entries.associateWith { level ->
            AuthoredGuidancePresentation(
                instruction = step.instruction.takeUnless { level == GuidancePresentationLevel.UTILITIES_ONLY &&
                    !step.instruction.contains(Regex("(?i)never|avoid|safe|hot|pressure|flame|overflow")) },
                target = clockOriginText().takeIf { step.id == originStepId }, completionCue = step.completion,
                explanation = step.why.takeIf(String::isNotBlank), practicalTip = null,
                nextAction = null, controlRequirements = emptyList(), warning = warning,
                utilities = emptyList(), accessibleAltText = alt,
            )
        }
        BuiltInGuidanceContent(contentId(step), familyId, BrewerProfileId(profileId),
            StageId(contentId(step).value), BuiltInGuidancePlacement.LIVE_STAGE,
            GuidanceTextMetadata(step.instruction, explanation = step.why.takeIf(String::isNotBlank),
                warning = warning, altText = alt),
            visibility = GuidanceVisibilityPolicy(alwaysVisible = warning != null),
            safetyCritical = warning != null, authoredPresentations = authored)
    },
)

fun ReviewedMethodGuide.learnResolution(): LearnGuidanceCatalogResolution = LearnGuidanceCatalogResolution(
    policy = null, availability = LearnGuidanceCatalogAvailability.Available,
    content = guidanceCatalog().content.map { content ->
        val presentation = content.authoredPresentations.getValue(GuidancePresentationLevel.FULL)
        ResolvedLearnGuidanceContent(content.id, content.placement, presentation.instruction.orEmpty(),
            presentation.target, presentation.completionCue, presentation.explanation, null, null, emptyList(),
            presentation.warning, emptyList(), presentation.accessibleAltText, content.safetyCritical)
    },
)

fun ReviewedMethodGuide.learnFacts(): Map<com.adsamcik.starlitcoffee.domain.brewing.StageContentId, P1ExactLearnStageFacts> =
    steps.associate { step ->
        contentId(step) to P1ExactLearnStageFacts(clockOriginText().takeIf { step.id == originStepId },
            step.timePrecision.takeIf(String::isNotBlank),
            step.addedWaterMl?.let { "$it mL" }, step.cumulativeWaterMl?.let { "$it mL" }, null, null, "As specified",
            action = step.action(), referenceTargets = targetsFor(step), title = step.title())
    }

/** Explicit bindings to the reviewed V1 warning order: handling now, cleaning when finished. */
internal fun ReviewedMethodGuide.safetyForStep(stepId: String): List<String> {
    val placements = requireNotNull(SAFETY_STEPS[methodId])
    require(placements.size == safety.size) { "Safety placement needs review for $id" }
    return safety.filterIndexed { index, _ -> stepId in placements[index].split(' ') }
}

private val SAFETY_STEPS = mapOf(
    // Original/Clear upright with its standard cap; XL-only warnings remain in the source record.
    "aeropress" to listOf("prepare", "", "prepare press", "press", "", "prepare", "cleanup"),
    "automatic-drip" to listOf("prepare", "start finish serve", "start", "serve", "finish", "serve", "serve"),
    "chemex" to listOf("prepare bloom pour_1 pour_2", "rinse bloom pour_1 pour_2 drain serve", "serve", "serve", "serve"),
    "clever" to listOf("prepare", "fill_water add_coffee_start_clock", "release_and_drain remove_serve", "remove_serve", "remove_serve"),
    "cold-brew" to listOf("prepare", "combine steep filter dilute", "dilute", "filter dilute", "filter dilute"),
    "espresso" to listOf("start_shot stop_and_taste", "prepare_machine start_shot stop_and_taste", "clean", "clean"),
    "french-press" to listOf("prepare add-water", "prepare", "plunge", "prepare", "plunge decant", "decant"),
    "hario-switch" to listOf("prepare fill release", "release", "prepare", "finish", "finish", "fill"),
    "kalita-wave" to listOf("prepare", "bloom pour_one pour_two", "rinse_load drain_serve", "prepare", "drain_serve", "prepare"),
    "melitta" to listOf("setup", "bloom first_main_pour last_pour", "first_main_pour last_pour drain_serve", "drain_serve", "drain_serve"),
    "moka-pot" to listOf("heat", "fill_water", "fill_coffee", "assemble", "heat", "heat stop",
        "stop serve_cool_clean", "serve_cool_clean", "serve_cool_clean", "serve_cool_clean"),
    "percolator" to listOf("prepare", "brew", "assemble brew disconnect_serve", "disconnect_serve", "cool_clean", "cool_clean", "cool_clean"),
    "phin" to listOf("prepare", "initial_wetting main_fill", "initial_wetting main_fill serve", "coffee_and_disc observe_drainage", "serve", "serve"),
    "pulsar" to listOf("open_start_bloom open_and_pulse drain_and_serve", "prime_paper open_start_bloom open_and_pulse",
        "retained_bloom open_and_pulse", "prime_paper", "drain_and_serve"),
    "siphon" to listOf("prepare", "heat_and_transfer", "prepare", "heat_and_transfer remove_heat", "return serve", "clean_up", "clean_up"),
    "turkish" to listOf("heat", "heat", "pour", "settle"),
    "v60" to listOf("prepare bloom_pour main_pour final_pour", "bloom_pour main_pour final_pour finish", "finish", "finish"),
)

fun ReviewedMethodGuide.clockOriginText(): String = recipe.getValue("expected_time_s").jsonObject.text("clock_start")
    .let { if (methodId == "siphon") it.substringAfter("Editorial steep-clock convention: ") else it }
