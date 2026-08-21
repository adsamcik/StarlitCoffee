package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode

/**
 * Audited translation coverage for exact P1 guidance.
 *
 * The packaged research manifest is canonical English source copy, not an
 * Android localization catalogue. Production therefore remains empty until a
 * recipe has complete, released copy for every locale declared by the app.
 */
data class P1ExactRecipeLocalizationCoverage(
    val supportedLocaleTags: Set<String>,
    val coveredLocaleTagsByRecipe: Map<BuiltInRecipeId, Set<String>>,
) {
    init {
        require(supportedLocaleTags == supportedAppLocaleTags) {
            "Exact guidance must cover every locale declared by the app"
        }
        require(supportedLocaleTags.isNotEmpty()) { "Supported locales cannot be empty" }
        require(supportedLocaleTags.all(LOCALE_TAG_PATTERN::matches)) {
            "Supported locales must use normalized language tags"
        }
        require(
            coveredLocaleTagsByRecipe.values.flatten()
                .all(LOCALE_TAG_PATTERN::matches),
        ) {
            "Exact-guidance locale coverage must use normalized language tags"
        }
        require(
            coveredLocaleTagsByRecipe.values
                .all { covered -> covered.all(supportedLocaleTags::contains) },
        ) {
            "Exact-guidance locale coverage cannot name an unsupported locale"
        }
    }

    fun isComplete(
        recipeId: BuiltInRecipeId,
        localeTag: String,
    ): Boolean = localeTag in supportedLocaleTags &&
        coveredLocaleTagsByRecipe[recipeId]?.contains(localeTag) == true

    companion object {
        val supportedAppLocaleTags: Set<String> =
            P1ExactGuidanceLocaleResolver.supportedLanguageTags

        /** Every packaged app locale has complete, released exact guidance. */
        val production: P1ExactRecipeLocalizationCoverage =
            P1ExactRecipeLocalizationCoverage(
                supportedLocaleTags = supportedAppLocaleTags,
                coveredLocaleTagsByRecipe = BuiltInP1RecipeCatalog.recipes.associate { recipe ->
                    recipe.id to supportedAppLocaleTags
                },
            )
    }
}

/**
 * Recipe-level release authority for exact P1 Learn, setup, and live sessions.
 *
 * Eligibility requires one canonical recipe, its exact ordered plan, its
 * exact-recipe guidance, released localization for the active locale, and
 * an approved exact illustration for every required stage. Nothing is inferred
 * from another recipe that shares the same physical brewer profile.
 */
