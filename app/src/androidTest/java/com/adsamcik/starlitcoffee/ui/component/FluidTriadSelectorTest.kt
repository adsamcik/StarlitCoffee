package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class FluidTriadSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allDirectedTransitionsUpdateSelectionImmediately() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(CalculatorQuantityTarget.COFFEE) }
            StarlitCoffeeTheme(dynamicColor = false) {
                TestTriad(
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }

        val transitions = listOf(
            CalculatorQuantityTarget.COFFEE to CalculatorQuantityTarget.WATER_IN,
            CalculatorQuantityTarget.WATER_IN to CalculatorQuantityTarget.COFFEE,
            CalculatorQuantityTarget.WATER_IN to CalculatorQuantityTarget.IN_CUP,
            CalculatorQuantityTarget.IN_CUP to CalculatorQuantityTarget.WATER_IN,
            CalculatorQuantityTarget.COFFEE to CalculatorQuantityTarget.IN_CUP,
            CalculatorQuantityTarget.IN_CUP to CalculatorQuantityTarget.COFFEE,
        )

        transitions.forEach { (_, target) ->
            composeRule.onNodeWithTag(tag(target)).performClick()
            composeRule.onNodeWithTag(tag(target)).assertIsSelected()
        }
    }

    @Test
    fun rapidRetargetingLeavesLastTappedQuantitySelected() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(CalculatorQuantityTarget.COFFEE) }
            StarlitCoffeeTheme(dynamicColor = false) {
                TestTriad(
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN)).performClick()
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN)).assertIsSelected()
        composeRule.mainClock.advanceTimeBy(48L)
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).performClick()
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).assertIsSelected()
        composeRule.mainClock.advanceTimeBy(48L)
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE)).performClick()
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE)).assertIsSelected()
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE)).assertIsSelected()
    }

    @Test
    fun tappingSelectedItemDoesNotRestartLogicalSelection() {
        val callbacks = AtomicInteger(0)
        composeRule.setContent {
            var selected by remember { mutableStateOf(CalculatorQuantityTarget.COFFEE) }
            StarlitCoffeeTheme(dynamicColor = false) {
                TestTriad(
                    selected = selected,
                    onSelect = {
                        callbacks.incrementAndGet()
                        selected = it
                    },
                )
            }
        }

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE)).performClick()
        composeRule.runOnIdle {
            assertEquals(0, callbacks.get())
        }
    }

    @Test
    fun unavailableCupTargetIsDisabled() {
        val callbacks = AtomicInteger(0)
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                TestTriad(
                    selected = CalculatorQuantityTarget.WATER_IN,
                    onSelect = { callbacks.incrementAndGet() },
                    cupEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).assertIsNotEnabled()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).performClick()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN)).assertIsSelected()
        composeRule.runOnIdle { assertEquals(0, callbacks.get()) }
    }

    @Test
    fun rtlKeepsLogicalCellsSelectable() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(CalculatorQuantityTarget.COFFEE) }
            StarlitCoffeeTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TestTriad(
                        selected = selected,
                        onSelect = { selected = it },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).performClick()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).assertIsSelected()
    }

    @Composable
    private fun TestTriad(
        selected: CalculatorQuantityTarget,
        onSelect: (CalculatorQuantityTarget) -> Unit,
        cupEnabled: Boolean = true,
    ) {
        FluidTriadSelector(
            items = listOf(
                FluidTriadItem(
                    target = CalculatorQuantityTarget.COFFEE,
                    label = "Coffee",
                    value = "20g",
                    icon = CalculationQuantityIconType.COFFEE_DOSE,
                ),
                FluidTriadItem(
                    target = CalculatorQuantityTarget.WATER_IN,
                    label = "Water in",
                    value = "320g",
                    icon = CalculationQuantityIconType.WATER_IN,
                ),
                FluidTriadItem(
                    target = CalculatorQuantityTarget.IN_CUP,
                    label = "In cup",
                    value = "≈278g",
                    icon = CalculationQuantityIconType.CUP_OUTPUT,
                    enabled = cupEnabled,
                ),
            ),
            selected = selected,
            onSelect = onSelect,
            idleMotionEnabled = false,
        )
    }

    private fun tag(target: CalculatorQuantityTarget): String =
        "fluid_triad_${target.name.lowercase()}"
}
