package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CalculatorQuantitySelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingEachCardSelectsItsQuantity() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(CalculatorQuantityTarget.COFFEE) }
            StarlitCoffeeTheme(dynamicColor = false) {
                TestSelector(
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }

        listOf(
            CalculatorQuantityTarget.WATER_IN,
            CalculatorQuantityTarget.IN_CUP,
            CalculatorQuantityTarget.COFFEE,
        ).forEach { target ->
            composeRule.onNodeWithTag(tag(target)).performClick()
            composeRule.onNodeWithTag(tag(target)).assertIsSelected()
        }
    }

    @Test
    fun selectedCardIsIdempotentAndUnavailableQuantitiesReflow() {
        val callbacks = AtomicInteger(0)
        var cupEnabled by mutableStateOf(true)
        var waterEnabled by mutableStateOf(true)
        var selected by mutableStateOf(CalculatorQuantityTarget.WATER_IN)
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                TestSelector(
                    selected = selected,
                    onSelect = { callbacks.incrementAndGet() },
                    cupEnabled = cupEnabled,
                    waterEnabled = waterEnabled,
                )
            }
        }

        val threeCardWidth = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE))
            .fetchSemanticsNode().boundsInRoot.width
        composeRule.runOnIdle { cupEnabled = false }
        val coffeeNode = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE))
        coffeeNode.assertIsEnabled()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN))
            .assertIsSelected()
            .performClick()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).assertDoesNotExist()
        val coffeeBounds = coffeeNode.fetchSemanticsNode().boundsInRoot
        val waterBounds = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN))
            .fetchSemanticsNode().boundsInRoot
        val selectorBounds = composeRule.onNodeWithTag("quantity_card_selector")
            .fetchSemanticsNode().boundsInRoot
        composeRule.runOnIdle {
            assertTrue(coffeeBounds.width > threeCardWidth)
            assertEquals(coffeeBounds.width, waterBounds.width, 1f)
            assertEquals(selectorBounds.left, coffeeBounds.left, 1f)
            assertEquals(selectorBounds.right, waterBounds.right, 1f)
            waterEnabled = false
            cupEnabled = true
            selected = CalculatorQuantityTarget.IN_CUP
        }
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN)).assertDoesNotExist()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP))
            .assertIsSelected()
            .performClick()
        val cupBounds = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP))
            .fetchSemanticsNode().boundsInRoot
        assertEquals(selectorBounds.right, cupBounds.right, 1f)
        composeRule.runOnIdle { assertEquals(0, callbacks.get()) }
    }

    @Test
    fun cardsExposeCompleteSpokenLabels() {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                TestSelector(
                    selected = CalculatorQuantityTarget.COFFEE,
                    onSelect = {},
                )
            }
        }

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE))
            .assertContentDescriptionEquals("Coffee, 20 g")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.RadioButton,
                ),
            )
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN))
            .assertContentDescriptionEquals("Water in, 320 g")
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP))
            .assertContentDescriptionEquals("In cup, about 278 g")
    }

    @Test
    fun rtlPreservesLogicalSelection() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(CalculatorQuantityTarget.COFFEE) }
            StarlitCoffeeTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TestSelector(
                        selected = selected,
                        onSelect = { selected = it },
                        compact = true,
                        waterEnabled = false,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).performClick()
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP)).assertIsSelected()

        val coffeeLeft = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE))
            .fetchSemanticsNode().boundsInRoot.left
        composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN)).assertDoesNotExist()
        val cupLeft = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP))
            .fetchSemanticsNode().boundsInRoot.left
        composeRule.runOnIdle {
            assertTrue(coffeeLeft > cupLeft)
        }
    }

    @Test
    fun enlargedTextUsesReadableNonOverlappingCards() {
        var cupEnabled by mutableStateOf(true)
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 1.3f),
                ) {
                    Box(modifier = Modifier.width(320.dp)) {
                        TestSelector(
                            selected = CalculatorQuantityTarget.COFFEE,
                            onSelect = {},
                            waterLabel = "Sisään tuleva vesi",
                            modifier = Modifier.fillMaxWidth(),
                            cupEnabled = cupEnabled,
                        )
                    }
                }
            }
        }

        val coffeeNode = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.COFFEE))
        val waterNode = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.WATER_IN))
        val cupNode = composeRule.onNodeWithTag(tag(CalculatorQuantityTarget.IN_CUP))
        val coffeeBounds = coffeeNode.fetchSemanticsNode().boundsInRoot
        val waterBounds = waterNode.fetchSemanticsNode().boundsInRoot
        val cupBounds = cupNode.fetchSemanticsNode().boundsInRoot

        coffeeNode.assertHeightIsAtLeast(48.dp)
        waterNode.assertHeightIsAtLeast(48.dp)
        cupNode.assertHeightIsAtLeast(48.dp)
        composeRule.runOnIdle {
            assertTrue(coffeeBounds.bottom <= waterBounds.top)
            assertTrue(waterBounds.bottom <= cupBounds.top)
            assertEquals(coffeeBounds.width, waterBounds.width, 1f)
            assertEquals(waterBounds.width, cupBounds.width, 1f)
            cupEnabled = false
        }
        cupNode.assertDoesNotExist()
        val selectorBounds = composeRule.onNodeWithTag("quantity_card_selector")
            .fetchSemanticsNode().boundsInRoot
        val remainingWaterBounds = waterNode.fetchSemanticsNode().boundsInRoot
        assertEquals(remainingWaterBounds.bottom, selectorBounds.bottom, 1f)
        assertTrue(selectorBounds.bottom < cupBounds.bottom)
    }

    @Composable
    private fun TestSelector(
        selected: CalculatorQuantityTarget,
        onSelect: (CalculatorQuantityTarget) -> Unit,
        modifier: Modifier = Modifier,
        cupEnabled: Boolean = true,
        waterEnabled: Boolean = true,
        compact: Boolean = false,
        waterLabel: String = "Water in",
    ) {
        CalculatorQuantitySelector(
            items = listOf(
                CalculatorQuantityCardItem(
                    target = CalculatorQuantityTarget.COFFEE,
                    label = "Coffee",
                    value = "20",
                    spokenValue = "20 g",
                    icon = CalculationQuantityIconType.COFFEE_DOSE,
                ),
                CalculatorQuantityCardItem(
                    target = CalculatorQuantityTarget.WATER_IN,
                    label = waterLabel,
                    value = "320",
                    spokenValue = "320 g",
                    icon = CalculationQuantityIconType.WATER_IN,
                    enabled = waterEnabled,
                ),
                CalculatorQuantityCardItem(
                    target = CalculatorQuantityTarget.IN_CUP,
                    label = "In cup",
                    value = "278",
                    spokenValue = "about 278 g",
                    icon = CalculationQuantityIconType.CUP_OUTPUT,
                    approximate = true,
                    enabled = cupEnabled,
                ),
            ),
            selected = selected,
            onSelect = onSelect,
            modifier = modifier,
            compact = compact,
        )
    }

    private fun tag(target: CalculatorQuantityTarget): String =
        "quantity_card_${target.name.lowercase()}"
}
