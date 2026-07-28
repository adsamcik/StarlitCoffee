package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.BrewerProfileId
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
    fun harioOptionsAreProgressivelyDisclosedWhileStartRemainsVisible() {
        val profileId = BrewerProfileId("hario_switch")
        val state = P1BrewerProfileSetupStateFactory.create(
            selectedProfileId = profileId,
            visibleProfileIds = setOf(profileId),
        ).updateEquipmentCapacity("400")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val startLabel = context.getString(R.string.action_start_brewing)
        val optionsTitle = context.getString(R.string.heading_brewer_profile_hario_switch_style)
        val manualGravityLabel = context.getString(
            R.string.label_brewer_profile_hario_switch_manual_gravity,
        )

        setScreen(state)

        composeRule.onNodeWithText(startLabel).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(manualGravityLabel).assertDoesNotExist()

        composeRule.onNodeWithText(optionsTitle).performScrollTo().performClick()
        composeRule.onNodeWithText(manualGravityLabel).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(startLabel).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun cezveSafetyStaysVisibleWhileOptionalChoicesStartCollapsed() {
        val profileId = BrewerProfileId("cezve_generic")
        val state = P1BrewerProfileSetupStateFactory.create(
            selectedProfileId = profileId,
            visibleProfileIds = setOf(profileId),
        ).updateEquipmentCapacity("120")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val startLabel = context.getString(R.string.action_start_brewing)
        val optionsTitle = context.getString(R.string.heading_brewer_profile_cezve_choices)
        val sugarLabel = context.getString(R.string.label_brewer_profile_cezve_sugar)
        val heatSourceTitle = context.getString(
            R.string.heading_brewer_profile_cezve_heat_source,
        )

        setScreen(state)

        composeRule.onNodeWithText(sugarLabel).assertDoesNotExist()
        composeRule.onNodeWithText(heatSourceTitle).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(startLabel).assertIsDisplayed().assertIsNotEnabled()

        composeRule.onNodeWithText(optionsTitle).performScrollTo().performClick()
        composeRule.onNodeWithText(sugarLabel).performScrollTo().assertIsDisplayed()
    }

    private fun setScreen(state: P1BrewerProfileSetupUiState) {
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                P1BrewerProfileSetupScreen(
                    state = state,
                    onProfileSelected = {},
                    onHarioSwitchWorkflowSelected = {},
                    onEquipmentCapacityChanged = {},
                    onCezveSugarSelected = {},
                    onCezveFoamRiseCyclesSelected = {},
                    onCezveHeatSourceSelected = {},
                    onStart = {},
                    onBack = {},
                )
            }
        }
    }
}
