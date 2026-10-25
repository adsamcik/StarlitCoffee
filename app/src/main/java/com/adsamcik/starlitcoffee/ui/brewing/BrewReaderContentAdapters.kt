package com.adsamcik.starlitcoffee.ui.brewing

import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import com.adsamcik.starlitcoffee.data.brewing.guides.contentId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.GuidanceOperationalCue
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactLearnGuide
import com.adsamcik.starlitcoffee.ui.guidance.findApprovedAssetForContent
import com.adsamcik.starlitcoffee.ui.guidance.learnFacts
import com.adsamcik.starlitcoffee.ui.guidance.safetyForStep

/** Availability describes missing sources, not permission to discard compatible supplied siblings. */
fun LearnGuidanceCatalogResolution.toBrewReaderContent(
    key: String,
    instructionAssets: InstructionAssetCatalog? = null,
    exactGuide: P1ExactLearnGuide? = null,
    reviewedGuide: ReviewedMethodGuide? = null,
): BrewReaderContent {
    val facts = exactGuide?.stageFactsByContentId ?: reviewedGuide?.learnFacts().orEmpty()
    val requiredWarnings = requiredReaderWarnings(exactGuide, reviewedGuide)
    val normalized = content.map { source ->
        val asset = instructionAssets?.findApprovedAssetForContent(source.id, source.visualContentRevision)
        val caption = asset?.let { instructionAssets?.approvedAltTextForContent(source.id,
            source.visualContentRevision, it.id, currentInstruction = source.visualInstruction) } ?: source.altText
        source.placement to source.toBrewStepContent(facts[source.id], approvedBrewStepIllustration(asset, caption))
    }
    val commonPlacements = setOf(BuiltInGuidancePlacement.GLOBAL_SAFETY, BuiltInGuidancePlacement.UTILITY)
    val suppliedSteps = normalized.filterNot { it.first in commonPlacements }
    val steps = if (facts.isEmpty()) suppliedSteps.map { it.second } else {
        val byId = suppliedSteps.associate { it.second.id to it.second }
        val extraSteps = suppliedSteps.filterNot { it.second.id in facts }
        val preparation = extraSteps.filter { it.first == BuiltInGuidancePlacement.PREPARATION }.map { it.second }
        val orderedStages = facts.map { (id, stageFacts) -> byId[id] ?: stageFacts.toBrewStepContent(id) }
        preparation + orderedStages + extraSteps.filterNot { it.first == BuiltInGuidancePlacement.PREPARATION }.map { it.second }
    }
    return BrewReaderContent(key = key,
        steps = steps.map { it.withRequiredWarning(requiredWarnings[it.id]).forUntimedReader() }.filter { it.isReadable },
        commonContent = normalized.filter { it.first in commonPlacements }
            .map { it.second.forUntimedReader() }.filter { it.isReadable },
        overview = exactGuide?.toBrewOverviewContent() ?: reviewedGuide?.toBrewOverviewContent())
}

/** Reader navigation cannot operate the live clock or advance the physical stage. */
private fun BrewStepContent.forUntimedReader(): BrewStepContent {
    val liveControls = setOf(GuidanceOperationalCue.ELAPSED_TIMER, GuidanceOperationalCue.STAGE_ADVANCE)
    return copy(controlRequirements = controlRequirements.filterNot { it in liveControls },
        utilities = utilities.filterNot { it in liveControls })
}

/** Mandatory safety follows the matched physical source, independently of teaching density or presence. */
private fun requiredReaderWarnings(
    exactGuide: P1ExactLearnGuide?,
    reviewedGuide: ReviewedMethodGuide?,
): Map<StageContentId, Pair<String, Boolean>> = exactGuide?.guidance?.stages?.mapNotNull { stage ->
    stage.concise.essentialWarning.normalizedBrewText()?.let {
        stage.contentId to (it to (stage.safetySeverity == StageSafetySeverity.CRITICAL))
    }
}?.toMap() ?: reviewedGuide?.steps?.mapNotNull { step ->
    reviewedGuide.safetyForStep(step.id).joinToString("\n").normalizedBrewText()?.let {
        reviewedGuide.contentId(step) to (it to true)
    }
}?.toMap().orEmpty()

private fun BrewStepContent.withRequiredWarning(required: Pair<String, Boolean>?): BrewStepContent {
    if (required == null) return this
    val warnings = listOfNotNull(warning, required.first).flatMap { it.lines() }
        .mapNotNull { it.normalizedBrewText() }.distinct()
    return copy(warning = warnings.joinToString("\n"), safetyCritical = safetyCritical || required.second)
}
