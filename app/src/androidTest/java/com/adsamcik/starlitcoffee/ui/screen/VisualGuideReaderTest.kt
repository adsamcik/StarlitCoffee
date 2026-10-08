package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.GuidanceOperationalCue
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogAvailability
import com.adsamcik.starlitcoffee.ui.guidance.LearnGuidanceCatalogResolution
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedLearnGuidanceContent
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Rule
import org.junit.Test

class VisualGuideReaderTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun essentialControlsAndSafetyRemainVisibleWhenWhyIsCollapsedAt200Percent() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                    var expanded by remember { mutableStateOf(false) }
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        GuideStepCanvas(
                            copy = GuideStepCopy(
                                instruction = "Release the valve",
                                target = "300 g total water",
                                completionCue = "Wait for drainage",
                                essentialOperations = listOf("Open the valve yourself"),
                                warning = "Keep hot glass stable",
                                safetyCritical = true,
                                explanation = listOf("Water flows through the coffee bed"),
                            ),
                            visualAsset = null,
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("300 g total water").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open the valve yourself").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Keep hot glass stable").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Water flows through the coffee bed").assertDoesNotExist()
        val why = context.getString(R.string.heading_guide_why)
        composeRule.onNodeWithText(why).performScrollTo().performClick()
        composeRule.onNodeWithText("Water flows through the coffee bed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(why).performScrollTo().performClick()
        composeRule.onNodeWithText("Keep hot glass stable").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Water flows through the coffee bed").assertDoesNotExist()
    }

    @Test
    fun overviewAndStepPickerRetainUntimedBookmarkAcrossRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                LearnBrewerScreen(
                    title = "Cold brew",
                    resolution = LearnGuidanceCatalogResolution(
                        policy = null,
                        availability = LearnGuidanceCatalogAvailability.Available,
                        content = listOf(step("steep", "Keep covered and refrigerated"),
                            step("filter", "Separate the grounds")),
                    ),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.label_guide_untimed)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_read_guide)).performClick()
        composeRule.onNodeWithText("Keep covered and refrigerated").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.guidance_cue_elapsed_timer)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.guidance_cue_stage_advance)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.action_guide_steps)).performClick()
        composeRule.onNodeWithText("Separate the grounds").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Separate the grounds").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.label_elapsed)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.action_guide_overview)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_continue_reading)).performClick()
        composeRule.onNodeWithText("Separate the grounds").assertIsDisplayed()
    }

    private fun step(id: String, instruction: String) = ResolvedLearnGuidanceContent(
        id = StageContentId(id), placement = BuiltInGuidancePlacement.LIVE_STAGE,
        instruction = instruction, target = null, completionCue = null, explanation = null,
        tip = null, nextAction = null,
        controlRequirements = listOf(GuidanceOperationalCue.ELAPSED_TIMER, GuidanceOperationalCue.STAGE_ADVANCE), warning = null,
        utilities = emptyList(), altText = instruction, safetyCritical = false,
    )
}
