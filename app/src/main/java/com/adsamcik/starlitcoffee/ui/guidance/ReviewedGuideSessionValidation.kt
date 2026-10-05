package com.adsamcik.starlitcoffee.ui.guidance

import com.adsamcik.starlitcoffee.data.brewing.guides.stagePlan
import com.adsamcik.starlitcoffee.data.brewing.guides.familyId
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition

/** A frozen reviewed procedure must match its actual plan and units, never today's library fallback. */
fun reviewedGuideMatchesSession(recipe: BrewRecipeSnapshotV1, stages: List<BrewStageDefinition>): Boolean =
    runCatching {
        val guide = requireNotNull(recipe.reviewedGuide).validate()
        val quantities = recipe.quantities
        listOf(
            recipe.builtInRecipeId == guide.id,
            recipe.brewerProfileId == guide.profileId,
            recipe.equipment.brewerProfileId == guide.profileId,
            recipe.methodFamilyId == guide.familyId.value,
            recipe.sourceMetadata?.sourceSha256 == guide.sourceSha256,
            stages == guide.stagePlan().stages.map { it.definition },
            quantities.dryCoffeeDoseG.isFinite() && quantities.dryCoffeeDoseG > 0.0,
            guide.doseG == null || quantities.dryCoffeeDoseG == guide.doseG,
            guide.waterG == null || quantities.brewWaterInputG == guide.waterG,
            quantities.brewWaterInputG == null || quantities.brewWaterInputG.isFinite() && quantities.brewWaterInputG > 0.0,
            quantities.brewWaterInputMl == guide.waterMl,
            quantities.targetBeverageYieldG == guide.yieldG,
        ).all { it }
    }.getOrDefault(false)

fun P1ExactRecipeReleaseGate.shouldGateSession(recipe: BrewRecipeSnapshotV1, stages: List<BrewStageDefinition>): Boolean =
    if (recipe.reviewedGuide != null) !reviewedGuideMatchesSession(recipe, stages)
    else shouldGatePersistedSession(recipe.builtInRecipeId, recipe.brewerProfileId)
