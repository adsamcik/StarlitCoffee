package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
import com.adsamcik.starlitcoffee.domain.brewing.BuiltInRecipeId
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupStateFactory
import com.adsamcik.starlitcoffee.ui.brewerprofile.P1BrewerProfileSetupUiState
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P1BrewerProfileSetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun multipleExactRecipesStayExplicitBeforeStart() {
        val profileId = BrewerProfileId("hario_switch")
        val initial = P1BrewerProfileSetupStateFactory.create(
            selectedProfileId = profileId,
            visibleProfileIds = setOf(profileId),
        ).updateEquipmentCapacity("400")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val startLabel = context.getString(R.string.action_start_brewing)
        val officialRecipe = context.getString(R.string.recipe_p1_switch_official_20_240)
        val hybridRecipe = context.getString(R.string.recipe_p1_switch_hybrid_16_5_240)

        setScreen(initial)

        composeRule.onNodeWithText(officialRecipe).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(hybridRecipe).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(startLabel).assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun cezveSafetyStaysVisibleWhileOptionalSugarStartsCollapsed() {
        val profileId = BrewerProfileId("cezve_generic")
        val state = P1BrewerProfileSetupStateFactory.create(
            selectedProfileId = profileId,
            visibleProfileIds = setOf(profileId),
        )
            .selectRecipe(BuiltInRecipeId("cezve_turkish_single_rise_6_65"))
            .updateEquipmentCapacity("120")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val startLabel = context.getString(R.string.action_start_brewing)
        val optionsTitle = context.getString(R.string.heading_brewer_profile_cezve_choices)
        val sugarLabel = context.getString(R.string.label_brewer_profile_cezve_sugar)
        val heatSourceTitle = context.getString(R.string.heading_brewer_profile_cezve_heat_source)

        setScreen(state)

        composeRule.onNodeWithText(sugarLabel).assertDoesNotExist()
        composeRule.onNodeWithText(heatSourceTitle).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(startLabel).assertIsDisplayed().assertIsNotEnabled()

        composeRule.onNodeWithText(optionsTitle).performScrollTo().performClick()
        composeRule.onNodeWithText(sugarLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selectingBrewerRevealsTheExactRecipeStep() {
        val harioSwitch = BrewerProfileId("hario_switch")
        val cezve = BrewerProfileId("cezve_generic")
        val initial = P1BrewerProfileSetupStateFactory.create(
            visibleProfileIds = setOf(harioSwitch, cezve),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val harioSwitchLabel = context.getString(R.string.label_brewer_profile_hario_switch)
        val recipeHeading = context.getString(R.string.heading_exact_recipe_choose)

        composeRule.setContent {
            var state by remember { mutableStateOf(initial) }
            StarlitCoffeeTheme(dynamicColor = false) {
                P1BrewerProfileSetupScreen(
                    state = state,
                    onProfileSelected = { profileId -> state = state.selectProfile(profileId) },
                    onRecipeSelected = { recipeId -> state = state.selectRecipe(recipeId) },
                    onEquipmentOptionSelected = { index ->
                        state = state.selectEquipmentOption(index)
                    },
                    onEquipmentCapacityChanged = { capacity ->
                        state = state.updateEquipmentCapacity(capacity)
                    },
                    onCezveSugarSelected = { includeSugar ->
                        state = state.selectCezveSugar(includeSugar)
                    },
                    onCezveHeatSourceSelected = { heatSource ->
                        state = state.selectCezveHeatSource(heatSource)
                    },
                    onStart = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(harioSwitchLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(recipeHeading).assertIsDisplayed()
    }

    private fun setScreen(state: P1BrewerProfileSetupUiState) {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                P1BrewerProfileSetupScreen(
                    state = state,
                    onProfileSelected = {},
                    onRecipeSelected = {},
                    onEquipmentOptionSelected = {},
                    onEquipmentCapacityChanged = {},
                    onCezveSugarSelected = {},
                    onCezveHeatSourceSelected = {},
                    onStart = {},
                    onBack = {},
                )
            }
        }
    }
}
