package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.guides.contentId
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import java.util.Locale

/** One source decision shared by live content, Help and the untimed reader. */
internal data class BrewSessionContentSource(
    val resolver: DurableBrewSessionGuidanceResolver,
    val reader: BrewReadAheadGuide?,
    val terminology: BrewingTerminologyUiCopy?,
    val executionBlocked: Boolean,
    // Operational warnings belong to the source, independently of optional guide copy.
    val requiredWarnings: Map<StageContentId, List<String>> = emptyMap(),
)

internal fun resolveBrewSessionContentSource(
    recipe: BrewRecipeSnapshotV1,
    stages: List<BrewStageDefinition>,
    releaseGate: P1ExactRecipeReleaseGate,
    preferences: DurableBrewSessionGuidancePreferences,
    language: String = Locale.getDefault().language,
    instructionAssets: InstructionAssetCatalog = BuiltInInstructionAssetCatalog.catalog,
): BrewSessionContentSource {
    val exactId = recipe.builtInRecipeId?.let { runCatching { BuiltInRecipeId(it) }.getOrNull() }
        ?.takeIf { BuiltInP1RecipeCatalog.find(it) != null }
    val reviewed = recipe.reviewedGuide?.takeIf { reviewedGuideMatchesSession(recipe, stages) }
    val catalogs = when {
        reviewed != null -> listOf(reviewed.guidanceCatalog(instructionAssets))
        // A mismatched reviewed snapshot must not borrow generic or another recipe's content.
        recipe.reviewedGuide != null -> emptyList()
        exactId != null -> listOfNotNull(releaseGate.catalogFor(exactId))
        language == Locale.ENGLISH.language -> listOf(
            LegacyBuiltInGuidanceCatalog.catalog, P1BuiltInGuidanceCatalog.catalog, ColdBrewGuidanceCatalog.catalog,
        )
        else -> emptyList()
    }
    return BrewSessionContentSource(
        resolver = DurableBrewSessionGuidanceResolver(guidanceCatalogs = catalogs, instructionAssets = instructionAssets),
        reader = resolveBrewReadAheadGuide(recipe, stages, releaseGate, preferences),
        terminology = exactId?.let(releaseGate::terminologyUiCopyFor),
        executionBlocked = releaseGate.shouldGateSession(recipe, stages),
        requiredWarnings = reviewed?.steps?.associate { reviewed.contentId(it) to reviewed.safetyForStep(it.id) }.orEmpty(),
    )
}
