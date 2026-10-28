package com.adsamcik.starlitcoffee.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress
import com.adsamcik.starlitcoffee.ui.brewing.LiveBrewContent
import com.adsamcik.starlitcoffee.ui.guidance.BuiltInP1ExactGuidanceLoadResult
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidancePreferences
import com.adsamcik.starlitcoffee.ui.guidance.DurableBrewSessionGuidanceRequest
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetCatalog
import com.adsamcik.starlitcoffee.ui.guidance.P1ExactRecipeReleaseGate
import com.adsamcik.starlitcoffee.ui.guidance.liveSourceMetadata
import com.adsamcik.starlitcoffee.ui.guidance.resolveBrewSessionContentSource
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentationMapper
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.ReviewedGuideStartFactory
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Same frozen real Pulsar stage through production chrome/body/footer; no database, alarms or stock. */
class AdaptiveLiveBrewScreenTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val events = mutableListOf<SessionEvent>()
    private enum class Packet { FULL, PARTIAL, MISSING }

    @Test fun fullGuidanceAtNormalText() = checkScreen(Packet.FULL, large = false)
    @Test fun partialGuidanceAtNormalText() = checkScreen(Packet.PARTIAL, large = false)
    @Test fun missingGuidanceAtNormalText() = checkScreen(Packet.MISSING, large = false)
    @Test fun fullGuidanceAt200PercentAnd320Dp() = checkScreen(Packet.FULL, large = true)
    @Test fun partialGuidanceAt200PercentAnd320Dp() = checkScreen(Packet.PARTIAL, large = true)
    @Test fun missingGuidanceAt200PercentAnd320Dp() = checkScreen(Packet.MISSING, large = true)

    private fun checkScreen(packet: Packet, large: Boolean) {
        val guide = ReviewedMethodGuideLibrary.decode(
            context.assets.open(ReviewedMethodGuideLibrary.ASSET_PATH).bufferedReader().use { it.readText() },
        ).single { it.methodId == "pulsar" }
        val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null)
        val stageIndex = guide.steps.indexOfFirst { it.id == "open_and_pulse" }
        check(stageIndex > 0)
        val runtime = SessionRuntimeState.create(request.sessionId, request.stagePlan).copy(
            status = BrewSessionStatus.RUNNING,
            currentStageIndex = stageIndex,
            stageProgress = request.stagePlan.stages.mapIndexed { index, _ ->
                StageRuntimeProgress(
                    status = when {
                        index < stageIndex -> StageRunStatus.COMPLETED
                        index == stageIndex -> StageRunStatus.ACTIVE
                        else -> StageRunStatus.PENDING
                    },
                    elapsedActiveMillis = if (index == stageIndex) 70_000L else 0L,
                )
            },
            totalActiveElapsedMillis = 130_000L,
            physicalClock = request.physicalClock?.copy(hasStarted = true),
            startedAtWallClockMillis = 1_800_000_000_000L - 130_000L,
        )
        val presentation = ActiveBrewSessionPresentationMapper.map(runtime) as ActiveBrewSessionPresentation.Available
        val stage = requireNotNull(presentation.currentStage)
        val preferences = DurableBrewSessionGuidancePreferences()
        val gate = P1ExactRecipeReleaseGate(BuiltInP1ExactGuidanceLoadResult.Unavailable("fixture"),
            InstructionAssetCatalog(emptyList()))
        val source = resolveBrewSessionContentSource(request.recipe,
            request.stagePlan.stages.map { it.definition }, gate, preferences, language = "en")
        check(!source.executionBlocked)
        val resolution = source.resolver.resolve(DurableBrewSessionGuidanceRequest(
            request.recipe.methodFamilyId, request.recipe.brewerProfileId, stage, preferences,
        ))
        val full = projectLiveBrewContent(
            resolution, guide.profileId, primaryLiveBrewCue(stage.referenceCues),
            minimal = false, showSupportingCopy = false,
            requiredWarnings = source.requiredWarnings[stage.contentId].orEmpty(),
        )
        check(full.target != null && full.visualCue != null)
        val liveContent = when (packet) {
            Packet.FULL -> full
            Packet.PARTIAL -> full.copy(
                step = null, visualCue = null, instruction = null,
                instructionResource = liveSourceMetadata(stage.contentId, guide.profileId).instruction,
            )
            Packet.MISSING -> LiveBrewContent(target = full.target, warnings = full.warnings)
        }
        if (packet == Packet.PARTIAL) check(liveContent.instructionResource != null)
        val action = requireNotNull(primaryBrewSessionAction(presentation.actions))
        check(action == BrewSessionPrimaryAction.COMPLETE_STEP)
        var renderedDensity = 0f
        rule.setContent {
            val physicalDensity = LocalDensity.current.density
            val viewportDensity = if (large) LocalWindowInfo.current.containerSize.width / 320f else physicalDensity
            renderedDensity = viewportDensity
            CompositionLocalProvider(LocalDensity provides Density(viewportDensity, if (large) 2f else 1f)) {
                StarlitCoffeeTheme(dynamicColor = false, darkTheme = true) {
                    Scaffold(
                        topBar = { BrewSessionHeader(onBack = {}, onHelp = {}) },
                        bottomBar = {
                            BrewSessionPrimaryActionBar(action, presentation,
                                isDispatching = false, onDispatch = { events += it }, equipmentProfileId = guide.profileId)
                        },
                    ) { padding ->
                        BrewSessionContent(
                            presentation = presentation, completedLogId = null, isDispatching = false,
                            onDispatch = { events += it }, liveContent = liveContent,
                            runtime = runtime, bloomSpritesheetId = "coffee_flower",
                            equipmentProfileId = guide.profileId,
                            modifier = Modifier.fillMaxSize().padding(padding),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val name = "minimal-ui-" + packet.name.lowercase() + if (large) "-200" else ""
        // Save untouched production layout before assertions, scrolling, or completion interaction.
        save(name, renderedDensity, large)
        rule.onNodeWithText(context.getString(R.string.label_elapsed)).assertIsDisplayed()
        rule.onNodeWithText("2:10", substring = true).assertIsDisplayed()
        rule.onNodeWithText("340 g").performScrollTo().assertIsDisplayed()
        val spokenTarget = context.getString(R.string.label_live_target) + " · " +
            context.getString(R.string.label_brew_reference_brew_water) + " · " +
            context.getString(R.string.label_brew_reference_brew_total) + " 340 g"
        rule.onNodeWithContentDescription(spokenTarget).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.cd_bloom_animation))
            .performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTag("approved_instruction_asset", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithContentDescription(context.getString(R.string.cd_brew_water_height_target)).assertDoesNotExist()
        full.step?.illustration?.description?.let {
            rule.onNodeWithContentDescription(it).assertDoesNotExist()
        }
        rule.onNodeWithText(guide.steps[stageIndex].instruction).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.heading_guide_why)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_guidance_unavailable)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.msg_brew_session_unavailable)).assertDoesNotExist()
        rule.onNodeWithText(guide.steps[stageIndex].why).assertDoesNotExist()
        assertTextFits()
        rule.runOnIdle { assertTrue("Rendering and reading must not emit session events", events.isEmpty()) }
        if (packet == Packet.FULL && !large) {
            val primary = rule.onNodeWithText(context.getString(R.string.action_brew_pour_done))
            primary.performTouchInput { down(center) }
            try {
                save("minimal-ui-full-pressed", renderedDensity, large)
            } finally {
                primary.performTouchInput { cancel() }
            }
            rule.runOnIdle { assertTrue("Press feedback must not confirm a physical step", events.isEmpty()) }
        }
        rule.onNodeWithContentDescription(context.getString(R.string.action_pause_brew_clock)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.action_brew_pour_done)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(events.single() is SessionEvent.ManualAdvance) }
    }

    private fun assertTextFits() {
        val nodes = rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true,
        )
        repeat(nodes.fetchSemanticsNodes().size) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            nodes[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            layouts.forEach { layout ->
                val message = "Clipped text: " + layout.layoutInput.text + "; size=" + layout.size
                assertFalse(message, layout.multiParagraph.didExceedMaxLines)
                repeat(layout.lineCount) { line ->
                    assertTrue(message, layout.getLineLeft(line) >= -1f)
                    assertTrue(message, layout.getLineRight(line) <= layout.size.width + 1f)
                    assertTrue(message, layout.getLineBottom(line) <= layout.size.height + 1f)
                }
            }
        }
    }

    private fun save(name: String, density: Float, large: Boolean) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500L, 5_000L)
        // Compose can be idle before the window compositor latches its first complete frame.
        android.os.SystemClock.sleep(250L)
        val directory = File(context.getExternalFilesDir(null), "minimal-ui-redesign").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name + ".png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(directory, name + ".txt").writeText(
            "widthPx=" + bitmap.width + " heightPx=" + bitmap.height + " widthDp=" + bitmap.width / density +
                " heightDp=" + bitmap.height / density + " fontScale=" + (if (large) 2f else 1f) + "\n",
        )
    }
}
