package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide

internal data class BrewReadAheadGuide(
    val exactGuide: P1ExactLearnGuide?,
    val resolution: LearnGuidanceCatalogResolution,
    val reviewedGuide: ReviewedMethodGuide? = null,
)

/**
 * Creates an untimed reader only for the recipe and plan actually being brewed.
 * No session owner, clock or event dispatcher can be called by this projection.
 * An edited, historical or unreviewed plan cannot silently borrow today's guide.
 */
internal fun resolveBrewReadAheadGuide(
    recipe: BrewRecipeSnapshotV1,
    stageDefinitions: List<BrewStageDefinition>,
    releaseGate: P1ExactRecipeReleaseGate,
    preferences: DurableBrewSessionGuidancePreferences,
): BrewReadAheadGuide? {
    val guide = recipe.reviewedGuide
    return if (guide != null) BrewReadAheadGuide(null, guide.learnResolution(), guide)
        .takeIf { reviewedGuideMatchesSession(recipe, stageDefinitions) }
    else resolveExactReadAheadGuide(recipe, stageDefinitions, releaseGate, preferences)
}

private fun resolveExactReadAheadGuide(
    recipe: BrewRecipeSnapshotV1,
    stageDefinitions: List<BrewStageDefinition>,
    releaseGate: P1ExactRecipeReleaseGate,
    preferences: DurableBrewSessionGuidancePreferences,
): BrewReadAheadGuide? {
    val id = recipe.builtInRecipeId?.let { runCatching { BuiltInRecipeId(it) }.getOrNull() } ?: return null
    val source = BuiltInP1RecipeCatalog.find(id) ?: return null
    val guidance = releaseGate.guidanceFor(id)
    val catalog = releaseGate.catalogFor(id)
    val plan = BuiltInP1ExactStagePlanCatalog.find(id) ?: return null
    val sourceStages = plan.nodes.mapNotNull { (it as? StagePlanNode.Stage)?.definition }
    val matches = listOf(
        guidance != null,
        catalog != null,
        sourceStages.size == plan.nodes.size,
        recipe.methodFamilyId == source.methodFamilyId.value,
        recipe.brewerProfileId == source.brewerProfileId.value,
        recipe.equipment.brewerProfileId == source.brewerProfileId.value,
        stageDefinitions == sourceStages,
        recipe.quantities.dryCoffeeDoseG == source.quantities.dryCoffeeDoseG,
        recipe.quantities.brewWaterInputG == source.quantities.brewWaterInputG,
        recipe.quantities.reservoirInputG == source.quantities.reservoirInputG,
        recipe.quantities.iceG == source.quantities.iceG,
        recipe.quantities.bypassWaterG == source.quantities.bypassWaterG,
        recipe.quantities.dilutionWaterG == source.quantities.dilutionWaterG,
        recipe.quantities.targetBeverageYieldG == source.quantities.targetBeverageYieldG,
        recipe.quantities.targetConcentrateYieldG == source.quantities.targetConcentrateYieldG,
        recipe.quantities.finalServedBeverageG == source.quantities.finalServedBeverageG,
    ).all { it }
    if (!matches) return null
    val reviewedGuidance = requireNotNull(guidance)
    val resolution = LearnGuidanceCatalogResolver(guidanceCatalogs = listOf(requireNotNull(catalog))).resolve(
        LearnGuidanceCatalogRequest(
            methodFamilyId = source.methodFamilyId.value,
            brewerProfileId = source.brewerProfileId.value,
            preferences = preferences.copy(sessionOverride = GuidancePresentationLevel.FULL),
            exactStageOrder = reviewedGuidance.stages.map { it.stageId },
        ),
    )
    return BrewReadAheadGuide(P1ExactLearnGuideFactory.create(source, reviewedGuidance, plan), resolution)
        .takeIf { resolution.availability is LearnGuidanceCatalogAvailability.Available }
}
