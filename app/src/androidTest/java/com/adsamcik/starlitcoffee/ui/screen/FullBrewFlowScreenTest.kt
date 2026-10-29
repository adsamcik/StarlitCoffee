package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSession
import com.adsamcik.starlitcoffee.data.brewing.session.ActiveBrewSessionEntityMapper
import com.adsamcik.starlitcoffee.data.brewing.session.BrewSessionOperationResult
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInP1ExactGuidanceLoadResult
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidancePreferences
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactRecipeReleaseGate
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.ReviewedGuideStartFactory
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test


/** Real source recipe and reducer transitions; no user database, stock, alarms or delivered effects. */
class FullBrewFlowScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun fullFlowAtNormalText() = captureFlow(false)
    @Test fun fullFlowAtLargeText() = captureFlow(true)

    private fun captureFlow(large: Boolean) {
        val guide = ReviewedMethodGuideLibrary.decode(context.assets.open(ReviewedMethodGuideLibrary.ASSET_PATH)
            .bufferedReader().use { it.readText() }).single { it.methodId == "pulsar" }
        val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null)
        var runtime = SessionRuntimeState.create(request.sessionId, request.stagePlan).copy(physicalClock = request.physicalClock)
        var millis = 0L
        val wallOrigin = System.currentTimeMillis()
        val events = mutableListOf<SessionEvent>()
        fun entity() = ActiveBrewSessionEntityMapper.create(request.recipe,
            runtime.copy(activeClockAnchor = null), request.executionContext, wallOrigin + millis)
            .copy(completedLogId = if (runtime.status == BrewSessionStatus.COMPLETED) 17L else null)
        val flow = MutableStateFlow(entity())
        fun apply(event: SessionEvent) {
            runtime = SessionReducer.reduce(runtime, event, SessionClockReading(millis, wallOrigin + millis)).state
            flow.value = entity()
        }
        val gate = P1ExactRecipeReleaseGate(BuiltInP1ExactGuidanceLoadResult.Unavailable("capture"), InstructionAssetCatalog(emptyList()))
        var renderedDensity = 0f
        rule.setContent {
            val density = LocalDensity.current
            renderedDensity = if (large) LocalWindowInfo.current.containerSize.width / 320f else density.density
            CompositionLocalProvider(LocalDensity provides Density(renderedDensity, if (large) 2f else 1f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                    BrewSessionScreen(request.sessionId.value, flow,
                        onDispatch = { event ->
                            events += event
                            apply(event)
                            BrewSessionOperationResult.Active(ActiveBrewSession(request.recipe, runtime, request.executionContext))
                        }, onBack = {}, guidancePreferences = DurableBrewSessionGuidancePreferences(),
                        exactRecipeReleaseGate = gate, onRememberGuidanceForProfile = { _, _ -> }, dimModeEnabled = false,
                        bloomSpritesheetWeights = com.adsamcik.starlitcoffee.domain.BloomSpritesheetIds
                            .associateWith { if (it == "coffee_flower") 1 else 0 })
                }
            }
        }
        fun capture(name: String, label: Int?, stage: Int?) {
            rule.waitForIdle()
            assertEquals(stage, runtime.currentStageIndex)
            label?.let { rule.onNodeWithText(context.getString(it)).assertIsDisplayed() }
            run {
                rule.onNodeWithText(context.getString(R.string.action_next)).assertDoesNotExist()
                assertTextFits()
            }
            val expectedValue = when (name) {
                "02-coffee" -> "20 g"
                "03-start-bloom", "04-bloom", "05-bloom-wait", "06-bloom-ready" -> "60 g"
                "07-pour", "08-paused" -> "340 g"
                else -> null
            }
            expectedValue?.let { assertFullyVisible(it) }
            if (name == "09-drawdown") rule.onNodeWithText("340 g").assertDoesNotExist()
            if (name in setOf("00-ready", "01-rinse", "02-coffee", "03-start-bloom")) {
                rule.onNodeWithText(context.getString(R.string.label_brew_activity_ready)).assertDoesNotExist()
            }
            save(name + if (large) "-200" else "", renderedDensity, large, runtime)
        }
        fun click(label: Int, expected: Class<out SessionEvent>) {
            val before = events.size
            rule.onNodeWithText(context.getString(label)).assertIsDisplayed().performClick()
            rule.waitForIdle()
            assertEquals(before + 1, events.size)
            assertTrue(expected.isInstance(events.last()))
        }
        fun tick(to: Long) { rule.runOnIdle { millis = to; apply(SessionEvent.Tick()) } }
        capture("00-ready", R.string.action_brew_start_short, 0)
        click(R.string.action_brew_start_short, SessionEvent.Start::class.java)
        capture("01-rinse", R.string.action_brew_rinse_done, 0)
        click(R.string.action_brew_rinse_done, SessionEvent.ManualAdvance::class.java)
        capture("02-coffee", R.string.action_brew_coffee_added, 1)
        click(R.string.action_brew_coffee_added, SessionEvent.ManualAdvance::class.java)
        capture("03-start-bloom", R.string.action_brew_start_short, 2)
        click(R.string.action_brew_start_short, SessionEvent.Start::class.java)
        tick(15_000L)
        capture("04-bloom", R.string.action_brew_bloom_done, 2)
        click(R.string.action_brew_bloom_done, SessionEvent.ManualAdvance::class.java)
        tick(40_000L)
        capture("05-bloom-wait", null, 3)
        rule.onNodeWithText(context.getString(R.string.action_brew_bloom_done)).assertDoesNotExist()
        tick(60_000L)
        capture("06-bloom-ready", R.string.action_brew_bloom_done, 3)
        click(R.string.action_brew_bloom_done, SessionEvent.ManualAdvance::class.java)
        tick(130_000L)
        capture("07-pour", R.string.action_brew_pour_done, 4)
        val beforeReading = events.size
        rule.onNodeWithContentDescription(context.getString(R.string.action_brew_help)).performClick()
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_brew_recipe)).assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_return_to_live_brew)).performClick()
        rule.waitForIdle()
        assertEquals(beforeReading, events.size)
        rule.onNodeWithContentDescription(context.getString(R.string.action_pause_brew_clock)).performClick()
        rule.waitForIdle()
        assertTrue(events.last() is SessionEvent.Pause)
        assertEquals(BrewSessionStatus.PAUSED, runtime.status)
        capture("08-paused", R.string.action_brew_pour_done, 4)
        rule.onNodeWithContentDescription(context.getString(R.string.action_resume)).performClick()
        rule.waitForIdle()
        assertTrue(events.last() is SessionEvent.Resume)
        click(R.string.action_brew_pour_done, SessionEvent.ManualAdvance::class.java)
        tick(230_000L)
        capture("09-drawdown", R.string.action_finish, 5)
        click(R.string.action_finish, SessionEvent.Finish::class.java)
        assertEquals(BrewSessionStatus.COMPLETED, runtime.status)
        capture("10-completed", R.string.action_brew_done, null)
    }

    private fun assertFullyVisible(text: String) {
        val node = rule.onNodeWithText(text).assertIsDisplayed().fetchSemanticsNode()
        assertTrue("Value clipped vertically: " + text, node.boundsInRoot.height + 1f >= node.layoutInfo.height)
        assertTrue("Value clipped horizontally: " + text, node.boundsInRoot.width + 1f >= node.layoutInfo.width)
    }

    private fun assertTextFits() {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        repeat(nodes.fetchSemanticsNodes().size) { index ->
            val node = nodes[index].fetchSemanticsNode()
            assertTrue("Live text below viewport", node.boundsInRoot.height + 1f >= node.layoutInfo.height)
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { layout ->
                val message = "Clipped text: " + layout.layoutInput.text
                assertFalse(message, layout.multiParagraph.didExceedMaxLines)
                repeat(layout.lineCount) { line ->
                    assertTrue(message, layout.getLineLeft(line) >= -1f)
                    assertTrue(message, layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue(message, layout.getLineBottom(line) <= layout.size.height + 1f)
                }
            }
        }
    }

    private fun save(name: String, density: Float, large: Boolean, runtime: SessionRuntimeState) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500L, 5_000L)
        android.os.SystemClock.sleep(250L)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "minimal-full-brew-flow").apply { mkdirs() }
        File(dir, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(dir, name + ".txt").writeText("widthPx=" + bitmap.width + " heightPx=" + bitmap.height +
            " widthDp=" + bitmap.width / density + " fontScale=" + (if (large) 2f else 1f) +
            " stage=" + runtime.currentStageIndex + " elapsedMillis=" + runtime.totalActiveElapsedMillis +
            " status=" + runtime.status + " source=reviewed_pulsar_v1")
    }
}
