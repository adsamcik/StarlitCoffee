package com.adsamcik.starlitcoffee.viewmodel

import com.adsamcik.starlitcoffee.calculator.CalcEvaluator.InputDirection
import com.adsamcik.starlitcoffee.data.db.dao.CupPresetDao
import com.adsamcik.starlitcoffee.data.db.entity.CupPresetEntity
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CupPreset
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private lateinit var viewModel: CalculatorViewModel
    private lateinit var fakeDao: FakeCupPresetDao
    private lateinit var repository: CupPresetRepository

    private val espresso = CupPreset(
        id = 1L,
        name = "Espresso",
        iconName = "espresso",
        doseG = 18f,
        waterMl = 36f,
    )
    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeDao = FakeCupPresetDao()
        repository = CupPresetRepository(fakeDao)
        viewModel = CalculatorViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Digit Input ---

    @Test
    fun `append single digit creates number token`() {
        viewModel.appendDigit('5')

        val state = viewModel.uiState.value
        assertEquals(1, state.tokens.size)
        assertEquals(CalcToken.Number("5"), state.tokens[0])
    }

    @Test
    fun `append multiple digits builds multi-digit number`() {
        viewModel.appendDigit('3')
        viewModel.appendDigit('4')
        viewModel.appendDigit('0')

        val state = viewModel.uiState.value
        assertEquals(1, state.tokens.size)
        assertEquals(CalcToken.Number("340"), state.tokens[0])
    }

    @Test
    fun `append decimal point`() {
        viewModel.appendDigit('2')
        viewModel.appendDigit('0')
        viewModel.appendDecimal()
        viewModel.appendDigit('5')

        val state = viewModel.uiState.value
        assertEquals(1, state.tokens.size)
        assertEquals(CalcToken.Number("20.5"), state.tokens[0])
    }

    @Test
    fun `double decimal point is ignored`() {
        viewModel.appendDigit('2')
        viewModel.appendDecimal()
        viewModel.appendDecimal()

        val state = viewModel.uiState.value
        assertEquals(CalcToken.Number("2."), state.tokens[0])
    }

    // --- Operator Input ---

    @Test
    fun `append operator after number`() {
        viewModel.appendDigit('3')
        viewModel.appendOperator(CalcOp.MULTIPLY)

        val state = viewModel.uiState.value
        assertEquals(2, state.tokens.size)
        assertEquals(CalcToken.Operator(CalcOp.MULTIPLY), state.tokens[1])
    }

    @Test
    fun `consecutive operators replace previous`() {
        viewModel.appendDigit('3')
        viewModel.appendOperator(CalcOp.MULTIPLY)
        viewModel.appendOperator(CalcOp.ADD)

        val state = viewModel.uiState.value
        assertEquals(2, state.tokens.size)
        assertEquals(CalcToken.Operator(CalcOp.ADD), state.tokens[1])
    }

    @Test
    fun `operator on empty expression is ignored`() {
        viewModel.appendOperator(CalcOp.ADD)

        val state = viewModel.uiState.value
        assertTrue(state.tokens.isEmpty())
    }

    // --- Preset Input ---

    @Test
    fun `append preset creates token`() {
        viewModel.appendPreset(espresso)

        val state = viewModel.uiState.value
        assertEquals(1, state.tokens.size)
        assertTrue(state.tokens[0] is CalcToken.PresetRef)
    }

    @Test
    fun `append preset after number auto-inserts add`() {
        viewModel.appendDigit('5')
        viewModel.appendPreset(espresso)

        val state = viewModel.uiState.value
        assertEquals(3, state.tokens.size)
        assertEquals(CalcToken.Operator(CalcOp.ADD), state.tokens[1])
    }

    // --- Backspace ---

    @Test
    fun `backspace removes digit from multi-digit number`() {
        viewModel.appendDigit('3')
        viewModel.appendDigit('4')
        viewModel.backspace()

        val state = viewModel.uiState.value
        assertEquals(CalcToken.Number("3"), state.tokens[0])
    }

    @Test
    fun `backspace removes single-char token entirely`() {
        viewModel.appendDigit('3')
        viewModel.backspace()

        val state = viewModel.uiState.value
        assertTrue(state.tokens.isEmpty())
    }

    @Test
    fun `backspace on empty does nothing`() {
        viewModel.backspace()

        val state = viewModel.uiState.value
        assertTrue(state.tokens.isEmpty())
    }

    // --- Clear ---

    @Test
    fun `clear resets everything`() {
        viewModel.appendDigit('3')
        viewModel.appendOperator(CalcOp.MULTIPLY)
        viewModel.appendPreset(espresso)
        viewModel.clear()

        val state = viewModel.uiState.value
        assertTrue(state.tokens.isEmpty())
        assertEquals(0f, state.previewDoseG, 0.01f)
        assertEquals(0f, state.previewWaterMl, 0.01f)
        assertFalse(state.hasValidExpression)
    }

    // --- Live Preview ---

    @Test
    fun `live preview updates in water mode`() {
        viewModel.toggleDirection()
        assertEquals(InputDirection.WATER, viewModel.uiState.value.inputDirection)

        viewModel.appendDigit('3')
        viewModel.appendDigit('4')
        viewModel.appendDigit('0')

        val state = viewModel.uiState.value
        assertEquals(340f, state.previewWaterMl, 0.01f)
        assertEquals(20f, state.previewDoseG, 0.01f)
    }

    @Test
    fun `live preview updates for preset multiplication`() {
        viewModel.appendDigit('3')
        viewModel.appendOperator(CalcOp.MULTIPLY)
        viewModel.appendPreset(espresso)

        val state = viewModel.uiState.value
        // Preset only sets volume: 3 × 36ml = 108ml; dose = 108/17 ≈ 6.35
        assertEquals(3f * 36f / 17f, state.previewDoseG, 0.01f)
        assertEquals(108f, state.previewWaterMl, 0.01f)
    }

    // --- Direction Toggle ---

    @Test
    fun `toggle direction switches and recalculates`() {
        viewModel.appendDigit('2')
        viewModel.appendDigit('0')

        // Default direction is DOSE: "20" is 20g of coffee → 340ml water at 1:17.
        assertEquals(InputDirection.DOSE, viewModel.uiState.value.inputDirection)
        assertEquals(20f, viewModel.uiState.value.previewDoseG, 0.01f)
        assertEquals(340f, viewModel.uiState.value.previewWaterMl, 0.01f)

        viewModel.toggleDirection()

        // After toggle: "20" is 20ml water → ~1.18g dose at 1:17.
        assertEquals(InputDirection.WATER, viewModel.uiState.value.inputDirection)
        assertEquals(20f, viewModel.uiState.value.previewWaterMl, 0.01f)
        assertEquals(20f / 17f, viewModel.uiState.value.previewDoseG, 0.01f)
    }

    // --- Ratio ---

    @Test
    fun `set ratio recalculates preview`() {
        viewModel.appendDigit('2')
        viewModel.appendDigit('0')
        viewModel.setRatio(10f)

        // Default direction DOSE: 20g coffee × 1:10 → 200ml water.
        assertEquals(200f, viewModel.uiState.value.previewWaterMl, 0.01f)
        assertEquals(20f, viewModel.uiState.value.previewDoseG, 0.01f)
    }

    @Test
    fun `set zero ratio is ignored`() {
        viewModel.setRatio(0f)

        assertEquals(17f, viewModel.uiState.value.ratio, 0.01f)
    }

    @Test
    fun `has valid expression is true when result is non-zero`() {
        viewModel.appendDigit('1')

        assertTrue(viewModel.uiState.value.hasValidExpression)
    }

    @Test
    fun `has valid expression is false when empty`() {
        assertFalse(viewModel.uiState.value.hasValidExpression)
    }

    // --- Fake DAO ---

    private class FakeCupPresetDao : CupPresetDao {
        private val data = MutableStateFlow<List<CupPresetEntity>>(emptyList())
        private var nextId = 1L

        override fun getAll(): Flow<List<CupPresetEntity>> = data

        override suspend fun getCount(): Int = data.value.size

        override suspend fun insert(preset: CupPresetEntity): Long {
            val id = nextId++
            val withId = preset.copy(id = id)
            data.value = data.value + withId
            return id
        }

        override suspend fun insertAll(presets: List<CupPresetEntity>) {
            val newEntities = presets.map { it.copy(id = nextId++) }
            data.value = data.value + newEntities
        }

        override suspend fun update(preset: CupPresetEntity) {
            data.value = data.value.map { entity ->
                if (entity.id == preset.id) preset else entity
            }
        }

        override suspend fun delete(preset: CupPresetEntity) {
            data.value = data.value.filter { it.id != preset.id }
        }

        override suspend fun deleteAll() {
            data.value = emptyList()
        }
    }
}
