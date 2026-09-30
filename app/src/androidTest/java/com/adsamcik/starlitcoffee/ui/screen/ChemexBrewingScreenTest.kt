package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses in-memory UI state and never edits the app's saved preferences or inventory. */
@RunWith(AndroidJUnit4::class)
class ChemexBrewingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chemexCanBeSelectedAsTheOnlyOnboardingMethod() {
        var selected: Set<BrewMethod>? = null
        var default: BrewMethod? = null
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                OnboardingMethodsScreen(onNext = { methods, method -> selected = methods; default = method })
            }
        }
        composeRule.onNodeWithTag("onboarding_method_CHEMEX").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding_next_button").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(BrewMethod.CHEMEX), selected)
            assertEquals(BrewMethod.CHEMEX, default)
        }
    }

    @Test
    fun chemexPreparationShowsItsOwnFilterAndAirChannelInstructions() {
        val store = ViewModelStore()
        lateinit var brew: BrewViewModel
        composeRule.runOnIdle {
            brew = BrewViewModel()
            store.put("brew", brew)
            brew.setMethod(BrewMethod.CHEMEX)
            brew.setAmount("30")
        }
        try {
            composeRule.setContent {
                StarlitCoffeeTheme(dynamicColor = false) {
                    GrindPrepScreen(brew, dimModeEnabled = false, onNavigateToBrew = {}, onBack = {})
                }
            }
            composeRule.onNodeWithText("Chemex · 1:16").assertIsDisplayed()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val preparation = context.getString(R.string.prep_tip_chemex)
            preparation.split(". ").forEach { sentence ->
                composeRule.onNodeWithText(sentence.trimEnd('.'), substring = true)
                    .performScrollTo().assertIsDisplayed()
            }
        } finally {
            composeRule.runOnIdle { store.clear() }
        }
    }
}
