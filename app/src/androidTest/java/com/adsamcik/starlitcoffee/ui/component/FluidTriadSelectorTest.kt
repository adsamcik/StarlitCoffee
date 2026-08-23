package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CoffeeMaker
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN)).performClick()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).performClick()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE)).performClick()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE)).assertIsSelected()
    }

    @Test
    fun tappingSelectedItemDoesNotRestartLogicalSelection() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(CalculatorQuantityTarget.COFFEE) }
            var callbacks by remember { mutableIntStateOf(0) }
            StarlitCoffeeTheme(dynamicColor = false) {
                TestTriad(
                    selected = selected,
                    onSelect = {
                        callbacks += 1
                        selected = it
                    },
                )
            }
        }

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE)).performClick()
        composeRule.runOnIdle {
            // Same-item tap is consumed as a normal press response without a logical retarget.
        }
    }

    @Test
    fun unavailableCupTargetIsDisabled() {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                TestTriad(
                    selected = CalculatorQuantityTarget.WATER_IN,
                    onSelect = {},
                    cupEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).assertIsNotEnabled()
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
                    icon = Icons.Filled.LocalCafe,
                ),
                FluidTriadItem(
                    target = CalculatorQuantityTarget.WATER_IN,
                    label = "Water in",
                    value = "320g",
                    icon = Icons.Filled.WaterDrop,
                ),
                FluidTriadItem(
                    target = CalculatorQuantityTarget.IN_CUP,
                    label = "In cup",
                    value = "≈278g",
                    icon = Icons.Filled.CoffeeMaker,
                    enabled = cupEnabled,
                ),
            ),
            selected = selected,
            onSelect = onSelect,
        )
    }

    private fun tag(target: CalculatorQuantityTarget): String =
        "fluid_triad_${target.name.lowercase()}"
}
