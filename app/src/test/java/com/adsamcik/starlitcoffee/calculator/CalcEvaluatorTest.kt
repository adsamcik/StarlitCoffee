package com.adsamcik.starlitcoffee.calculator

import com.adsamcik.starlitcoffee.calculator.CalcEvaluator.InputDirection
import com.adsamcik.starlitcoffee.data.model.CalcOp
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CupPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalcEvaluatorTest {

    private val espresso = CupPreset(
        id = 1,
        name = "Espresso",
        iconName = "espresso",
        doseG = 18f,
        waterMl = 36f,
    )
    private val mug = CupPreset(
        id = 2,
        name = "Mug",
        iconName = "mug",
        doseG = 22f,
        waterMl = 374f,
    )
    private val ratio = 17f

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Empty / Edge Cases ---

    @Test
    fun `empty expression returns zero`() {
        val result = CalcEvaluator.evaluate(emptyList(), ratio, InputDirection.WATER)
        assertEquals(0f, result.totalDoseG, 0.01f)
        assertEquals(0f, result.totalWaterMl, 0.01f)
    }

    @Test
    fun `zero ratio returns zero`() {
        val tokens = listOf(CalcToken.Number("100"))
        val result = CalcEvaluator.evaluate(tokens, 0f, InputDirection.WATER)
        assertEquals(0f, result.totalDoseG, 0.01f)
        assertEquals(0f, result.totalWaterMl, 0.01f)
    }

    // --- Simple Number ---

    @Test
    fun `single number in water mode`() {
        val tokens = listOf(CalcToken.Number("340"))
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        assertEquals(20f, result.totalDoseG, 0.01f)
        assertEquals(340f, result.totalWaterMl, 0.01f)
    }

    @Test
    fun `single number in dose mode`() {
        val tokens = listOf(CalcToken.Number("20"))
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.DOSE)
        assertEquals(20f, result.totalDoseG, 0.01f)
        assertEquals(340f, result.totalWaterMl, 0.01f)
    }

    // --- Simple Preset ---

    @Test
    fun `single preset`() {
        val tokens = listOf(CalcToken.PresetRef(espresso))
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        // Preset only sets volume (36ml); dose derived from ratio: 36/17 ≈ 2.12
        assertEquals(36f / ratio, result.totalDoseG, 0.01f)
        assertEquals(36f, result.totalWaterMl, 0.01f)
    }

    // --- Multiplication ---

    @Test
    fun `number times preset`() {
        val tokens = listOf(
            CalcToken.Number("3"),
            CalcToken.Operator(CalcOp.MULTIPLY),
            CalcToken.PresetRef(espresso),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        // 3 × espresso volume = 3 × 36 = 108ml; dose = 108/17 ≈ 6.35
        assertEquals(3f * 36f / ratio, result.totalDoseG, 0.01f)
        assertEquals(108f, result.totalWaterMl, 0.01f)
    }

    @Test
    fun `preset times number`() {
        val tokens = listOf(
            CalcToken.PresetRef(espresso),
            CalcToken.Operator(CalcOp.MULTIPLY),
            CalcToken.Number("2"),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        // 2 × espresso volume = 72ml; dose = 72/17 ≈ 4.24
        assertEquals(2f * 36f / ratio, result.totalDoseG, 0.01f)
        assertEquals(72f, result.totalWaterMl, 0.01f)
    }

    // --- Addition ---

    @Test
    fun `number plus number in water mode`() {
        val tokens = listOf(
            CalcToken.Number("200"),
            CalcToken.Operator(CalcOp.ADD),
            CalcToken.Number("100"),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        assertEquals(300f / ratio, result.totalDoseG, 0.01f)
        assertEquals(300f, result.totalWaterMl, 0.01f)
    }

    @Test
    fun `preset plus number in water mode`() {
        val tokens = listOf(
            CalcToken.PresetRef(espresso),
            CalcToken.Operator(CalcOp.ADD),
            CalcToken.Number("50"),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        // Espresso volume (36ml) dose = 36/17; plus 50ml water dose = 50/17
        assertEquals(36f / ratio + 50f / ratio, result.totalDoseG, 0.01f)
        assertEquals(36f + 50f, result.totalWaterMl, 0.01f)
    }

    // --- Compound: multiplication + addition ---

    @Test
    fun `3 times espresso plus 50 in water mode`() {
        val tokens = listOf(
            CalcToken.Number("3"),
            CalcToken.Operator(CalcOp.MULTIPLY),
            CalcToken.PresetRef(espresso),
            CalcToken.Operator(CalcOp.ADD),
            CalcToken.Number("50"),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        // 3 × espresso dose = 3×36/17; plus 50ml water dose = 50/17
        assertEquals(3f * 36f / ratio + 50f / ratio, result.totalDoseG, 0.01f)
        assertEquals(108f + 50f, result.totalWaterMl, 0.01f)
    }

    @Test
    fun `2 times mug plus 1 times espresso`() {
        val tokens = listOf(
            CalcToken.Number("2"),
            CalcToken.Operator(CalcOp.MULTIPLY),
            CalcToken.PresetRef(mug),
            CalcToken.Operator(CalcOp.ADD),
            CalcToken.Number("1"),
            CalcToken.Operator(CalcOp.MULTIPLY),
            CalcToken.PresetRef(espresso),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        // 2×mug(374ml) + 1×espresso(36ml); dose = (748+36)/17
        assertEquals((2f * 374f + 36f) / ratio, result.totalDoseG, 0.01f)
        assertEquals(784f, result.totalWaterMl, 0.01f)
    }

    // --- Edge cases ---

    @Test
    fun `trailing operator is ignored`() {
        val tokens = listOf(
            CalcToken.Number("100"),
            CalcToken.Operator(CalcOp.ADD),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        assertEquals(100f / ratio, result.totalDoseG, 0.01f)
        assertEquals(100f, result.totalWaterMl, 0.01f)
    }

    @Test
    fun `leading operator is ignored`() {
        val tokens = listOf(
            CalcToken.Operator(CalcOp.MULTIPLY),
            CalcToken.Number("100"),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        assertEquals(100f / ratio, result.totalDoseG, 0.01f)
        assertEquals(100f, result.totalWaterMl, 0.01f)
    }

    @Test
    fun `number with decimal`() {
        val tokens = listOf(CalcToken.Number("20.5"))
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.DOSE)
        assertEquals(20.5f, result.totalDoseG, 0.01f)
        assertEquals(20.5f * ratio, result.totalWaterMl, 0.01f)
    }

    @Test
    fun `multiple numbers multiplied`() {
        val tokens = listOf(
            CalcToken.Number("2"),
            CalcToken.Operator(CalcOp.MULTIPLY),
            CalcToken.Number("3"),
        )
        val result = CalcEvaluator.evaluate(tokens, ratio, InputDirection.WATER)
        assertEquals(6f / ratio, result.totalDoseG, 0.01f)
        assertEquals(6f, result.totalWaterMl, 0.01f)
    }
}
