package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.calculator.calculatorRatioOptions
import com.adsamcik.starlitcoffee.calculator.formatCalculatorRatio
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingPersistenceMapper
import com.adsamcik.starlitcoffee.data.brewing.snapshot.StoredRecipePayload
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.db.dao.CupPresetDao
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.CalculatorSetupCodec
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.repository.CalculatorSetupStore
import com.adsamcik.starlitcoffee.data.repository.BrewingSetWriter
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferences
import com.adsamcik.starlitcoffee.testutil.FakeRecipeDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorSetupTest {
    private val repository = CupPresetRepository(SetupCupPresetDao())

    @Before
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun teardown() = Dispatchers.resetMain()

    @Test
    fun `each new method starts at its own ratio without carrying the previous amount`() {
        val vm = CalculatorViewModel(repository)
        BrewMethod.entries.forEach { method ->
            vm.setBrewMethod(method)
            assertEquals(method.defaultRatio, vm.uiState.value.ratio, 0f)
            assertTrue(vm.uiState.value.tokens.isEmpty())
            vm.appendDigit('2')
        }
    }

    @Test
    fun `switching methods restores the complete setup`() {
        val vm = CalculatorViewModel(repository)
        vm.setRatio(18f)
        vm.selectQuantity(CalculatorQuantityTarget.IN_CUP)
        vm.appendDigit('3')
        vm.appendDigit('0')
        vm.appendDigit('0')
        vm.setEquipment(FilterType.METAL_19K, "home-grinder")
        val home = vm.currentSetup()

        vm.setBrewMethod(BrewMethod.ESPRESSO)
        assertEquals(2f, vm.uiState.value.ratio, 0f)
        vm.setRatio(2.5f)
        vm.selectQuantity(CalculatorQuantityTarget.COFFEE)
        vm.appendDigit('1')
        vm.appendDigit('8')
        vm.setEquipment(null, "work-grinder")
        val work = vm.currentSetup()

        vm.setBrewMethod(BrewMethod.PULSAR)
        assertEquals(home, vm.currentSetup())
        vm.setBrewMethod(BrewMethod.ESPRESSO)
        assertEquals(work, vm.currentSetup())
        assertEquals(45f, requireNotNull(vm.uiState.value.previewBeverageG), 0f)
    }

    @Test
    fun `restart restores per method state and clearing an amount is remembered`() {
        val store = MemorySetupStore()
        val first = CalculatorViewModel(repository, store)
        first.setRatio(18f)
        first.appendDigit('2')
        first.appendDigit('0')
        first.setEquipment(FilterType.PAPER, "home")
        val home = first.currentSetup()
        first.setBrewMethod(BrewMethod.ESPRESSO)
        first.setRatio(2.5f)
        first.selectQuantity(CalculatorQuantityTarget.IN_CUP)
        first.appendDigit('4')
        first.appendDigit('5')
        first.setEquipment(null, "work")
        val work = first.currentSetup()

        val restarted = CalculatorViewModel(repository, store)
        assertEquals(home, restarted.currentSetup())
        restarted.setBrewMethod(BrewMethod.ESPRESSO)
        assertEquals(work, restarted.currentSetup())
        restarted.clear()
        val cleared = CalculatorViewModel(repository, store)
        cleared.setBrewMethod(BrewMethod.ESPRESSO)
        assertTrue(cleared.uiState.value.tokens.isEmpty())
        assertEquals(2.5f, cleared.uiState.value.ratio, 0f)
        assertEquals("work", cleared.uiState.value.grinderId)
    }

    @Test
    fun `legacy global filter ratio does not become an espresso default`() {
        val vm = CalculatorViewModel(
            repository,
            MemorySetupStore(UserPreferences(defaultMethod = BrewMethod.ESPRESSO, lastUsedRatio = 17f)),
        )
        assertEquals(BrewMethod.ESPRESSO, vm.uiState.value.brewMethod)
        assertEquals(2f, vm.uiState.value.ratio, 0f)
    }

    @Test
    fun `late preferences do not overwrite an already edited setup`() {
        val preferences = MutableSharedFlow<UserPreferences>(replay = 1)
        val store = object : CalculatorSetupStore, BrewingSetWriter by NoBrewingSetWriter {
            override val userPreferences = preferences
            override suspend fun updateCalculatorSetup(method: BrewMethod, setup: CalculatorSetup) = Unit
        }
        val vm = CalculatorViewModel(repository, store)
        vm.setRatio(18f)
        vm.appendDigit('2')
        vm.appendDigit('0')
        preferences.tryEmit(
            UserPreferences(
                defaultMethod = BrewMethod.ESPRESSO,
                calculatorSetups = mapOf(BrewMethod.PULSAR to CalculatorSetup(ratio = 16f)),
            ),
        )
        assertEquals(BrewMethod.PULSAR, vm.uiState.value.brewMethod)
        assertEquals(18f, vm.uiState.value.ratio, 0f)
        assertEquals(20f, vm.uiState.value.previewDoseG, 0f)
    }

    @Test
    fun `explicit no filter and no grinder survive restart instead of restoring global defaults`() {
        val store = MemorySetupStore(
            UserPreferences(defaultFilterType = FilterType.PAPER, selectedGrinderId = "default"),
        )
        val first = CalculatorViewModel(repository, store)
        assertEquals(FilterType.PAPER, first.uiState.value.filterType)
        first.setEquipment(null, null)
        val restarted = CalculatorViewModel(repository, store)
        assertNull(restarted.uiState.value.filterType)
        assertNull(restarted.uiState.value.grinderId)
    }

    @Test
    fun `espresso supports direct yield and never labels it as water input`() {
        val vm = CalculatorViewModel(repository)
        vm.setBrewMethod(BrewMethod.ESPRESSO)
        vm.selectQuantity(CalculatorQuantityTarget.IN_CUP)
        vm.appendDigit('3')
        vm.appendDigit('6')
        assertEquals(18f, vm.uiState.value.previewDoseG, 0f)
        assertEquals(36f, requireNotNull(vm.uiState.value.previewBeverageG), 0f)
        assertNull(vm.uiState.value.previewApparentLossG)
        vm.selectQuantity(CalculatorQuantityTarget.WATER_IN)
        assertEquals(CalculatorQuantityTarget.IN_CUP, vm.uiState.value.quantityTarget)
        assertFalse(CalculatorQuantityTarget.WATER_IN.isAvailableFor(BrewMethod.ESPRESSO))
        assertFalse(CalculatorQuantityTarget.IN_CUP.isAvailableFor(BrewMethod.MOKA_POT))
    }

    @Test
    fun `espresso ratio choices include half steps and exclude filter defaults`() {
        val options = BrewMethod.ESPRESSO.calculatorRatioOptions(2f)
        assertTrue(1.5f in options && 2.5f in options)
        assertFalse(17f in options)
        assertEquals("2.5", formatCalculatorRatio(2.5f))
        assertEquals("16", formatCalculatorRatio(16f))
        assertEquals(listOf(15f, 16f, 17f), BrewMethod.V60.calculatorRatioOptions(16f))
    }

    @Test
    fun `setup encoding preserves expression and cup definitions`() {
        val setup = CalculatorSetup(
            ratio = 16f,
            quantity = CalculatorQuantityTarget.IN_CUP.name,
            tokens = listOf(
                CalcToken.Number("2"),
                CalcToken.Operator(CalcOp.MULTIPLY),
                CalcToken.PresetRef(CupPreset(name = "Home cup", iconName = "mug", doseG = 18f, waterMl = 250f)),
            ),
            grinderId = "home",
        )
        assertEquals(setup, CalculatorSetupCodec.decode(CalculatorSetupCodec.encode(setup), BrewMethod.V60))
        assertNull(CalculatorSetupCodec.decode("broken", BrewMethod.V60))
        assertNull(CalculatorSetupCodec.decode("{\"ratio\":-1}", BrewMethod.V60))
        assertNull(CalculatorSetupCodec.decode("{\"ratio\":2,\"quantity\":\"FUTURE\"}", BrewMethod.ESPRESSO))
    }

    @Test
    fun `named favorites preserve quantity expression and equipment without overwriting each other`() {
        val brew = BrewViewModel(recipeRepository = RecipeRepository(FakeRecipeDao()))
        val vm = CalculatorViewModel(repository)
        vm.setRatio(18f)
        vm.selectQuantity(CalculatorQuantityTarget.IN_CUP)
        vm.appendDigit('3')
        vm.appendDigit('0')
        vm.appendDigit('0')
        vm.setEquipment(FilterType.PAPER, "home")
        val home = vm.currentSetup()
        saveSetup(brew, vm, "Home")

        vm.clear()
        vm.setRatio(16f)
        vm.selectQuantity(CalculatorQuantityTarget.COFFEE)
        vm.appendDigit('1')
        vm.appendDigit('8')
        vm.setEquipment(FilterType.METAL_40K, "work")
        val work = vm.currentSetup()
        saveSetup(brew, vm, "Work")

        val favorites = brew.savedRecipes.value
        assertEquals(2, favorites.size)
        val homeRecipe = favorites.single { it.coffeeName == "Home" }
        val payload = BrewingPersistenceMapper.recipeRecord(homeRecipe).payload as StoredRecipePayload.Versioned
        assertEquals(home, payload.snapshot.calculatorSetup)
        assertTrue(vm.loadRecipe(homeRecipe))
        assertEquals(home, vm.currentSetup())
        assertEquals(300f, requireNotNull(vm.uiState.value.previewBeverageG), 0.01f)
        assertTrue(vm.loadRecipe(favorites.single { it.coffeeName == "Work" }))
        assertEquals(work, vm.currentSetup())
    }

    @Test
    fun `existing favorites load their saved dose and equipment and unknown methods are rejected`() {
        val vm = CalculatorViewModel(repository)
        val favorite = SavedRecipeEntity(
            method = "ESPRESSO", ratio = 2.5f, doseG = 18f, waterG = 45f, grinderId = "work",
        )
        assertTrue(vm.loadRecipe(favorite))
        assertEquals(18f, vm.uiState.value.previewDoseG, 0f)
        assertEquals(45f, requireNotNull(vm.uiState.value.previewBeverageG), 0f)
        assertEquals("work", vm.uiState.value.grinderId)
        val before = vm.currentSetup()
        assertFalse(vm.loadRecipe(favorite.copy(method = "FUTURE_METHOD")))
        assertEquals(before, vm.currentSetup())
    }

    private fun saveSetup(brew: BrewViewModel, vm: CalculatorViewModel, name: String) {
        val state = vm.uiState.value
        brew.setMethod(state.brewMethod)
        brew.setFilterType(state.filterType)
        brew.setGrinder(state.grinderId)
        brew.setCustomRatio(state.ratio.toString())
        brew.setAmount(state.previewDoseG.toString())
        brew.saveRecipe(name, vm.currentSetup())
    }
}

