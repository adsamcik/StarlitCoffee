package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.ui.screen.rememberRecognitionSetupLaunch
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.ModelSetupLaunchOutcome
import com.adsamcik.starlitcoffee.util.RecognitionCapability
import com.adsamcik.starlitcoffee.util.RecognitionAnnouncement
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionPresentation
import com.adsamcik.starlitcoffee.util.RecognitionRunState
import com.adsamcik.starlitcoffee.util.RecognitionUiStateMapper
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecognitionStatusAccessibilityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun ordinaryAddAnnouncesOneStableCheckingStatusThenAllHighConfidenceReadiness() {
        val input = showAdd(presentation(processing = true, hasValues = false))
        val initialNode = announcementNode(R.string.msg_checking_label)

        composeRule.runOnIdle {
            input.value = presentation(processing = true, hasValues = true, unresolvedCount = 4)
        }
        assertEquals(initialNode, announcementNode(R.string.msg_checking_label))
        composeRule.runOnIdle {
            input.value = presentation(status = LlmEnrichmentStatus.SUCCEEDED, unresolvedCount = 0)
        }
        assertEquals(initialNode, announcementNode(R.string.announce_label_details_ready))
        composeRule.onNodeWithText(label(R.string.announce_label_details_ready)).assertDoesNotExist()
    }

    @Test
    fun durableReviewAnnouncesReadinessWithoutCountChatterOrLosingEditedFieldFocus() {
        val input = showAdd(durablePresentation(1), durable = true)
        val initialNode = announcementNode(R.string.announce_label_details_ready)
        val nameField = hasSetTextAction() and hasText("Test coffee")
        composeRule.onNode(nameField).performScrollTo().performClick().performTextReplacement("My edited coffee")

        composeRule.runOnIdle { input.value = durablePresentation(5) }

        composeRule.onNode(hasSetTextAction() and hasText("My edited coffee")).assertIsFocused()
        assertEquals(initialNode, announcementNode(R.string.announce_label_details_ready))
    }

    @Test
    fun rescanUsesOneStableLiveRegionForProgressAndRetryWithoutReadingDiffs() {
        val input = mutableStateOf(presentation(processing = true, unresolvedCount = 1))
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                RescanDeltaDialog(
                    bag = CoffeeBagEntity(id = 1L, name = "Stored coffee"),
                    resolvedFields = mapOf("name" to "Private scanned coffee"),
                    recognition = input.value,
                    isProcessing = input.value.announcement == RecognitionAnnouncement.CHECKING,
                    isComplete = false,
                    recognitionActions = RecognitionActions(onRetry = {}),
                    onUpdateBag = {},
                    onNewBag = {},
                    onDismiss = {},
                )
            }
        }
        val initialNode = announcementNode(R.string.msg_checking_label)
        composeRule.runOnIdle { input.value = presentation(processing = true, unresolvedCount = 7) }
        assertEquals(initialNode, announcementNode(R.string.msg_checking_label))
        composeRule.runOnIdle { input.value = presentation(status = LlmEnrichmentStatus.UNAVAILABLE) }
        assertEquals(
            initialNode,
            announcementNode(R.string.action_try_label_again, prefixResourceId = R.string.msg_could_not_read_more_details),
        )
    }

    @Test
    fun untouchedManualFormDoesNotCreateRecognitionAnnouncements() {
        showAdd(RecognitionPresentation())

        composeRule.onAllNodes(liveRegion(), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun unavailableSetupGivesInlineFeedbackAndNeverArmsReturnRetryOrConsent() {
        val harness = showSetup(ModelSetupLaunchOutcome.UNAVAILABLE)

        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).performClick()
        composeRule.onNodeWithText(label(R.string.msg_could_not_open_label_recognition_setup)).assertIsDisplayed()
        announcementNode(R.string.msg_could_not_open_label_recognition_setup)
        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(2, harness.launches.get())
            assertEquals(0, harness.opened.get())
            assertEquals(0, harness.retries.get())
            assertEquals(0, harness.enables.get())
        }
    }

    @Test
    fun failedRescanSetupKeepsCurrentResultsAndGivesInlineFeedback() {
        val harness = showSetup(ModelSetupLaunchOutcome.FAILED, rescan = true)

        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).performClick()
        composeRule.onNodeWithText(label(R.string.msg_could_not_open_label_recognition_setup)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.action_update_bag_so_far)).assertIsEnabled()
        announcementNode(R.string.msg_could_not_open_label_recognition_setup)
        composeRule.runOnIdle {
            assertEquals(0, harness.opened.get())
            assertEquals(0, harness.retries.get())
            assertEquals(0, harness.enables.get())
        }
    }

    @Test
    fun durableSetupFailurePreservesTheExistingFormValueAndRecoveryAction() {
        val harness = showSetup(ModelSetupLaunchOutcome.UNAVAILABLE, durable = true)

        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).performClick()
        composeRule.onNodeWithText(label(R.string.msg_could_not_open_label_recognition_setup)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction() and hasText("Test coffee")).performScrollTo().assertIsEnabled()
        composeRule.runOnIdle { assertEquals(0, harness.opened.get()) }
    }

    @Test
    fun openingSetupDisablesDoubleTapsAndRestoresActionAfterTypedFailure() {
        val harness = showSetup()
        val setupButton = composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup))

        setupButton.performClick().assertIsNotEnabled().performClick()
        composeRule.onNodeWithText(label(R.string.msg_opening_label_recognition_setup)).assertIsDisplayed()
        announcementNode(R.string.msg_opening_label_recognition_setup)
        composeRule.runOnIdle {
            assertEquals(1, harness.launches.get())
            harness.result.complete(ModelSetupLaunchOutcome.FAILED)
        }

        setupButton.assertIsEnabled()
        composeRule.onNodeWithText(label(R.string.msg_could_not_open_label_recognition_setup)).assertIsDisplayed()
    }

    @Test
    fun openedModelSetupArmsTheCapturedGenerationOnce() {
        val harness = showSetup(ModelSetupLaunchOutcome.OPENED_SETUP)

        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).performClick()
        composeRule.onNodeWithText(label(R.string.msg_could_not_open_label_recognition_setup)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.msg_opening_label_recognition_setup)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, harness.opened.get())
            assertEquals("generation-1", harness.openedGeneration)
        }
    }

    @Test
    fun openedAppFallbackAlsoArmsReturnWithoutClaimingRecognitionSucceeded() {
        val harness = showSetup(ModelSetupLaunchOutcome.OPENED_APP, rescan = true)

        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).performClick()
        composeRule.onNodeWithText(label(R.string.action_update_bag_so_far)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, harness.opened.get()) }
    }

    @Test
    fun setupCompletionForReplacedGenerationCannotArmTheNewReview() {
        val harness = showSetup()
        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).performClick()
        composeRule.runOnIdle {
            harness.generation.value = "generation-2"
        }
        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).assertIsEnabled()
        composeRule.runOnIdle { harness.result.complete(ModelSetupLaunchOutcome.OPENED_SETUP) }

        composeRule.runOnIdle {
            assertEquals(0, harness.opened.get())
            assertEquals(0, harness.destinationsOpened.get())
            assertEquals(1, harness.cancelledQueries.get())
        }
        composeRule.onNodeWithText(label(R.string.msg_could_not_open_label_recognition_setup)).assertDoesNotExist()
    }

    @Test
    fun disablingRecognitionDuringSetupCannotArmAnAutomaticReturnRetry() {
        val harness = showSetup()
        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).performClick()
        composeRule.runOnIdle { harness.enabled.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { harness.result.complete(ModelSetupLaunchOutcome.OPENED_SETUP) }

        composeRule.runOnIdle {
            assertEquals(0, harness.opened.get())
            assertEquals(0, harness.destinationsOpened.get())
            assertEquals(1, harness.cancelledQueries.get())
        }
    }

    @Test
    fun callerCancellationRestoresSetupActionWithoutFailureFeedbackOrReturnRetry() {
        val harness = showSetup(cancelLaunch = true)

        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).performClick().assertIsEnabled()
        composeRule.onNodeWithText(label(R.string.msg_could_not_open_label_recognition_setup)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.msg_opening_label_recognition_setup)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, harness.opened.get()) }
    }

    private fun showAdd(initial: RecognitionPresentation, durable: Boolean = false): MutableState<RecognitionPresentation> {
        val presentation = mutableStateOf(initial)
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                AddBagSheet(
                    initialName = "Test coffee",
                    initialFormOverride = if (durable) BagFormSnapshot(name = "Test coffee") else null,
                    recognition = presentation.value,
                    preserveDraftOnDismiss = durable,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                )
            }
        }
        return presentation
    }

    private fun showSetup(
        outcome: ModelSetupLaunchOutcome? = null,
        rescan: Boolean = false,
        durable: Boolean = false,
        cancelLaunch: Boolean = false,
    ): SetupHarness {
        val harness = SetupHarness()
        if (outcome != null) harness.result.complete(outcome)
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                val launch = rememberRecognitionSetupLaunch(
                    sessionId = "session-1",
                    generationId = harness.generation.value,
                    enabled = harness.enabled.value,
                    launchSetup = {
                        harness.launches.incrementAndGet()
                        try {
                            if (cancelLaunch) throw CancellationException("Review closed")
                            harness.result.await().also { result ->
                                if (result == ModelSetupLaunchOutcome.OPENED_SETUP || result == ModelSetupLaunchOutcome.OPENED_APP) {
                                    harness.destinationsOpened.incrementAndGet()
                                }
                            }
                        } catch (error: CancellationException) {
                            harness.cancelledQueries.incrementAndGet()
                            throw error
                        }
                    },
                    onOpened = {
                        harness.openedGeneration = it
                        harness.opened.incrementAndGet()
                    },
                )
                val recognition = presentation(status = LlmEnrichmentStatus.SETUP_REQUIRED)
                if (rescan) {
                    RescanDeltaDialog(
                        bag = CoffeeBagEntity(id = 1L, name = "Stored coffee"),
                        resolvedFields = mapOf("name" to "Test coffee"),
                        recognition = recognition,
                        isComplete = false,
                        recognitionActions = RecognitionActions(
                            onSetup = launch.request,
                            onRetry = { harness.retries.incrementAndGet() },
                            onEnable = { harness.enables.incrementAndGet() },
                            setupState = launch.state,
                        ),
                        onUpdateBag = {},
                        onNewBag = {},
                        onDismiss = {},
                    )
                } else {
                    AddBagSheet(
                        initialName = "Test coffee",
                        initialFormOverride = if (durable) BagFormSnapshot(name = "Test coffee") else null,
                        recognition = recognition,
                        onSetupAi = launch.request,
                        setupLaunchState = launch.state,
                        onRetryLlmEnrichment = { harness.retries.incrementAndGet() },
                        onEnableAi = { harness.enables.incrementAndGet() },
                        preserveDraftOnDismiss = durable,
                        onDismiss = {},
                        onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    )
                }
            }
        }
        return harness
    }

    private fun presentation(
        status: LlmEnrichmentStatus = LlmEnrichmentStatus.NOT_RUN,
        processing: Boolean = false,
        hasValues: Boolean = true,
        unresolvedCount: Int = 0,
    ) = RecognitionUiStateMapper.fromPipeline(
        pipelineStatus = status,
        isProcessing = processing,
        hasValues = hasValues,
        unresolvedCount = unresolvedCount,
        preference = RecognitionPreference.ENABLED,
        mindlayerSupported = true,
        mindlayerInstalled = true,
    )

    private fun durablePresentation(count: Int) = RecognitionUiStateMapper.fromPipeline(
        pipelineStatus = LlmEnrichmentStatus.NOT_RUN,
        isProcessing = false,
        hasValues = true,
        unresolvedCount = count,
        preference = RecognitionPreference.ENABLED,
        mindlayerSupported = true,
        mindlayerInstalled = true,
        fallbackCapability = RecognitionCapability.READY,
        fallbackRunState = RecognitionRunState.COMPLETE,
    )

    private fun liveRegion() = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun announcementNode(resourceId: Int, prefixResourceId: Int? = null): Int {
        composeRule.onAllNodes(liveRegion(), useUnmergedTree = true).assertCountEquals(1)
        val node = composeRule.onNode(liveRegion(), useUnmergedTree = true).fetchSemanticsNode()
        val expected = prefixResourceId?.let { label(it) + " " }.orEmpty() + label(resourceId)
        assertEquals(listOf(expected), node.config[SemanticsProperties.ContentDescription])
        return node.id
    }

    private fun label(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private class SetupHarness {
        val generation = mutableStateOf("generation-1")
        val enabled = mutableStateOf(true)
        val result = CompletableDeferred<ModelSetupLaunchOutcome>()
        val launches = AtomicInteger()
        val opened = AtomicInteger()
        val retries = AtomicInteger()
        val enables = AtomicInteger()
        val destinationsOpened = AtomicInteger()
        val cancelledQueries = AtomicInteger()
        var openedGeneration: String? = null
    }
}
