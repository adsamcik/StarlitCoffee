package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.ui.session.BrewStageReferenceCuePresentation
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import java.text.NumberFormat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrewSessionReferenceCuesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun multipleCuesKeepOneVisibleAndProgressivelyDiscloseTheRest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val showMore = context.getString(R.string.action_show_more_brew_reference_cues)
        val hideExtra = context.getString(R.string.action_hide_brew_reference_cues)
        val massLabel = context.getString(R.string.label_brew_reference_bypass_water) +
            " · " + context.getString(R.string.label_brew_reference_recipe_total)
        val temperatureLabel = context.getString(R.string.label_temperature)
        val deadline = context.getString(
            R.string.format_brew_reference_no_later_than,
            formatDuration(seconds = 120),
        )
        val approximateMass = context.getString(
            R.string.format_brew_reference_approximate,
            "${formatNumber(80.0)} g",
        )
        val temperatureRange = context.getString(
            R.string.format_brew_reference_range,
            formatNumber(92.0),
            formatNumber(96.0),
        )
        val approximateTemperature = context.getString(
            R.string.format_brew_reference_approximate,
            temperatureRange,
        )
        val startingTimeRange = context.getString(
            R.string.format_brew_reference_starting_point,
            context.getString(
                R.string.format_brew_reference_range,
                formatDuration(seconds = 150),
                formatDuration(seconds = 240),
            ),
        )

        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                BrewSessionReferenceCues(
                    sessionKey = "reference-session",
                    cues = referenceCues(),
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.msg_brew_reference_cues_not_completion),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.label_brew_reference_brew_completion_time),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(deadline).assertIsDisplayed()
        composeRule.onNodeWithText(massLabel).assertDoesNotExist()
        composeRule.onNodeWithText(temperatureLabel).assertDoesNotExist()

        composeRule.onNodeWithText(showMore).assertIsDisplayed().performClick()

        composeRule.onNodeWithText(massLabel).assertIsDisplayed()
        composeRule.onNodeWithText(approximateMass).assertIsDisplayed()
        composeRule.onNodeWithText(temperatureLabel).assertIsDisplayed()
        composeRule.onNodeWithText(approximateTemperature).assertIsDisplayed()
        composeRule.onNodeWithText(startingTimeRange).assertIsDisplayed()
        composeRule.onNodeWithText(hideExtra).assertIsDisplayed()
    }

    private fun referenceCues(): List<BrewStageReferenceCuePresentation> = listOf(
        BrewStageReferenceCuePresentation.Time(
            reference = StageTimeReference.BREW_ELAPSED_AT_COMPLETION,
            qualifier = StageTargetQualifier.NO_LATER_THAN,
            minimumMillis = 0L,
            maximumMillis = 120_000L,
        ),
        BrewStageReferenceCuePresentation.Mass(
            role = QuantityRole.BYPASS_WATER,
            reference = StageMassReference.RECIPE_TOTAL,
            qualifier = StageTargetQualifier.APPROXIMATE,
            minimumGrams = 80.0,
            maximumGrams = 80.0,
        ),
        BrewStageReferenceCuePresentation.Temperature(
            qualifier = StageTargetQualifier.APPROXIMATE,
            minimumC = 92.0,
            maximumC = 96.0,
        ),
        BrewStageReferenceCuePresentation.Time(
            reference = StageTimeReference.BREW_ELAPSED_AT_COMPLETION,
            qualifier = StageTargetQualifier.STARTING_POINT,
            minimumMillis = 150_000L,
            maximumMillis = 240_000L,
        ),
    )

    private fun formatDuration(seconds: Int): String = String.format(
        Locale.getDefault(),
        "%d:%02d",
        seconds / 60,
        seconds % 60,
    )

    private fun formatNumber(value: Double): String = NumberFormat.getNumberInstance(
        Locale.getDefault(),
    ).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(value)
}
