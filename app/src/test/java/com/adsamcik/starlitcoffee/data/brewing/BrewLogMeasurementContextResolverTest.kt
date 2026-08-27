package com.adsamcik.starlitcoffee.data.brewing

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecordSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterSelectionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.FilterStackEntrySnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RecipeTechniqueSnapshotV1
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewLogMeasurementContextResolverTest {

    @Test
    fun `versioned context uses immutable snapshot quantities and stored estimate`() {
        val log = versionedLog(
            legacyDoseG = 99f,
            legacyWaterG = 999f,
            expectedOutputG = 284f,
            coffeeDoseG = 20.0,
            brewWaterInputG = 320.0,
            filterSelection = FilterSelectionSnapshotV1(
                mode = "STACK",
                entries = listOf(
                    FilterStackEntrySnapshotV1("paper", position = 1, role = "SECONDARY"),
                    FilterStackEntrySnapshotV1("metal", position = 0, role = "PRIMARY"),
                ),
            ),
        )

        val context = requireNotNull(BrewLogMeasurementContextResolver.resolve(log))

        assertEquals("v60_02|stack:metal@0:primary,paper@1:secondary", context.processKey)
        assertEquals(20f, context.plannedCoffeeG, 0f)
        assertEquals(320f, context.plannedWaterInputG, 0f)
        assertEquals(284f, requireNotNull(context.frozenExpectedOutputG), 0f)
    }

    @Test
    fun `versioned exact recipe is ineligible until its planner consumes calibration`() {
        val exactRecipeLog = versionedLog(stagePlanVariantId = "v60_single_pour_v1")

        assertNull(BrewLogMeasurementContextResolver.resolve(exactRecipeLog))
    }

    @Test
    fun `direct beverage yield semantics come from versioned model or legacy method`() {
        val exactEspresso = versionedLog(
            outputModelKind = "DIRECT_TARGET_BEVERAGE_YIELD",
            stagePlanVariantId = "espresso_standard_v1",
        )
        val legacyEspresso = BrewLogEntity(
            method = BrewMethod.ESPRESSO.displayName,
            doseG = 18f,
            waterG = 36f,
            ratio = 2f,
        )
        val legacyV60 = legacyEspresso.copy(method = BrewMethod.V60.displayName)

        assertEquals(true, BrewLogMeasurementContextResolver.isDirectBeverageYield(exactEspresso))
        assertEquals(true, BrewLogMeasurementContextResolver.isDirectBeverageYield(legacyEspresso))
        assertEquals(false, BrewLogMeasurementContextResolver.isDirectBeverageYield(legacyV60))
    }

    @Test
    fun `versioned context requires calibratable model and positive snapshot inputs`() {
        assertNull(
            BrewLogMeasurementContextResolver.resolve(
                versionedLog(outputModelKind = "DIRECT_TARGET_BEVERAGE_YIELD"),
            ),
        )
        assertNull(
            BrewLogMeasurementContextResolver.resolve(versionedLog(coffeeDoseG = 0.0)),
        )
        assertNull(
            BrewLogMeasurementContextResolver.resolve(versionedLog(brewWaterInputG = null)),
        )
    }

    @Test
    fun `legacy context and current calculator share the same process key`() {
        val log = BrewLogEntity(
            method = BrewMethod.V60.name,
            doseG = 20f,
            waterG = 320f,
            ratio = 16f,
            expectedBeverageOutputG = 278f,
        )

        val context = requireNotNull(BrewLogMeasurementContextResolver.resolve(log))

        assertEquals(
            BrewLogMeasurementContextResolver.currentProcessKey(BrewMethod.V60),
            context.processKey,
        )
        assertEquals(20f, context.plannedCoffeeG, 0f)
        assertEquals(320f, context.plannedWaterInputG, 0f)
        assertEquals(278f, requireNotNull(context.frozenExpectedOutputG), 0f)
    }

    private fun versionedLog(
        legacyDoseG: Float = 20f,
        legacyWaterG: Float = 320f,
        expectedOutputG: Float? = 278f,
        coffeeDoseG: Double = 20.0,
        brewWaterInputG: Double? = 320.0,
        outputModelKind: String = "BREW_WATER_MINUS_RETENTION",
        stagePlanVariantId: String? = null,
        filterSelection: FilterSelectionSnapshotV1 = FilterSelectionSnapshotV1(),
    ): BrewLogEntity {
        val recipe = BrewRecipeSnapshotV1(
            methodFamilyId = "manual_gravity",
            brewerProfileId = "v60_02",
            equipment = EquipmentConfigurationSnapshotV1(
                brewerProfileId = "v60_02",
                filterSelection = filterSelection,
            ),
            quantities = BrewQuantitiesSnapshotV1(
                dryCoffeeDoseG = coffeeDoseG,
                brewWaterInputG = brewWaterInputG,
            ),
            ratioDefinition = RatioDefinitionSnapshotV1(
                numerator = "DRY_COFFEE_DOSE",
                denominator = "BREW_WATER_INPUT",
            ),
            technique = RecipeTechniqueSnapshotV1(stagePlanVariantId = stagePlanVariantId),
            outputModel = OutputModelSnapshotV1(kind = outputModelKind),
        )
        return BrewingPersistenceMapper.withBrewRecordSnapshot(
            legacyFields = BrewLogEntity(
                method = BrewMethod.ESPRESSO.name,
                doseG = legacyDoseG,
                waterG = legacyWaterG,
                ratio = legacyWaterG / legacyDoseG,
                expectedBeverageOutputG = expectedOutputG,
            ),
            snapshot = BrewRecordSnapshotV1(recipe = recipe),
        )
    }
}
