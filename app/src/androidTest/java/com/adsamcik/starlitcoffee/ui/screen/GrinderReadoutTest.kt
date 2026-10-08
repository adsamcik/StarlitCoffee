package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GrinderReadoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun odeDisplaysPrintedSubdivisionsRatherThanDecimalPositions() {
        verifyPreparation("fellow-ode-gen2", BrewMethod.PULSAR, FilterType.PAPER,
            "5.2", "5 + 2 clicks", "start 5.2 · ±1 click to taste")
    }

    @Test
    fun encoreEspDisplaysItsPublishedSingleStartingPoint() {
        verifyPreparation("baratza-encore-esp", BrewMethod.V60, null,
            "25", "on dial", "start 25 · ±1 click to taste")
    }

    private fun verifyPreparation(id: String, method: BrewMethod, filter: FilterType?,
        mark: String, breakdown: String, caption: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ViewModelStore()
        val brew = BrewViewModel(grinderData = GrinderDataSource.getInstance(context))
        store.put("brew", brew)
        try {
            brew.setMethod(method)
            brew.setFilterType(filter)
            brew.setGrinder(id)
            composeRule.setContent {
                StarlitCoffeeTheme(dynamicColor = false) {
                    GrindPrepScreen(brew, dimModeEnabled = false,
                        showBrewingInstructions = false, onNavigateToBrew = {}, onBack = {})
                }
            }
            composeRule.onNodeWithText(mark).assertIsDisplayed()
            composeRule.onNodeWithText(breakdown).assertIsDisplayed()
            composeRule.onNodeWithText(caption, substring = true).assertIsDisplayed()
        } finally {
            composeRule.runOnIdle { store.clear() }
        }
    }
}
