package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeDefinition
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.BuiltInP1ExactStagePlanCatalog
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanNode
import java.time.LocalDate

/**
 * Audited translation coverage for exact P1 guidance.
 *
 * The packaged research manifest is canonical English source copy, not proof
 * that every translation has received technical review. Eligibility is
 * evaluated for the active locale, and production coverage names only locale
 * packs that have completed that review.
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

        /** Only canonical English has completed independent technical review. */
        val production: P1ExactRecipeLocalizationCoverage =
            P1ExactRecipeLocalizationCoverage(
                supportedLocaleTags = supportedAppLocaleTags,
                coveredLocaleTagsByRecipe = BuiltInP1RecipeCatalog.recipes.associate { recipe ->
                    recipe.id to setOf("en")
                },
            )
    }
}

/** Machine-readable factual or hardware blockers that must be cleared before release. */
data class P1ExactRecipeReleaseReadiness(
    val openBlockerCodesByRecipe: Map<BuiltInRecipeId, Set<String>>,
) {
    init {
        require(openBlockerCodesByRecipe.values.flatten().all(BLOCKER_CODE_PATTERN::matches)) {
            "Exact-recipe blocker codes must use stable uppercase identifiers"
        }
    }

    fun isUnblocked(recipeId: BuiltInRecipeId): Boolean =
        openBlockerCodesByRecipe[recipeId].isNullOrEmpty()

    companion object {
        val production = P1ExactRecipeReleaseReadiness(
            openBlockerCodesByRecipe = mapOf(
                BuiltInRecipeId("v60_kasuya_4_6_20_300") to setOf("BLOCK-V60-VARIANT"),
                BuiltInRecipeId("v60_kurasu_flash_16_150_70") to setOf("BLOCK-V60-VARIANT"),
                BuiltInRecipeId("cezve_turkish_single_rise_6_65") to setOf("BLOCK-CEZVE-HARDWARE"),
                BuiltInRecipeId("cezve_bounded_repeated_rise_12_130") to setOf("BLOCK-CEZVE-HARDWARE"),
                BuiltInRecipeId("phin_screw_18_120") to setOf("BLOCK-PHIN-PRIMARY-HARDWARE-EVIDENCE"),
            ),
        )

        val allClear = P1ExactRecipeReleaseReadiness(emptyMap())
    }
}

/** Independent, pixel-level verdict bound to the exact packaged drawable bytes. */
data class P1ExactInstructionVisualReview(
    val assetId: com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId,
    val resourceSha256: String,
    val reviewer: String,
    val reviewedOn: LocalDate,
    val fullResolutionReviewed: Boolean,
    val phoneScaleReviewed: Boolean,
    val mechanicsReviewed: Boolean,
    val altTextReviewed: Boolean,
) {
    init {
        require(resourceSha256.matches(SHA256_PATTERN)) {
            "Visual-review hashes must be lowercase SHA-256 values"
        }
        require(reviewer.isNotBlank()) { "Visual reviews require an independent reviewer" }
    }

    val isApproved: Boolean
        get() = fullResolutionReviewed && phoneScaleReviewed && mechanicsReviewed && altTextReviewed
}

class P1ExactVisualReviewLedger(
    reviews: List<P1ExactInstructionVisualReview>,
) {
    private val reviewsByAssetId = reviews.associateBy(P1ExactInstructionVisualReview::assetId)

    init {
        require(reviewsByAssetId.size == reviews.size) { "Visual reviews must use unique asset IDs" }
    }

    fun approvedReviewFor(asset: InstructionAssetRecord): P1ExactInstructionVisualReview? {
        val review = reviewsByAssetId[asset.id] ?: return null
        return review.takeIf {
            it.isApproved &&
                it.resourceSha256 == asset.resourceSha256 &&
                it.reviewer != asset.review.reviewer
        }
    }

    companion object {
        /** Independent pixel verdicts, bound to the final packaged drawable hashes. */
        val production = P1ExactVisualReviewLedger(
            P1ExactIndependentVisualReviewCatalog.reviews,
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
    private val releaseReadiness: P1ExactRecipeReleaseReadiness =
        P1ExactRecipeReleaseReadiness.production,
    private val visualReviewLedger: P1ExactVisualReviewLedger =
        P1ExactVisualReviewLedger.production,
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
            releaseReadiness.isUnblocked(recipeId),
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
        val independentVisualReview = visualReviewLedger.approvedReviewFor(asset)
        return listOf(
            asset.review.isApproved,
            asset.mandatoryForFullGuidance,
            asset.namingConvention == InstructionAssetNamingConvention.EXACT_CONTENT_ID,
            asset.id == guidance.instructionAssetId,
            asset.familyId == guidance.methodFamilyId,
            asset.profileId == guidance.brewerProfileId,
            asset.stageId == guidance.stageId,
            asset.contentId == guidance.contentId,
            asset.resourceSha256 != null,
            independentVisualReview != null,
            safetyReviewMatches,
        ).all { propertyMatches -> propertyMatches }
    }
}

private val LOCALE_TAG_PATTERN = Regex("[a-z]{2,3}(?:-[A-Z]{2})?")
private val BLOCKER_CODE_PATTERN = Regex("BLOCK-[A-Z0-9-]+")
private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
