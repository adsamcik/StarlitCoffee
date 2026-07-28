package com.adsamcik.starlitcoffee.data.brewing.snapshot

import com.adsamcik.starlitcoffee.domain.brewing.BrewQuantities
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentConfiguration
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.OutputModel

/**
 * Pure conversion of typed brewing values into their durable V1 snapshots.
 *
 * Session orchestration should decide which values belong in a recipe; this
 * mapper owns only their stable persistence representation. Keeping that
 * boundary separate prevents a session-start flow from accumulating codec
 * details whenever the domain model grows.
 */
object BrewingSnapshotValueMapper {

    fun equipment(value: EquipmentConfiguration): EquipmentConfigurationSnapshotV1 =
        EquipmentConfigurationSnapshotV1(
            brewerProfileId = value.brewerProfileId.value,
            capacityOverrideG = value.capacityOverrideG,
            filterSelection = filterSelection(value.filterSelection),
            accessoryIds = value.accessoryIds.map { it.value }.sorted(),
            basketId = value.basketId?.value,
            heatSource = value.heatSource.name,
        )

    fun quantities(value: BrewQuantities): BrewQuantitiesSnapshotV1 = BrewQuantitiesSnapshotV1(
        dryCoffeeDoseG = value.dryCoffeeDoseG,
        brewWaterInputG = value.brewWaterInputG,
        reservoirInputG = value.reservoirInputG,
        targetBeverageYieldG = value.targetBeverageYieldG,
        targetConcentrateYieldG = value.targetConcentrateYieldG,
        finalServedBeverageG = value.finalServedBeverageG,
        iceG = value.iceG,
        bypassWaterG = value.bypassWaterG,
        dilutionWaterG = value.dilutionWaterG,
        measuredOutputG = value.measuredOutputG,
    )

    fun outputModel(value: OutputModel): OutputModelSnapshotV1 = when (value) {
        is OutputModel.BrewWaterMinusRetention -> OutputModelSnapshotV1(
            kind = OUTPUT_BREW_WATER_MINUS_RETENTION,
            retainedWaterGPerCoffeeG = value.retainedWaterGPerCoffeeG,
        )

        OutputModel.DirectTargetBeverageYield -> OutputModelSnapshotV1(
            kind = OUTPUT_DIRECT_TARGET_BEVERAGE_YIELD,
        )

        is OutputModel.CollectedConcentrate -> OutputModelSnapshotV1(
            kind = OUTPUT_COLLECTED_CONCENTRATE,
            retainedWaterGPerCoffeeG = value.retainedWaterGPerCoffeeG,
        )

        OutputModel.PreparedUnfilteredVolume -> OutputModelSnapshotV1(
            kind = OUTPUT_PREPARED_UNFILTERED_VOLUME,
        )

        is OutputModel.ReservoirToEstimatedOutput -> OutputModelSnapshotV1(
            kind = OUTPUT_RESERVOIR_TO_ESTIMATED_OUTPUT,
            internalRetentionG = value.internalRetentionG,
        )

        OutputModel.UserMeasuredOutput -> OutputModelSnapshotV1(kind = OUTPUT_USER_MEASURED_OUTPUT)
        OutputModel.NoMeaningfulBeverageYield -> {
            OutputModelSnapshotV1(kind = OUTPUT_NO_MEANINGFUL_BEVERAGE_YIELD)
        }
    }

    private fun filterSelection(value: FilterSelection): FilterSelectionSnapshotV1 = when (value) {
        FilterSelection.Unspecified -> FilterSelectionSnapshotV1(mode = FILTER_UNSPECIFIED)
        FilterSelection.IntentionallyUnfiltered -> {
            FilterSelectionSnapshotV1(mode = FILTER_INTENTIONALLY_UNFILTERED)
        }

        is FilterSelection.Stack -> FilterSelectionSnapshotV1(
            mode = FILTER_STACK,
            entries = value.entries.sortedBy { entry -> entry.position }.map { entry ->
                FilterStackEntrySnapshotV1(
                    filterProfileId = entry.filterProfileId.value,
                    position = entry.position,
                    role = entry.role.name,
                )
            },
        )
    }

    private const val FILTER_UNSPECIFIED = "UNSPECIFIED"
    private const val FILTER_INTENTIONALLY_UNFILTERED = "INTENTIONALLY_UNFILTERED"
    private const val FILTER_STACK = "STACK"

    private const val OUTPUT_BREW_WATER_MINUS_RETENTION = "BREW_WATER_MINUS_RETENTION"
    private const val OUTPUT_DIRECT_TARGET_BEVERAGE_YIELD = "DIRECT_TARGET_BEVERAGE_YIELD"
    private const val OUTPUT_COLLECTED_CONCENTRATE = "COLLECTED_CONCENTRATE"
    private const val OUTPUT_PREPARED_UNFILTERED_VOLUME = "PREPARED_UNFILTERED_VOLUME"
    private const val OUTPUT_RESERVOIR_TO_ESTIMATED_OUTPUT = "RESERVOIR_TO_ESTIMATED_OUTPUT"
    private const val OUTPUT_USER_MEASURED_OUTPUT = "USER_MEASURED_OUTPUT"
    private const val OUTPUT_NO_MEANINGFUL_BEVERAGE_YIELD = "NO_MEANINGFUL_BEVERAGE_YIELD"
}
