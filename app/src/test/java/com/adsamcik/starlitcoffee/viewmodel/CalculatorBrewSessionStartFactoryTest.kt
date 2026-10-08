package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorBrewSessionStartFactoryTest {
    private val factory = CalculatorBrewSessionStartFactory()

    @Test
    fun `every calculator method freezes the visible amounts and selected coffee`() {
        BrewMethod.entries.forEach { method ->
            val calculator = calculator(method)
            val request = requireNotNull(factory.create(calculator, preparation(method), selectedCoffeeBagId = 81L))
            assertEquals(calculator.previewDoseG.toDouble(), request.recipe.quantities.dryCoffeeDoseG, 0.0)
            assertEquals(calculator.previewDoseG.toDouble(), request.executionContext.logPresentation.doseG, 0.0)
            assertEquals(calculator.previewWaterMl.toDouble(), request.executionContext.logPresentation.waterG, 0.0)
            assertEquals(81L, request.executionContext.coffeeBagId)
            assertEquals(null, request.recipe.builtInRecipeId)
            assertEquals(null, request.executionContext.sourceRecipeId)
            val setup = requireNotNull(request.recipe.calculatorSetup)
            assertEquals(calculator.tokens, setup.tokens)
            assertEquals(calculator.ratio, setup.ratio)
            assertEquals("COFFEE", setup.quantity)
            assertEquals("selected-grinder", setup.grinderId)
            assertEquals(method.name, request.executionContext.logPresentation.methodLabel)
            assertEquals("legacy_${method.name.lowercase()}", request.stagePlan.id.value)
        }
    }

    @Test
    fun `espresso freezes beverage yield without inventing brew water`() {
        val method = BrewMethod.ESPRESSO
        val request = requireNotNull(factory.create(calculator(method), preparation(method), null))
        assertNull(request.recipe.quantities.brewWaterInputG)
        assertEquals(211.7f.toDouble(), requireNotNull(request.recipe.quantities.targetBeverageYieldG), 0.0)
    }

    @Test
    fun `cold brew freezes concentrate rather than a final diluted drink`() {
        val method = BrewMethod.COLD_BREW
        val request = requireNotNull(factory.create(calculator(method), preparation(method), null))
        assertEquals(249.3f.toDouble(), requireNotNull(request.recipe.quantities.brewWaterInputG), 0.0)
        assertEquals(211.7f.toDouble(), requireNotNull(request.recipe.quantities.targetConcentrateYieldG), 0.0)
        assertNull(request.recipe.quantities.finalServedBeverageG)
    }

    @Test
    fun `selected bloom duration belongs to the immutable plan for every bloom method`() {
        BrewMethod.entries.filter(BrewMethod::hasBloom).forEach { method ->
            val request = requireNotNull(factory.create(calculator(method), preparation(method), null))
            val bloom = request.stagePlan.stages.first().definition.completionMode as StageCompletionMode.Countdown
            assertEquals(57_000L, bloom.durationMillis)
            assertEquals(57, request.recipe.technique.bloomDurationSeconds)
        }
    }

    @Test
    fun `selected quantities and reference times are frozen without completing physical actions`() {
        val method = BrewMethod.CHEMEX
        val request = requireNotNull(factory.create(calculator(method), preparation(method).copy(
            bloomG = 47.1f, timeTargetLowS = 210, timeTargetHighS = 270), null))
        val bloom = request.stagePlan.stages.first().definition
        assertEquals(47.1f.toDouble(), bloom.referenceTargets.massTargets.single().minimumGrams, 0.0)
        assertEquals(StageMassReference.STAGE_ADDED, bloom.referenceTargets.massTargets.single().reference)
        val pour = request.stagePlan.stages.first { it.definition.action == BrewStageAction.POUR }.definition
        assertEquals(StageCompletionMode.Manual, pour.completionMode)
        assertEquals(249.3f.toDouble(), pour.referenceTargets.massTargets.single().minimumGrams, 0.0)
        assertEquals(StageMassReference.BREW_CUMULATIVE, pour.referenceTargets.massTargets.single().reference)
        assertEquals(210_000L, pour.referenceTargets.timeTargets.single().minimumMillis)
        val espresso = requireNotNull(factory.create(calculator(BrewMethod.ESPRESSO),
            preparation(BrewMethod.ESPRESSO), null))
        val pull = espresso.stagePlan.stages.single().definition
        assertEquals(StageCompletionMode.Manual, pull.completionMode)
        assertEquals(QuantityRole.BEVERAGE_YIELD, pull.referenceTargets.massTargets.single().role)
    }

    @Test
    fun `invalid or mismatched calculator input never borrows a built in recipe`() {
        val calculator = calculator(BrewMethod.CHEMEX)
        val prep = preparation(BrewMethod.CHEMEX)
        assertNull(factory.create(calculator.copy(hasValidExpression = false), prep, null))
        assertNull(factory.create(calculator.copy(previewDoseG = Float.NaN), prep, null))
        assertNull(factory.create(calculator.copy(previewWaterMl = 0f), prep, null))
        assertNull(factory.create(calculator.copy(previewBeverageG = Float.POSITIVE_INFINITY), prep, null))
        assertNull(factory.create(calculator, prep.copy(method = BrewMethod.V60), null))
        val request = requireNotNull(factory.create(calculator, prep, null))
        assertNotEquals(42.0, request.recipe.quantities.dryCoffeeDoseG)
        assertNotEquals(700.0, request.recipe.quantities.brewWaterInputG)
    }

    private fun calculator(method: BrewMethod) = CalcUiState(
        brewMethod = method,
        tokens = listOf(CalcToken.Number("15.7")),
        previewDoseG = 15.7f,
        previewWaterMl = 249.3f,
        previewBeverageG = 211.7f,
        ratio = method.defaultRatio,
        hasValidExpression = true,
    )

    private fun preparation(method: BrewMethod) = BrewUiState(
        method = method,
        coffeeG = 15f,
        waterG = 250f,
        predictedCupVolumeG = 210f,
        filterType = FilterType.PAPER.takeIf { method == BrewMethod.PULSAR },
        selectedGrinderId = "selected-grinder",
        effectiveBloomDurationSeconds = 57,
    )
}
