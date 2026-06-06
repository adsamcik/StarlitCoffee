package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.testutil.FakeBrewLogDao
import com.adsamcik.starlitcoffee.testutil.FakeCoffeeBagDao
import com.adsamcik.starlitcoffee.testutil.FakeFlavorTagDao
import com.adsamcik.starlitcoffee.testutil.FakeRecipeDao
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalibrationStyle
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrindDescriptor
import com.adsamcik.starlitcoffee.data.model.InputMode
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import com.adsamcik.starlitcoffee.data.repository.BrewLogRepository
import com.adsamcik.starlitcoffee.data.repository.CoffeeBagRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.repository.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `brew size accounts for ground absorption`() {
        viewModel.setMethod(BrewMethod.V60) // ratio 16
        viewModel.setInputMode(InputMode.BREW_SIZE_TO_BOTH)
        viewModel.setAmount("250")

        val state = viewModel.uiState.value
        // 250ml brew: coffeeG = 250 / (16 - 2) = 17.86g, waterG = 17.86 * 16 = 285.71g
        assertEquals(250f / 14f, state.coffeeG, 0.01f)
        assertEquals(250f / 14f * 16f, state.waterG, 0.01f)
    }

    @Test
    fun `brew size with Pulsar accounts for absorption`() {
        viewModel.setMethod(BrewMethod.PULSAR) // ratio 17
        viewModel.setInputMode(InputMode.BREW_SIZE_TO_BOTH)
        viewModel.setAmount("300")

        val state = viewModel.uiState.value
        // 300ml brew: coffeeG = 300 / (17 - 2) = 20g, waterG = 20 * 17 = 340g
        assertEquals(20f, state.coffeeG, 0.01f)
        assertEquals(340f, state.waterG, 0.01f)
    }

    @Test
    fun `brew size falls back for low ratio methods`() {
        viewModel.setMethod(BrewMethod.ESPRESSO) // ratio 2
        viewModel.setInputMode(InputMode.BREW_SIZE_TO_BOTH)
        viewModel.setAmount("36")

        val state = viewModel.uiState.value
        // ratio 2 ≤ absorption 2, falls back to water-based calc
        assertEquals(36f, state.waterG, 0.01f)
        assertEquals(18f, state.coffeeG, 0.01f)
    }

    // --- Ratio Presets ---

    @Test
    fun `selecting ratio preset changes effective ratio`() {
        viewModel.setMethod(BrewMethod.PULSAR) // presets: 16(Bright), 17(Balanced,default), 18(Rich)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.selectRatioPreset(2) // 1:18 Rich
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(18f, state.effectiveRatio, 0.01f)
        assertEquals(360f, state.waterG, 0.01f) // 20 * 18
    }

    @Test
    fun `selecting lower ratio preset gives stronger brew`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.selectRatioPreset(0) // 1:16 Bright
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(16f, state.effectiveRatio, 0.01f)
        assertEquals(320f, state.waterG, 0.01f) // 20 * 16
    }

    @Test
    fun `custom ratio overrides selected preset`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.selectRatioPreset(2) // would be 18 Rich
        viewModel.setCustomRatio("16.5")
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(16.5f, state.effectiveRatio, 0.01f)
        assertEquals(330f, state.waterG, 0.01f) // 20 * 16.5
    }

    @Test
    fun `switching coffee to water mode converts using custom ratio not preset`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")
        viewModel.setCustomRatio("20") // differs from Pulsar default preset (17)

        viewModel.setInputMode(InputMode.WATER_TO_COFFEE)

        // 20 g coffee * custom ratio 20 = 400 ml (regression: was 20 * 17 = 340)
        assertEquals("400", viewModel.uiState.value.amount)
    }

    @Test
    fun `switching back to coffee mode converts using custom ratio not preset`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setInputMode(InputMode.WATER_TO_COFFEE)
        viewModel.setAmount("400")
        viewModel.setCustomRatio("20") // differs from Pulsar default preset (17)

        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)

        // 400 ml / custom ratio 20 = 20 g (regression: was 400 / 17 ≈ 24)
        assertEquals("20", viewModel.uiState.value.amount)
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

    @Test
    fun `V60 keeps bloom and pulse behavior without Pulsar capacity refills`() {
        viewModel.setMethod(BrewMethod.V60)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(16f, state.effectiveRatio, 0.01f)
        assertEquals(320f, state.waterG, 0.01f)
        assertEquals(50f, state.bloomG, 0.01f)
        assertEquals(270f, state.remainingWaterG, 0.01f)
        assertEquals(4, state.effectivePulseCount)
        assertEquals(67.5f, state.pulseSizeG, 0.01f)
        assertEquals(0, state.refillCount)
    }

    @Test
    fun `French Press has no bloom or pulses and keeps steep target`() {
        viewModel.setMethod(BrewMethod.FRENCH_PRESS)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("30")

        val state = viewModel.uiState.value
        assertEquals(15f, state.effectiveRatio, 0.01f)
        assertEquals(450f, state.waterG, 0.01f)
        assertEquals(0f, state.bloomG, 0.01f)
        assertEquals(0, state.effectivePulseCount)
        assertEquals(240, state.timeTargetLowS)
        assertEquals(240, state.timeTargetHighS)
    }

    @Test
    fun `AeroPress has no bloom or pulses and reports chamber refills over capacity`() {
        viewModel.setMethod(BrewMethod.AEROPRESS)
        viewModel.setInputMode(InputMode.WATER_TO_COFFEE)
        viewModel.setAmount("300")

        val state = viewModel.uiState.value
        assertEquals(20f, state.coffeeG, 0.01f)
        assertEquals(300f, state.waterG, 0.01f)
        assertEquals(0f, state.bloomG, 0.01f)
        assertEquals(0, state.effectivePulseCount)
        assertEquals(1, state.refillCount)
        assertEquals(90, state.timeTargetLowS)
        assertEquals(150, state.timeTargetHighS)
    }

    @Test
    fun `Espresso has beverage yield semantics and no bloom or pulses`() {
        viewModel.setMethod(BrewMethod.ESPRESSO)
        viewModel.setInputMode(InputMode.BREW_SIZE_TO_BOTH)
        viewModel.setAmount("36")

        val state = viewModel.uiState.value
        assertEquals(18f, state.coffeeG, 0.01f)
        assertEquals(36f, state.waterG, 0.01f)
        assertEquals(36f, state.predictedCupVolumeG, 0.01f)
        assertEquals(0f, state.retainedWaterG, 0.01f)
        assertEquals(0f, state.bloomG, 0.01f)
        assertEquals(0, state.effectivePulseCount)
    }

    @Test
    fun `Moka Pot has no bloom or pulses and accepts classic ratios`() {
        viewModel.setMethod(BrewMethod.MOKA_POT)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("20")

        val state = viewModel.uiState.value
        assertEquals(10f, state.effectiveRatio, 0.01f)
        assertEquals(200f, state.waterG, 0.01f)
        assertEquals(0f, state.bloomG, 0.01f)
        assertEquals(0, state.effectivePulseCount)
        assertEquals(240, state.timeTargetLowS)
        assertEquals(300, state.timeTargetHighS)
        assertNull(state.ratioWarning)

        viewModel.setCustomRatio("8")
        assertNull(viewModel.uiState.value.ratioWarning)
    }

    @Test
    fun `Cold Brew has passive long duration profile without bloom or pulses`() {
        viewModel.setMethod(BrewMethod.COLD_BREW)
        viewModel.setInputMode(InputMode.COFFEE_TO_WATER)
        viewModel.setAmount("50")

        val state = viewModel.uiState.value
        assertEquals(8f, state.effectiveRatio, 0.01f)
        assertEquals(400f, state.waterG, 0.01f)
        assertEquals(0f, state.bloomG, 0.01f)
        assertEquals(0, state.effectivePulseCount)
        assertEquals(43_200, state.timeTargetLowS)
        assertEquals(86_400, state.timeTargetHighS)
        assertNull(state.ratioWarning)
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

    // --- Guardrail Warnings ---

    @Test
    fun `ratio warning for extremely weak ratio`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setCustomRatio("25")
        viewModel.setAmount("20")

        assertNotNull(viewModel.uiState.value.ratioWarning)
    }

    @Test
    fun `ratio warning for extremely strong ratio`() {
        viewModel.setMethod(BrewMethod.V60)
        viewModel.setCustomRatio("8")
        viewModel.setAmount("20")

        assertNotNull(viewModel.uiState.value.ratioWarning)
    }

    @Test
    fun `no ratio warning for espresso`() {
        viewModel.setMethod(BrewMethod.ESPRESSO)
        viewModel.setAmount("18")

        // Espresso default ratio is 1:2, which is "strong" but normal for espresso
        assertNull(viewModel.uiState.value.ratioWarning)
    }

    @Test
    fun `cold brew default ratio does not warn as strong`() {
        viewModel.setMethod(BrewMethod.COLD_BREW)
        viewModel.setAmount("50")

        assertNull(viewModel.uiState.value.ratioWarning)
    }

    @Test
    fun `moka default and strong ratios do not warn as strong`() {
        viewModel.setMethod(BrewMethod.MOKA_POT)
        viewModel.setAmount("20")
        assertNull(viewModel.uiState.value.ratioWarning)

        viewModel.setCustomRatio("8")
        assertNull(viewModel.uiState.value.ratioWarning)
    }

    @Test
    fun `decaf espresso keeps espresso timing range`() {
        viewModel.setMethod(BrewMethod.ESPRESSO)
        viewModel.setDecafBrew(true)
        viewModel.setAmount("18")

        val state = viewModel.uiState.value
        assertEquals(25, state.timeTargetLowS)
        assertEquals(35, state.timeTargetHighS)
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

    // --- Decaf Grind Adjustment ---
    //
    // Direction is COARSER (not finer). Mechanism: decaf is more brittle →
    // grinding produces more fines → fines reduce bed permeability → coarsen
    // to restore flow. See grinders.json `_meta.sources.decaf` for citations
    // (Coffee ad Astra / Gagné, Sci. Rep. 2024 fines paper, Al-Shemmeri).
    //
    // Base step per brew family (from BrewViewModel.decafCoarserStepsFor):
    //   PULSAR / V60 / MOKA_POT / ESPRESSO  → +1 (percolation: fines hurt)
    //   FRENCH_PRESS / AEROPRESS / COLD_BREW → 0 (immersion: fines OK)
    // Roast modifier: DARK / MEDIUM_DARK / ESPRESSO roast +1 (already brittle)
    // Process relief: SWISS_WATER / MOUNTAIN_WATER / CO2_SUPERCRITICAL -1
    //   (gentler on bean structure → fewer extra fines)
    // Final clamped to ≥ 0; suggested moves toward rangeEnd (coerceAtMost).

    private fun setupZp6Pulsar(vm: BrewViewModel) {
        vm.setMethod(BrewMethod.PULSAR)
        vm.setFilterType(FilterType.PAPER)
        vm.setGrinder("1zpresso-zp6-special")
    }

    private fun selectFirstBag(vm: BrewViewModel) {
        vm.selectBag(vm.coffeeBags.value.first().id)
    }

    @Test
    fun `decaf bag with no roast or process coarsens Pulsar paper by 1 step`() {
        val vm = createPersistenceViewModel()
        setupZp6Pulsar(vm)
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Plain Decaf", isDecaf = true))
        selectFirstBag(vm)

        val rec = (vm.uiState.value.grindResult as GrindResult.Specific).recommendation
        // suggested 5.2 + 1 step (0.2) = 5.4
        assertEquals(5.4f, rec.suggestedStart, 0.01f)
        assertTrue(
            "note should mention 'coarser': ${rec.adjustmentNote}",
            rec.adjustmentNote.contains("coarser"),
        )
    }

    @Test
    fun `decaf with dark roast coarsens Pulsar paper by 2 steps`() {
        val vm = createPersistenceViewModel()
        setupZp6Pulsar(vm)
        vm.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "Dark Decaf",
                isDecaf = true,
                roastLevel = "DARK",
            ),
        )
        selectFirstBag(vm)

        val rec = (vm.uiState.value.grindResult as GrindResult.Specific).recommendation
        // suggested 5.2 + (1 base + 1 roast) * 0.2 = 5.6
        assertEquals(5.6f, rec.suggestedStart, 0.01f)
        assertTrue(
            "note should say '2 steps coarser': ${rec.adjustmentNote}",
            rec.adjustmentNote.contains("2 steps coarser"),
        )
    }

    @Test
    fun `decaf Swiss Water process gives full relief on Pulsar paper`() {
        val vm = createPersistenceViewModel()
        setupZp6Pulsar(vm)
        vm.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "Swiss Decaf",
                isDecaf = true,
                decafProcess = "SWISS_WATER",
            ),
        )
        selectFirstBag(vm)

        val rec = (vm.uiState.value.grindResult as GrindResult.Specific).recommendation
        // 1 base - 1 relief = 0 → suggested unchanged
        assertEquals(5.2f, rec.suggestedStart, 0.01f)
        assertTrue(
            "note should mention 'same start' for relief case: ${rec.adjustmentNote}",
            rec.adjustmentNote.contains("same start"),
        )
        assertTrue(
            "note should mention Swiss Water shortLabel: ${rec.adjustmentNote}",
            rec.adjustmentNote.contains("Swiss Water"),
        )
    }

    @Test
    fun `decaf CO2 supercritical process gives full relief`() {
        val vm = createPersistenceViewModel()
        setupZp6Pulsar(vm)
        vm.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "CO2 Decaf",
                isDecaf = true,
                decafProcess = "CO2_SUPERCRITICAL",
            ),
        )
        selectFirstBag(vm)

        val rec = (vm.uiState.value.grindResult as GrindResult.Specific).recommendation
        assertEquals(5.2f, rec.suggestedStart, 0.01f)
    }

    @Test
    fun `decaf solvent process EA Sugarcane gets no relief`() {
        val vm = createPersistenceViewModel()
        setupZp6Pulsar(vm)
        vm.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "EA Decaf",
                isDecaf = true,
                decafProcess = "EA_SUGARCANE",
            ),
        )
        selectFirstBag(vm)

        val rec = (vm.uiState.value.grindResult as GrindResult.Specific).recommendation
        // 1 base + 0 relief = 1 → +0.2
        assertEquals(5.4f, rec.suggestedStart, 0.01f)
    }

    @Test
    fun `decaf with dark roast plus Swiss Water relief nets 1 step coarser`() {
        val vm = createPersistenceViewModel()
        setupZp6Pulsar(vm)
        vm.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "Dark Swiss Decaf",
                isDecaf = true,
                roastLevel = "DARK",
                decafProcess = "SWISS_WATER",
            ),
        )
        selectFirstBag(vm)

        val rec = (vm.uiState.value.grindResult as GrindResult.Specific).recommendation
        // 1 base + 1 roast - 1 relief = 1 step → +0.2
        assertEquals(5.4f, rec.suggestedStart, 0.01f)
    }

    @Test
    fun `decaf on French Press immersion does not coarsen`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Decaf", isDecaf = true))
        selectFirstBag(vm)
        vm.setMethod(BrewMethod.FRENCH_PRESS)
        vm.setGrinder("1zpresso-zp6-special")

        // FRENCH_PRESS has no entry in DefaultGrinders.kt → falls back to
        // GrindResult.Generic before reaching the decaf coarsening path. We
        // can still assert the user-visible outcome: no Specific recommendation,
        // no decaf adjustment to apply.
        assertTrue(
            "expected Generic for FRENCH_PRESS (no recommendation in DefaultGrinders)",
            vm.uiState.value.grindResult is GrindResult.Generic,
        )
    }

    @Test
    fun `decaf on AeroPress immersion does not coarsen`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Decaf", isDecaf = true))
        selectFirstBag(vm)
        vm.setMethod(BrewMethod.AEROPRESS)
        vm.setGrinder("1zpresso-zp6-special")

        // Same DefaultGrinders limitation as French Press — see note above.
        assertTrue(
            "expected Generic for AEROPRESS (no recommendation in DefaultGrinders)",
            vm.uiState.value.grindResult is GrindResult.Generic,
        )
    }

    @Test
    fun `non-decaf bag does not adjust grind`() {
        val vm = createPersistenceViewModel()
        setupZp6Pulsar(vm)
        vm.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "Regular",
                isDecaf = false,
                roastLevel = "DARK",
            ),
        )
        selectFirstBag(vm)

        val rec = (vm.uiState.value.grindResult as GrindResult.Specific).recommendation
        assertEquals(5.2f, rec.suggestedStart, 0.01f)
        assertFalse(
            "note must NOT contain decaf wording: ${rec.adjustmentNote}",
            rec.adjustmentNote.contains("Decaf"),
        )
    }

    @Test
    fun `decaf coarsening clamps to rangeEnd on tight METAL_19K`() {
        val vm = createPersistenceViewModel()
        vm.setMethod(BrewMethod.PULSAR)
        vm.setFilterType(FilterType.METAL_19K)
        vm.setGrinder("1zpresso-zp6-special")
        // Dark roast → 2 steps coarser → 5.7 + 0.4 = 6.1, but range is 5.5–5.9
        vm.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "Dark Decaf",
                isDecaf = true,
                roastLevel = "DARK",
            ),
        )
        selectFirstBag(vm)

        val rec = (vm.uiState.value.grindResult as GrindResult.Specific).recommendation
        // Clamped to rangeEnd 5.9, not 6.1
        assertEquals(5.9f, rec.suggestedStart, 0.01f)
    }

    // NOTE: Immersion (FRENCH_PRESS / AEROPRESS / COLD_BREW) and ESPRESSO
    // branches of decafCoarserStepsFor are not directly asserted here because
    // DefaultGrinders.kt (used by the in-test BrewViewModel) only ships PULSAR
    // recommendations — non-PULSAR lookups fall back to GrindResult.Generic
    // and never reach the decaf coarsening path. The branch logic itself is
    // small and exercised in production via grinders.json. If we expand
    // DefaultGrinders coverage, add tests here for:
    //   - immersion methods → 0 base steps → suggested unchanged + "same start" note
    //   - ESPRESSO → 1 base step → suggested += stepSize

    // --- Filter Normalization on Method Change ---
    //
    // FilterType is a Pulsar-specific concept (paper / 19K / 40K mesh). The
    // brew screen only exposes the filter row when the active method is
    // PULSAR, so any non-Pulsar method that retains a FilterType is invisible
    // stale state that can leak into saved recipes. setMethod() is the single
    // chokepoint that enforces this invariant.

    @Test
    fun `setMethod PULSAR to V60 clears filter`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setFilterType(FilterType.PAPER)
        assertEquals(FilterType.PAPER, viewModel.uiState.value.filterType)

        viewModel.setMethod(BrewMethod.V60)

        assertNull(viewModel.uiState.value.filterType)
    }

    @Test
    fun `setMethod PULSAR to AEROPRESS clears filter`() {
        // Regression test: prior implementation used `capacityMaxG != null` as
        // the "filter-capable" predicate. Both PULSAR (380g) and AEROPRESS
        // (250g) have non-null capacityMaxG, so switching to AeroPress would
        // silently retain a FilterType.METAL_19K — invisible because the UI
        // hides the filter row, but persisted into saved recipes.
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setFilterType(FilterType.METAL_19K)
        assertEquals(FilterType.METAL_19K, viewModel.uiState.value.filterType)

        viewModel.setMethod(BrewMethod.AEROPRESS)

        assertNull(viewModel.uiState.value.filterType)
    }

    @Test
    fun `setMethod round trip PULSAR V60 PULSAR restores filter`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setFilterType(FilterType.METAL_19K)

        // Switch away — filter cleared.
        viewModel.setMethod(BrewMethod.V60)
        assertNull(viewModel.uiState.value.filterType)

        // Switch back — filter restored from per-method memory.
        viewModel.setMethod(BrewMethod.PULSAR)
        assertEquals(FilterType.METAL_19K, viewModel.uiState.value.filterType)
    }

    @Test
    fun `setFilterType remembered per method`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setFilterType(FilterType.PAPER)

        // Round-trip via a non-Pulsar method then back — original filter
        // restored, last-by-method map retains the choice.
        viewModel.setMethod(BrewMethod.V60)
        viewModel.setMethod(BrewMethod.PULSAR)
        assertEquals(FilterType.PAPER, viewModel.uiState.value.filterType)

        // Update filter on PULSAR, round-trip again — newest choice survives.
        viewModel.setFilterType(FilterType.METAL_40K)
        viewModel.setMethod(BrewMethod.V60)
        viewModel.setMethod(BrewMethod.PULSAR)
        assertEquals(FilterType.METAL_40K, viewModel.uiState.value.filterType)
    }

    @Test
    fun `setMethod explicit no-filter choice is remembered`() {
        // User explicitly clears filter on Pulsar; round-trip must not silently
        // resurrect a prior filter when returning to Pulsar.
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setFilterType(FilterType.PAPER)
        viewModel.setFilterType(null)
        assertNull(viewModel.uiState.value.filterType)

        viewModel.setMethod(BrewMethod.V60)
        viewModel.setMethod(BrewMethod.PULSAR)

        assertNull(viewModel.uiState.value.filterType)
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
        viewModel.selectRatioPreset(0)
        viewModel.resetBrew()

        val state = viewModel.uiState.value
        assertEquals(BrewMethod.PULSAR, state.method) // default
        assertEquals(1, state.selectedPresetIndex) // Pulsar default preset index (1:17 Balanced)
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

        assertEquals("4.3-6.5", persistenceViewModel.savedRecipes.value.first().grindSetting)
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
    fun `saveRecipe persists decaf flag`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setDecafBrew(true)

        persistenceViewModel.saveRecipe("Evening Brew")

        assertTrue(persistenceViewModel.savedRecipes.value.first().isDecaf)
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
    fun `loadRecipe restores decaf flag`() {
        val persistenceViewModel = createPersistenceViewModel()
        val recipe = SavedRecipeEntity(
            method = "PULSAR",
            ratio = 17f,
            doseG = 20f,
            waterG = 340f,
            isDecaf = true,
        )

        persistenceViewModel.loadRecipe(recipe)

        assertTrue(persistenceViewModel.uiState.value.isDecafBrew)
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
        assertEquals(4f, logs.first().rating)
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

        assertEquals(4f, persistenceViewModel.brewLogs.value.first().rating)
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

    @Test
    fun `logBrew captures decaf flag`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.setDecafBrew(true)

        persistenceViewModel.logBrew()

        assertTrue(persistenceViewModel.brewLogs.value.first().isDecaf)
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

        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Kenya AA"))

        val bags = persistenceViewModel.coffeeBags.value
        assertEquals(1, bags.size)
        assertEquals("Kenya AA", bags.first().name)
    }

    @Test
    fun `addCoffeeBag with all fields stores all fields`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
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
                isDecaf = true,
                photoUri = "content://photo",
                status = "OPEN",
            ),
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
        assertTrue(bag.isDecaf)
        assertEquals("content://photo", bag.photoUri)
        assertEquals("OPEN", bag.status)
    }

    @Test
    fun `selectBag syncs decaf flag into brew ui state`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Half Caf", isDecaf = true))
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Daily Driver", isDecaf = false))

        val decafBagId = persistenceViewModel.coffeeBags.value.first { it.isDecaf }.id
        val regularBagId = persistenceViewModel.coffeeBags.value.first { !it.isDecaf }.id

        persistenceViewModel.selectBag(decafBagId)
        assertTrue(persistenceViewModel.uiState.value.isDecafBrew)
        assertFalse(
            "no override set → no mismatch",
            persistenceViewModel.uiState.value.decafMismatchWithBag,
        )

        persistenceViewModel.selectBag(regularBagId)
        assertFalse(persistenceViewModel.uiState.value.isDecafBrew)
        assertFalse(persistenceViewModel.uiState.value.decafMismatchWithBag)
    }

    @Test
    fun `manual setDecafBrew overrides bag and flags mismatch`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Regular Bag", isDecaf = false))
        val regularBagId = vm.coffeeBags.value.first().id

        vm.selectBag(regularBagId)
        assertFalse(vm.uiState.value.isDecafBrew)
        assertFalse(vm.uiState.value.decafMismatchWithBag)

        // User manually toggles decaf on → override disagrees with bag.
        vm.setDecafBrew(true)
        assertTrue(vm.uiState.value.isDecafBrew)
        assertTrue(vm.uiState.value.decafMismatchWithBag)

        // syncDecafToBag clears override and returns to bag's state.
        vm.syncDecafToBag()
        assertFalse(vm.uiState.value.isDecafBrew)
        assertFalse(vm.uiState.value.decafMismatchWithBag)
    }

    @Test
    fun `manual decaf override persists across bag changes`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Regular A", isDecaf = false))
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Regular B", isDecaf = false))
        val bagA = vm.coffeeBags.value[0].id
        val bagB = vm.coffeeBags.value[1].id

        vm.selectBag(bagA)
        vm.setDecafBrew(true)  // override
        assertTrue(vm.uiState.value.isDecafBrew)
        assertTrue(vm.uiState.value.decafMismatchWithBag)

        // Switching bags keeps the override (no silent clobber).
        vm.selectBag(bagB)
        assertTrue(vm.uiState.value.isDecafBrew)
        assertTrue(vm.uiState.value.decafMismatchWithBag)
    }

    @Test
    fun `no mismatch when no bag is selected even with override`() {
        val vm = createPersistenceViewModel()
        vm.setDecafBrew(true)
        assertTrue(vm.uiState.value.isDecafBrew)
        assertFalse(
            "no bag → no mismatch",
            vm.uiState.value.decafMismatchWithBag,
        )
    }

    @Test
    fun `selectBag auto-switches method to last used with that bag`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Kenya AA"))
        val bagId = vm.coffeeBags.value.first().id

        // Start with default method, pick bag, log a brew with AEROPRESS.
        vm.selectBag(bagId)
        vm.setMethod(BrewMethod.AEROPRESS)
        vm.setAmount("15")
        vm.logBrew()

        // Switch method away, deselect bag.
        vm.setMethod(BrewMethod.V60)
        vm.selectBag(null)
        assertEquals(BrewMethod.V60, vm.uiState.value.method)

        // Re-selecting the bag should restore the last-used method for it.
        vm.selectBag(bagId)
        assertEquals(BrewMethod.AEROPRESS, vm.uiState.value.method)
    }

    @Test
    fun `selectBag keeps current method when bag has no brew history`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "New Bag"))
        val bagId = vm.coffeeBags.value.first().id

        vm.setMethod(BrewMethod.V60)
        vm.selectBag(bagId)

        assertEquals(BrewMethod.V60, vm.uiState.value.method)
    }

    @Test
    fun `addCoffeeBag normalizes barcode digits before storing`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Beansmith's Gedeb", barcode = "859 4206 183060"))

        assertEquals("8594206183060", persistenceViewModel.coffeeBags.value.first().barcode)
    }

    @Test
    fun `findBagByBarcode matches normalized scanner input`() {
        val persistenceViewModel = createPersistenceViewModel()
        var foundName: String? = null

        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Beansmith's Gedeb", barcode = "8594206183060"))
        persistenceViewModel.findBagByBarcode("859 4206 183060") { bag ->
            foundName = bag?.name
        }

        assertEquals("Beansmith's Gedeb", foundName)
    }

    @Test
    fun `deleteCoffeeBag removes bag`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Bag 1"))
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Bag 2"))
        val toDelete = persistenceViewModel.coffeeBags.value.first()

        persistenceViewModel.deleteCoffeeBag(toDelete)

        assertEquals(1, persistenceViewModel.coffeeBags.value.size)
        assertEquals("Bag 2", persistenceViewModel.coffeeBags.value.first().name)
    }

    @Test
    fun `updateBagStatus changes bag status`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Status Bag"))
        val bagId = persistenceViewModel.coffeeBags.value.first().id

        persistenceViewModel.updateBagStatus(bagId, "OPEN")

        assertEquals("OPEN", persistenceViewModel.coffeeBags.value.first().status)
    }

    @Test
    fun `updateBagStatus ignores unknown bag ID`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Existing"))

        persistenceViewModel.updateBagStatus(9999L, "OPEN")

        assertEquals(1, persistenceViewModel.coffeeBags.value.size)
        assertEquals("SEALED", persistenceViewModel.coffeeBags.value.first().status)
    }

    @Test
    fun `addCoffeeBag ignores call when repository is null`() {
        viewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "No Repo Bag"))

        assertTrue(viewModel.coffeeBags.value.isEmpty())
    }

    @Test
    fun `multiple bags can be added`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Bag A"))
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Bag B"))
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Bag C"))

        assertEquals(3, persistenceViewModel.coffeeBags.value.size)
    }

    @Test
    fun `addCoffeeBag stores region separately`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "Test",
                origin = "Ethiopia",
                region = "Guji",
            ),
        )

        val bag = persistenceViewModel.coffeeBags.value.first()
        assertEquals("Ethiopia", bag.origin)
        assertEquals("Guji", bag.region)
    }

    @Test
    fun `addCoffeeBag stores canonical ids for multilingual metadata`() {
        val persistenceViewModel = createPersistenceViewModel()

        persistenceViewModel.addCoffeeBag(
            BrewViewModel.CoffeeBagInput(
                name = "Vacation Bag",
                origin = "Etiopie",
                region = "Guji",
                roastLevel = "Světlé",
                processType = "Lavado",
                variety = "Gesha",
                tastingNotes = "Lesní jahoda, Zelený čaj",
            ),
        )

        val bag = persistenceViewModel.coffeeBags.value.first()
        assertEquals("Etiopie", bag.origin)
        assertEquals("ETHIOPIA", bag.originId)
        assertEquals("Guji", bag.region)
        assertEquals("GUJI", bag.regionId)
        assertEquals("Světlé", bag.roastLevel)
        assertEquals("LIGHT", bag.roastLevelIds)
        assertEquals("Lavado", bag.processType)
        assertEquals("WASHED", bag.processTypeId)
        assertEquals("Gesha", bag.variety)
        assertEquals("GEISHA", bag.varietyIds)
        assertEquals("Lesní jahoda, Zelený čaj", bag.tastingNotes)
        assertEquals("green_tea,wild_strawberry", bag.tasteNoteIds)
    }

    @Test
    fun `updateCoffeeBag refreshes canonical ids from edited multilingual fields`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Editable Bag"))

        val original = persistenceViewModel.coffeeBags.value.first()
        persistenceViewModel.updateCoffeeBag(
            original.copy(
                origin = "Etiopia",
                region = "Gedeb",
                roastLevel = "Oscuro",
                processType = "Praný",
                variety = "Gesha",
                tastingNotes = "Yuzu, Lesní jahoda",
            ),
        )

        val updated = persistenceViewModel.coffeeBags.value.first()
        assertEquals("ETHIOPIA", updated.originId)
        assertEquals("GEDEB", updated.regionId)
        assertEquals("DARK", updated.roastLevelIds)
        assertEquals("WASHED", updated.processTypeId)
        assertEquals("GEISHA", updated.varietyIds)
        assertEquals("wild_strawberry,yuzu", updated.tasteNoteIds)
    }

    @Test
    fun `addCoffeeBag sets initialWeightG equal to weightG`() {
        val persistenceViewModel = createPersistenceViewModel()
        persistenceViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Test", weightG = 250f))

        val bag = persistenceViewModel.coffeeBags.value.first()
        assertEquals(250f, bag.weightG!!, 0.01f)
        assertEquals(250f, bag.initialWeightG!!, 0.01f)
    }

    // --- Auto-status transitions ---

    @Test
    fun `logBrew changes bag status from SEALED to OPEN`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Sealed Bag", weightG = 250f))
        val bagId = vm.coffeeBags.value.first().id

        vm.selectBag(bagId)
        vm.setAmount("20")
        vm.logBrew()

        val bag = vm.coffeeBags.value.first()
        assertEquals("OPEN", bag.status)
        assertNotNull("openedDate should be set", bag.openedDate)
    }

    @Test
    fun `logBrew groups its log and inventory writes into a single transaction`() {
        val recordingRunner = RecordingTransactionRunner()
        val vm = BrewViewModel(
            recipeRepository = RecipeRepository(FakeRecipeDao()),
            brewLogRepository = BrewLogRepository(null, FakeBrewLogDao(), FakeFlavorTagDao()),
            coffeeBagRepository = CoffeeBagRepository(FakeCoffeeBagDao()),
            transactionRunner = recordingRunner,
        )
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Atomic Bag", weightG = 250f))
        vm.selectBag(vm.coffeeBags.value.first().id)
        vm.setAmount("18")

        vm.logBrew()

        // Both the brew-log insert and the bag weight decrement must run under
        // exactly one transaction, not one-per-write and not outside it.
        assertEquals(1, recordingRunner.invocations)
        assertEquals(1, vm.brewLogs.value.size)
        assertEquals(232f, vm.coffeeBags.value.first().weightG!!, 0.01f)
    }

    @Test
    fun `logBrew that depletes weight changes status to FINISHED`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Almost Empty", weightG = 15f))
        val bagId = vm.coffeeBags.value.first().id

        vm.selectBag(bagId)
        vm.setAmount("20")
        vm.logBrew()

        val bag = vm.coffeeBags.value.first()
        assertEquals(0f, bag.weightG!!, 0.01f)
        assertEquals("FINISHED", bag.status)
    }

    @Test
    fun `logBrew on bag without weight does not crash`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "No Weight Bag"))
        val bagId = vm.coffeeBags.value.first().id

        vm.selectBag(bagId)
        vm.setAmount("20")
        vm.logBrew()

        val bag = vm.coffeeBags.value.first()
        assertEquals("OPEN", bag.status)
        assertNull("weightG should remain null", bag.weightG)
    }

    // --- adjustBagWeight ---

    @Test
    fun `adjustBagWeight sets new weight`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Test", weightG = 250f))
        val bagId = vm.coffeeBags.value.first().id

        vm.adjustBagWeight(bagId, 180f)

        assertEquals(180f, vm.coffeeBags.value.first().weightG!!, 0.01f)
    }

    @Test
    fun `adjustBagWeight sets initialWeightG for legacy bags`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Legacy"))
        val bagId = vm.coffeeBags.value.first().id

        vm.adjustBagWeight(bagId, 200f)

        val bag = vm.coffeeBags.value.first()
        assertEquals(200f, bag.weightG!!, 0.01f)
        assertEquals(200f, bag.initialWeightG!!, 0.01f)
    }

    @Test
    fun `adjustBagWeight to zero marks bag FINISHED`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Deplete", weightG = 50f))
        val bagId = vm.coffeeBags.value.first().id
        vm.updateBagStatus(bagId, "OPEN")

        vm.adjustBagWeight(bagId, 0f)

        val bag = vm.coffeeBags.value.first()
        assertEquals(0f, bag.weightG!!, 0.01f)
        assertEquals("FINISHED", bag.status)
    }

    @Test
    fun `adjustBagWeight clamps negative to zero`() {
        val vm = createPersistenceViewModel()
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Negative", weightG = 50f))
        val bagId = vm.coffeeBags.value.first().id

        vm.adjustBagWeight(bagId, -10f)

        assertEquals(0f, vm.coffeeBags.value.first().weightG!!, 0.01f)
    }

    // --- loadRecipe preset matching ---

    @Test
    fun `loadRecipe matches ratio to available preset`() {
        // Pulsar presets: 16(Bright), 17(Balanced,default), 18(Rich) — ratio 16 matches index 0
        val entity = SavedRecipeEntity(
            method = "PULSAR", ratio = 16f, doseG = 20f, waterG = 320f,
        )
        viewModel.loadRecipe(entity)
        val state = viewModel.uiState.value
        assertEquals(0, state.selectedPresetIndex)
        assertEquals("", state.customRatio)
        assertEquals(16f, state.effectiveRatio, 0.01f)
    }

    @Test
    fun `loadRecipe uses customRatio for non-preset ratio`() {
        val entity = SavedRecipeEntity(
            method = "PULSAR", ratio = 15.5f, doseG = 20f, waterG = 310f,
        )
        viewModel.loadRecipe(entity)
        val state = viewModel.uiState.value
        assertEquals("15.5", state.customRatio)
        assertEquals(15.5f, state.effectiveRatio, 0.01f)
    }

    // --- Guardrail for extreme ratios ---

    @Test
    fun `ratio zero shows guardrail warning`() {
        viewModel.setCustomRatio("0")
        val state = viewModel.uiState.value
        assertNotNull(state.ratioWarning)
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
        noArgViewModel.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "No Repo Bag"))

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

    private class RecordingTransactionRunner : TransactionRunner {
        var invocations = 0
            private set

        override suspend fun <R> runInTransaction(block: suspend () -> R): R {
            invocations++
            return block()
        }
    }

    private fun createPersistenceViewModel(): BrewViewModel {
        val recipeRepository = RecipeRepository(FakeRecipeDao())
        val brewLogRepository = BrewLogRepository(null, FakeBrewLogDao(), FakeFlavorTagDao())
        val coffeeBagRepository = CoffeeBagRepository(FakeCoffeeBagDao())
        return BrewViewModel(
            recipeRepository = recipeRepository,
            brewLogRepository = brewLogRepository,
            coffeeBagRepository = coffeeBagRepository,
        )
    }

    // --- Post-Brew Check-In ---

    @Test
    fun `lastUnratedBrew is null when no brews exist`() {
        val vm = createPersistenceViewModel()
        assertNull(vm.lastUnratedBrew.value)
    }

    @Test
    fun `lastUnratedBrew returns brew without rating after logBrew`() {
        val vm = createPersistenceViewModel()
        vm.setAmount("20")
        vm.logBrew()

        assertNotNull(vm.lastUnratedBrew.value)
        assertNull(vm.lastUnratedBrew.value?.rating)
    }

    @Test
    fun `lastUnratedBrew is null when all brews are rated`() {
        val vm = createPersistenceViewModel()
        vm.setRating(4)
        vm.setAmount("20")
        vm.logBrew()

        assertNull(vm.lastUnratedBrew.value)
    }

    @Test
    fun `quickRateBrewLog updates rating and clears unrated state`() {
        val vm = createPersistenceViewModel()
        vm.setAmount("20")
        vm.logBrew()

        val unrated = vm.lastUnratedBrew.value
        assertNotNull(unrated)

        vm.quickRateBrewLog(
            logId = unrated!!.id,
            rating = 5f,
            tasteFeedback = TasteFeedback.BALANCED,
        )

        assertNull(vm.lastUnratedBrew.value)
        val log = vm.brewLogs.value.first()
        assertEquals(5f, log.rating)
        assertEquals("BALANCED", log.tasteFeedback)
    }

    @Test
    fun `quickRateBrewLog with taste issue stores feedback`() {
        val vm = createPersistenceViewModel()
        vm.setAmount("20")
        vm.logBrew()

        val unrated = vm.lastUnratedBrew.value!!

        vm.quickRateBrewLog(
            logId = unrated.id,
            rating = 2f,
            tasteFeedback = TasteFeedback.TOO_BITTER,
        )

        val log = vm.brewLogs.value.first()
        assertEquals(2f, log.rating)
        assertEquals("TOO_BITTER", log.tasteFeedback)
        assertNull(vm.lastUnratedBrew.value)
    }

    private fun setElapsedSeconds(targetViewModel: BrewViewModel, elapsedSeconds: Int) {
        val currentState = targetViewModel.uiState.value
        targetViewModel.setUiStateForTesting(currentState.copy(elapsedSeconds = elapsedSeconds))
    }

    // --- Water Retention ---

    @Test
    fun `retention is calculated as coffeeG times absorptionRatio`() {
        viewModel.setAmount("20")
        val state = viewModel.uiState.value
        assertEquals(40f, state.retainedWaterG, 0.01f)
    }

    @Test
    fun `predicted cup volume subtracts retained water from total`() {
        viewModel.setAmount("20")
        val state = viewModel.uiState.value
        assertEquals(300f, state.predictedCupVolumeG, 0.01f)
    }

    @Test
    fun `retention scales with coffee dose`() {
        viewModel.setAmount("25")
        val state = viewModel.uiState.value
        assertEquals(50f, state.retainedWaterG, 0.01f)
        assertEquals(375f, state.predictedCupVolumeG, 0.01f)
    }

    @Test
    fun `espresso predicted cup volume uses beverage yield semantics`() {
        viewModel.setMethod(BrewMethod.ESPRESSO)
        viewModel.setAmount("18")
        val state = viewModel.uiState.value
        assertEquals(36f, state.waterG, 0.01f)
        assertEquals(0f, state.retainedWaterG, 0.01f)
        assertEquals(36f, state.predictedCupVolumeG, 0.01f)
    }

    @Test
    fun `non-bloom methods still calculate retention`() {
        viewModel.setMethod(BrewMethod.FRENCH_PRESS)
        viewModel.setAmount("20")
        val state = viewModel.uiState.value
        assertEquals(40f, state.retainedWaterG, 0.01f)
        assertEquals(260f, state.predictedCupVolumeG, 0.01f)
    }

    @Test
    fun `zero coffee dose gives zero retention`() {
        viewModel.setAmount("0")
        val state = viewModel.uiState.value
        assertEquals(0f, state.retainedWaterG, 0.01f)
        assertEquals(0f, state.predictedCupVolumeG, 0.01f)
    }

    // --- Effective Bloom Duration (roast-date aware) ---

    @Test
    fun `effectiveBloomDuration defaults to method value without bag`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        assertEquals(45, viewModel.uiState.value.effectiveBloomDurationSeconds)
    }

    @Test
    fun `effectiveBloomDuration defaults to method value for non-bloom method`() {
        viewModel.setMethod(BrewMethod.FRENCH_PRESS)
        assertEquals(BrewMethod.FRENCH_PRESS.bloomDurationSeconds, viewModel.uiState.value.effectiveBloomDurationSeconds)
    }

    @Test
    fun `effectiveBloomDuration increases for very fresh coffee`() {
        val vm = createPersistenceViewModel()
        vm.setMethod(BrewMethod.PULSAR)
        vm.setAmount("20")

        val threeDaysAgo = System.currentTimeMillis() - 3 * 86_400_000L
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Fresh Bag", roastDate = threeDaysAgo))
        val bagId = vm.coffeeBags.value.first().id
        vm.selectBag(bagId)

        assertEquals(55, vm.uiState.value.effectiveBloomDurationSeconds)
    }

    @Test
    fun `effectiveBloomDuration stays default for normally rested coffee`() {
        val vm = createPersistenceViewModel()
        vm.setMethod(BrewMethod.PULSAR)
        vm.setAmount("20")

        val fourteenDaysAgo = System.currentTimeMillis() - 14 * 86_400_000L
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Normal Bag", roastDate = fourteenDaysAgo))
        val bagId = vm.coffeeBags.value.first().id
        vm.selectBag(bagId)

        assertEquals(45, vm.uiState.value.effectiveBloomDurationSeconds)
    }

    @Test
    fun `effectiveBloomDuration decreases for older coffee`() {
        val vm = createPersistenceViewModel()
        vm.setMethod(BrewMethod.PULSAR)
        vm.setAmount("20")

        val thirtyDaysAgo = System.currentTimeMillis() - 30 * 86_400_000L
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Old Bag", roastDate = thirtyDaysAgo))
        val bagId = vm.coffeeBags.value.first().id
        vm.selectBag(bagId)

        assertEquals(35, vm.uiState.value.effectiveBloomDurationSeconds)
    }

    @Test
    fun `effectiveBloomDuration clamped to 60 max`() {
        val vm = createPersistenceViewModel()
        vm.setMethod(BrewMethod.V60)
        vm.setAmount("20")

        val oneDayAgo = System.currentTimeMillis() - 1 * 86_400_000L
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Super Fresh", roastDate = oneDayAgo))
        val bagId = vm.coffeeBags.value.first().id
        vm.selectBag(bagId)

        assertTrue(vm.uiState.value.effectiveBloomDurationSeconds <= 60)
    }

    @Test
    fun `effectiveBloomDuration clamped to 30 min`() {
        val vm = createPersistenceViewModel()
        vm.setMethod(BrewMethod.PULSAR)
        vm.setAmount("20")

        val ninetyDaysAgo = System.currentTimeMillis() - 90 * 86_400_000L
        vm.addCoffeeBag(BrewViewModel.CoffeeBagInput(name = "Very Old Bag", roastDate = ninetyDaysAgo))
        val bagId = vm.coffeeBags.value.first().id
        vm.selectBag(bagId)

        assertTrue(vm.uiState.value.effectiveBloomDurationSeconds >= 30)
    }


    // --- Timer Profile ---

    @Test
    fun `cold brew does not start an active timer`() {
        viewModel.setMethod(BrewMethod.COLD_BREW)

        viewModel.startTimer()

        val state = viewModel.uiState.value
        assertFalse(state.timerRunning)
        assertEquals(0, state.elapsedSeconds)
    }

    @Test
    fun `short methods can start an active timer`() {
        viewModel.setMethod(BrewMethod.V60)

        viewModel.startTimer()

        assertTrue(viewModel.uiState.value.timerRunning)
        viewModel.stopTimer()
    }

    // --- New Brew Session Reset ---

    @Test
    fun `startNewBrewSession clears timer progress`() {
        viewModel.setMethod(BrewMethod.PULSAR)
        viewModel.setAmount("20")
        viewModel.setUiStateForTesting(
            viewModel.uiState.value.copy(
                timerRunning = true,
                elapsedSeconds = 180,
                bloomMarkedAtSeconds = 5,
                bloomCountdownSeconds = 10,
                bloomFinished = true,
            ),
        )

        viewModel.startNewBrewSession()

        val state = viewModel.uiState.value
        assertFalse(state.timerRunning)
        assertEquals(0, state.elapsedSeconds)
        assertNull(state.bloomMarkedAtSeconds)
        assertNull(state.bloomCountdownSeconds)
        assertFalse(state.bloomFinished)
    }

    @Test
    fun `startNewBrewSession clears feedback state`() {
        viewModel.setTasteFeedback(TasteFeedback.TOO_BITTER)
        viewModel.setRating(4)
        viewModel.setFeedbackNotes("Pretty good")
        viewModel.requestFeedbackSnackbar()

        viewModel.startNewBrewSession()

        val state = viewModel.uiState.value
        assertNull(state.tasteFeedback)
        assertEquals(0, state.rating)
        assertEquals("", state.feedbackNotes)
        assertFalse(state.showFeedbackSnackbar)
    }

    @Test
    fun `startNewBrewSession resets minute-alert toggle to default-on`() {
        viewModel.toggleMinuteAlert() // disables it
        assertFalse(viewModel.uiState.value.minuteAlertEnabled)

        viewModel.startNewBrewSession()

        assertTrue(viewModel.uiState.value.minuteAlertEnabled)
    }

    @Test
    fun `startNewBrewSession preserves recipe configuration`() {
        viewModel.setMethod(BrewMethod.V60)
        viewModel.setAmount("25")
        viewModel.setFilterType(FilterType.PAPER)
        viewModel.setCustomRatio("18")
        val before = viewModel.uiState.value

        viewModel.setUiStateForTesting(before.copy(elapsedSeconds = 120))
        viewModel.startNewBrewSession()

        val after = viewModel.uiState.value
        assertEquals(before.method, after.method)
        assertEquals(before.amount, after.amount)
        assertEquals(before.filterType, after.filterType)
        assertEquals(before.customRatio, after.customRatio)
        assertEquals(before.coffeeG, after.coffeeG, 0.01f)
        assertEquals(before.waterG, after.waterG, 0.01f)
    }

    @Test
    fun `startNewBrewSession satisfies auto-start condition for non-bloom methods`() {
        // Reproduces the bug: leaving BrewTimer mid-brew left elapsedSeconds non-zero,
        // which prevented the auto-start LaunchedEffect from firing on re-entry.
        viewModel.setMethod(BrewMethod.FRENCH_PRESS) // non-bloom
        viewModel.setUiStateForTesting(
            viewModel.uiState.value.copy(elapsedSeconds = 240, timerRunning = false),
        )

        viewModel.startNewBrewSession()

        val state = viewModel.uiState.value
        assertFalse(state.timerRunning)
        assertEquals(0, state.elapsedSeconds)
        assertFalse(state.method.hasBloom)
    }

    // --- Bloom Spritesheet Selection ---

    @Test
    fun `selectBloomSpritesheetIfNeeded picks an id when none is set`() {
        assertNull(viewModel.uiState.value.bloomSpritesheetId)

        viewModel.selectBloomSpritesheetIfNeeded(emptyMap())

        val picked = viewModel.uiState.value.bloomSpritesheetId
        assertNotNull(picked)
        assertTrue(
            "Picked id $picked must be one of the known bloom spritesheet ids",
            picked in com.adsamcik.starlitcoffee.domain.BloomSpritesheetIds,
        )
    }

    @Test
    fun `selectBloomSpritesheetIfNeeded is idempotent`() {
        // Regression: previously each BloomSpritesheetAnimation made its own
        // random pick, so the animation that ran during bloom and the
        // post-bloom flash on BrewTimerScreen could disagree. The selection
        // must lock in once per brew session.
        viewModel.selectBloomSpritesheetIfNeeded(emptyMap())
        val firstPick = viewModel.uiState.value.bloomSpritesheetId
        assertNotNull(firstPick)

        repeat(20) {
            viewModel.selectBloomSpritesheetIfNeeded(emptyMap())
            assertEquals(firstPick, viewModel.uiState.value.bloomSpritesheetId)
        }
    }

    @Test
    fun `selectBloomSpritesheetIfNeeded honors disabled weights`() {
        // Force only one viable id and verify it gets picked.
        val onlyId = "rose"
        val weights = com.adsamcik.starlitcoffee.domain.BloomSpritesheetIds
            .associateWith { id -> if (id == onlyId) 1 else 0 }

        viewModel.selectBloomSpritesheetIfNeeded(weights)

        assertEquals(onlyId, viewModel.uiState.value.bloomSpritesheetId)
    }

    @Test
    fun `selectBloomSpritesheetIfNeeded leaves id null when all weights are zero`() {
        val weights = com.adsamcik.starlitcoffee.domain.BloomSpritesheetIds
            .associateWith { 0 }

        viewModel.selectBloomSpritesheetIfNeeded(weights)

        assertNull(viewModel.uiState.value.bloomSpritesheetId)
    }

    @Test
    fun `stopTimer clears bloom spritesheet id so next brew gets a fresh pick`() {
        viewModel.selectBloomSpritesheetIfNeeded(emptyMap())
        assertNotNull(viewModel.uiState.value.bloomSpritesheetId)

        viewModel.stopTimer()

        assertNull(viewModel.uiState.value.bloomSpritesheetId)
    }

    @Test
    fun `startNewBrewSession clears bloom spritesheet id`() {
        viewModel.selectBloomSpritesheetIfNeeded(emptyMap())
        assertNotNull(viewModel.uiState.value.bloomSpritesheetId)

        viewModel.startNewBrewSession()

        assertNull(viewModel.uiState.value.bloomSpritesheetId)
    }
}
