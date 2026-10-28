package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewSessionAccessibilityPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability
import com.adsamcik.starlitcoffee.ui.session.BrewSessionLiveRegion
import com.adsamcik.starlitcoffee.ui.session.BrewSessionStageProgressPresentation
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrewSessionActionBarTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun finishAndPhysicalPauseStayReachableAt200PercentWithoutScrolling() {
        val events = mutableListOf<SessionEvent>()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = false) {
                    var presentation by remember { mutableStateOf(presentation(manual = true)) }
                    Scaffold(bottomBar = {
                        BrewSessionPrimaryActionBar(
                            action = requireNotNull(primaryBrewSessionAction(presentation.actions)),
                            presentation = presentation,
                            isDispatching = false,
                            onDispatch = { event ->
                                events += event
                                if (event is SessionEvent.Pause || event is SessionEvent.Resume) {
                                    val paused = event is SessionEvent.Pause
                                    presentation = presentation.copy(status = if (paused) BrewSessionStatus.PAUSED else BrewSessionStatus.RUNNING,
                                        actions = presentation.actions.copy(canPause = !paused, canResume = paused))
                                }
                            },
                        )
                    }) { padding ->
                        Text("Instruction\n".repeat(100), Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState()))
                    }
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.action_finish)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_pause_brew_clock)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_finish)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_resume)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_finish)).performClick()
        composeRule.runOnIdle {
            assertTrue(events[0] is SessionEvent.Pause)
            assertTrue(events[1] is SessionEvent.Resume)
            assertTrue(events[2] is SessionEvent.Finish)
            assertTrue(events.none { it is SessionEvent.SetGuidancePaused })
        }
    }

    @Test
    fun waitingStagePausesThePhysicalClockWithoutGuidanceCopyInDarkTheme() {
        var dispatched: SessionEvent? = null
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                    val presentation = presentation(manual = false)
                    BrewSessionPrimaryActionBar(
                        action = requireNotNull(primaryBrewSessionAction(presentation.actions)),
                        presentation = presentation,
                        isDispatching = false,
                        onDispatch = { dispatched = it },
                    )
                }
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.msg_brew_clock_keeps_running)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_pause_brew_clock)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(dispatched is SessionEvent.Pause) }
    }

    private fun presentation(manual: Boolean): ActiveBrewSessionPresentation.Available {
        val progress = BrewSessionStageProgressPresentation(0, 0, 0, 1, 1)
        return ActiveBrewSessionPresentation.Available(
            sessionId = "action-bar-test",
            status = BrewSessionStatus.RUNNING,
            totalActiveElapsedMillis = 5_000L,
            stageProgress = progress,
            currentStage = null,
            safetyMessages = emptyList(),
            actions = BrewSessionActionAvailability(false, true, false, manual, false, true, manual, false),
            accessibility = BrewSessionAccessibilityPresentation(
                BrewSessionLiveRegion.POLITE, BrewSessionStatus.RUNNING, progress, null, null, 5_000L, null, null, emptyList(),
            ),
        )
    }
}
