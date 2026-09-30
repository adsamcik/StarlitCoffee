package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun editingRequiresANameAndShowsOnlySupportedEquipment() {
        var saved: BrewingSet? = null
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            BrewingSetEditor(BrewingSet("new", method = BrewMethod.PULSAR), true, data, false,
                onSave = { saved = it }, onDismiss = {})
        } }
        composeRule.onNodeWithTag("save_brewing_set").assertIsNotEnabled()
        composeRule.onNodeWithTag("brewing_set_name").performTextInput("Work espresso")
        composeRule.onNodeWithTag("brewing_set_method").performClick()
        composeRule.onNodeWithTag("brewing_set_method_option_ESPRESSO").performClick()
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
        composeRule.onNodeWithTag("edit_brewing_set_only").assertExists()
    }
}
