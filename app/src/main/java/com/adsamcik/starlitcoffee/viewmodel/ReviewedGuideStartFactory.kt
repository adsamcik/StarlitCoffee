package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuide
import com.adsamcik.starlitcoffee.data.brewing.guides.number
import com.adsamcik.starlitcoffee.data.brewing.guides.stagePlan
import com.adsamcik.starlitcoffee.data.brewing.guides.text
import com.adsamcik.starlitcoffee.data.brewing.session.BrewLogPresentationContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.data.brewing.session.SessionExecutionContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RecipeSourceMetadataSnapshotV1
import com.adsamcik.starlitcoffee.domain.brewing.session.PhysicalBrewClock
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.data.brewing.guides.familyId
import java.util.UUID

/** Equipment-fill recipes need the user's weighed dose; source volume stays volume. */
object ReviewedGuideStartFactory {
    fun create(
        guide: ReviewedMethodGuide,
        preparation: BrewUiState,
        coffeeBagId: Long?,
        measuredDoseG: Double? = null,
        measuredWaterG: Double? = null,
    ): BrewSessionStartRequest {
        guide.validate()
        val dose = guide.doseG ?: requireNotNull(measuredDoseG)
        require(dose.isFinite() && dose > 0.0)
        require(measuredWaterG == null || measuredWaterG.isFinite() && measuredWaterG > 0.0)
        val water = guide.waterG ?: measuredWaterG
        val ratio = guide.recipe.number("ratio_value")
        val plan = guide.stagePlan()
        val origin = plan.stages[guide.steps.indexOfFirst { it.id == guide.originStepId }].instanceId
        val end = plan.stages[guide.steps.indexOfFirst { it.id == guide.endStepId }].instanceId
        val duration = guide.steps.first { it.id == guide.originStepId }.durationSeconds
            ?.takeIf { guide.methodId == "cold-brew" }?.let { (it * 1_000.0).toLong() }
        val methodKey = guide.methodId.replace('-', '_').uppercase(java.util.Locale.ROOT)
        // A remembered setting for a different method cannot become this source recipe's grind.
        val compatibleGrind = preparation.method.name == methodKey &&
            (guide.methodId != "pulsar" || preparation.filterType == com.adsamcik.starlitcoffee.data.model.FilterType.PAPER)
        val hasMassRatio = ratio != null && guide.waterMl == null
        val grind = if (compatibleGrind && preparation.preparedGrind.value != null) {
            preparation.preparedGrind.displayValue
        } else guide.recipe.text("grind_description")
        val recipe = BrewRecipeSnapshotV1(
            methodFamilyId = guide.familyId.value, brewerProfileId = guide.profileId,
            builtInRecipeId = guide.id, reviewedGuide = guide,
            equipment = EquipmentConfigurationSnapshotV1(guide.profileId),
            quantities = BrewQuantitiesSnapshotV1(dose, brewWaterInputG = water,
                brewWaterInputMl = guide.waterMl, targetBeverageYieldG = guide.yieldG),
            ratioDefinition = RatioDefinitionSnapshotV1("DRY_COFFEE_DOSE", when {
                guide.yieldG != null -> "BEVERAGE_YIELD"
                guide.waterMl != null -> "BREW_WATER_VOLUME"
                else -> "BREW_WATER_INPUT"
            }),
            ratioValue = ratio, completionSemantics = guide.recipe.text("completion_signal"),
            sourceMetadata = RecipeSourceMetadataSnapshotV1("reviewed-method-guide-v1", guide.sourceSha256,
                guide.reviewedOn, guide.familyId.value, guide.profileId, guide.id,
                guide.recipe.text("provenance"), "EDITORIALLY_REVIEWED", guide.sources.map { it.id },
                guide.uncertainties, plan.stages.size),
            grinderId = preparation.selectedGrinderId, grindSetting = grind,
            isDecaf = preparation.isDecafBrew,
            outputModel = OutputModelSnapshotV1(if (guide.yieldG != null) "DIRECT_TARGET_BEVERAGE_YIELD"
                else "USER_MEASURED_OUTPUT"),
        )
        return BrewSessionStartRequest(SessionId(UUID.randomUUID().toString()), recipe, plan,
            SessionExecutionContextSnapshotV1(coffeeBagId = coffeeBagId,
                coffeeIdentityId = preparation.selectedCoffeeIdentityId,
                grindMemory = preparation.preparedGrind.value?.takeIf { compatibleGrind }?.let { value ->
                    com.adsamcik.starlitcoffee.data.brewing.session.GrindMemorySnapshotV1(
                        com.adsamcik.starlitcoffee.data.model.GrindContext(preparation.selectedGrinderId.orEmpty(),
                            methodKey, preparation.filterType?.name.orEmpty()), value, preparation.preparedGrind.source.name)
                },
                logPresentation = BrewLogPresentationContextSnapshotV1(guide.methodName, dose,
                    water ?: 0.0, if (hasMassRatio) requireNotNull(ratio) else 0.0,
                    grindLabel = grind, isDecaf = preparation.isDecafBrew, hasMassRatio = hasMassRatio)),
            physicalClock = PhysicalBrewClock(origin, reminderDurationMillis = duration, endStageId = end))
    }
}
