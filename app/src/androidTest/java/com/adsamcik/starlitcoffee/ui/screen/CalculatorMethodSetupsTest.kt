package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.model.grindersFor
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.RecipeRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculatorMethodSetupsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun methodRatiosNamedFavoritesAndRestartRestoreTheWholeSetup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = UserPreferencesRepository(context)
        // This test is run on a disposable emulator. Seed only calculator state,
        // keeping the test independent of a previous run's remembered values.
        runBlocking {
            preferences.updateMethodSelection(BrewMethod.entries.toSet(), BrewMethod.PULSAR)
            BrewMethod.entries.forEach { method ->
                preferences.updateCalculatorSetup(method, CalculatorSetup(ratio = method.defaultRatio))
            }
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val presets = CupPresetRepository(database.cupPresetDao())
        val store = ViewModelStore()
        val calculator = CalculatorViewModel(presets, preferences)
        val grinderData = GrinderDataSource.getInstance(context)
        val brew = BrewViewModel(recipeRepository = RecipeRepository(database.recipeDao()), grinderData = grinderData)
        store.put("calculator", calculator)
        store.put("brew", brew)
        try {
            composeRule.setContent {
                StarlitCoffeeTheme(dynamicColor = false) {
                    CalculatorBrewScreen(
                        calculatorViewModel = calculator,
                        brewViewModel = brew,
                        userPreferencesRepository = preferences,
                        onNavigateToBrew = {},
                        recoverableSessionId = null,
                        onResumeSession = {},
                        onNavigateToBarcode = {},
                        onNavigateToBags = {},
                    )
                }
            }
            composeRule.waitUntil(10_000) { calculator.uiState.value.preferencesLoaded }
            composeRule.onNodeWithText("Pulsar").performClick()
            composeRule.onNodeWithText("Espresso").performClick()
            composeRule.runOnIdle { assertEquals(2f, calculator.uiState.value.ratio, 0f) }
            composeRule.onNodeWithTag("quantity_card_water_in").assertDoesNotExist()
            composeRule.onNodeWithTag("quantity_card_in_cup").assertIsEnabled().performClick()
            composeRule.onNodeWithText("1:2").performClick()
            composeRule.onNodeWithText("1:2.5").performClick()
            composeRule.onNodeWithText("4").performClick()
            composeRule.onNodeWithText("5").performClick()
            val workGrinder = grinderData.grindersFor(BrewMethod.ESPRESSO, null).first().id
            val homeGrinder = grinderData.grindersFor(BrewMethod.PULSAR, FilterType.PAPER).last().id
            composeRule.runOnIdle { calculator.setEquipment(null, workGrinder) }
            saveFavorite(context.getString(R.string.action_save_as_favorite), "Work")
            composeRule.waitUntil(10_000) { brew.savedRecipes.value.any { it.coffeeName == "Work" } }
            val work = calculator.currentSetup()
            composeRule.runOnIdle { assertEquals(18f, calculator.uiState.value.previewDoseG, 0.01f) }

            composeRule.onNodeWithText("Espresso").performClick()
            composeRule.onNodeWithText("Moka Pot").performClick()
            composeRule.onNodeWithTag("quantity_card_water_in").assertIsEnabled()
            composeRule.onNodeWithTag("quantity_card_in_cup").assertDoesNotExist()
            composeRule.onNodeWithText("Moka Pot").performClick()
            composeRule.onNodeWithText("Pulsar").performClick()
            composeRule.onNodeWithTag("quantity_card_water_in").assertIsEnabled()
            composeRule.onNodeWithText("1:17").performClick()
            composeRule.onNodeWithText("1:18").performClick()
            composeRule.onNodeWithText("2").performClick()
            composeRule.onNodeWithText("0").performClick()
            composeRule.runOnIdle { calculator.setEquipment(FilterType.PAPER, homeGrinder) }
            saveFavorite(context.getString(R.string.action_save_as_favorite), "Home")
            composeRule.waitUntil(10_000) { brew.savedRecipes.value.size == 2 }
            val home = calculator.currentSetup()
            assertEquals(CalculatorQuantityTarget.COFFEE.name, home.quantity)

            val workRecipe = brew.savedRecipes.value.single { it.coffeeName == "Work" }
            val homeRecipe = brew.savedRecipes.value.single { it.coffeeName == "Home" }
            assertEquals("5.2", homeRecipe.grindSetting)
            composeRule.onNodeWithTag("saved_setup_picker").performClick()
            composeRule.onNodeWithTag("saved_setup_${workRecipe.id}").performClick()
            composeRule.runOnIdle {
                assertEquals(work, calculator.currentSetup())
                assertEquals(workGrinder, brew.uiState.value.selectedGrinderId)
            }
            composeRule.onNodeWithTag("saved_setup_picker").performClick()
            composeRule.onNodeWithTag("saved_setup_${homeRecipe.id}").performClick()
            composeRule.runOnIdle {
                assertEquals(home, calculator.currentSetup())
                assertEquals(FilterType.PAPER, brew.uiState.value.filterType)
                assertEquals(homeGrinder, brew.uiState.value.selectedGrinderId)
            }
            runBlocking {
                withTimeout(10_000) {
                    preferences.userPreferences.first {
                        it.calculatorSetups[BrewMethod.PULSAR] == home &&
                            it.calculatorSetups[BrewMethod.ESPRESSO] == work
                    }
                }
            }
            lateinit var restarted: CalculatorViewModel
            composeRule.runOnIdle {
                restarted = CalculatorViewModel(presets, preferences)
                store.put("restarted", restarted)
            }
            composeRule.waitUntil(10_000) { restarted.uiState.value.preferencesLoaded }
            composeRule.runOnIdle {
                assertEquals(home, restarted.currentSetup())
                restarted.setBrewMethod(BrewMethod.ESPRESSO)
                assertEquals(work, restarted.currentSetup())
            }
        } finally {
            composeRule.runOnIdle { store.clear() }
            database.close()
        }
    }

    @Test
    fun incompatibleSelectionsAreClearedAndUnsupportedControlsAreHidden() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = UserPreferencesRepository(context)
        runBlocking {
            preferences.updateMethodSelection(BrewMethod.entries.toSet(), BrewMethod.PULSAR)
            BrewMethod.entries.forEach { method ->
                preferences.updateCalculatorSetup(method, CalculatorSetup(ratio = method.defaultRatio))
            }
            preferences.updateCalculatorSetup(BrewMethod.PULSAR,
                CalculatorSetup(ratio = 17f, filterType = FilterType.PAPER.name, grinderId = "df64"))
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        val calculator = CalculatorViewModel(CupPresetRepository(database.cupPresetDao()), preferences)
        val brew = BrewViewModel(grinderData = GrinderDataSource.getInstance(context))
        store.put("calculator", calculator)
        store.put("brew", brew)
        try {
            composeRule.setContent {
                StarlitCoffeeTheme(dynamicColor = false) {
                    CalculatorBrewScreen(calculator, brew, preferences,
                        onNavigateToBrew = {}, recoverableSessionId = null, onResumeSession = {},
                        onNavigateToBarcode = {}, onNavigateToBags = {})
                }
            }
            composeRule.waitUntil(10_000) {
                calculator.uiState.value.preferencesLoaded && calculator.uiState.value.grinderId == null
            }
            composeRule.onNodeWithTag("grinder_picker").performClick()
            composeRule.onNodeWithText("1Zpresso ZP6 Special").assertIsDisplayed()
            composeRule.onNodeWithText("Fellow Ode Gen 2 (Gen 2 burrs)").performClick()
            composeRule.onNodeWithTag("filter_picker").performClick()
            composeRule.onNodeWithText(FilterType.METAL_19K.displayName).performClick()
            composeRule.onNodeWithTag("grinder_picker").assertDoesNotExist()
            composeRule.runOnIdle {
                assertNull(calculator.uiState.value.grinderId)
                assertTrue(brew.uiState.value.grindResult is com.adsamcik.starlitcoffee.viewmodel.GrindResult.Generic)
            }
            composeRule.onNodeWithText("Pulsar").performClick()
            composeRule.onNodeWithText("Espresso").performClick()
            composeRule.onNodeWithTag("grinder_picker").performClick()
            composeRule.onNodeWithText("Comandante C40 (standard clicks)").assertIsDisplayed()
            composeRule.onNodeWithText("Baratza Encore ESP").assertIsDisplayed()
            composeRule.onNodeWithText("Niche Zero").assertIsDisplayed()
            composeRule.onNodeWithText("1Zpresso ZP6 Special").assertDoesNotExist()
            composeRule.onNodeWithText("Fellow Ode Gen 2 (Gen 2 burrs)").assertDoesNotExist()
        } finally {
            composeRule.runOnIdle { store.clear() }
            database.close()
        }
    }

    private fun saveFavorite(description: String, name: String) {
        composeRule.onNodeWithContentDescription(description).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(name)
        composeRule.onNodeWithText("Save").performClick()
    }
}
