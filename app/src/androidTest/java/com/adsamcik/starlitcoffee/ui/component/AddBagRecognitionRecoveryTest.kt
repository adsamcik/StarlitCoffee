package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.util.LlmEnrichmentStatus
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.RecognitionUiStateMapper
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddBagRecognitionRecoveryTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun temporaryConnectionOrModelUnavailabilityRetriesWithoutConsent() {
        val callbacks = showReview(LlmEnrichmentStatus.UNAVAILABLE)

        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_use_label_recognition)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, callbacks.retries.get())
            assertEquals(0, callbacks.enables.get())
            assertEquals(0, callbacks.setups.get())
        }
    }

    @Test
    fun temporaryUnavailabilityWithoutReadFieldsStillRetriesWithoutConsent() {
        val callbacks = showReview(LlmEnrichmentStatus.UNAVAILABLE, hasValues = false)

        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_use_label_recognition)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, callbacks.retries.get())
            assertEquals(0, callbacks.enables.get())
            assertEquals(0, callbacks.setups.get())
        }
    }

    @Test
    fun explicitAuthorizationRequestsConsentWithoutRetrying() {
        val callbacks = showReview(LlmEnrichmentStatus.AUTHORIZATION_REQUIRED)

        composeRule.onNodeWithText(label(R.string.action_use_label_recognition)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, callbacks.enables.get())
            assertEquals(0, callbacks.retries.get())
            assertEquals(0, callbacks.setups.get())
        }
    }

    @Test
    fun requiredModelSetupOpensSetupWithoutRetryingOrRequestingConsent() {
        val callbacks = showReview(LlmEnrichmentStatus.SETUP_REQUIRED)

        composeRule.onNodeWithText(label(R.string.action_finish_label_recognition_setup)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(label(R.string.action_try_label_again)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.action_use_label_recognition)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, callbacks.setups.get())
            assertEquals(0, callbacks.retries.get())
            assertEquals(0, callbacks.enables.get())
        }
    }

    private fun showReview(status: LlmEnrichmentStatus, hasValues: Boolean = true): CallbackCounts {
        val callbacks = CallbackCounts()
        val presentation = RecognitionUiStateMapper.fromPipeline(
            pipelineStatus = status,
            isProcessing = false,
            hasValues = hasValues,
            unresolvedCount = if (hasValues) 1 else 0,
            preference = RecognitionPreference.ENABLED,
            mindlayerSupported = true,
            mindlayerInstalled = true,
        )
        composeRule.setContent {
            StarlitCoffeeTheme(dynamicColor = false) {
                AddBagSheet(
                    initialName = if (hasValues) "Test coffee" else null,
                    recognition = presentation,
                    preserveDraftOnDismiss = true,
                    onDismiss = {},
                    onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onRetryLlmEnrichment = { callbacks.retries.incrementAndGet() },
                    onEnableAi = { callbacks.enables.incrementAndGet() },
                    onSetupAi = { callbacks.setups.incrementAndGet() },
                )
            }
        }
        return callbacks
    }

    private fun label(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private class CallbackCounts {
        val retries = AtomicInteger()
        val enables = AtomicInteger()
        val setups = AtomicInteger()
    }
}
