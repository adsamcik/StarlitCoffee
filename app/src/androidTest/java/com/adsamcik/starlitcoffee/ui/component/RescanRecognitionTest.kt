package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionUiStateMapper
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RescanRecognitionTest {
    @get:Rule val composeRule = createComposeRule()
    private val bag = CoffeeBagEntity(id = 1L, name = "Stored coffee", roaster = "Stored roaster")

    @Test
    fun ongoingEmptyScanShowsProgressWithoutClaimingTheLabelMatches() {
        showReview(ReviewInput(status = LlmEnrichmentStatus.NOT_RUN, processing = true))

        composeRule.onNodeWithText(label(R.string.title_rescan_results)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.msg_checking_label)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.msg_rescan_no_changes_yet)).assertIsDisplayed()
        assertNoFinalMatch()
        assertNoSaveActions()
    }

    @Test
    fun partialChangesCanUpdateOrCreateUsingTheCurrentResults() {
        val fields = mapOf("name" to "Found coffee")
        val review = showReview(ReviewInput(fields = fields, status = LlmEnrichmentStatus.NOT_RUN, processing = true))

        composeRule.onNodeWithText(label(R.string.msg_checking_more_details)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.action_update_bag_so_far)).assertIsEnabled().performClick()
        composeRule.onNodeWithText(label(R.string.action_new_bag_so_far)).assertIsEnabled().performClick()
        assertNoFinalMatch()
        composeRule.runOnIdle {
            assertEquals("Found coffee", review.callbacks.updatedBag?.name)
            assertEquals(fields, review.callbacks.createdFields)
            assertEquals(1, review.callbacks.updates.get())
            assertEquals(1, review.callbacks.creates.get())
        }
    }

    @Test
    fun lateCompletedResultReplacesProgressAndUsesCompletedSaveActions() {
        val review = showReview(
            ReviewInput(fields = mapOf("name" to "Found coffee"), status = LlmEnrichmentStatus.NOT_RUN, processing = true),
        )
        composeRule.onNodeWithText(label(R.string.action_update_bag_so_far)).assertExists()

        composeRule.runOnIdle {
            review.input.value = review.input.value.copy(
                fields = mapOf("name" to "Found coffee", "origin" to "Colombia"),
                status = LlmEnrichmentStatus.SUCCEEDED,
                processing = false,
                complete = true,
            )
        }

        composeRule.onNodeWithText(label(R.string.msg_checking_more_details)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.action_update_bag_so_far)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.action_update_bag)).assertIsEnabled()
        composeRule.onNodeWithText(label(R.string.action_new_bag)).assertIsEnabled()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Colombia"))
        composeRule.onNodeWithText("Colombia").assertIsDisplayed()
    }

    @Test
    fun matchingFieldsOnlyBecomeAFinalMatchWhenRecognitionCompletes() {
        val review = showReview(
            ReviewInput(fields = mapOf("name" to bag.name), status = LlmEnrichmentStatus.NOT_RUN, processing = true),
        )
        assertNoFinalMatch()

        composeRule.runOnIdle {
            review.input.value = review.input.value.copy(
                status = LlmEnrichmentStatus.SUCCEEDED,
                processing = false,
                complete = true,
            )
        }

        composeRule.onNodeWithText(label(R.string.title_rescan_no_changes)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.msg_rescan_no_changes)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.msg_rescan_no_changes_yet)).assertDoesNotExist()
        assertNoSaveActions()
    }

    @Test
    fun temporaryUnavailabilityRetriesWithoutAskingForConsent() {
        val review = showReview(ReviewInput(fields = mapOf("name" to bag.name)))

        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_use_label_recognition)).assertDoesNotExist()
        assertNoFinalMatch()
        composeRule.runOnIdle {
            assertEquals(1, review.callbacks.retries.get())
            assertEquals(0, review.callbacks.enables.get())
            assertEquals(0, review.callbacks.setups.get())
        }
    }

    @Test
    fun unreadableScanOffersRetakeWithoutAnEmptySaveOrConsent() {
        val review = showReview(ReviewInput(status = LlmEnrichmentStatus.FAILED))

        composeRule.onNodeWithText(label(R.string.action_retake_label_photo)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.action_use_label_recognition)).assertDoesNotExist()
        assertNoFinalMatch()
        assertNoSaveActions()
        composeRule.runOnIdle {
            assertEquals(1, review.callbacks.retakes.get())
            assertEquals(0, review.callbacks.enables.get())
            assertEquals(0, review.callbacks.updates.get())
        }
    }

    @Test
    fun explicitAuthorizationOffersConsentAndNeverACompletedMatch() {
        val review = showReview(ReviewInput(fields = mapOf("name" to bag.name), status = LlmEnrichmentStatus.AUTHORIZATION_REQUIRED))

        composeRule.onNodeWithText(label(R.string.action_use_label_recognition)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertDoesNotExist()
        assertNoFinalMatch()
        composeRule.runOnIdle {
            assertEquals(1, review.callbacks.enables.get())
            assertEquals(0, review.callbacks.retries.get())
        }
    }

    @Test
    fun missingAssetsOfferSetupAndNeverACompletedMatch() {
        val review = showReview(ReviewInput(fields = mapOf("name" to bag.name), status = LlmEnrichmentStatus.SETUP_REQUIRED))

        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertDoesNotExist()
        assertNoFinalMatch()
        composeRule.runOnIdle {
            assertEquals(1, review.callbacks.setups.get())
            assertEquals(0, review.callbacks.enables.get())
            assertEquals(0, review.callbacks.retries.get())
        }
    }

    @Test
    fun missingRuntimeOffersInstallationWithoutRetryingOrRequestingConsent() {
        val review = showReview(ReviewInput(fields = mapOf("name" to bag.name), installed = false))

        composeRule.onNodeWithText(label(R.string.action_set_up_label_recognition)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.action_use_label_recognition)).assertDoesNotExist()
        assertNoFinalMatch()
        composeRule.runOnIdle {
            assertEquals(1, review.callbacks.installs.get())
            assertEquals(0, review.callbacks.enables.get())
            assertEquals(0, review.callbacks.retries.get())
        }
    }

    @Test
    fun failedRecognitionDoesNotTurnMatchingPartialFieldsIntoAFinalMatch() {
        showReview(ReviewInput(fields = mapOf("name" to bag.name), status = LlmEnrichmentStatus.FAILED))

        composeRule.onNodeWithText(label(R.string.title_rescan_results)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.msg_could_not_read_more_details)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.msg_rescan_incomplete)).assertIsDisplayed()
        assertNoFinalMatch()
        assertNoSaveActions()
    }

    @Test
    fun savingDisablesSaveDismissAndRecognitionActions() {
        val review = showReview(ReviewInput(fields = mapOf("name" to "Found coffee"), updating = true))

        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertIsNotEnabled()
        composeRule.onNodeWithText(label(R.string.action_update_bag_so_far)).assertIsNotEnabled()
        composeRule.onNodeWithText(label(R.string.action_new_bag_so_far)).assertIsNotEnabled()
        composeRule.onNodeWithText(label(R.string.action_cancel)).assertIsNotEnabled()

        listOf(
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED to R.string.action_use_label_recognition,
            LlmEnrichmentStatus.SETUP_REQUIRED to R.string.action_finish_label_recognition_setup,
        ).forEach { (status, action) ->
            composeRule.runOnIdle { review.input.value = review.input.value.copy(status = status) }
            composeRule.onNodeWithText(label(action)).assertIsNotEnabled()
            composeRule.onNodeWithText(label(R.string.action_always_enter_manually)).assertIsNotEnabled()
        }
        composeRule.runOnIdle {
            assertEquals(0, review.callbacks.updates.get())
            assertEquals(0, review.callbacks.creates.get())
            assertEquals(0, review.callbacks.retries.get())
            assertEquals(0, review.callbacks.enables.get())
            assertEquals(0, review.callbacks.setups.get())
        }
    }

    @Test
    fun recoveryRemainsReachableWhenManyChangesNeedScrolling() {
        val review = showReview(
            ReviewInput(
                fields = mapOf(
                    "name" to "Found coffee",
                    "roaster" to "Found roaster",
                    "origin" to "Colombia",
                    "region" to "Huila",
                    "variety" to "Geisha",
                    "roastLevel" to "Light",
                    "processType" to "Washed",
                    "tastingNotes" to "Peach",
                    "farm" to "Found farm",
                    "altitude" to "1800 m",
                ),
            ),
        )

        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("1800 m"))
        composeRule.onNodeWithText("1800 m").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label(R.string.action_try_label_again)))
        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, review.callbacks.retries.get()) }
    }

    @Test
    fun photoOnlyResultPreservesPhotoUpdateWithoutCreatingAnEmptyNewBag() {
        val review = showReview(ReviewInput(reviewedPhotos = "content://photos/reviewed"))

        composeRule.onNodeWithText(label(R.string.action_update_bag_so_far)).assertIsEnabled().performClick()
        composeRule.onNodeWithText(label(R.string.action_new_bag_so_far)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.action_new_bag)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(bag, review.callbacks.updatedBag)
            assertEquals(1, review.callbacks.updates.get())
            assertEquals(0, review.callbacks.creates.get())
        }
    }

    @Test
    fun largerTextKeepsCurrentResultActionsAndRecoveryReachable() {
        val review = showReview(
            ReviewInput(fields = mapOf("name" to "Found coffee")),
            fontScale = 1.6f,
        )

        listOf(
            R.string.action_update_bag_so_far,
            R.string.action_new_bag_so_far,
            R.string.action_cancel,
        ).forEach { action ->
            composeRule.onNodeWithText(label(action)).assertIsDisplayed().assertIsEnabled().performClick()
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label(R.string.action_try_label_again)))
        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, review.callbacks.updates.get())
            assertEquals(1, review.callbacks.creates.get())
            assertEquals(1, review.callbacks.dismissals.get())
            assertEquals(1, review.callbacks.retries.get())
            assertEquals(0, review.callbacks.enables.get())
        }
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
            val destination = File(instrumentation.targetContext.cacheDir, "rescan-current-results-large-font.png")
            destination.outputStream().use { output -> screenshot.compress(Bitmap.CompressFormat.PNG, 100, output) }
            screenshot.recycle()
        }
    }

    private fun showReview(initial: ReviewInput, fontScale: Float? = null): ReviewHarness {
        val input = mutableStateOf(initial)
        val callbacks = Callbacks()
        composeRule.setContent {
            val current = input.value
            val presentation = RecognitionUiStateMapper.fromPipeline(
                pipelineStatus = current.status,
                isProcessing = current.processing,
                hasValues = current.fields.values.any(String::isNotBlank),
                unresolvedCount = 0,
                preference = RecognitionPreference.ENABLED,
                mindlayerSupported = true,
                mindlayerInstalled = current.installed,
            )
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                StarlitCoffeeTheme(dynamicColor = false) {
                    RescanDeltaDialog(
                        bag = bag,
                        resolvedFields = current.fields,
                        reviewedPhotoUris = current.reviewedPhotos,
                        recognition = presentation,
                        isProcessing = current.processing,
                        isComplete = current.complete,
                        isUpdating = current.updating,
                        recognitionActions = RecognitionActions(
                            onRetry = { callbacks.retries.incrementAndGet() },
                            onEnable = { callbacks.enables.incrementAndGet() },
                            onInstall = { callbacks.installs.incrementAndGet() },
                            onSetup = { callbacks.setups.incrementAndGet() },
                            onDisable = { callbacks.disables.incrementAndGet() },
                            onRetake = { callbacks.retakes.incrementAndGet() },
                        ),
                        onUpdateBag = { callbacks.updatedBag = it; callbacks.updates.incrementAndGet() },
                        onNewBag = { callbacks.createdFields = it; callbacks.creates.incrementAndGet() },
                        onDismiss = { callbacks.dismissals.incrementAndGet() },
                    )
                }
            }
        }
        return ReviewHarness(input, callbacks)
    }

    private fun assertNoFinalMatch() {
        composeRule.onNodeWithText(label(R.string.title_rescan_no_changes)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.msg_rescan_no_changes)).assertDoesNotExist()
    }

    private fun assertNoSaveActions() {
        listOf(R.string.action_update_bag, R.string.action_update_bag_so_far, R.string.action_new_bag, R.string.action_new_bag_so_far)
            .forEach { resource -> composeRule.onNodeWithText(label(resource)).assertDoesNotExist() }
    }

    private fun label(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private data class ReviewInput(
        val fields: Map<String, String> = emptyMap(),
        val status: LlmEnrichmentStatus = LlmEnrichmentStatus.UNAVAILABLE,
        val processing: Boolean = false,
        val complete: Boolean = false,
        val updating: Boolean = false,
        val installed: Boolean = true,
        val reviewedPhotos: String? = null,
    )

    private data class ReviewHarness(val input: MutableState<ReviewInput>, val callbacks: Callbacks)

    private class Callbacks {
        val retries = AtomicInteger()
        val enables = AtomicInteger()
        val installs = AtomicInteger()
        val setups = AtomicInteger()
        val disables = AtomicInteger()
        val retakes = AtomicInteger()
        val updates = AtomicInteger()
        val creates = AtomicInteger()
        val dismissals = AtomicInteger()
        var updatedBag: CoffeeBagEntity? = null
        var createdFields: Map<String, String>? = null
    }
}
