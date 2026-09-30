package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.calculator.CalculatorQuantityTarget
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.model.CalculatorSetup
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.data.model.GrinderDataSource
import com.adsamcik.starlitcoffee.data.repository.CupPresetRepository
import com.adsamcik.starlitcoffee.data.repository.UserPreferencesRepository
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.CalculatorViewModel
import com.adsamcik.starlitcoffee.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Seeds preferences only on the task's disposable emulator. */
@RunWith(AndroidJUnit4::class)
class CalculatorBrewingSetsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun homeWorkAndEspressoSetsRememberInputThroughSettingsEditsDeletionAndRestart() {
        val fixture = fixture()
        try {
            show(fixture)
            composeRule.waitUntil(10_000) { fixture.calculator.uiState.value.preferencesLoaded }
            composeRule.runOnIdle { assertEquals(fixture.espresso.setup, fixture.calculator.currentSetup()) }
            composeRule.onNodeWithTag("quantity_card_water_in").assertDoesNotExist()
            composeRule.onNodeWithTag("quantity_card_in_cup").assertIsEnabled()

            choose(fixture.home.id)
            composeRule.onNodeWithText("1:18").performClick()
            composeRule.onNodeWithText("1:17").performClick()
            composeRule.runOnIdle { fixture.calculator.clear() }
            composeRule.onNodeWithText("2").performClick()
            composeRule.onNodeWithText("5").performClick()
            val home = fixture.calculator.currentSetup()
            choose(fixture.work.id)
            composeRule.runOnIdle { assertEquals(fixture.work.setup, fixture.calculator.currentSetup()) }
            composeRule.onNodeWithTag("quantity_card_water_in").assertIsEnabled()
            choose(fixture.espresso.id)
            composeRule.runOnIdle {
                assertEquals(fixture.espresso.setup, fixture.calculator.currentSetup())
                assertEquals(18f, fixture.calculator.uiState.value.previewDoseG, 0.01f)
            }
            composeRule.onNodeWithText("Start").performClick()
            composeRule.runOnIdle {
                assertEquals(BrewMethod.ESPRESSO, fixture.started?.method)
                assertEquals("niche-zero", fixture.started?.selectedGrinderId)
                assertEquals(18f, requireNotNull(fixture.started).coffeeG, 0.01f)
                assertEquals(45f, requireNotNull(fixture.started).waterG, 0.01f)
            }

            composeRule.onNodeWithTag("save_set_button").performClick()
            composeRule.onNodeWithTag("brewing_set_name").performTextInput("Travel espresso")
            composeRule.onNodeWithTag("brewing_set_filter").assertDoesNotExist()
            composeRule.onNodeWithTag("save_brewing_set").performClick()
            composeRule.waitUntil(10_000) { fixture.calculator.uiState.value.brewingSets.any { it.name == "Travel espresso" } }
            composeRule.onNodeWithTag("brewing_set_name").assertDoesNotExist()
            choose(fixture.home.id)
            composeRule.runOnIdle { assertEquals(home, fixture.calculator.currentSetup()) }

            manage()
            composeRule.onNodeWithTag("edit_brewing_set_${fixture.home.id}").performScrollTo().performClick()
            composeRule.onNodeWithTag("brewing_set_name").performTextReplacement("Home filter renamed")
            composeRule.onNodeWithTag("save_brewing_set").performClick()
            composeRule.waitUntil(10_000) { fixture.calculator.uiState.value.brewingSets.any { it.name == "Home filter renamed" } }
            composeRule.onNodeWithTag("brewing_set_${fixture.work.id}").performScrollTo().performClick()
            composeRule.waitUntil(10_000) { fixture.calculator.uiState.value.activeBrewingSetId == fixture.work.id }
            composeRule.onNodeWithTag("delete_brewing_set_${fixture.work.id}").performScrollTo().performClick()
            composeRule.onNodeWithTag("confirm_destructive_action").performClick()
            composeRule.waitUntil(10_000) { fixture.calculator.uiState.value.brewingSets.none { it.id == fixture.work.id } }
            composeRule.onNodeWithTag("back_button").performClick()
            composeRule.runOnIdle {
                assertEquals(fixture.home.id, fixture.calculator.uiState.value.activeBrewingSetId)
                assertEquals(home, fixture.calculator.currentSetup())
            }
            runBlocking { withTimeout(10_000) { fixture.preferences.userPreferences.first {
                it.activeBrewingSetId == fixture.home.id && it.brewingSets.first { set -> set.id == fixture.home.id }.setup == home
            } } }
            lateinit var restarted: CalculatorViewModel
            composeRule.runOnIdle {
                restarted = CalculatorViewModel(fixture.presets, fixture.preferences)
                fixture.store.put("restarted", restarted)
            }
            composeRule.waitUntil(10_000) { restarted.uiState.value.preferencesLoaded }
            composeRule.runOnIdle {
                assertEquals(home, restarted.currentSetup())
                assertEquals("Home filter renamed", restarted.uiState.value.brewingSets.first().name)
            }
        } finally { fixture.close() }
    }

    @Test
    fun setEditorHidesUnsupportedEquipmentAndUsesEspressoDefaultsOnMethodChange() {
        val fixture = fixture(invalidGrinder = true)
        try {
            show(fixture)
            composeRule.waitUntil(10_000) { fixture.calculator.uiState.value.preferencesLoaded && fixture.calculator.uiState.value.grinderId == null }
            manage()
            composeRule.onNodeWithTag("edit_brewing_set_${fixture.home.id}").performScrollTo().performClick()
            composeRule.onNodeWithTag("brewing_set_filter").performClick()
            composeRule.onNodeWithTag("brewing_set_filter_option_METAL_19K").performClick()
            composeRule.onNodeWithTag("brewing_set_grinder").assertDoesNotExist()
            composeRule.onNodeWithTag("save_brewing_set").performClick()
            composeRule.waitUntil(10_000) { fixture.calculator.uiState.value.filterType == FilterType.METAL_19K }
            composeRule.onNodeWithTag("back_button").performClick()
            composeRule.runOnIdle { assertNull(fixture.calculator.currentSetup().grinderId) }

            manage()
            composeRule.onNodeWithTag("edit_brewing_set_${fixture.home.id}").performScrollTo().performClick()
            composeRule.onNodeWithTag("brewing_set_method").performClick()
            composeRule.onNodeWithTag("brewing_set_method_option_ESPRESSO").performClick()
            composeRule.onNodeWithTag("brewing_set_filter").assertDoesNotExist()
            composeRule.onNodeWithTag("brewing_set_grinder").performClick()
            composeRule.onNodeWithTag("brewing_set_grinder_option_1zpresso-zp6-special").assertDoesNotExist()
            composeRule.onNodeWithTag("brewing_set_grinder_option_fellow-ode-gen2").assertDoesNotExist()
            composeRule.onNodeWithTag("brewing_set_grinder_option_niche-zero").performClick()
            composeRule.onNodeWithTag("save_brewing_set").performClick()
            composeRule.waitUntil(10_000) { fixture.calculator.uiState.value.brewMethod == BrewMethod.ESPRESSO }
            composeRule.onNodeWithTag("back_button").performClick()
            composeRule.runOnIdle {
                assertEquals(2f, fixture.calculator.currentSetup().ratio, 0f)
                assertEquals(emptyList<CalcToken>(), fixture.calculator.currentSetup().tokens)
                assertEquals("niche-zero", fixture.calculator.currentSetup().grinderId)
                assertEquals(CalculatorQuantityTarget.COFFEE, fixture.calculator.uiState.value.quantityTarget)
            }
            composeRule.onNodeWithTag("quantity_card_water_in").assertDoesNotExist()
        } finally { fixture.close() }
    }

    private fun choose(id: String) {
        composeRule.onNodeWithTag("brewing_set_picker").performClick()
        composeRule.onNodeWithTag("choose_brewing_set_$id").performClick()
    }
    private fun manage() {
        composeRule.onNodeWithTag("brewing_set_picker").performClick()
        composeRule.onNodeWithTag("manage_brewing_sets").performClick()
    }
    private fun show(fixture: Fixture) {
        var settings by mutableStateOf(false)
        val vm = SettingsViewModel(fixture.preferences)
        fixture.store.put("settings", vm)
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                if (settings) SettingsScreen(vm, fixture.presets, {}, {}, {}, {}, { settings = false })
                else CalculatorBrewScreen(fixture.calculator, fixture.brew, onNavigateToBrew = { fixture.started = fixture.brew.uiState.value },
                    recoverableSessionId = null, onResumeSession = {}, onNavigateToBarcode = {},
                    onNavigateToBags = {}, onNavigateToSettings = { settings = true })
            }
        }
    }
    private fun fixture(invalidGrinder: Boolean = false): Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = UserPreferencesRepository(context)
        val suffix = UUID.randomUUID().toString()
        val home = BrewingSet("home-$suffix", "Home filter", BrewMethod.PULSAR,
            CalculatorSetup(18f, tokens = listOf(CalcToken.Number("20")), filterType = "PAPER",
                grinderId = if (invalidGrinder) "df64" else "fellow-ode-gen2"))
        val work = home.copy(id = "work-$suffix", name = "Work filter", setup =
            CalculatorSetup(16f, "WATER_IN", listOf(CalcToken.Number("250")), "PAPER", "1zpresso-zp6-special"))
        val espresso = BrewingSet("espresso-$suffix", "Home espresso", BrewMethod.ESPRESSO,
            CalculatorSetup(2.5f, "IN_CUP", listOf(CalcToken.Number("45")), grinderId = "niche-zero"))
        runBlocking {
            preferences.completeOnboarding(setOf(BrewMethod.PULSAR), BrewMethod.PULSAR, FilterType.PAPER, null)
            listOf(home, work, espresso).forEach { preferences.saveBrewingSet(it) }
            val ids = setOf(home.id, work.id, espresso.id)
            preferences.userPreferences.first().brewingSets.filter { it.id !in ids }
                .forEach { preferences.deleteBrewingSet(it.id) }
            preferences.selectBrewingSet(if (invalidGrinder) home.id else espresso.id)
        }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val presets = CupPresetRepository(db.cupPresetDao())
        val calc = CalculatorViewModel(presets, preferences)
        val brew = BrewViewModel(grinderData = GrinderDataSource.getInstance(context))
        val store = ViewModelStore().apply { put("calc", calc); put("brew", brew) }
        return Fixture(preferences, db, presets, calc, brew, store, home, work, espresso)
    }
    private inner class Fixture(
        val preferences: UserPreferencesRepository, val db: AppDatabase, val presets: CupPresetRepository,
        val calculator: CalculatorViewModel, val brew: BrewViewModel, val store: ViewModelStore,
        val home: BrewingSet, val work: BrewingSet, val espresso: BrewingSet,
    ) {
        var started: BrewUiState? = null
        fun close() { composeRule.runOnIdle { store.clear() }; db.close() }
    }
}