private class SetupCupPresetDao : CupPresetDao {
    private val data = MutableStateFlow<List<CupPresetEntity>>(emptyList())
    override fun getAll() = data
    override suspend fun getCount() = data.value.size
    override suspend fun insert(preset: CupPresetEntity): Long {
        val id = (data.value.maxOfOrNull { it.id } ?: 0L) + 1L
        data.value = data.value + preset.copy(id = id)
        return id
    }
    override suspend fun insertAll(presets: List<CupPresetEntity>) {
        presets.forEach { insert(it) }
    }
    override suspend fun update(preset: CupPresetEntity) {
        data.value = data.value.map { if (it.id == preset.id) preset else it }
    }
    override suspend fun delete(preset: CupPresetEntity) {
        data.value = data.value.filterNot { it.id == preset.id }
    }
    override suspend fun deleteAll() {
        data.value = emptyList()
    }
}

private class MemorySetupStore(initial: UserPreferences = UserPreferences()) :
    CalculatorSetupStore, BrewingSetWriter by NoBrewingSetWriter {
    override val userPreferences = MutableStateFlow(initial)

    override suspend fun updateCalculatorSetup(method: BrewMethod, setup: CalculatorSetup) {
        // Exercise the same serialized representation used by DataStore.
        val decoded = requireNotNull(CalculatorSetupCodec.decode(CalculatorSetupCodec.encode(setup), method))
        userPreferences.value = userPreferences.value.copy(
            calculatorSetups = userPreferences.value.calculatorSetups + (method to decoded),
        )
    }
}
