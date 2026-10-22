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
        checkBasicTimer(large = false)
    }

    @Test
    fun hotBasicTimerKeepsEssentialsAt200Percent() {
        checkBasicTimer(large = true)
    }

    private fun checkBasicTimer(large: Boolean) {
        val request = requireNotNull(CalculatorBrewSessionStartFactory().create(
            CalcUiState(brewMethod = BrewMethod.PULSAR, previewDoseG = 20f, previewWaterMl = 340f,
                previewBeverageG = 300f, ratio = 17f, hasValidExpression = true),
            BrewUiState(method = BrewMethod.PULSAR, filterType = FilterType.PAPER), null))
        val pour = request.stagePlan.stages.indexOfFirst { it.definition.action == BrewStageAction.POUR }
        assertTrue("Fixture needs a real post-bloom pour", pour > 0)
        val runtime = runningAt(request, pour, totalElapsed = 82_000L)
        render(request, runtime, large = large, dark = true)
        rule.onNodeWithContentDescription(context.getString(R.string.cd_bloom_animation)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.label_elapsed)).assertIsDisplayed()
        rule.onNodeWithText("340 g").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.action_finish)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_basic_pour)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_hot_liquid), substring = true).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_overflow), substring = true).assertDoesNotExist()
        save(if (large) "minimal-hot-basic-200-dark" else "minimal-hot-basic-dark")
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performScrollTo().performClick()
        save("minimal-hot-help-dark")
        rule.onNodeWithText("Hot water and brewed coffee can burn.", substring = true)
            .performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText(context.getString(R.string.heading_guide_why))[0]
            .performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performScrollTo().performClick()
        rule.runOnIdle { assertTrue(events.isEmpty()) }
        rule.onNodeWithText(context.getString(R.string.action_finish)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(events.single() is SessionEvent.Finish) }
    }

    @Test
    fun guidedPourAtNormalSizeKeepsTargetAndWaterHeightVisible() {
        val guide = ReviewedMethodGuideLibrary.decode(context.assets.open(ReviewedMethodGuideLibrary.ASSET_PATH)
            .bufferedReader().use { it.readText() }).single { it.methodId == "pulsar" }
        val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null)
        val sourceStep = guide.steps[4]
        render(request, runningAt(request, 4, 130_000L), large = false, dark = true)
        rule.onNodeWithText("340 g").assertIsDisplayed()
        val spokenTarget = context.getString(R.string.label_brew_reference_brew_water) + " · " +
            context.getString(R.string.label_brew_reference_brew_total) + " 340 g"
        rule.onNodeWithContentDescription(spokenTarget).assertIsDisplayed()
        rule.onNodeWithText(sourceStep.instruction).assertDoesNotExist()
        rule.onNodeWithText(sourceStep.why).assertDoesNotExist()
        rule.onNodeWithText(sourceStep.completion).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_pour_safety)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_main_pour)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_pause_high)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.label_elapsed)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.cd_bloom_animation)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_complete_step)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.cd_brew_water_height_target))
            .assertIsDisplayed()
        save("minimal-guided-pour-dark")
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performScrollTo().performClick()
        val quantities = context.getString(R.string.label_coffee) + " 20 g · " +
            context.getString(R.string.label_brew_reference_brew_water) + " 340 g"
        rule.onNodeWithText(quantities).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertIsDisplayed()
        rule.onNodeWithText(sourceStep.instruction).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(guide.safety.first(), substring = true).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText(context.getString(R.string.heading_guide_why))[0].performScrollTo().performClick()
        rule.onNodeWithText(sourceStep.why).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(sourceStep.completion).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performScrollTo().performClick()
        rule.runOnIdle { assertTrue("Help must not change the running brew", events.isEmpty()) }
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
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertDoesNotExist()
        rule.onNodeWithText("340 g").assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.cd_bloom_animation)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_main_pour)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_pour_safety)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_pulsar_pause_high)).assertDoesNotExist()
        save("minimal-pulsar-200-dark")
        rule.onNodeWithContentDescription(context.getString(R.string.cd_brew_water_height_target))
            .performScrollTo().assertIsDisplayed()
        save("minimal-guided-pour-200-cue-dark")
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performScrollTo().performClick()
        save("minimal-pulsar-help-dark")
        rule.onNodeWithText(guide.steps[4].instruction).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(guide.safety.first(), substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertIsDisplayed().performClick()
        save("minimal-recipe-200-dark")
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performClick()
        rule.runOnIdle { assertTrue("Reading must not advance or pause the brew", events.isEmpty()) }
        rule.onNodeWithText(complete).assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(events.single() is SessionEvent.ManualAdvance) }
    }

    @Test
    fun completedBrewKeepsRecipeAvailableThroughHelp() {
        val guide = ReviewedMethodGuideLibrary.decode(context.assets.open(ReviewedMethodGuideLibrary.ASSET_PATH)
            .bufferedReader().use { it.readText() }).single { it.methodId == "pulsar" }
        val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null)
        val endIndex = guide.steps.indexOfFirst { it.id == guide.endStepId }
        val completed = SessionReducer.reduce(runningAt(request, endIndex, 190_000L), SessionEvent.Finish(),
            SessionClockReading(190_000L, System.currentTimeMillis())).state
        assertTrue(completed.status == BrewSessionStatus.COMPLETED)
        render(request, completed, large = false, dark = true)
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).assertIsDisplayed()
        rule.runOnIdle { assertTrue(events.isEmpty()) }
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
        rule.onNodeWithText(context.getString(R.string.msg_cold_brew_refrigerated)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.label_brew_safety_warning)).assertIsDisplayed()
        rule.onNodeWithText("Use clean, food-safe equipment", substring = true).assertDoesNotExist()
        save("minimal-basic-timer-light")
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performScrollTo().performClick()
        rule.onNodeWithText("Refrigerate promptly at 4 °C or colder.", substring = true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performScrollTo().performClick()
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
