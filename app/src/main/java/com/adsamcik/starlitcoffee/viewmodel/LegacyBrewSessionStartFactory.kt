package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.brewing.LegacyBrewingAdapter
import com.adsamcik.starlitcoffee.data.brewing.LegacyBrewingReference
import com.adsamcik.starlitcoffee.data.brewing.session.BrewLogPresentationContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.data.brewing.session.SessionExecutionContextSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterSelectionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterStackEntrySnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RecipeTechniqueSnapshotV1
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfile
import com.adsamcik.starlitcoffee.domain.brewing.BrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.CatalogResolution
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.OutputModel
import com.adsamcik.starlitcoffee.domain.brewing.session.LegacyStagePlanFactory
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanCompileResult
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanCompiler
import com.adsamcik.starlitcoffee.domain.brewing.session.StagePlanValidationIssue
import java.util.UUID

/**
 * A lossless input boundary for starting a durable session from the existing
 * calculator flow. The normal caller uses [BrewUiState], while raw IDs are
 * explicit here so a future legacy value can be surfaced as unavailable rather
 * than silently substituted with Pulsar.
 */
data class LegacyBrewSessionStartInput(
    val state: BrewUiState,
    val selectedCoffeeBagId: Long?,
    val sourceRecipeId: Long?,
    val rawMethodId: String = state.method.name,
    val rawFilterId: String? = state.filterType?.name,
)

/** A start request is either safe to run or contains enough raw data to repair. */
sealed interface LegacyBrewSessionStartResult {
    data class Ready(
        val request: BrewSessionStartRequest,
    ) : LegacyBrewSessionStartResult

    data class Unavailable(
        val rawMethodId: String,
        val rawFilterId: String?,
        val legacyReference: LegacyBrewingReference,
        val reason: LegacyBrewSessionUnavailableReason,
    ) : LegacyBrewSessionStartResult

    data class InvalidStagePlan(
        val rawMethodId: String,
        val rawFilterId: String?,
        val issues: List<StagePlanValidationIssue>,
    ) : LegacyBrewSessionStartResult
}

enum class LegacyBrewSessionUnavailableReason {
    UNKNOWN_LEGACY_METHOD,
    MISSING_BREWER_PROFILE,
    NO_LEGACY_STAGE_PLAN,
}

/**
 * Pure adapter between [BrewUiState] and the durable-session boundary.
 *
 * It deliberately does not recalculate the state or read a selected bag: the
 * caller supplies the already-derived UI snapshot and selected bag ID. That
 * keeps the exact user-visible dose, water, ratio, grind label, filter label,
 * decaf choice, and notes immutable for this session and its eventual log.
 */
