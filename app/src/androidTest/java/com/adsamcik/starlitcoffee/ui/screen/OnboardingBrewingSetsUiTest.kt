package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.BrewingSet
import com.adsamcik.starlitcoffee.ui.component.BrewingSetDraft
import com.adsamcik.starlitcoffee.ui.component.initialBrewingSetup
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingBrewingSetsUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val first = BrewingSetDraft(BrewingSet("home", method = BrewMethod.PULSAR, setup = initialBrewingSetup(BrewMethod.PULSAR)))

    @Test
    fun onboardingCreatesTwoIndependentSetsAndChoosesTheOneToOpen() {
        var submitted: List<BrewingSet>? = null
        var activeId: String? = null
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            val drafts = remember { mutableStateOf(listOf(first)) }
            val active = remember { mutableStateOf(first.set.id) }
            OnboardingPersonalizeScreen(drafts.value, active.value, onBack = {},
                onDraftsChanged = { changed, id -> drafts.value = changed; active.value = id },
                onFinish = { sets, id -> submitted = sets; activeId = id })
        } }
        composeRule.onNodeWithTag("brewing_set_name").performScrollTo().performTextInput("Home")
        composeRule.onNodeWithTag("add_brewing_set").performScrollTo().performClick()
        composeRule.onNode(hasTestTag("brewing_set_method") and hasAnyAncestor(hasTestTag("brewing_set_editor"))).performClick()
        composeRule.onNodeWithTag("brewing_set_method_option_ESPRESSO").performScrollTo().performClick()
        composeRule.onNode(hasTestTag("brewing_set_name") and hasAnyAncestor(hasTestTag("brewing_set_editor")))
            .performScrollTo().performTextInput("Work")
        composeRule.onNodeWithTag("save_brewing_set").performClick()
        composeRule.onNodeWithTag("onboarding_finish_button").performClick()
        composeRule.runOnIdle {
            val sets = requireNotNull(submitted)
            assertEquals(listOf("Home", "Work"), sets.map { it.name })
            assertEquals(listOf(BrewMethod.PULSAR, BrewMethod.ESPRESSO), sets.map { it.method })
            assertEquals(listOf(17f, 2f), sets.map { it.setup.ratio })
            assertEquals(sets.last().id, activeId)
        }
    }

    @Test
    fun invalidHiddenRecipeIsIdentifiedAndCanBeReopened() {
        composeRule.setContent { StarlitCoffeeTheme(dynamicColor = false) {
            val invalid = first.copy(ratio = "bad")
            val work = BrewingSetDraft(BrewingSet("work", "Work", BrewMethod.ESPRESSO))
            val active = remember { mutableStateOf("work") }
            val drafts = remember { mutableStateOf(listOf(invalid, work)) }
            OnboardingPersonalizeScreen(drafts.value, active.value, onBack = {},
                onDraftsChanged = { changed, id -> drafts.value = changed; active.value = id }, onFinish = { _, _ -> })
        } }
        composeRule.onNodeWithTag("onboarding_finish_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("fix_invalid_brewing_set").performScrollTo().performClick()
        composeRule.onNodeWithTag("brewing_set_recipe").performScrollTo().performClick()
        composeRule.onNodeWithTag("brewing_set_ratio").performScrollTo().performTextReplacement("17")
        composeRule.onNodeWithTag("onboarding_finish_button").assertIsEnabled()
    }
}
