package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.data.db.dao.CupPresetDao
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.BrewingSetSelection
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.repository.CalculatorSetupStore
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorBrewingSetsTest {
    private val home = BrewingSet("home", "Home filter", BrewMethod.PULSAR,
        CalculatorSetup(18f, tokens = listOf(CalcToken.Number("20")), filterType = "PAPER", grinderId = "fellow-ode-gen2"))
    private val work = home.copy(id = "work", name = "Work filter", setup =
        CalculatorSetup(16f, "WATER_IN", listOf(CalcToken.Number("250")), "PAPER", "1zpresso-zp6-special"))
    private val espresso = BrewingSet("espresso", "Home espresso", BrewMethod.ESPRESSO,
        CalculatorSetup(2.5f, "IN_CUP", listOf(CalcToken.Number("45")), grinderId = "niche-zero"))
    private val presets = CupPresetRepository(EmptyPresets())

    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test
    fun `cup visibility updates without changing the amount or selected brewing set`() {
        val store = SetStore(listOf(home, work))
        val vm = CalculatorViewModel(presets, store)
        vm.clear()
        vm.appendDigit('3')
        val setup = vm.currentSetup()
        store.userPreferences.value = store.userPreferences.value.copy(showCupPresets = false)
        assertFalse(vm.uiState.value.showCupPresets)
        assertEquals(setup, vm.currentSetup())
        assertEquals(home.id, vm.uiState.value.activeBrewingSetId)
        val restarted = CalculatorViewModel(presets, store)
        assertFalse(restarted.uiState.value.showCupPresets)
        store.userPreferences.value = store.userPreferences.value.copy(showCupPresets = true)
        assertTrue(vm.uiState.value.showCupPresets)
    }

    @Test
    fun `sets using the same method remember their own input and survive restart`() {
        val store = SetStore(listOf(home, work, espresso))
        val vm = CalculatorViewModel(presets, store)
        assertEquals(home.setup, vm.currentSetup())
        vm.setRatio(17f)
        vm.clear()
        vm.appendDigit('2')
        vm.appendDigit('5')
        val changedHome = vm.currentSetup()
        vm.selectBrewingSet(work.id)
        assertEquals(work.setup, vm.currentSetup())
        vm.selectBrewingSet(espresso.id)
        assertEquals(espresso.setup, vm.currentSetup())
        assertEquals(18f, vm.uiState.value.previewDoseG, 0.01f)
        vm.selectBrewingSet(home.id)
        assertEquals(changedHome, vm.currentSetup())
        vm.selectBrewingSet(work.id)
        val restarted = CalculatorViewModel(presets, store)
        assertEquals(work.id, restarted.uiState.value.activeBrewingSetId)
        assertEquals(work.setup, restarted.currentSetup())
    }

    @Test
    fun `typing while selection acknowledgement is delayed cannot overwrite the new amount`() = runTest {
        val store = SetStore(listOf(home, work))
        val vm = CalculatorViewModel(presets, store)
        val gate = CompletableDeferred<Unit>()
        store.selectionGate = gate
        vm.selectBrewingSet(work.id)
        vm.clear()
        vm.appendDigit('3')
        vm.appendDigit('0')
        vm.appendDigit('0')
        val expected = vm.currentSetup()
        gate.complete(Unit)
        assertEquals(expected, vm.currentSetup())
        assertEquals(expected, store.selection.active.setup)
        vm.selectBrewingSet(home.id)
        assertEquals(home.setup, vm.currentSetup())
    }

    @Test
    fun `rapid switching retains pending amounts for outgoing sets`() = runTest {
        val store = SetStore(listOf(home, work))
        val vm = CalculatorViewModel(presets, store)
        val gate = CompletableDeferred<Unit>()
        store.rememberGate = gate
        val laterWrites = CompletableDeferred<Unit>()
        store.nextRememberGate = laterWrites
        vm.clear()
        vm.appendDigit('2')
        vm.appendDigit('5')
        val latest = vm.currentSetup()
        vm.selectBrewingSet(work.id)
        gate.complete(Unit)
        vm.selectBrewingSet(home.id)
        assertEquals(latest, vm.currentSetup())
        laterWrites.complete(Unit)
        assertEquals(latest, store.selection.active.setup)
    }

    @Test
    fun `settings edits reload equipment without losing the latest input and deletion selects a fallback`() = runTest {
        val store = SetStore(listOf(home, work))
        val vm = CalculatorViewModel(presets, store)
        vm.setRatio(16f)
        vm.selectQuantity(CalculatorQuantityTarget.WATER_IN)
        store.saveBrewingSet(home.copy(name = "Renamed", setup = home.setup.copy(grinderId = "comandante-c40")))
        assertEquals(16f, vm.currentSetup().ratio, 0f)
        assertEquals("WATER_IN", vm.currentSetup().quantity)
        assertEquals("comandante-c40", vm.currentSetup().grinderId)
        assertEquals("Renamed", vm.uiState.value.brewingSets.first().name)
        store.deleteBrewingSet(home.id)
        assertEquals(work.id, vm.uiState.value.activeBrewingSetId)
        assertEquals(work.setup, vm.currentSetup())
    }

    @Test
    fun `first onboarding equipment update is observed even when the initial set id stays the same`() = runTest {
        val store = SetStore(listOf(home.copy(setup = CalculatorSetup(17f))))
        val vm = CalculatorViewModel(presets, store)
        store.saveBrewingSet(home)
        assertEquals(home.setup.filterType, vm.currentSetup().filterType)
        assertEquals(home.setup.grinderId, vm.currentSetup().grinderId)
    }

    @Test
    fun `failed migration still loads existing sets and can be retried`() {
        val store = SetStore(listOf(home))
        store.failInitialize = true
        val vm = CalculatorViewModel(presets, store)
        assertTrue(vm.uiState.value.preferencesLoaded)
        assertTrue(vm.uiState.value.setSaveFailed)
        assertEquals(home.setup, vm.currentSetup())
        store.failInitialize = false
        vm.retrySetupSave()
        assertFalse(vm.uiState.value.setSaveFailed)
    }

    @Test
    fun `new successful input replaces a failed write so retry cannot restore an obsolete amount`() {
        val store = SetStore(listOf(home))
        val vm = CalculatorViewModel(presets, store)
        store.failRemember = true
        vm.clear()
        assertTrue(vm.uiState.value.setSaveFailed)
        store.failRemember = false
        vm.appendDigit('2')
        vm.appendDigit('5')
        val latest = vm.currentSetup()
        assertFalse(vm.uiState.value.setSaveFailed)
        vm.retrySetupSave()
        assertEquals(latest, store.selection.active.setup)
    }

    @Test
    fun `failed save keeps its draft for retry and only reports success after persistence`() {
        val store = SetStore(listOf(home))
        val vm = CalculatorViewModel(presets, store)
        store.failSave = true
        vm.saveBrewingSet(espresso)
        assertTrue(vm.uiState.value.setSaveFailed)
        assertFalse(vm.uiState.value.setSaveInProgress)
        assertEquals(null, vm.uiState.value.savedSetId)
        assertEquals(home.id, vm.uiState.value.activeBrewingSetId)
        store.failSave = false
        vm.retrySetupSave()
        assertFalse(vm.uiState.value.setSaveFailed)
        assertEquals(espresso.id, vm.uiState.value.savedSetId)
        assertEquals(espresso.setup, vm.currentSetup())
        vm.consumeSetSaveOutcome()
        assertEquals(null, vm.uiState.value.savedSetId)
    }

    private class SetStore(sets: List<BrewingSet>) : CalculatorSetupStore {
        var selection = BrewingSetSelection(sets, sets.first().id)
        override val userPreferences = MutableStateFlow(preferences())
        var selectionGate: CompletableDeferred<Unit>? = null
        var rememberGate: CompletableDeferred<Unit>? = null
        var nextRememberGate: CompletableDeferred<Unit>? = null
        var failSave = false
        var failRemember = false
        var failInitialize = false
        private fun preferences() = UserPreferences(defaultMethod = selection.active.method,
            brewingSets = selection.sets, activeBrewingSetId = selection.activeId)
        private fun publish() { userPreferences.value = preferences() }
        override suspend fun initializeBrewingSets(savedRecipes: List<com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity>?) {
            check(!failInitialize) { "Migration unavailable" }
        }
        override suspend fun updateCalculatorSetup(method: BrewMethod, setup: CalculatorSetup) = Unit
        override suspend fun saveBrewingSet(set: BrewingSet) {
            check(!failSave) { "Storage unavailable" }
            selection = selection.upsert(set)
            publish()
        }
        override suspend fun selectBrewingSet(id: String) {
            selectionGate?.await()
            selection = selection.select(id)
            publish()
        }
        override suspend fun deleteBrewingSet(id: String) { selection = selection.remove(id); publish() }
        override suspend fun updateBrewingSetSetup(id: String, revision: Int, setup: CalculatorSetup) {
            check(!failRemember) { "Storage unavailable" }
            rememberGate?.await()
            nextRememberGate?.let { rememberGate = it; nextRememberGate = null }
            selection = selection.remember(id, revision, setup)
            publish()
        }
    }

    private class EmptyPresets : CupPresetDao {
        override fun getAll() = MutableStateFlow<List<CupPresetEntity>>(emptyList())
        override suspend fun getCount() = 1
        override suspend fun insert(preset: CupPresetEntity) = 1L
        override suspend fun insertAll(presets: List<CupPresetEntity>) = Unit
        override suspend fun update(preset: CupPresetEntity) = Unit
        override suspend fun delete(preset: CupPresetEntity) = Unit
        override suspend fun deleteAll() = Unit
    }
}