class LegacyBrewSessionStartFactory(
    private val catalog: BrewingCatalog = BuiltinBrewingCatalog.instance,
    private val newUuid: () -> UUID = UUID::randomUUID,
) {
    fun create(
        state: BrewUiState,
        selectedCoffeeBagId: Long?,
        sourceRecipeId: Long?,
    ): LegacyBrewSessionStartResult = create(
        LegacyBrewSessionStartInput(
            state = state,
            selectedCoffeeBagId = selectedCoffeeBagId,
            sourceRecipeId = sourceRecipeId,
        ),
    )

    fun create(input: LegacyBrewSessionStartInput): LegacyBrewSessionStartResult {
        val legacyReference = LegacyBrewingAdapter.fromLegacy(
            rawMethodId = input.rawMethodId,
            rawFilterId = input.rawFilterId,
        )
        val profileId = when (val resolution = legacyReference.brewerProfile) {
            is CatalogResolution.Known -> resolution.value
            is CatalogResolution.Unknown -> {
                return unavailable(
                    input = input,
                    legacyReference = legacyReference,
                    reason = LegacyBrewSessionUnavailableReason.UNKNOWN_LEGACY_METHOD,
                )
            }
        }
        val profile = catalog.findBrewerProfile(profileId)
            ?: return unavailable(
                input = input,
                legacyReference = legacyReference,
                reason = LegacyBrewSessionUnavailableReason.MISSING_BREWER_PROFILE,
            )
        val legacyMethod = BrewMethod.entries.firstOrNull { method -> method.name == input.rawMethodId }
            ?: return unavailable(
                input = input,
                legacyReference = legacyReference,
                reason = LegacyBrewSessionUnavailableReason.NO_LEGACY_STAGE_PLAN,
            )

        val compiledPlan = when (
            val compiled = StagePlanCompiler.compile(LegacyStagePlanFactory.create(legacyMethod))
        ) {
            is StagePlanCompileResult.Compiled -> compiled.value
            is StagePlanCompileResult.Invalid -> {
                return LegacyBrewSessionStartResult.InvalidStagePlan(
                    rawMethodId = input.rawMethodId,
                    rawFilterId = input.rawFilterId,
                    issues = compiled.issues,
                )
            }
        }
        val recipe = recipe(
            state = input.state,
            profile = profile,
            filterSelection = legacyReference.equipment.filterSelection,
        )
        val context = SessionExecutionContextSnapshotV1(
            coffeeBagId = input.selectedCoffeeBagId,
            sourceRecipeId = input.sourceRecipeId,
            logPresentation = BrewLogPresentationContextSnapshotV1(
                methodLabel = input.rawMethodId,
                doseG = input.state.coffeeG.toDouble(),
                waterG = input.state.waterG.toDouble(),
                ratio = input.state.effectiveRatio.toDouble(),
                grindLabel = input.state.grindLabel(),
                // Preserve the original raw label even when it was invalid for
                // the method and therefore omitted from equipment configuration.
                filterLabel = input.rawFilterId,
                isDecaf = input.state.isDecafBrew,
                notes = input.state.feedbackNotes.takeIf(String::isNotBlank),
            ),
        )
        return LegacyBrewSessionStartResult.Ready(
            BrewSessionStartRequest(
                sessionId = SessionId(newUuid().toString()),
                recipe = recipe,
                stagePlan = compiledPlan,
                executionContext = context,
            ),
        )
    }

    private fun unavailable(
        input: LegacyBrewSessionStartInput,
        legacyReference: LegacyBrewingReference,
        reason: LegacyBrewSessionUnavailableReason,
    ): LegacyBrewSessionStartResult.Unavailable = LegacyBrewSessionStartResult.Unavailable(
        rawMethodId = input.rawMethodId,
        rawFilterId = input.rawFilterId,
        legacyReference = legacyReference,
        reason = reason,
    )

    private fun recipe(
        state: BrewUiState,
        profile: BrewerProfile,
        filterSelection: FilterSelection,
    ): BrewRecipeSnapshotV1 = BrewRecipeSnapshotV1(
        methodFamilyId = profile.familyId.value,
        brewerProfileId = profile.id.value,
        equipment = EquipmentConfigurationSnapshotV1(
            brewerProfileId = profile.id.value,
            filterSelection = filterSelection.toSnapshot(),
        ),
        quantities = quantities(state, profile.outputModel),
        ratioDefinition = ratioDefinition(profile.outputModel),
        ratioValue = state.effectiveRatio.toDouble(),
        temperatureC = state.tempC.toIntOrNull(),
        grinderId = state.selectedGrinderId,
        grindSetting = state.grindLabel(),
        technique = state.technique(),
        isDecaf = state.isDecafBrew,
        notes = state.feedbackNotes.takeIf(String::isNotBlank),
        outputModel = profile.outputModel.toSnapshot(),
    )

    private fun quantities(
        state: BrewUiState,
        outputModel: OutputModel,
    ): BrewQuantitiesSnapshotV1 {
        val doseG = state.coffeeG.toDouble()
        val currentWaterG = state.waterG.toDouble()
        return when (outputModel) {
            is OutputModel.BrewWaterMinusRetention,
            is OutputModel.CollectedConcentrate,
            OutputModel.PreparedUnfilteredVolume,
            -> BrewQuantitiesSnapshotV1(
                dryCoffeeDoseG = doseG,
                brewWaterInputG = currentWaterG,
            )

            OutputModel.DirectTargetBeverageYield -> BrewQuantitiesSnapshotV1(
                dryCoffeeDoseG = doseG,
                targetBeverageYieldG = currentWaterG,
            )

            is OutputModel.ReservoirToEstimatedOutput -> BrewQuantitiesSnapshotV1(
                dryCoffeeDoseG = doseG,
                reservoirInputG = currentWaterG,
            )

            OutputModel.UserMeasuredOutput -> BrewQuantitiesSnapshotV1(
                dryCoffeeDoseG = doseG,
                measuredOutputG = currentWaterG,
            )

            OutputModel.NoMeaningfulBeverageYield -> BrewQuantitiesSnapshotV1(
                dryCoffeeDoseG = doseG,
            )
        }
    }

    private fun ratioDefinition(outputModel: OutputModel): RatioDefinitionSnapshotV1 = when (outputModel) {
        OutputModel.DirectTargetBeverageYield -> RatioDefinitionSnapshotV1(
            numerator = ROLE_DRY_COFFEE_DOSE,
            denominator = ROLE_BEVERAGE_YIELD,
        )

        is OutputModel.ReservoirToEstimatedOutput -> RatioDefinitionSnapshotV1(
            numerator = ROLE_DRY_COFFEE_DOSE,
            denominator = ROLE_RESERVOIR_INPUT,
        )

        OutputModel.UserMeasuredOutput -> RatioDefinitionSnapshotV1(
            numerator = ROLE_DRY_COFFEE_DOSE,
            denominator = ROLE_MEASURED_OUTPUT,
        )

        is OutputModel.BrewWaterMinusRetention,
        is OutputModel.CollectedConcentrate,
        OutputModel.PreparedUnfilteredVolume,
        OutputModel.NoMeaningfulBeverageYield,
        -> RatioDefinitionSnapshotV1(
            numerator = ROLE_DRY_COFFEE_DOSE,
            denominator = ROLE_BREW_WATER_INPUT,
        )
    }

    private fun FilterSelection.toSnapshot(): FilterSelectionSnapshotV1 = when (this) {
        FilterSelection.Unspecified -> FilterSelectionSnapshotV1(mode = FILTER_UNSPECIFIED)
        FilterSelection.IntentionallyUnfiltered -> {
            FilterSelectionSnapshotV1(mode = FILTER_INTENTIONALLY_UNFILTERED)
        }

        is FilterSelection.Stack -> FilterSelectionSnapshotV1(
            mode = FILTER_STACK,
            entries = entries.sortedBy { entry -> entry.position }.map { entry ->
                FilterStackEntrySnapshotV1(
                    filterProfileId = entry.filterProfileId.value,
                    position = entry.position,
                    role = entry.role.name,
                )
            },
        )
    }

    private fun OutputModel.toSnapshot(): OutputModelSnapshotV1 = when (this) {
        is OutputModel.BrewWaterMinusRetention -> OutputModelSnapshotV1(
            kind = OUTPUT_BREW_WATER_MINUS_RETENTION,
            retainedWaterGPerCoffeeG = retainedWaterGPerCoffeeG,
        )

        OutputModel.DirectTargetBeverageYield -> OutputModelSnapshotV1(
            kind = OUTPUT_DIRECT_TARGET_BEVERAGE_YIELD,
        )

        is OutputModel.CollectedConcentrate -> OutputModelSnapshotV1(
            kind = OUTPUT_COLLECTED_CONCENTRATE,
            retainedWaterGPerCoffeeG = retainedWaterGPerCoffeeG,
        )

        OutputModel.PreparedUnfilteredVolume -> OutputModelSnapshotV1(
            kind = OUTPUT_PREPARED_UNFILTERED_VOLUME,
        )

        is OutputModel.ReservoirToEstimatedOutput -> OutputModelSnapshotV1(
            kind = OUTPUT_RESERVOIR_TO_ESTIMATED_OUTPUT,
            internalRetentionG = internalRetentionG,
        )

        OutputModel.UserMeasuredOutput -> OutputModelSnapshotV1(
            kind = OUTPUT_USER_MEASURED_OUTPUT,
        )

        OutputModel.NoMeaningfulBeverageYield -> OutputModelSnapshotV1(
            kind = OUTPUT_NO_MEANINGFUL_BEVERAGE_YIELD,
        )
    }

    private fun BrewUiState.technique(): RecipeTechniqueSnapshotV1 {
        val pulseCount = effectivePulseCount.takeIf { method.hasPulses && it > 0 }
        return RecipeTechniqueSnapshotV1(
            bloomWaterG = bloomG.toDouble().takeIf { method.hasBloom },
            bloomDurationSeconds = effectiveBloomDurationSeconds.takeIf { method.hasBloom },
            pourPattern = when {
                pulseCount == null -> POUR_NONE
                pulseCount == 1 -> POUR_SINGLE
                else -> POUR_PULSED
            },
            pulseCount = pulseCount,
        )
    }

    private fun BrewUiState.grindLabel(): String = when (val result = grindResult) {
        is GrindResult.Generic -> result.descriptor.displayName
        is GrindResult.Specific -> {
            "%.1f".format(result.recommendation.rangeStart) +
                "-" +
                "%.1f".format(result.recommendation.rangeEnd)
        }
    }

    private companion object {
        const val FILTER_UNSPECIFIED = "UNSPECIFIED"
        const val FILTER_INTENTIONALLY_UNFILTERED = "INTENTIONALLY_UNFILTERED"
        const val FILTER_STACK = "STACK"

        const val ROLE_DRY_COFFEE_DOSE = "DRY_COFFEE_DOSE"
        const val ROLE_BREW_WATER_INPUT = "BREW_WATER_INPUT"
        const val ROLE_RESERVOIR_INPUT = "RESERVOIR_INPUT"
        const val ROLE_BEVERAGE_YIELD = "BEVERAGE_YIELD"
        const val ROLE_MEASURED_OUTPUT = "MEASURED_OUTPUT"

        const val OUTPUT_BREW_WATER_MINUS_RETENTION = "BREW_WATER_MINUS_RETENTION"
        const val OUTPUT_DIRECT_TARGET_BEVERAGE_YIELD = "DIRECT_TARGET_BEVERAGE_YIELD"
        const val OUTPUT_COLLECTED_CONCENTRATE = "COLLECTED_CONCENTRATE"
        const val OUTPUT_PREPARED_UNFILTERED_VOLUME = "PREPARED_UNFILTERED_VOLUME"
        const val OUTPUT_RESERVOIR_TO_ESTIMATED_OUTPUT = "RESERVOIR_TO_ESTIMATED_OUTPUT"
        const val OUTPUT_USER_MEASURED_OUTPUT = "USER_MEASURED_OUTPUT"
        const val OUTPUT_NO_MEANINGFUL_BEVERAGE_YIELD = "NO_MEANINGFUL_BEVERAGE_YIELD"

        const val POUR_NONE = "NONE"
        const val POUR_SINGLE = "SINGLE_POUR"
        const val POUR_PULSED = "PULSED"
    }
}
