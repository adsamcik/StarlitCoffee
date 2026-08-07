package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GrindPrepCoffeeTypeSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingDecafShowsTheAdjustedGrindReminder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val regular = context.getString(R.string.label_regular)
        val decaf = context.getString(R.string.label_decaf)
        val reminder = context.getString(R.string.msg_decaf_grind_adjustment)

        composeRule.setContent {
            var isDecaf by remember { mutableStateOf(false) }
            StarlitCoffeeTheme(dynamicColor = false) {
                CoffeeTypeSelector(
                    isDecaf = isDecaf,
                    onTypeSelected = { isDecaf = it },
                )
            }
        }

        composeRule.onNodeWithText(regular).assertIsSelected()
        composeRule.onNodeWithText(reminder).assertDoesNotExist()

        composeRule.onNodeWithText(decaf).performClick()
        composeRule.onNodeWithText(decaf).assertIsSelected()
        composeRule.onNodeWithText(reminder).assertIsDisplayed()

        composeRule.onNodeWithText(regular).performClick()
        composeRule.onNodeWithText(regular).assertIsSelected()
        composeRule.onNodeWithText(reminder).assertDoesNotExist()
    }
}
