package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.UserBrewTimer
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.ui.component.BrewActivityStrip
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInGuidancePlacement
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceAvailability
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewGuidanceVisualStatus
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceResolution
import com.adsamcik.starlitcoffee.ui.guidance.PulsarLearnGuideCatalog
import com.adsamcik.starlitcoffee.ui.guidance.ResolvedBrewGuidanceContent
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import com.adsamcik.starlitcoffee.ui.session.BrewActivityPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewActivityState
import com.adsamcik.starlitcoffee.ui.session.BrewSessionAccessibilityPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability
import com.adsamcik.starlitcoffee.ui.session.BrewSessionLiveRegion
import com.adsamcik.starlitcoffee.ui.session.BrewSessionStageProgressPresentation
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Narrow, large-text checks for truthful clocks and independently accessible background brews. */
class MinimalBrewAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sourceCountdownAndCustomReminderKeepSeparateLabels() {
        val stageId = StageInstanceId(StageId("countdown"), 1)
        val stage = CurrentBrewStagePresentation(stageId, BrewStageAction.STEEP, StageContentId("countdown_copy"),
            null, false, StageRunStatus.ACTIVE, 60_000L, BrewStageCompletionPresentation.Countdown(90_000L, 30_000L),
            safetyMessages = emptyList())
        rule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                Surface {
                    BrewSessionHero(presentation().copy(currentStage = stage, userTimerRemainingMillis = 60_000L,
                        userTimer = UserBrewTimer(stageId, 1L, 120_000L, 0L, null, null)),
                        recipe = null, runtime = null, bloomSpritesheetId = null)
                }
            }
        }
        val source = context.getString(R.string.label_remaining) + " · " +
            context.getString(R.string.format_brew_activity_minutes, 0L, 30L)
        val reminder = context.getString(R.string.label_brew_reminder) + " · " +
            context.getString(R.string.format_brew_activity_minutes, 1L, 0L)
        rule.onNodeWithText(source).assertIsDisplayed()
        rule.onNodeWithText(reminder).assertIsDisplayed()
    }

    @Test
    fun manyHourClockRetainsExplicitUnitsWithoutClippingAt200Percent() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                    Surface(Modifier.width(320.dp)) {
                        BrewSessionHero(presentation(), recipe = null, runtime = null, bloomSpritesheetId = null)
                    }
                }
            }
        }
        rule.onNodeWithText(context.getString(R.string.format_brew_activity_hours, 12L, 37L)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.label_elapsed)).assertIsDisplayed()
        save("minimal-clock-200-dark")
        assertTextFits()
    }

    @Test
    fun unknownOriginShowsNoInventedElapsedOrRemainingTime() {
        rule.setContent {
            StarlitCoffeeTheme(dynamicColor = false, darkTheme = false) {
                Surface(Modifier.width(320.dp)) {
                    BrewSessionHero(
                        presentation().copy(isPhysicalOriginKnown = false, userTimerRemainingMillis = 60_000L),
                        recipe = null,
                        runtime = null,
                        bloomSpritesheetId = null,
                    )
                }
            }
        }
        rule.onNodeWithText(context.getString(R.string.label_brew_start_unknown)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.label_elapsed)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.label_remaining), substring = true).assertDoesNotExist()
        save("minimal-unknown-light")
        assertTextFits()
    }

    @Test
    fun compactBackgroundChooserKeepsBothNamedActionsAt200Percent() {
        val first = activity("other-pulsar", "Pulsar")
        val second = activity("other-cold", "Cold brew")
        var opened: String? = null
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                    Surface(Modifier.width(320.dp)) {
                        BrewActivityStrip(listOf(first, second), { opened = it }, compact = true)
                    }
                }
            }
        }
        rule.onNodeWithContentDescription(description(first, 2)).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription(description(second, 1)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("other-cold", opened) }
        save("minimal-background-200-dark")
        assertTextFits()
    }

    @Test
    fun guidedPourKeepsTargetDiagramSafetyAndNamedHelpAccessibleAt200Percent() {
        var helpRequests = 0
        val instruction = ResolvedBrewGuidanceContent(
            id = StageContentId("${PulsarLearnGuideCatalog.GUIDE_ID}_stage_06_instruction"),
            placement = BuiltInGuidancePlacement.LIVE_STAGE,
            instruction = "Open valve and pulse pour.",
            target = "340 g total",
            completionCue = "All water added; let the coffee drain.",
            explanation = null,
            tip = null,
            nextAction = null,
            controlRequirements = emptyList(),
            warning = "Hot liquid: keep the brewer on its server.",
            utilities = emptyList(),
            altText = "Pouring target, not a measured water level.",
            safetyCritical = true,
        )
        val resolution = DurableBrewSessionGuidanceResolution(
            policy = null,
            availability = DurableBrewGuidanceAvailability.Available,
            routineContent = listOf(instruction),
            criticalContent = emptyList(),
            stageSafetyMessages = emptyList(),
            visualStatus = DurableBrewGuidanceVisualStatus.NotRequested,
        )
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = false) {
                    Surface(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        LiveBrewGuidance(resolution, onHelp = { helpRequests++ },
                            equipmentProfileId = PulsarLearnGuideCatalog.profileId.value)
                    }
                }
            }
        }
        rule.onNodeWithText("340 g total").assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help))
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals(1, helpRequests) }
        rule.onNodeWithContentDescription(context.getString(R.string.cd_brew_water_height_target))
            .performScrollTo().assertIsDisplayed()
        save("minimal-guided-pour-200-light")
        rule.onNodeWithText(requireNotNull(instruction.warning)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(requireNotNull(instruction.completionCue)).performScrollTo().assertIsDisplayed()
        assertTextFits()
    }

    private fun assertTextFits() {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true)
        repeat(nodes.fetchSemanticsNodes().size) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { layout ->
                val message = "Clipped text: ${layout.layoutInput.text}; size=${layout.size}"
                assertFalse(message, layout.multiParagraph.didExceedMaxLines)
                // Paragraph width can retain the allocated maximum after Text wraps to its intrinsic width.
                // Verify the actual lines fit, allowing only pixel rounding at the measured boundary.
                repeat(layout.lineCount) { line ->
                    assertTrue(message, layout.getLineLeft(line) >= -1f)
                    assertTrue(message, layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue(message, layout.getLineBottom(line) <= layout.size.height + 1f)
                }
            }
        }
    }

    private fun save(name: String) {
        val directory = File(context.getExternalFilesDir(null), "minimal-brew-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun presentation(): ActiveBrewSessionPresentation.Available {
        val progress = BrewSessionStageProgressPresentation(0, 0, 0, 1, 1)
        val elapsed = (12L * 60L + 37L) * 60_000L
        return ActiveBrewSessionPresentation.Available(
            sessionId = "accessibility-timer",
            status = BrewSessionStatus.RUNNING,
            totalActiveElapsedMillis = elapsed,
            stageProgress = progress,
            currentStage = null,
            safetyMessages = emptyList(),
            actions = BrewSessionActionAvailability(false, true, false, false, false, true, true, false),
            accessibility = BrewSessionAccessibilityPresentation(
                BrewSessionLiveRegion.POLITE, BrewSessionStatus.RUNNING, progress, null, null, elapsed, null, null, emptyList(),
            ),
        )
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
