package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.ui.session.BrewActivityPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewActivityState
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class BrewActivityStripTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun noBrewAddsNoActivityControl() {
        composeRule.setContent {
            StarlitCoffeeTheme {
                Text("Existing screen")
                BrewActivityStrip(emptyList(), {})
            }
        }
        composeRule.onNodeWithText("Existing screen").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.title_active_brews)).assertDoesNotExist()
    }

    @Test
    fun oneBrewOpensExactSessionAtLargeText() {
        var opened: String? = null
        val activity = activity("one", "Chemex")
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                StarlitCoffeeTheme { BrewActivityStrip(listOf(activity), { opened = it }) }
            }
        }
        composeRule.onNodeWithContentDescription(description(activity, 1)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("one", opened) }
    }

    @Test
    fun multipleBrewsChooseByIdentityWithoutAutomaticNavigation() {
        var opened: String? = null
        val first = activity("one", "Chemex")
        val second = activity("two", "Cold brew")
        composeRule.setContent {
            StarlitCoffeeTheme { BrewActivityStrip(listOf(first, second), { opened = it }) }
        }
        composeRule.onNodeWithContentDescription(description(first, 2)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.title_active_brews)).assertIsDisplayed()
        composeRule.runOnIdle { assertNull(opened) }
        composeRule.onNodeWithContentDescription(description(second, 1)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("two", opened) }
    }

    private fun activity(id: String, label: String) = BrewActivityPresentation(
        id, label, BrewStageAction.STEEP, BrewActivityState.PAUSED, 5_000L,
    )

    private fun description(activity: BrewActivityPresentation, count: Int): String = context.getString(
        if (count > 1) R.string.cd_choose_active_brews else R.string.cd_open_active_brew,
        activity.methodLabel,
        context.getString(R.string.action_brew_steep) + " · " + context.getString(R.string.label_brew_activity_paused),
        count,
    )
}
