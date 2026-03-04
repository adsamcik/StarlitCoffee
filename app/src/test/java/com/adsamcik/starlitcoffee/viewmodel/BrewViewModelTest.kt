package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.db.dao.BrewLogDao
import com.adsamcik.starlitcoffee.data.db.dao.CoffeeBagDao
import com.adsamcik.starlitcoffee.data.db.dao.RecipeDao
import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.StrengthPreset
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrewViewModelTest {

    private lateinit var viewModel: BrewViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = BrewViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Ratio Calculation ---

    @Test
    fun `coffee to water with Pulsar default ratio`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(20f, state.coffeeG, 0.01f)
        assertEquals(340f, state.waterG, 0.01f) // 20 * 17
        assertEquals(17f, state.effectiveRatio, 0.01f)
    }

    @Test
    fun `water to coffee calculation`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.WATER_TO_COFFEE)
        viewModel.setAmount("340")

        val state = viewModel.uiState.value
        assertEquals(340f, state.waterG, 0.01f)
        assertEquals(20f, state.coffeeG, 0.01f) // 340 / 17
    }

    @Test
    fun `cup size accounts for ground absorption`() {
        viewModel.setMethod(BrewMethod.V60) // ratio 16
        viewModel.setInputMode(InputMode.CUP_SIZE_TO_BOTH)
        viewModel.setAmount("250")

        val state = viewModel.uiState.value
        // 250ml cup: coffeeG = 250 / (16 - 2) = 17.86g, waterG = 17.86 * 16 = 285.71g
        assertEquals(250f / 14f, state.coffeeG, 0.01f)
        assertEquals(250f / 14f * 16f, state.waterG, 0.01f)
    }

    @Test
    fun `cup size with Pulsar accounts for absorption`() {
        viewModel.setMethod(BrewMethod.PULSAR) // ratio 17
        viewModel.setInputMode(InputMode.CUP_SIZE_TO_BOTH)
        viewModel.setAmount("300")

        val state = viewModel.uiState.value
        // 300ml cup: coffeeG = 300 / (17 - 2) = 20g, waterG = 20 * 17 = 340g
        assertEquals(20f, state.coffeeG, 0.01f)
        assertEquals(340f, state.waterG, 0.01f)
    }

    @Test
    fun `cup size falls back for low ratio methods`() {
        viewModel.setMethod(BrewMethod.ESPRESSO) // ratio 2
        viewModel.setInputMode(InputMode.CUP_SIZE_TO_BOTH)
        viewModel.setAmount("36")

        val state = viewModel.uiState.value
        // ratio 2 ≤ absorption 2, falls back to water-based calc
        assertEquals(36f, state.waterG, 0.01f)
        assertEquals(18f, state.coffeeG, 0.01f)
    }

    // --- Strength Presets ---

    @Test
    fun `light strength increases ratio by 1`() {
        viewModel.setMethod(BrewMethod.PULSAR) // default ratio 17
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setStrengthPreset(StrengthPreset.LIGHT)
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(18f, state.effectiveRatio, 0.01f)
        assertEquals(360f, state.waterG, 0.01f) // 20 * 18
    }

    @Test
    fun `strong strength decreases ratio by 1`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setStrengthPreset(StrengthPreset.STRONG)
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(16f, state.effectiveRatio, 0.01f)
        assertEquals(320f, state.waterG, 0.01f) // 20 * 16
    }

    @Test
    fun `custom ratio overrides strength preset`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setStrengthPreset(StrengthPreset.LIGHT) // would be 18
        viewModel.setCustomRatio("16.5")
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(16.5f, state.effectiveRatio, 0.01f)
        assertEquals(330f, state.waterG, 0.01f) // 20 * 16.5
    }

    // --- Bloom Calculation ---

    @Test
    fun `bloom calculated for methods with bloom`() {
        viewModel.setMethod(BrewMethod.PULSAR) // bloom 3x
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(60f, state.bloomG, 0.01f) // 20 * 3
        assertEquals(280f, state.remainingWaterG, 0.01f) // 340 - 60
    }

    @Test
    fun `no bloom for immersion methods`() {
        viewModel.setMethod(BrewMethod.FRENCH_PRESS)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("30")

        val state = viewModel.uiState.value
        assertEquals(0f, state.bloomG, 0.01f)
    }

    @Test
    fun `custom bloom multiplier`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")
        viewModel.setBloomMultiplier("2.5")

        val state = viewModel.uiState.value
        assertEquals(50f, state.bloomG, 0.01f) // 20 * 2.5
    }

    // --- Pulse Calculation ---

    @Test
    fun `pulse size calculated correctly`() {
        viewModel.setMethod(BrewMethod.PULSAR) // 5 pulses
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(5, state.effectivePulseCount)
        assertEquals(56f, state.pulseSizeG, 0.01f) // 280 / 5
    }

    @Test
    fun `custom pulse count`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")
        viewModel.setPulseCount("4")

        val state = viewModel.uiState.value
        assertEquals(4, state.effectivePulseCount)
        assertEquals(70f, state.pulseSizeG, 0.01f) // 280 / 4
    }

    @Test
    fun `no pulses for non-pulse methods`() {
        viewModel.setMethod(BrewMethod.FRENCH_PRESS)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("30")

        val state = viewModel.uiState.value
        assertEquals(0, state.effectivePulseCount)
        assertEquals(0f, state.pulseSizeG, 0.01f)
    }

    // --- Refill Count ---

    @Test
    fun `refill needed for Pulsar over 380g water`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.WATER_TO_COFFEE)
        viewModel.setAmount("400")

        assertEquals(1, viewModel.uiState.value.refillCount)
    }

    @Test
    fun `no refill within capacity limits`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.WATER_TO_COFFEE)
        viewModel.setAmount("340")

        assertEquals(0, viewModel.uiState.value.refillCount)
    }

    @Test
    fun `no refill for unlimited methods`() {
        viewModel.setMethod(BrewMethod.V60)
        viewModel.setInputMode(InputMode.WATER_TO_COFFEE)
        viewModel.setAmount("1000")

        assertEquals(0, viewModel.uiState.value.refillCount)
    }

    @Test
    fun `multiple refills for large Pulsar dose`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("50") // 50 * 17 = 850g → ceil(850/380)-1 = 2 refills

        val state = viewModel.uiState.value
        assertEquals(850f, state.waterG, 0.01f)
        assertEquals(2, state.refillCount)
    }

    @Test
    fun `timer phases include drain phases for large dose`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("40") // 40 * 17 = 680g → needs refill

        val phases = viewModel.uiState.value.timerPhases
        val drainPhases = phases.filter { it.name.startsWith("Drain") }
        assertTrue(drainPhases.isNotEmpty())
    }

    @Test
    fun `no drain phases when within capacity`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20") // 20 * 17 = 340g → within 380g

        val phases = viewModel.uiState.value.timerPhases
        val drainPhases = phases.filter { it.name.startsWith("Drain") }
        assertTrue(drainPhases.isEmpty())
    }

    // --- Guardrail Warnings ---

    @Test
    fun `ratio warning for extremely weak ratio`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setCustomRatio("25")
        viewModel.setAmount("20")

        assertNotNull(viewModel.uiState.value.ratioWarning)
        assertTrue(viewModel.uiState.value.ratioWarning!!.contains("weak"))
    }

    @Test
    fun `ratio warning for extremely strong ratio`() {
        viewModel.setMethod(BrewMethod.V60)
        viewModel.setCustomRatio("8")
        viewModel.setAmount("20")

        assertNotNull(viewModel.uiState.value.ratioWarning)
        assertTrue(viewModel.uiState.value.ratioWarning!!.contains("strong"))
    }

    @Test
    fun `no ratio warning for espresso`() {
        viewModel.setMethod(BrewMethod.ESPRESSO)
        viewModel.setAmount("18")

        // Espresso default ratio is 1:2, which is "strong" but normal for espresso
        assertNull(viewModel.uiState.value.ratioWarning)
    }

    @Test
    fun `no ratio warning for normal range`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setAmount("20")

        assertNull(viewModel.uiState.value.ratioWarning)
    }

    @Test
    fun `bloom warning when bloom exceeds total water`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setBloomMultiplier("20") // 20 * 20 = 400g bloom
        viewModel.setAmount("20") // water = 340g

        assertNotNull(viewModel.uiState.value.bloomWarning)
    }

    @Test
    fun `no bloom warning for normal values`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")

        assertNull(viewModel.uiState.value.bloomWarning)
    }

    // --- Grind Resolution ---

    @Test
    fun `generic grind when no grinder selected`() {
        viewModel.setMethod(BrewMethod.PULSAR)

        val result = viewModel.uiState.value.grindResult
        assertTrue(result is GrindResult.Generic)
        assertEquals(
            GrindDescriptor.MEDIUM_COARSE,
            (result as GrindResult.Generic).descriptor,
        )
    }

    @Test
    fun `specific grind for ZP6 with Pulsar paper filter`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setFilterType(FilterType.PAPER)
        viewModel.setGrinder("1zpresso-zp6-special")

        val result = viewModel.uiState.value.grindResult
        assertTrue(result is GrindResult.Specific)
        val rec = (result as GrindResult.Specific).recommendation
        assertEquals(5.2f, rec.suggestedStart, 0.01f)
    }

    @Test
    fun `unknown calibration widens grind range`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setFilterType(FilterType.PAPER)
        viewModel.setGrinder("1zpresso-zp6-special")

        val baseResult = viewModel.uiState.value.grindResult as GrindResult.Specific
        val baseWidth = baseResult.recommendation.rangeEnd - baseResult.recommendation.rangeStart

        viewModel.setCalibrationStyle(CalibrationStyle.UNKNOWN)
        val widened = viewModel.uiState.value.grindResult as GrindResult.Specific
        val widenedWidth = widened.recommendation.rangeEnd - widened.recommendation.rangeStart

        assertTrue(widenedWidth > baseWidth)
    }

    // --- Timer Phases ---

    @Test
    fun `timer phases for bloom and pulse method`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")

        val phases = viewModel.uiState.value.timerPhases
        assertTrue(phases.isNotEmpty())
        assertEquals("Bloom", phases.first().name)
        assertEquals("Drawdown", phases.last().name)
        // Bloom + 5 pours + drawdown = 7 phases
        assertEquals(7, phases.size)
    }

    @Test
    fun `multi-fill phases when Pulsar exceeds capacity`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("25") // 25 * 17 = 425g > 380g capacity

        val phases = viewModel.uiState.value.timerPhases
        // Bloom(75g) + 4 pours fit (355g total) + Drain + Pour 5/5 + Drawdown = 8
        assertEquals(8, phases.size)
        assertEquals("Bloom", phases[0].name)
        assertEquals("Pour 4/5", phases[4].name)
        assertEquals("Drain & Refill", phases[5].name)
        assertEquals("Pour 5/5", phases[6].name)
        assertEquals("Drawdown", phases.last().name)
    }

    @Test
    fun `Pulsar bloom instruction includes valve timing`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")

        val bloomPhase = viewModel.uiState.value.timerPhases.first()
        assertTrue(bloomPhase.instruction.contains("wait ~10s"))
        assertTrue(bloomPhase.instruction.contains("CLOSE valve"))
    }

    @Test
    fun `timer phases for immersion method`() {
        viewModel.setMethod(BrewMethod.FRENCH_PRESS)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("30")

        val phases = viewModel.uiState.value.timerPhases
        assertTrue(phases.isNotEmpty())
        assertEquals("Pour", phases.first().name)
        assertEquals("Drawdown", phases.last().name)
    }

    // --- Method Defaults ---

    @Test
    fun `espresso uses ratio of 2`() {
        viewModel.setMethod(BrewMethod.ESPRESSO)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("18")

        val state = viewModel.uiState.value
        assertEquals(2f, state.effectiveRatio, 0.01f)
        assertEquals(36f, state.waterG, 0.01f)
    }

    @Test
    fun `cold brew uses ratio of 8`() {
        viewModel.setMethod(BrewMethod.COLD_BREW)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("100")

        val state = viewModel.uiState.value
        assertEquals(8f, state.effectiveRatio, 0.01f)
        assertEquals(800f, state.waterG, 0.01f)
    }

    // --- Edge Cases ---

    @Test
    fun `empty amount defaults to zero`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setAmount("")

        val state = viewModel.uiState.value
        assertEquals(0f, state.coffeeG, 0.01f)
        assertEquals(0f, state.waterG, 0.01f)
    }

    @Test
    fun `invalid amount text is rejected`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setAmount("20")
        viewModel.setAmount("abc") // should be rejected

        assertEquals("20", viewModel.uiState.value.amount)
    }

    @Test
    fun `reset brew clears state`() {
        viewModel.setMethod(BrewMethod.V60)
        viewModel.setAmount("30")
        viewModel.setStrengthPreset(StrengthPreset.STRONG)
        viewModel.resetBrew()

        val state = viewModel.uiState.value
        assertEquals(BrewMethod.PULSAR, state.method) // default
        assertEquals(StrengthPreset.BALANCED, state.strengthPreset)
    }

    // --- Time Target ---

    @Test
    fun `Pulsar time target is 3m30s to 4m30s`() {
        viewModel.setMethod(BrewMethod.PULSAR)

        val state = viewModel.uiState.value
        assertEquals(210, state.timeTargetLowS) // 3:30
        assertEquals(270, state.timeTargetHighS) // 4:30
    }

    // --- Save Recipe ---

    @Test
    fun `saveRecipe stores current brew state as recipe entity`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setMethod(BrewMethod.V60)
        persistenceViewModel.setAmount("20")
        persistenceViewModel.setFilterType(FilterType.PAPER)

        persistenceViewModel.saveRecipe("V60 Daily")

        val saved = persistenceViewModel.savedRecipes.value
        assertEquals(1, saved.size)
        assertEquals("V60", saved.first().method)
        assertEquals(20f, saved.first().doseG, 0.01f)
        assertEquals(320f, saved.first().waterG, 0.01f)
    }

    @Test
    fun `saveRecipe with name stores the name`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.saveRecipe("Named Recipe")

        assertEquals("Named Recipe", persistenceViewModel.savedRecipes.value.first().coffeeName)
    }

    @Test
    fun `saveRecipe without name stores null coffeeName`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.saveRecipe(null)

        assertNull(persistenceViewModel.savedRecipes.value.first().coffeeName)
    }

    @Test
    fun `saveRecipe captures grind setting for specific grinder`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setMethod(BrewMethod.PULSAR)
        persistenceViewModel.setFilterType(FilterType.PAPER)
        persistenceViewModel.setGrinder("1zpresso-zp6-special")

        persistenceViewModel.saveRecipe("Specific Grind")

        assertEquals("5.0-5.4", persistenceViewModel.savedRecipes.value.first().grindSetting)
    }

    @Test
    fun `saveRecipe captures grind setting for generic grinder`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setMethod(BrewMethod.PULSAR)
        persistenceViewModel.setGrinder(null)

        persistenceViewModel.saveRecipe("Generic Grind")

        assertEquals("Medium Coarse", persistenceViewModel.savedRecipes.value.first().grindSetting)
    }

    @Test
    fun `saveRecipe captures filter type`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setFilterType(FilterType.METAL_19K)

        persistenceViewModel.saveRecipe("Metal Recipe")

        assertEquals("METAL_19K", persistenceViewModel.savedRecipes.value.first().filterType)
    }

    @Test
    fun `saveRecipe ignores call when repository is null`() {
        viewModel.saveRecipe("No Repo")

        assertTrue(viewModel.savedRecipes.value.isEmpty())
    }

    @Test
    fun `multiple recipes can be saved`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.setAmount("18")
        persistenceViewModel.saveRecipe("A")
        persistenceViewModel.setAmount("20")
        persistenceViewModel.saveRecipe("B")
        persistenceViewModel.setAmount("22")
        persistenceViewModel.saveRecipe("C")

        assertEquals(3, persistenceViewModel.savedRecipes.value.size)
    }

    // --- Delete Recipe ---

    @Test
    fun `deleteRecipe removes recipe from list`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.saveRecipe("Delete Me")
        persistenceViewModel.saveRecipe("Keep Me")
        val toDelete = persistenceViewModel.savedRecipes.value.first()

        persistenceViewModel.deleteRecipe(toDelete)

        assertEquals(1, persistenceViewModel.savedRecipes.value.size)
        assertEquals("Keep Me", persistenceViewModel.savedRecipes.value.first().coffeeName)
    }

    @Test
    fun `deleteRecipe ignores call when repository is null`() {
        viewModel.deleteRecipe(
            SavedRecipeEntity(
                id = 1L,
                method = "PULSAR",
                ratio = 17f,
                doseG = 20f,
                waterG = 340f,
            ),
        )

        assertTrue(viewModel.savedRecipes.value.isEmpty())
    }

    // --- Load Recipe ---

    @Test
    fun `loadRecipe restores method from entity`() {
        val persistenceViewModel = createPersistenceViewModel()
        val recipe = SavedRecipeEntity(method = "V60", ratio = 16f, doseG = 20f, waterG = 320f)

        persistenceViewModel.loadRecipe(recipe)

        assertEquals(BrewMethod.V60, persistenceViewModel.uiState.value.method)
    }

    @Test
    fun `loadRecipe restores dose and calculates water`() {
        val persistenceViewModel = createPersistenceViewModel()
        val recipe = SavedRecipeEntity(method = "V60", ratio = 16f, doseG = 25f, waterG = 400f)

        persistenceViewModel.loadRecipe(recipe)

        val state = persistenceViewModel.uiState.value
        assertEquals(25f, state.coffeeG, 0.01f)
        assertEquals(400f, state.waterG, 0.01f)
    }

    @Test
    fun `loadRecipe restores filter type`() {
        val persistenceViewModel = createPersistenceViewModel()
        val recipe = SavedRecipeEntity(
            method = "PULSAR",
            ratio = 17f,
            doseG = 20f,
            waterG = 340f,
            filterType = "PAPER",
        )

        persistenceViewModel.loadRecipe(recipe)

        assertEquals(FilterType.PAPER, persistenceViewModel.uiState.value.filterType)
    }

    @Test
    fun `loadRecipe restores grinder ID`() {
        val persistenceViewModel = createPersistenceViewModel()
        val recipe = SavedRecipeEntity(
            method = "PULSAR",
            ratio = 17f,
            doseG = 20f,
            waterG = 340f,
            grinderId = "1zpresso-zp6-special",
        )

        persistenceViewModel.loadRecipe(recipe)

        assertEquals("1zpresso-zp6-special", persistenceViewModel.uiState.value.selectedGrinderId)
    }

    @Test
    fun `loadRecipe resets bloom multiplier override`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setBloomMultiplier("5.0")

        persistenceViewModel.loadRecipe(
            SavedRecipeEntity(method = "PULSAR", ratio = 17f, doseG = 20f, waterG = 340f),
        )

        assertEquals("", persistenceViewModel.uiState.value.bloomMultiplier)
    }

    @Test
    fun `loadRecipe resets pulse count override`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setPulseCount("3")

        persistenceViewModel.loadRecipe(
            SavedRecipeEntity(method = "PULSAR", ratio = 17f, doseG = 20f, waterG = 340f),
        )

        assertEquals("", persistenceViewModel.uiState.value.pulseCount)
    }

    @Test
    fun `loadRecipe resets calibration style`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setCalibrationStyle(CalibrationStyle.UNKNOWN)

        persistenceViewModel.loadRecipe(
            SavedRecipeEntity(method = "PULSAR", ratio = 17f, doseG = 20f, waterG = 340f),
        )

        assertNull(persistenceViewModel.uiState.value.calibrationStyle)
    }

    @Test
    fun `loadRecipe resets temp override`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setTempC("95")

        persistenceViewModel.loadRecipe(
            SavedRecipeEntity(method = "PULSAR", ratio = 17f, doseG = 20f, waterG = 340f),
        )

        assertEquals("", persistenceViewModel.uiState.value.tempC)
    }

    @Test
    fun `loadRecipe sets input mode to coffee to water`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setInputMode(InputMode.WATER_TO_COFFEE)

        persistenceViewModel.loadRecipe(
            SavedRecipeEntity(method = "PULSAR", ratio = 17f, doseG = 20f, waterG = 340f),
        )

        assertEquals(InputMode.COFFEE_TO_WATER, persistenceViewModel.uiState.value.inputMode)
    }

    @Test
    fun `loadRecipe with unknown method defaults to Pulsar`() {
        val persistenceViewModel = createPersistenceViewModel()
        val recipe = SavedRecipeEntity(method = "UNKNOWN", ratio = 17f, doseG = 20f, waterG = 340f)

        persistenceViewModel.loadRecipe(recipe)

        assertEquals(BrewMethod.PULSAR, persistenceViewModel.uiState.value.method)
    }

    // --- Log Brew ---

    @Test
    fun `logBrew creates brew log from current state`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setMethod(BrewMethod.V60)
        persistenceViewModel.setAmount("20")
        persistenceViewModel.setTasteFeedback(TasteFeedback.BALANCED)
        persistenceViewModel.setRating(4)

        persistenceViewModel.logBrew()

        val logs = persistenceViewModel.brewLogs.value
        assertEquals(1, logs.size)
        assertEquals("V60", logs.first().method)
        assertEquals(20f, logs.first().doseG, 0.01f)
        assertEquals(320f, logs.first().waterG, 0.01f)
        assertEquals("BALANCED", logs.first().tasteFeedback)
        assertEquals(4, logs.first().rating)
    }

    @Test
    fun `logBrew captures taste feedback`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setTasteFeedback(TasteFeedback.TOO_SOUR)

        persistenceViewModel.logBrew()

        assertEquals("TOO_SOUR", persistenceViewModel.brewLogs.value.first().tasteFeedback)
    }

    @Test
    fun `logBrew captures rating`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setRating(4)

        persistenceViewModel.logBrew()

        assertEquals(4, persistenceViewModel.brewLogs.value.first().rating)
    }

    @Test
    fun `logBrew captures freeform notes`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setFeedbackNotes("Sweet cup with cocoa finish")

        persistenceViewModel.logBrew()

        assertEquals("Sweet cup with cocoa finish", persistenceViewModel.brewLogs.value.first().freeformNotes)
    }

    @Test
    fun `logBrew captures elapsed seconds`() {
        val persistenceViewModel = createPersistenceViewModel()
        setElapsedSeconds(persistenceViewModel, 215)

        persistenceViewModel.logBrew()

        assertEquals(215, persistenceViewModel.brewLogs.value.first().brewTimeSeconds)
    }

    @Test
    fun `logBrew ignores zero rating`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.logBrew()

        assertNull(persistenceViewModel.brewLogs.value.first().rating)
    }

    @Test
    fun `logBrew ignores empty notes`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setFeedbackNotes("")

        persistenceViewModel.logBrew()

        assertNull(persistenceViewModel.brewLogs.value.first().freeformNotes)
    }

    @Test
    fun `logBrew ignores call when repository is null`() {
        viewModel.logBrew()

        assertTrue(viewModel.brewLogs.value.isEmpty())
    }

    @Test
    fun `logBrew captures filter type`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setFilterType(FilterType.METAL_40K)

        persistenceViewModel.logBrew()

        assertEquals("METAL_40K", persistenceViewModel.brewLogs.value.first().filterType)
    }

    // --- Delete Brew Log ---

    @Test
    fun `deleteBrewLog removes log from list`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.logBrew()
        val log = persistenceViewModel.brewLogs.value.first()

        persistenceViewModel.deleteBrewLog(log)

        assertTrue(persistenceViewModel.brewLogs.value.isEmpty())
    }

    @Test
    fun `deleteBrewLog ignores call when repository is null`() {
        viewModel.deleteBrewLog(BrewLogEntity(method = "PULSAR", doseG = 20f, waterG = 340f, ratio = 17f))

        assertTrue(viewModel.brewLogs.value.isEmpty())
    }

    @Test
    fun `deleteBrewLog only removes targeted log`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setMethod(BrewMethod.V60)
        persistenceViewModel.logBrew()
        persistenceViewModel.setMethod(BrewMethod.PULSAR)
        persistenceViewModel.logBrew()
        val logToDelete = persistenceViewModel.brewLogs.value.first { it.method == "V60" }

        persistenceViewModel.deleteBrewLog(logToDelete)

        val remainingLogs = persistenceViewModel.brewLogs.value
        assertEquals(1, remainingLogs.size)
        assertEquals("PULSAR", remainingLogs.first().method)
    }

    // --- Coffee Bag ---

    @Test
    fun `addCoffeeBag stores bag in coffeeBags flow`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.addCoffeeBag(name = "Kenya AA")

        val bags = persistenceViewModel.coffeeBags.value
        assertEquals(1, bags.size)
        assertEquals("Kenya AA", bags.first().name)
    }

    @Test
    fun `addCoffeeBag with all fields stores all fields`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.addCoffeeBag(
            name = "Guatemala Huehue",
            roaster = "Local Roaster",
            origin = "Guatemala",
            roastLevel = "Medium",
            processType = "Washed",
            roastDate = 1_700_000_000_000L,
            openedDate = 1_700_100_000_000L,
            barcode = "1234567890",
            weightG = 340f,
            priceAmount = 18.5f,
            priceCurrency = "USD",
            notes = "Berry and chocolate",
            photoUri = "content://photo",
            status = "OPEN",
        )

        val bag = persistenceViewModel.coffeeBags.value.first()
        assertEquals("Guatemala Huehue", bag.name)
        assertEquals("Local Roaster", bag.roaster)
        assertEquals("Guatemala", bag.origin)
        assertEquals("Medium", bag.roastLevel)
        assertEquals("Washed", bag.processType)
        assertEquals(1_700_000_000_000L, bag.roastDate)
        assertEquals(1_700_100_000_000L, bag.openedDate)
        assertEquals("1234567890", bag.barcode)
        assertEquals(340f, bag.weightG!!, 0.01f)
        assertEquals(18.5f, bag.priceAmount!!, 0.01f)
        assertEquals("USD", bag.priceCurrency)
        assertEquals("Berry and chocolate", bag.notes)
        assertEquals("content://photo", bag.photoUri)
        assertEquals("OPEN", bag.status)
    }

    @Test
    fun `deleteCoffeeBag removes bag`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(name = "Bag 1")
        persistenceViewModel.addCoffeeBag(name = "Bag 2")
        val toDelete = persistenceViewModel.coffeeBags.value.first()

        persistenceViewModel.deleteCoffeeBag(toDelete)

        assertEquals(1, persistenceViewModel.coffeeBags.value.size)
        assertEquals("Bag 2", persistenceViewModel.coffeeBags.value.first().name)
    }

    @Test
    fun `updateBagStatus changes bag status`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(name = "Status Bag")
        val bagId = persistenceViewModel.coffeeBags.value.first().id

        persistenceViewModel.updateBagStatus(bagId, "OPEN")

        assertEquals("OPEN", persistenceViewModel.coffeeBags.value.first().status)
    }

    @Test
    fun `updateBagStatus ignores unknown bag ID`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(name = "Existing")

        persistenceViewModel.updateBagStatus(9999L, "OPEN")

        assertEquals(1, persistenceViewModel.coffeeBags.value.size)
        assertEquals("SEALED", persistenceViewModel.coffeeBags.value.first().status)
    }

    @Test
    fun `addCoffeeBag ignores call when repository is null`() {
        viewModel.addCoffeeBag(name = "No Repo Bag")

        assertTrue(viewModel.coffeeBags.value.isEmpty())
    }

    @Test
    fun `multiple bags can be added`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.addCoffeeBag(name = "Bag A")
        persistenceViewModel.addCoffeeBag(name = "Bag B")
        persistenceViewModel.addCoffeeBag(name = "Bag C")

        assertEquals(3, persistenceViewModel.coffeeBags.value.size)
    }

    // --- setMethod clears recipe overrides ---

    @Test
    fun `setMethod clears stale customRatio from loaded recipe`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setAmount("20")
        viewModel.setCustomRatio("17")

        viewModel.setMethod(BrewMethod.ESPRESSO)

        val state = viewModel.uiState.value
        assertEquals(2f, state.effectiveRatio, 0.01f)
        assertEquals(40f, state.waterG, 0.01f)
    }

    // --- Backward Compatibility ---

    @Test
    fun `no-arg constructor still works`() {
        val noArgViewModel = BrewViewModel()
        noArgViewModel.setMethod(BrewMethod.V60)
        noArgViewModel.setAmount("20")
        noArgViewModel.saveRecipe("No Repo Save")
        noArgViewModel.logBrew()
        noArgViewModel.addCoffeeBag(name = "No Repo Bag")

        assertEquals(BrewMethod.V60, noArgViewModel.uiState.value.method)
        assertEquals(20f, noArgViewModel.uiState.value.coffeeG, 0.01f)
        assertTrue(noArgViewModel.savedRecipes.value.isEmpty())
        assertTrue(noArgViewModel.brewLogs.value.isEmpty())
        assertTrue(noArgViewModel.coffeeBags.value.isEmpty())
    }

    @Test
    fun `saveRecipe is no-op without repository`() {
        val noArgViewModel = BrewViewModel()

        noArgViewModel.saveRecipe("No-op Save")

        assertTrue(noArgViewModel.savedRecipes.value.isEmpty())
    }

    @Test
    fun `logBrew is no-op without repository`() {
        val noArgViewModel = BrewViewModel()

        noArgViewModel.logBrew()

        assertTrue(noArgViewModel.brewLogs.value.isEmpty())
    }

    private fun createPersistenceViewModel(): BrewViewModel {
        val recipeRepository = RecipeRepository(FakeRecipeDao())
        val brewLogRepository = BrewLogRepository(FakeBrewLogDao())
        val coffeeBagRepository = CoffeeBagRepository(FakeCoffeeBagDao())
        return BrewViewModel(
            recipeRepository = recipeRepository,
            brewLogRepository = brewLogRepository,
            coffeeBagRepository = coffeeBagRepository,
        )
    }

    private fun setElapsedSeconds(targetViewModel: BrewViewModel, elapsedSeconds: Int) {
        val uiStateField = BrewViewModel::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = uiStateField.get(targetViewModel) as MutableStateFlow<BrewUiState>
        stateFlow.value = stateFlow.value.copy(elapsedSeconds = elapsedSeconds)
    }
}

