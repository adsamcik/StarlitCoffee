package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionEntityMapper
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionStartRequest
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInP1ExactGuidanceLoadResult
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidancePreferences
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.GuidancePresentationLevel
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactRecipeReleaseGate
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.CalcUiState
import com.adsamcik.starlitcoffee.viewmodel.CalculatorBrewSessionStartFactory
import com.adsamcik.starlitcoffee.viewmodel.ColdBrewStartFactory
import com.adsamcik.starlitcoffee.viewmodel.ReviewedGuideStartFactory
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Screen fixtures use frozen source recipes; no user database, alarms or stock are touched. */
class MinimalBrewScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val events = mutableListOf<SessionEvent>()

    @Test
    fun hotBasicTimerKeepsFinishedBloomAndCumulativeTargetWithoutCompletingOnRead() {
        val request = requireNotNull(CalculatorBrewSessionStartFactory().create(
            CalcUiState(brewMethod = BrewMethod.PULSAR, previewDoseG = 20f, previewWaterMl = 340f,
                previewBeverageG = 300f, ratio = 17f, hasValidExpression = true),
            BrewUiState(method = BrewMethod.PULSAR, filterType = FilterType.PAPER), null))
        val pour = request.stagePlan.stages.indexOfFirst { it.definition.action == BrewStageAction.POUR }
        assertTrue("Fixture needs a real post-bloom pour", pour > 0)
        val runtime = runningAt(request, pour, totalElapsed = 82_000L)
        render(request, runtime, large = false, dark = true, minimal = true)
        rule.onNodeWithContentDescription(context.getString(R.string.cd_bloom_animation)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.label_elapsed)).assertIsDisplayed()
        rule.onNodeWithText("340 g").assertIsDisplayed()
        save("minimal-hot-basic-dark")
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performScrollTo().performClick()
        save("minimal-hot-help-dark")
        rule.onAllNodesWithText(context.getString(R.string.heading_guide_why))[0]
            .performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performScrollTo().performClick()
        rule.runOnIdle { assertTrue(events.isEmpty()) }
    }

    @Test
    fun guidedPourAtNormalSizeKeepsTargetAndWaterHeightVisible() {
        val request = pulsarRequest()
        render(request, runningAt(request, 4, 130_000L), large = false, dark = false)
        rule.onNodeWithText("340 g").assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.cd_brew_water_height_target))
            .performScrollTo().assertIsDisplayed()
        save("minimal-guided-pour-light")
    }

    private fun pulsarRequest(): BrewSessionStartRequest {
        val guide = ReviewedMethodGuideLibrary.decode(context.assets.open(ReviewedMethodGuideLibrary.ASSET_PATH)
            .bufferedReader().use { it.readText() }).single { it.methodId == "pulsar" }
        return ReviewedGuideStartFactory.create(guide, BrewUiState(), null)
    }

    @Test
    fun guidedPourHelpAndRecipePreserveTheLiveStageAndKeepConfirmationReachable() {
        val guide = ReviewedMethodGuideLibrary.decode(context.assets.open(ReviewedMethodGuideLibrary.ASSET_PATH)
            .bufferedReader().use { it.readText() }).single { it.methodId == "pulsar" }
        val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null)
        val runtime = runningAt(request, 4, totalElapsed = 130_000L)
        render(request, runtime, large = true, dark = true)
        val complete = context.getString(R.string.action_complete_step)
        rule.onNodeWithText(complete).assertIsDisplayed()
        save("minimal-pulsar-200-dark")
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).performClick()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performClick()
        rule.runOnIdle { assertTrue("Reading must not advance or pause the brew", events.isEmpty()) }
        rule.onNodeWithText(complete).assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(events.single() is SessionEvent.ManualAdvance) }
    }

    @Test
    fun basicTimerHasExplicitEndAndRejectedGuidePauseShowsRecovery() {
        val calculator = requireNotNull(CalculatorBrewSessionStartFactory().create(
            CalcUiState(brewMethod = BrewMethod.COLD_BREW, previewDoseG = 50f, previewWaterMl = 400f,
                previewBeverageG = 300f, ratio = 8f, hasValidExpression = true),
            BrewUiState(method = BrewMethod.COLD_BREW), null))
        val request = ColdBrewStartFactory.create(calculator, timerOnly = true)
        val ready = SessionRuntimeState.create(request.sessionId, request.stagePlan).copy(physicalClock = request.physicalClock)
        val runtime = SessionReducer.reduce(ready, SessionEvent.Start(),
            SessionClockReading(0L, System.currentTimeMillis())).state
        render(request, runtime, large = false, dark = false, rejectActions = true)
        rule.onNodeWithText(context.getString(R.string.action_end_brew_timer)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.label_elapsed)).assertIsDisplayed()
        save("minimal-basic-timer-light")
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_controls)).performClick()
        rule.onNodeWithText(context.getString(R.string.action_pause_brew_guide)).performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(events.single() is SessionEvent.SetGuidancePaused)
            assertTrue(events.none { it is SessionEvent.Pause || it is SessionEvent.Finish })
        }
        rule.onNodeWithText(context.getString(R.string.msg_brew_session_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_end_brew_timer)).assertIsDisplayed()
    }

    private fun runningAt(request: BrewSessionStartRequest, stage: Int, totalElapsed: Long) =
        SessionRuntimeState.create(request.sessionId, request.stagePlan).copy(
            status = BrewSessionStatus.RUNNING, currentStageIndex = stage,
            stageProgress = request.stagePlan.stages.mapIndexed { index, _ ->
                StageRuntimeProgress(if (index < stage) StageRunStatus.COMPLETED else if (index == stage) StageRunStatus.ACTIVE
                    else StageRunStatus.PENDING, elapsedActiveMillis = if (index == stage) totalElapsed - 60_000L else 0L)
            }, totalActiveElapsedMillis = totalElapsed,
            physicalClock = request.physicalClock?.copy(hasStarted = true),
            startedAtWallClockMillis = System.currentTimeMillis() - totalElapsed,
        )

    private fun render(request: BrewSessionStartRequest, runtime: SessionRuntimeState, large: Boolean, dark: Boolean,
        minimal: Boolean = false, rejectActions: Boolean = false) {
        val entity = ActiveBrewSessionEntityMapper.create(request.recipe, runtime, request.executionContext,
            System.currentTimeMillis())
        val flow = MutableStateFlow(entity)
        val gate = P1ExactRecipeReleaseGate(BuiltInP1ExactGuidanceLoadResult.Unavailable("fixture"),
            InstructionAssetCatalog(emptyList()))
        rule.setContent {
            val density = LocalDensity.current
            val narrowDensity = if (large) LocalWindowInfo.current.containerSize.width / 320f else density.density
            CompositionLocalProvider(LocalDensity provides Density(narrowDensity, fontScale = if (large) 2f else 1f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = dark) {
                    BrewSessionScreen(request.sessionId.value, flow,
                        onDispatch = { event ->
                            events += event
                            if (rejectActions) BrewSessionOperationResult.ConcurrentUpdate(request.sessionId)
                            else BrewSessionOperationResult.Active(ActiveBrewSession(request.recipe, runtime, request.executionContext))
                        }, onBack = {}, guidancePreferences = DurableBrewSessionGuidancePreferences(
                            profileOverrides = if (minimal) mapOf(request.recipe.brewerProfileId
                                to GuidancePresentationLevel.UTILITIES_ONLY) else emptyMap()),
                        exactRecipeReleaseGate = gate, onRememberGuidanceForProfile = { _, _ -> }, dimModeEnabled = false,
                        bloomSpritesheetWeights = com.adsamcik.starlitcoffee.domain.BloomSpritesheetIds
                            .associateWith { if (it == "coffee_flower") 1 else 0 })
                }
            }
        }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        val directory = File(context.getExternalFilesDir(null), "minimal-brew-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
