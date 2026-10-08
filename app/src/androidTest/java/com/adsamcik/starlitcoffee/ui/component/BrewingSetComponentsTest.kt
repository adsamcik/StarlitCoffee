package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrewingSetComponentsTest {
    @get:Rule val composeRule = createComposeRule()
    private val data get() = GrinderDataSource.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun unnamedFirstSetAndNamedSetsCanBeChosenRepeatedly() {
        val home = BrewingSet("home", method = BrewMethod.PULSAR)
        val work = BrewingSet("work", "Work", BrewMethod.PULSAR)
        val choices = mutableListOf<String>()
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            BrewingSetPicker(listOf(home, work), home.id, data, choices::add)
        } }
        composeRule.onNodeWithTag("brewing_set_picker").performClick()
        composeRule.onNodeWithTag("choose_brewing_set_work").performClick()
        composeRule.onNodeWithTag("brewing_set_picker").performClick()
        composeRule.onNodeWithTag("choose_brewing_set_home").performClick()
        composeRule.runOnIdle { assertEquals(listOf("work", "home"), choices) }
    }

    @Test
    fun optionalNameAndContextualEquipmentKeepCreationUsable() {
        var saved: BrewingSet? = null
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            BrewingSetEditor(BrewingSet("new", method = BrewMethod.PULSAR), true, data, false,
                onSave = { saved = it }, onDismiss = {})
        } }
        composeRule.onNodeWithTag("save_brewing_set").assertIsEnabled()
        composeRule.onNodeWithTag("brewing_set_name").performTextInput("Work espresso")
        composeRule.onNodeWithTag("brewing_set_method").performClick()
        composeRule.onNodeWithTag("brewing_set_method_option_ESPRESSO").performScrollTo().performClick()
        composeRule.onNodeWithTag("brewing_set_filter").assertDoesNotExist()
        composeRule.onNodeWithTag("save_brewing_set").performClick()
        composeRule.runOnIdle {
            assertEquals("Work espresso", saved?.name)
            assertEquals(BrewMethod.ESPRESSO, saved?.method)
            assertEquals(2f, requireNotNull(saved).setup.ratio, 0f)
        }
    }

    @Test
    fun choosingTheCurrentMethodPreservesTheFilter() {
        var saved: BrewingSet? = null
        val set = BrewingSet("metal", "Home", BrewMethod.PULSAR, CalculatorSetup(17f, filterType = "METAL_19K"))
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            BrewingSetEditor(set, false, data, false, onSave = { saved = it }, onDismiss = {})
        } }
        composeRule.onNodeWithTag("brewing_set_method").performClick()
        composeRule.onNodeWithTag("brewing_set_method_option_PULSAR").performClick()
        composeRule.onNodeWithTag("brewing_set_grinder").assertDoesNotExist()
        composeRule.onNodeWithTag("save_brewing_set").performClick()
        composeRule.runOnIdle { assertEquals("METAL_19K", saved?.setup?.filterType) }
    }

    @Test
    fun lastSetHasNoDeleteAction() {
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            BrewingSetsSettings(listOf(BrewingSet("only", method = BrewMethod.ESPRESSO)), "only", data, true,
                onSelect = {}, onEdit = {}, onDelete = {}, onAdd = {})
        } }
        composeRule.onNodeWithTag("delete_brewing_set_only").assertDoesNotExist()
        composeRule.onNodeWithTag("brewing_set_options_only").performClick()
        composeRule.onNodeWithTag("edit_brewing_set_only").assertExists()
        composeRule.onNodeWithTag("delete_brewing_set_only").assertDoesNotExist()
    }

    @Test
    fun unnamedSetCanBeCreatedWithoutTyping() {
        var saved: BrewingSet? = null
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            BrewingSetEditor(BrewingSet("new", method = BrewMethod.ESPRESSO, setup = initialBrewingSetup(BrewMethod.ESPRESSO)),
                true, data, false, onSave = { saved = it }, onDismiss = {})
        } }
        composeRule.onNodeWithTag("save_brewing_set").performClick()
        composeRule.runOnIdle {
            assertEquals(null, saved?.name)
            assertEquals(2f, requireNotNull(saved).setup.ratio, 0f)
        }
    }

    @Test
    fun explicitRecipeChangesUseTheRecipeSaveContract() {
        var saved: BrewingSet? = null
        val set = BrewingSet("home", method = BrewMethod.PULSAR, setup = initialBrewingSetup(BrewMethod.PULSAR))
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            BrewingSetEditor(set, false, data, false, onSave = { error("Recipe change lost its intent") },
                onSaveRecipe = { saved = it }, onDismiss = {})
        } }
        composeRule.onNodeWithTag("brewing_set_recipe").performScrollTo().performClick()
        composeRule.onNodeWithTag("brewing_set_ratio").performScrollTo().performTextReplacement("16")
        composeRule.onNodeWithTag("brewing_set_amount").performScrollTo().performTextReplacement("25")
        composeRule.onNodeWithTag("save_brewing_set").performClick()
        composeRule.runOnIdle {
            assertEquals(16f, requireNotNull(saved).setup.ratio, 0f)
            assertEquals("25", (saved?.setup?.tokens?.single() as com.adsamcik.starlitcoffee.data.model.CalcToken.Number).value)
        }
    }

    @Test
    fun keyboardAndDoubleTextSizeKeepTheSaveActionReachable() {
        var saved: BrewingSet? = null
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                BrewingSetEditor(BrewingSet("large", method = BrewMethod.ESPRESSO,
                    setup = initialBrewingSetup(BrewMethod.ESPRESSO)), true, data, false,
                    onSave = { saved = it }, onDismiss = {})
            }
        } }
        composeRule.onNodeWithTag("brewing_set_name").performScrollTo().performTextInput("Home")
        composeRule.onNodeWithTag("save_brewing_set").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("Home", saved?.name) }
    }

    @Test
    fun backReturnsFromEquipmentChoicesBeforeDismissingEditor() {
        var dismissals = 0
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        composeRule.runOnIdle { dispatcher.addInput(input) }
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                BrewingSetEditor(BrewingSet("new", method = BrewMethod.PULSAR), true, data, false,
                    onSave = {}, onDismiss = { dismissals++ })
            }
        } }
        composeRule.onNodeWithTag("brewing_set_method").performClick()
        composeRule.runOnIdle { input.backCompleted() }
        composeRule.waitUntil(5_000) {
            dismissals > 0 || composeRule.onAllNodesWithTag("brewing_set_method").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { assertEquals(0, dismissals) }
        composeRule.onNodeWithTag("brewing_set_method").assertExists()
        composeRule.runOnIdle { input.backCompleted() }
        composeRule.waitUntil(5_000) { dismissals == 1 }
    }
}
