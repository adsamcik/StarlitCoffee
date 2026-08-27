package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrewQuantitySummaryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summaryDistinguishesPlannedEstimatedAndMeasuredValues() {
        val actions = AtomicInteger(0)
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                BrewQuantitySummary(
                    estimatedCoffeeDoseG = 20f,
                    estimatedWaterInputG = 340f,
                    estimatedBeverageOutputG = 296f,
                    measuredWaterInputG = 338.6f,
                    measuredBeverageOutputG = 294.2f,
                    onEditMeasurements = { actions.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithTag(BrewQuantitiesCoffeeTestTag)
            .assertContentDescriptionEquals("Coffee, 20 grams, planned")
        composeRule.onNodeWithTag(BrewQuantitiesWaterTestTag)
            .assertContentDescriptionEquals("Water in, 338.6 grams, measured; planned 340 grams")
        composeRule.onNodeWithTag(BrewQuantitiesBeverageTestTag)
            .assertContentDescriptionEquals("In cup, 294.2 grams, measured; estimate 296 grams")
        composeRule.onNodeWithTag(BrewQuantitiesMeasurementActionTestTag).performClick()
        composeRule.runOnIdle { assertEquals(1, actions.get()) }
    }

    @Test
    fun largeTextStacksQuantityItemsWithoutOverlap() {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 1.3f),
                ) {
                    Box(modifier = Modifier.width(320.dp)) {
                        BrewQuantitySummary(
                            estimatedCoffeeDoseG = 20f,
                            estimatedWaterInputG = 340f,
                            estimatedBeverageOutputG = 296f,
                            measuredWaterInputG = null,
                            measuredBeverageOutputG = null,
                            onEditMeasurements = {},
                        )
                    }
                }
            }
        }

        val coffee = composeRule.onNodeWithTag(BrewQuantitiesCoffeeTestTag)
            .fetchSemanticsNode().boundsInRoot
        val water = composeRule.onNodeWithTag(BrewQuantitiesWaterTestTag)
            .fetchSemanticsNode().boundsInRoot
        val beverage = composeRule.onNodeWithTag(BrewQuantitiesBeverageTestTag)
            .fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            assertTrue(coffee.bottom <= water.top)
            assertTrue(water.bottom <= beverage.top)
            assertEquals(coffee.width, water.width, 1f)
            assertEquals(water.width, beverage.width, 1f)
        }
    }

    @Test
    fun ordinaryPhoneWidthKeepsTheThreeColumnSummary() {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                Box(modifier = Modifier.width(320.dp)) {
                    BrewQuantitySummary(
                        estimatedCoffeeDoseG = 20f,
                        estimatedWaterInputG = 340f,
                        estimatedBeverageOutputG = 296f,
                        measuredWaterInputG = null,
                        measuredBeverageOutputG = null,
                        onEditMeasurements = {},
                    )
                }
            }
        }

        val coffee = composeRule.onNodeWithTag(BrewQuantitiesCoffeeTestTag)
            .fetchSemanticsNode().boundsInRoot
        val water = composeRule.onNodeWithTag(BrewQuantitiesWaterTestTag)
            .fetchSemanticsNode().boundsInRoot
        val beverage = composeRule.onNodeWithTag(BrewQuantitiesBeverageTestTag)
            .fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            assertEquals(coffee.top, water.top, 1f)
            assertEquals(water.top, beverage.top, 1f)
            assertTrue(coffee.right <= water.left)
            assertTrue(water.right <= beverage.left)
        }
    }

    @Test
    fun directYieldShowsPlannedCupWithoutWaterOrCalibrationAction() {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                Box(modifier = Modifier.width(320.dp)) {
                    BrewQuantitySummary(
                        estimatedCoffeeDoseG = 20f,
                        estimatedWaterInputG = null,
                        estimatedBeverageOutputG = 40f,
                        measuredWaterInputG = null,
                        measuredBeverageOutputG = null,
                        beverageOutputIsEstimate = false,
                        onEditMeasurements = null,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(BrewQuantitiesCoffeeTestTag)
            .assertContentDescriptionEquals("Coffee, 20 grams, planned")
        composeRule.onNodeWithTag(BrewQuantitiesWaterTestTag).assertDoesNotExist()
        composeRule.onNodeWithTag(BrewQuantitiesBeverageTestTag)
            .assertContentDescriptionEquals("In cup, 40 grams, planned")
        composeRule.onNodeWithTag(BrewQuantitiesMeasurementActionTestTag).assertDoesNotExist()
    }
}
