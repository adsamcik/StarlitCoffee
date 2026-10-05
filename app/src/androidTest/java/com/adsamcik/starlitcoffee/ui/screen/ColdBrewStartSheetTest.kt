package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.ColdBrewStartFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ColdBrewStartSheetTest {
    @get:Rule val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun guideStartsWithReviewedDefaultAndNoFabricatedEarlierStart() {
        var result: List<Any?>? = null
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                ColdBrewStartSheet("guide", {}, { timerOnly, duration, known, origin ->
                    result = listOf(timerOnly, duration, known, origin)
                })
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.action_follow_brew_guide)).performClick()
        assertEquals(listOf(false, ColdBrewStartFactory.DEFAULT_DURATION_MILLIS, true, null), result)
    }

    @Test
    fun standaloneTimerDisclosesUnknownStartBeforeSavingWithoutDeadline() {
        var result: List<Any?>? = null
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                ColdBrewStartSheet("unknown", {}, { timerOnly, duration, known, origin ->
                    result = listOf(timerOnly, duration, known, origin)
                })
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.action_just_set_brew_timer)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.label_brew_start_unknown)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.msg_brew_start_unknown)).assertIsDisplayed()
        assertNull(result)
        composeRule.onNodeWithText(context.getString(R.string.action_start_timer)).performScrollTo().performClick()
        assertEquals(listOf(true, ColdBrewStartFactory.DEFAULT_DURATION_MILLIS, false, null), result)
    }
}