private class FakeRecipeDao : RecipeDao {
    private val recipes = mutableListOf<SavedRecipeEntity>()
    private val flow = MutableStateFlow<List<SavedRecipeEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(recipe: SavedRecipeEntity): Long {
        val id = nextId++
        recipes.add(recipe.copy(id = id))
        flow.value = recipes.toList()
        return id
    }

    override suspend fun update(recipe: SavedRecipeEntity) {
        val index = recipes.indexOfFirst { it.id == recipe.id }
        if (index >= 0) {
            recipes[index] = recipe
            flow.value = recipes.toList()
        }
    }

    override suspend fun delete(recipe: SavedRecipeEntity) {
        recipes.removeAll { it.id == recipe.id }
        flow.value = recipes.toList()
    }

    override fun getAll(): Flow<List<SavedRecipeEntity>> = flow

    override fun getById(id: Long): Flow<SavedRecipeEntity?> = flow.map { list -> list.find { it.id == id } }
}

private class FakeBrewLogDao : BrewLogDao {
    private val logs = mutableListOf<BrewLogEntity>()
    private val flow = MutableStateFlow<List<BrewLogEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(log: BrewLogEntity): Long {
        val id = nextId++
        logs.add(log.copy(id = id))
        flow.value = logs.toList()
        return id
    }

