package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.data.brewing.LegacyBrewingAdapter
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.CalculatorSetupCodec
import com.adsamcik.starlitcoffee.data.model.DefaultGrinders
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltinBrewingCatalog
import com.adsamcik.starlitcoffee.domain.brewing.CatalogResolution
import com.adsamcik.starlitcoffee.domain.brewing.FilterProfileId
import com.adsamcik.starlitcoffee.domain.brewing.FilterSelection
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChemexBrewingSupportTest {
    private fun state(grinderId: String? = null) = BrewUiState(
        method = BrewMethod.CHEMEX,
        amount = "30",
        ratioPresets = BrewMethod.CHEMEX.defaultRatioPresets,
        selectedGrinderId = grinderId,
        filterType = null,
    )

    @Test
    fun `Chemex calculates dose water and bloom independently of Pulsar and V60`() {
        val result = BrewDerivation.derive(state(), null, DefaultGrinders, 0L)

        assertEquals(30f, result.coffeeG, 0.001f)
        assertEquals(480f, result.waterG, 0.001f)
        assertEquals(90f, result.bloomG, 0.001f)
        assertEquals(195f, result.pulseSizeG, 0.001f)
        assertEquals(240, result.timeTargetLowS)
        assertEquals(330, result.timeTargetHighS)
        assertEquals(GrindResult.Generic(GrindDescriptor.MEDIUM_COARSE), result.grindResult)
    }

    @Test
    fun `Encore ESP uses its Chemex manufacturer anchor while unverified grinders stay generic`() {
        val encore = BrewDerivation.derive(state("baratza-encore-esp"), null, DefaultGrinders, 0L)
        val recommendation = (encore.grindResult as GrindResult.Specific).recommendation
        assertEquals(30f, recommendation.suggestedStart, 0.001f)
        assertEquals(30f, recommendation.rangeStart, 0.001f)
        assertEquals(30f, recommendation.rangeEnd, 0.001f)
        assertEquals("CHEMEX", recommendation.methodId)

        val other = BrewDerivation.derive(state("comandante-c40"), null, DefaultGrinders, 0L)
        assertEquals(GrindResult.Generic(GrindDescriptor.MEDIUM_COARSE), other.grindResult)
    }

    @Test
    fun `Chemex session preserves its recipe equipment and manual drainage through the start boundary`() {
        val derived = BrewDerivation.derive(state(), null, DefaultGrinders, 0L)
        val input = state().copy(
            coffeeG = derived.coffeeG,
            waterG = derived.waterG,
            effectiveRatio = derived.effectiveRatio,
            bloomG = derived.bloomG,
            effectivePulseCount = derived.effectivePulseCount,
        )
        val result = LegacyBrewSessionStartFactory().create(input, 12L, null)
        val request = (result as LegacyBrewSessionStartResult.Ready).request

        assertEquals("chemex_unspecified", request.recipe.brewerProfileId)
        assertEquals("chemex_bonded_paper", request.recipe.equipment.filterSelection.entries.single().filterProfileId)
        assertEquals(30.0, request.recipe.quantities.dryCoffeeDoseG, 0.001)
        assertEquals(480.0, request.recipe.quantities.brewWaterInputG!!, 0.001)
        assertEquals("CHEMEX", request.executionContext.logPresentation.methodLabel)
        assertEquals(12L, request.executionContext.coffeeBagId)
        val stages = request.stagePlan.stages.map { it.definition }
        assertEquals(
            listOf(BrewStageAction.BLOOM, BrewStageAction.POUR, BrewStageAction.OBSERVE, BrewStageAction.SERVE),
            stages.map { it.action },
        )
        assertEquals(StageCompletionMode.Countdown(45_000L), stages.first().completionMode)
        assertTrue(stages.drop(1).all { it.completionMode == StageCompletionMode.Manual })
    }

    @Test
    fun `Chemex durable countdown uses the displayed freshness-adjusted bloom duration`() {
        val result = LegacyBrewSessionStartFactory().create(
            state().copy(coffeeG = 30f, waterG = 480f, effectiveBloomDurationSeconds = 60),
            null,
            null,
        ) as LegacyBrewSessionStartResult.Ready
        assertEquals(StageCompletionMode.Countdown(60_000L), result.request.stagePlan.stages.first().definition.completionMode)
    }

    @Test
    fun `Chemex selects bonded paper automatically and rejects stale Pulsar filter IDs`() {
        val reference = LegacyBrewingAdapter.fromLegacy("CHEMEX", FilterType.METAL_19K.name)
        assertEquals(CatalogResolution.Known(BrewerProfileId("chemex_unspecified")), reference.brewerProfile)
        val stack = reference.equipment.filterSelection as FilterSelection.Stack
        assertEquals(FilterProfileId("chemex_bonded_paper"), stack.entries.single().filterProfileId)
        assertTrue(reference.equipment.wasInvalidForMethod)
        assertNotNull(BuiltinBrewingCatalog.instance.findBrewerProfile(BrewerProfileId("manual_thick_paper_carafe")))
    }

    @Test
    fun `Chemex setups round trip with its method name and supported quantities`() {
        val setup = CalculatorSetup(ratio = 17f, grinderId = "baratza-encore-esp")
        assertEquals(setup, CalculatorSetupCodec.decode(CalculatorSetupCodec.encode(setup), BrewMethod.CHEMEX))
        assertTrue(CalculatorQuantityTarget.COFFEE.isAvailableFor(BrewMethod.CHEMEX))
        assertTrue(CalculatorQuantityTarget.WATER_IN.isAvailableFor(BrewMethod.CHEMEX))
        assertFalse(CalculatorQuantityTarget.IN_CUP.isAvailableFor(BrewMethod.CHEMEX))
    }
}
