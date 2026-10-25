package com.adsamcik.starlitcoffee.ui.brewing

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactLearnStageFacts
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedLearnGuidanceContent
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentationMapper
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation

fun ResolvedLearnGuidanceContent.toBrewStepContent(
    facts: P1ExactLearnStageFacts? = null,
    illustration: BrewStepIllustration? = null,
): BrewStepContent {
    val base = facts?.toBrewStepContent(id) ?: BrewStepContent(id)
    return base.copy(instruction = instruction.normalizedBrewText(),
        target = target.normalizedBrewText()?.takeUnless { value -> base.facts.any { it.value == value } },
        completionCue = completionCue.normalizedBrewText(),
        operations = listOfNotNull(nextAction.normalizedBrewText()), controlRequirements = controlRequirements.distinct(),
        utilities = utilities.distinct(), warning = warning.normalizedBrewText(), safetyCritical = safetyCritical,
        details = listOf(facts?.explanation, explanation, tip).normalizedDetails(), illustration = illustration)
}

/** Physical plan facts survive independently of optional authored teaching copy. */
fun P1ExactLearnStageFacts.toBrewStepContent(id: StageContentId): BrewStepContent {
    val references = ActiveBrewSessionPresentationMapper.referenceCuePresentations(referenceTargets).toMutableList()
    temperatureTarget?.let {
        references += BrewStageReferenceCuePresentation.Temperature(it.qualifier, it.minimumC, it.maximumC)
    }
    return BrewStepContent(id = id, title = title.normalizedBrewText(), action = action,
        referenceCues = references.distinct(), facts = labelledFacts(),
        details = listOf(explanation).normalizedDetails())
}

fun ResolvedBrewGuidanceContent.toBrewStepContent(
    illustration: BrewStepIllustration? = null,
    extraDetails: List<String> = emptyList(),
    referenceCues: List<BrewStageReferenceCuePresentation> = emptyList(),
): BrewStepContent = BrewStepContent(id = id, instruction = instruction.normalizedBrewText(),
    target = target.normalizedBrewText(), completionCue = completionCue.normalizedBrewText(),
    referenceCues = referenceCues.distinct(), operations = listOfNotNull(nextAction.normalizedBrewText()),
    controlRequirements = controlRequirements.distinct(), utilities = utilities.distinct(),
    warning = warning.normalizedBrewText(), safetyCritical = safetyCritical,
    details = (listOf(explanation, tip) + extraDetails).normalizedDetails(), illustration = illustration)

private fun List<String?>.normalizedDetails(): List<String> = mapNotNull { it.normalizedBrewText() }.distinct()

private fun P1ExactLearnStageFacts.labelledFacts(): List<BrewContentFact> {
    return listOf(
        R.string.label_exact_learn_start to startCondition,
        R.string.label_exact_learn_timing to timing,
        R.string.label_exact_learn_added_water to addedWater,
        R.string.label_exact_learn_cumulative_water to cumulativeWater,
        R.string.label_exact_learn_beverage_yield to beverageYield,
        R.string.label_exact_learn_equipment_state to equipmentState,
    ).mapNotNull { (label, value) -> value.normalizedBrewText()?.let { BrewContentFact(label, it) } }.distinct()
}