    override suspend fun delete(log: BrewLogEntity) {
        logs.removeAll { it.id == log.id }
        flow.value = logs.toList()
    }

    override fun getAll(): Flow<List<BrewLogEntity>> = flow

    override fun getByBag(bagId: Long): Flow<List<BrewLogEntity>> = flow.map { list ->
        list.filter { it.coffeeBagId == bagId }
    }

    override fun getByRecipe(recipeId: Long): Flow<List<BrewLogEntity>> = flow.map { list ->
        list.filter { it.recipeId == recipeId }
    }
}

private class FakeCoffeeBagDao : CoffeeBagDao {
    private val bags = mutableListOf<CoffeeBagEntity>()
    private val flow = MutableStateFlow<List<CoffeeBagEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(bag: CoffeeBagEntity): Long {
        val id = nextId++
        bags.add(bag.copy(id = id))
        flow.value = bags.toList()
        return id
    }

    override suspend fun update(bag: CoffeeBagEntity) {
        val index = bags.indexOfFirst { it.id == bag.id }
        if (index >= 0) {
            bags[index] = bag
            flow.value = bags.toList()
        }
    }

    override suspend fun delete(bag: CoffeeBagEntity) {
        bags.removeAll { it.id == bag.id }
        flow.value = bags.toList()
    }

    override fun getActive(): Flow<List<CoffeeBagEntity>> = flow.map { list ->
        list.filter { it.status != "FINISHED" }
    }

    override fun getAll(): Flow<List<CoffeeBagEntity>> = flow

    override fun getById(id: Long): Flow<CoffeeBagEntity?> = flow.map { list -> list.find { it.id == id } }

    override suspend fun findByBarcode(barcode: String): CoffeeBagEntity? = bags.find { it.barcode == barcode }
}

