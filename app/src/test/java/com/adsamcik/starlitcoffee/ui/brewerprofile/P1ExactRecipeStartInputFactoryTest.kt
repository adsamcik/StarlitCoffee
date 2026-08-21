package com.adsamcik.starlitcoffee.ui.brewerprofile

import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInP1RecipeCatalog
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.domain.brewing.HeatSourceClass
import com.adsamcik.starlitcoffee.domain.brewing.session.HarioSwitchWorkflow
import com.adsamcik.starlitcoffee.viewmodel.CezveSessionSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class P1ExactRecipeStartInputFactoryTest {

    @Test
    fun `exact source amounts and single temperature replace calculator defaults`() {
        val selection = selection("v60_02", "v60_rao_20_330", capacityG = 400.0)
            .copy(measuredReservoirInputG = 123.0)
        val result = P1ExactRecipeStartInputFactory.create(
            selection = selection,
            metadata = P1ExactRecipeStartMetadata(
                grinderId = "grinder-1",
                isDecaf = true,
                notes = "user note",
                coffeeBagId = 42L,
            ),
        ) as P1ExactRecipeStartInputResult.Ready

        assertEquals(BuiltInRecipeId("v60_rao_20_330"), result.input.builtInRecipeId)
        assertEquals(20.0, result.input.dryCoffeeDoseG, 0.001)
        assertEquals(330.0, requireNotNull(result.input.inputWaterG), 0.001)
        assertEquals(97, result.input.temperatureC)
        assertEquals("grinder-1", result.input.grinderId)
        assertEquals(true, result.input.isDecaf)
        assertEquals("user note", result.input.notes)
        assertEquals(42L, result.input.coffeeBagId)
    }

    @Test
    fun `temperature ranges machine control and cold observation never become invented settings`() {
        val rangeInput = readyInput(selection("v60_02", "v60_official_15_250", 300.0))
        val machineInput = readyInput(
            selection("moccamaster_kbgv_select", "auto_batch_500_30", 600.0),
        )
        val coldInput = readyInput(
            selection(
                "cezve_generic",
                "cezve_turkish_single_rise_6_65",
                100.0,
                heatSource = HeatSourceClass.HOB,
                cezveSetup = CezveSessionSetup(foamRiseCycles = 1),
            ),
        )

        assertNull(rangeInput.temperatureC)
        assertNull(machineInput.temperatureC)
        assertNull(coldInput.temperatureC)
    }

    @Test
    fun `selected KBGV equipment is transferred as one indivisible configuration`() {
        val selection = selection(
            "moccamaster_kbgv_select",
            "auto_batch_500_30",
            capacityG = 600.0,
        )
        val input = readyInput(selection)

        assertSame(selection.equipmentOption.filterSelection, input.equipment.filterSelection)
        assertEquals("moccamaster_kbgv_select_cone_basket", input.equipment.basketId?.value)
        assertEquals(selection.equipmentOption.accessoryIds, input.equipment.accessoryIds)
    }

    @Test
    fun `hybrid recipe reaches exact routing without generic workflow coercion`() {
        val input = readyInput(
            selection("hario_switch", "switch_ole_boen_hybrid_16_5_240", 300.0),
        )

        assertNull(input.harioSwitchWorkflow)
        assertEquals(BuiltInRecipeId("switch_ole_boen_hybrid_16_5_240"), input.builtInRecipeId)
    }

    @Test
    fun `unresolved reservoir requires a valid measured input while missing plan fails closed`() {
        val unresolvedRecipe = requireNotNull(
            BuiltInP1RecipeCatalog.find(BuiltInRecipeId("auto_cupone_20_300")),
        )
        val unresolvedSelection = P1BrewerProfileStartSelection(
            brewerProfileId = unresolvedRecipe.brewerProfileId,
            builtInRecipeId = unresolvedRecipe.id,
            equipmentOption = unresolvedRecipe.equipmentOptions.single(),
            equipmentCapacityG = 300.0,
            harioSwitchWorkflow = null,
            cezveSetup = null,
            heatSource = HeatSourceClass.NONE,
        )

        assertSame(
            P1ExactRecipeStartInputResult.Unavailable,
            P1ExactRecipeStartInputFactory.create(unresolvedSelection),
        )
        assertSame(
            P1ExactRecipeStartInputResult.Unavailable,
            P1ExactRecipeStartInputFactory.create(
                unresolvedSelection.copy(measuredReservoirInputG = Double.NaN),
            ),
        )

        val measured = P1ExactRecipeStartInputFactory.create(
            unresolvedSelection.copy(measuredReservoirInputG = 300.0),
        ) as P1ExactRecipeStartInputResult.Ready
        assertEquals(300.0, requireNotNull(measured.input.inputWaterG), 0.001)
        assertNull(unresolvedRecipe.quantities.reservoirInputG)
        assertNull(unresolvedRecipe.ratios.single().ratioValue)

        assertSame(
            P1ExactRecipeStartInputResult.Unavailable,
            P1ExactRecipeStartInputFactory.create(
                selection("v60_02", "v60_official_15_250", 300.0),
                hasExactPlan = { false },
            ),
        )
    }

    private fun readyInput(selection: P1BrewerProfileStartSelection) =
        (P1ExactRecipeStartInputFactory.create(selection) as P1ExactRecipeStartInputResult.Ready).input

    private fun selection(
        profileId: String,
        recipeId: String,
        capacityG: Double,
        equipmentIndex: Int = 0,
        heatSource: HeatSourceClass = HeatSourceClass.NONE,
        cezveSetup: CezveSessionSetup? = null,
    ): P1BrewerProfileStartSelection {
        val recipe = requireNotNull(BuiltInP1RecipeCatalog.find(BuiltInRecipeId(recipeId)))
        return P1BrewerProfileStartSelection(
            brewerProfileId = BrewerProfileId(profileId),
            builtInRecipeId = recipe.id,
            equipmentOption = recipe.equipmentOptions[equipmentIndex],
            equipmentCapacityG = capacityG,
            harioSwitchWorkflow = when (recipe.id.value) {
                "switch_official_20_240" -> HarioSwitchWorkflow.STEEP_AND_RELEASE
                "switch_gravity_15_250" -> HarioSwitchWorkflow.MANUAL_GRAVITY
                else -> null
            },
            cezveSetup = cezveSetup,
            heatSource = heatSource,
        )
    }
}
