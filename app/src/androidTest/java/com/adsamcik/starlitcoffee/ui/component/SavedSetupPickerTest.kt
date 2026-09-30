package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.starlitcoffee.data.db.entity.SavedRecipeEntity
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedSetupPickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val home = SavedRecipeEntity(
        id = 1L, coffeeName = "Home", method = "PULSAR", ratio = 17f,
        doseG = 20f, waterG = 340f, grinderId = "home", filterType = "PAPER",
    )
    private val work = home.copy(
        id = 2L, coffeeName = "Work", method = "ESPRESSO", ratio = 2.5f,
        doseG = 18f, waterG = 45f, grinderId = "work", filterType = null,
    )

    @Test
    fun namedSetupsCanBePickedRepeatedlyWithoutLeavingTheBrewScreen() {
        val selected = mutableListOf<SavedRecipeEntity>()
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                SavedSetupPicker(listOf(home, work), BrewMethod.entries.toSet(), selected::add)
            }
        }
        composeRule.onNodeWithTag("saved_setup_picker").performClick()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Work").assertIsDisplayed()
        composeRule.onNodeWithTag("saved_setup_2").performClick()
        composeRule.onNodeWithTag("saved_setup_picker").performClick()
        composeRule.onNodeWithTag("saved_setup_1").performClick()
        composeRule.runOnIdle { assertEquals(listOf(work, home), selected) }
    }

    @Test
    fun disabledAndUnknownMethodsAreNotOffered() {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                SavedSetupPicker(
                    listOf(home, work, home.copy(id = 3L, method = "FUTURE")),
                    setOf(BrewMethod.PULSAR),
                    {},
                )
            }
        }
        composeRule.onNodeWithTag("saved_setup_picker").performClick()
        composeRule.onNodeWithTag("saved_setup_1").assertIsDisplayed()
        composeRule.onNodeWithTag("saved_setup_2").assertDoesNotExist()
        composeRule.onNodeWithTag("saved_setup_3").assertDoesNotExist()
    }

    @Test
    fun noSavedSetupsAddsNoControlToTheCoreFlow() {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                SavedSetupPicker(emptyList(), BrewMethod.entries.toSet(), {})
            }
        }
        composeRule.onNodeWithTag("saved_setup_picker").assertDoesNotExist()
    }
}
