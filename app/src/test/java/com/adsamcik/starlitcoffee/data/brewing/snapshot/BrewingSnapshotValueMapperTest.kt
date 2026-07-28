package com.adsamcik.starlitcoffee.data.brewing.snapshot

import com.adsamcik.starlitcoffee.domain.brewing.AccessoryProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BasketProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BrewQuantities
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.EquipmentConfiguration
import com.adsamcik.starlitcoffee.domain.brewing.FilterProfileId
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.FilterStackEntry
import com.adsamcik.starlitcoffee.domain.brewing.FilterStackRole
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.OutputModel
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewingSnapshotValueMapperTest {

    @Test
    fun `equipment mapping preserves the ordered filter stack and physical configuration`() {
        val snapshot = BrewingSnapshotValueMapper.equipment(
            EquipmentConfiguration(
                brewerProfileId = BrewerProfileId("test_brewer"),
                capacityOverrideG = 500.0,
                filterSelection = FilterSelection.Stack(
                    listOf(
                        FilterStackEntry(FilterProfileId("top_filter"), 1, FilterStackRole.TOP),
                        FilterStackEntry(FilterProfileId("primary_filter"), 0),
                    ),
                ),
                accessoryIds = setOf(
                    AccessoryProfileId("z_accessory"),
                    AccessoryProfileId("a_accessory"),
                ),
                basketId = BasketProfileId("test_basket"),
                heatSource = HeatSourceClass.ELECTRIC_MACHINE,
            ),
        )

        assertEquals("test_brewer", snapshot.brewerProfileId)
        assertEquals(500.0, snapshot.capacityOverrideG)
        assertEquals("STACK", snapshot.filterSelection.mode)
        assertEquals(
            listOf("primary_filter", "top_filter"),
            snapshot.filterSelection.entries.map { it.filterProfileId },
        )
        assertEquals(listOf("a_accessory", "z_accessory"), snapshot.accessoryIds)
        assertEquals("test_basket", snapshot.basketId)
        assertEquals("ELECTRIC_MACHINE", snapshot.heatSource)
    }

    @Test
    fun `quantity mapping keeps every semantic role separate`() {
        val snapshot = BrewingSnapshotValueMapper.quantities(
            BrewQuantities(
                dryCoffeeDoseG = 20.0,
                brewWaterInputG = 300.0,
                reservoirInputG = 320.0,
                targetBeverageYieldG = 260.0,
                targetConcentrateYieldG = 120.0,
                finalServedBeverageG = 240.0,
                iceG = 80.0,
                bypassWaterG = 40.0,
                dilutionWaterG = 120.0,
                measuredOutputG = 255.0,
            ),
        )

        assertEquals(20.0, snapshot.dryCoffeeDoseG, 0.0)
        assertEquals(300.0, snapshot.brewWaterInputG)
        assertEquals(320.0, snapshot.reservoirInputG)
        assertEquals(260.0, snapshot.targetBeverageYieldG)
        assertEquals(120.0, snapshot.targetConcentrateYieldG)
        assertEquals(240.0, snapshot.finalServedBeverageG)
        assertEquals(80.0, snapshot.iceG, 0.0)
        assertEquals(40.0, snapshot.bypassWaterG, 0.0)
        assertEquals(120.0, snapshot.dilutionWaterG, 0.0)
        assertEquals(255.0, snapshot.measuredOutputG)
    }

    @Test
    fun `output mapping covers every domain model without losing parameters`() {
        val models = listOf(
            OutputModel.BrewWaterMinusRetention(2.1),
            OutputModel.DirectTargetBeverageYield,
            OutputModel.CollectedConcentrate(0.4),
            OutputModel.PreparedUnfilteredVolume,
            OutputModel.ReservoirToEstimatedOutput(12.0),
            OutputModel.UserMeasuredOutput,
            OutputModel.NoMeaningfulBeverageYield,
        )

        val snapshots = models.map(BrewingSnapshotValueMapper::outputModel)

        assertEquals(
            listOf(
                "BREW_WATER_MINUS_RETENTION",
                "DIRECT_TARGET_BEVERAGE_YIELD",
                "COLLECTED_CONCENTRATE",
                "PREPARED_UNFILTERED_VOLUME",
                "RESERVOIR_TO_ESTIMATED_OUTPUT",
                "USER_MEASURED_OUTPUT",
                "NO_MEANINGFUL_BEVERAGE_YIELD",
            ),
            snapshots.map { it.kind },
        )
        assertEquals(2.1, snapshots[0].retainedWaterGPerCoffeeG)
        assertEquals(0.4, snapshots[2].retainedWaterGPerCoffeeG)
        assertEquals(12.0, snapshots[4].internalRetentionG)
    }
}