class P1ExactRecipeReleaseGate(
    guidanceLoadResult: BuiltInP1ExactGuidanceLoadResult,
    private val instructionAssets: InstructionAssetCatalog,
    terminologyLoadResult: BuiltInP1ExactTerminologyLoadResult? = null,
    private val localizationCoverage: P1ExactRecipeLocalizationCoverage =
        P1ExactRecipeLocalizationCoverage.production,
    private val stagePlanFor: (BuiltInRecipeId) -> BrewStagePlan? =
        BuiltInP1ExactStagePlanCatalog::find,
) {
    private val loadedGuidance = guidanceLoadResult as? BuiltInP1ExactGuidanceLoadResult.Loaded
    private val guidanceCatalog = loadedGuidance?.catalog
    private val activeLocaleTag = loadedGuidance?.localeTag
    private val terminologyCatalog =
        (terminologyLoadResult as? BuiltInP1ExactTerminologyLoadResult.Loaded)?.catalog
    private val terminologyMatchesActiveLocale =
        terminologyCatalog?.localeTag == activeLocaleTag
    private val definitionsById = BuiltInP1RecipeCatalog.recipes.associateBy { recipe -> recipe.id }
    private val exactProfileIds = BuiltInP1RecipeCatalog.recipes
        .mapTo(linkedSetOf(), BuiltInP1RecipeDefinition::brewerProfileId)

    val eligibleRecipeIds: Set<BuiltInRecipeId> = BuiltInP1RecipeCatalog.recipes
        .mapNotNullTo(linkedSetOf()) { recipe ->
            recipe.id.takeIf(::isEligible)
        }

    fun isEligible(recipeId: BuiltInRecipeId): Boolean =
        isEligibleInternal(recipeId)

    private fun isEligibleInternal(recipeId: BuiltInRecipeId): Boolean {
        val definition = definitionsById[recipeId] ?: return false
        val recipeGuidance = guidanceCatalog?.findRecipe(recipeId) ?: return false
        val plan = stagePlanFor(recipeId) ?: return false
        val planStages = plan.nodes.mapNotNull { node ->
            (node as? StagePlanNode.Stage)?.definition
        }
        val stagesMatch = recipeGuidance.stages.size == planStages.size &&
            recipeGuidance.stages.zip(planStages).withIndex().all { (index, pair) ->
                val (guidance, stage) = pair
                guidance.matchesReleaseStage(recipeId, definition, stage, index)
            }

        return listOf(
            activeLocaleTag != null && localizationCoverage.isComplete(
                recipeId = recipeId,
                localeTag = activeLocaleTag,
            ),
            activeLocaleTag == "en" || terminologyMatchesActiveLocale,
            plan.id.value == "builtin_recipe_${recipeId.value}",
            planStages.size == plan.nodes.size,
            planStages.size == definition.orderedStageCount,
            recipeGuidance.matchesDefinition(definition),
            stagesMatch,
        ).all { requirement -> requirement }
    }

    private fun P1ExactRecipeGuidance.matchesDefinition(
        definition: BuiltInP1RecipeDefinition,
    ): Boolean = listOf(
        recipeId == definition.id,
        methodFamilyId == definition.methodFamilyId,
        brewerProfileId == definition.brewerProfileId,
    ).all { identityMatches -> identityMatches }

    private fun P1ExactStageGuidance.matchesReleaseStage(
        recipeId: BuiltInRecipeId,
        definition: BuiltInP1RecipeDefinition,
        stage: BrewStageDefinition,
        index: Int,
    ): Boolean = listOf(
        this.recipeId == recipeId,
        order == index + 1,
        methodFamilyId == definition.methodFamilyId,
        brewerProfileId == definition.brewerProfileId,
        stageId == stage.id,
        contentId == stage.contentId,
        instructionAssetId == stage.instructionAssetId,
        !stage.requiresIllustration || instructionAssets.isApprovedExactMatch(this),
    ).all { identityMatches -> identityMatches }

    fun guidanceFor(recipeId: BuiltInRecipeId): P1ExactRecipeGuidance? =
        if (isEligible(recipeId)) guidanceCatalog?.findRecipe(recipeId) else null

    fun catalogFor(recipeId: BuiltInRecipeId): BuiltInGuidanceCatalog? =
        if (isEligible(recipeId)) {
            guidanceCatalog?.forRecipe(
                recipeId = recipeId,
                terminologyCatalog = terminologyCatalog,
            )
        } else {
            null
        }

    fun terminologyUiCopyFor(recipeId: BuiltInRecipeId): BrewingTerminologyUiCopy? =
        terminologyCatalog?.uiCopy?.takeIf {
            isEligible(recipeId) &&
                activeLocaleTag != "en" &&
                terminologyCatalog.hasDistinctEnglishReferences
        }

    /**
     * Only exact-P1 identities are gated. Recipe-less legacy sessions and
     * unrelated built-ins remain resumable.
     */
    fun shouldGatePersistedSession(
        rawRecipeId: String?,
        rawBrewerProfileId: String,
    ): Boolean {
        if (rawRecipeId == null) return false
        val recipeId = runCatching { BuiltInRecipeId(rawRecipeId) }.getOrNull()
        if (recipeId != null && recipeId in definitionsById) return !isEligible(recipeId)
        val profileId = runCatching { BrewerProfileId(rawBrewerProfileId) }.getOrNull()
        return profileId in exactProfileIds
    }

    private fun InstructionAssetCatalog.isApprovedExactMatch(
        guidance: P1ExactStageGuidance,
    ): Boolean {
        val asset = find(guidance.instructionAssetId) ?: return false
        val safetyReviewMatches =
            guidance.visualPriority != P1ExactVisualPriority.SAFETY_CRITICAL ||
                asset.safetySensitive
        return listOf(
            asset.review.isApproved,
            asset.mandatoryForFullGuidance,
            asset.namingConvention == InstructionAssetNamingConvention.EXACT_CONTENT_ID,
            asset.id == guidance.instructionAssetId,
            asset.familyId == guidance.methodFamilyId,
            asset.profileId == guidance.brewerProfileId,
            asset.stageId == guidance.stageId,
            asset.contentId == guidance.contentId,
            safetyReviewMatches,
        ).all { propertyMatches -> propertyMatches }
    }
}

private val LOCALE_TAG_PATTERN = Regex("[a-z]{2,3}(?:-[A-Z]{2})?")
