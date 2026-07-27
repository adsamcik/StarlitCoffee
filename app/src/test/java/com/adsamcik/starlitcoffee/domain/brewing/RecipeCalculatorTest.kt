package com.adsamcik.starlitcoffee.domain.brewing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeCalculatorTest {

    @Test
    fun `espresso uses dry dose to beverage yield ratio`() {
        val recipe = recipe(
            outputModel = OutputModel.DirectTargetBeverageYield,
            quantities = BrewQuantities(
                dryCoffeeDoseG = 18.0,
                targetBeverageYieldG = 36.0,
            ),
            ratioDefinition = RatioDefinition(QuantityRole.DRY_COFFEE_DOSE, QuantityRole.BEVERAGE_YIELD),
        )

        val result = RecipeCalculator.calculate(recipe)

        assertEquals(36.0, requireNotNull(result.expectedBeverageYieldG), 0.001)
        assertEquals(2.0, requireNotNull(result.ratioValue), 0.001)
    }

    @Test
    fun `filter recipe separates brew input output and bypass`() {
        val recipe = recipe(
            outputModel = OutputModel.BrewWaterMinusRetention(2.0),
            quantities = BrewQuantities(
                dryCoffeeDoseG = 20.0,
                brewWaterInputG = 340.0,
                bypassWaterG = 40.0,
                iceG = 100.0,
            ),
            ratioDefinition = RatioDefinition(QuantityRole.DRY_COFFEE_DOSE, QuantityRole.BREW_WATER_INPUT),
        )

        val result = RecipeCalculator.calculate(recipe)

        assertEquals(40.0, requireNotNull(result.retainedWaterG), 0.001)
        assertEquals(300.0, requireNotNull(result.expectedBeverageYieldG), 0.001)
        assertEquals(340.0, requireNotNull(result.expectedFinalServedBeverageG), 0.001)
        assertEquals(17.0, requireNotNull(result.ratioValue), 0.001)
    }

    @Test
    fun `unfiltered preparation does not invent beverage yield`() {
        val recipe = recipe(
            outputModel = OutputModel.PreparedUnfilteredVolume,
            quantities = BrewQuantities(dryCoffeeDoseG = 8.0, brewWaterInputG = 80.0),
            ratioDefinition = RatioDefinition(QuantityRole.DRY_COFFEE_DOSE, QuantityRole.BREW_WATER_INPUT),
        )

        val result = RecipeCalculator.calculate(recipe)

        assertEquals(80.0, requireNotNull(result.expectedPreparedVolumeG), 0.001)
        assertNull(result.expectedBeverageYieldG)
    }

    private fun recipe(
        outputModel: OutputModel,
        quantities: BrewQuantities,
        ratioDefinition: RatioDefinition,
    ): BrewRecipe = BrewRecipe(
        methodFamilyId = MethodFamilyId("manual_gravity"),
        brewerProfileId = BrewerProfileId("v60_02"),
        equipment = EquipmentConfiguration(BrewerProfileId("v60_02")),
        quantities = quantities,
        ratioDefinition = ratioDefinition,
        outputModel = outputModel,
    )
}
