package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.scan.observability.ConnectionStatus
import com.adsamcik.starlitcoffee.scan.observability.ConnectionTestResult
import com.adsamcik.starlitcoffee.ui.theme.StarlitCoffeeTheme
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MindlayerIntegrationFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun connectionTestWaitsForApprovalThenEnablesReconnectsAndRuns() {
        val harness = Harness()
        showDiagnostics(harness)
        click(R.string.action_test_connection)
        rule.onNodeWithText(label(R.string.action_run_test_prompt)).assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(1, harness.consentRequests)
            assertEquals(emptyList<String>(), harness.calls)
            harness.deliver(ConsentOutcome.GRANTED)
        }
        rule.runOnIdle {
            assertEquals(listOf("enable", "reconnect", "connection"), harness.calls)
            assertEquals(RecognitionPreference.ENABLED, harness.preference)
        }
        rule.onNodeWithText("Status: Connected").assertExists()
    }

    @Test
    fun alreadyApprovedPromptTestRetainsTheChosenAction() {
        val harness = Harness()
        showDiagnostics(harness)
        click(R.string.action_run_test_prompt)
        rule.runOnIdle { harness.deliver(ConsentOutcome.ALREADY_APPROVED) }
        rule.runOnIdle { assertEquals(listOf("enable", "reconnect", "prompt"), harness.calls) }
    }

    @Test
    fun cancellationDoesNotEnableOrTestAndTheUserCanRetry() {
        val harness = Harness()
        showDiagnostics(harness)
        click(R.string.action_test_connection)
        rule.runOnIdle { harness.deliver(ConsentOutcome.DECLINED) }
        rule.onNodeWithText(label(R.string.consent_declined)).assertExists()
        rule.onNodeWithText(label(R.string.action_test_connection)).assertIsEnabled()
        rule.runOnIdle {
            assertEquals(emptyList<String>(), harness.calls)
            assertEquals(RecognitionPreference.UNDECIDED, harness.preference)
        }
        click(R.string.action_run_test_prompt)
        rule.runOnIdle {
            assertEquals(2, harness.consentRequests)
            harness.deliver(ConsentOutcome.GRANTED)
        }
        rule.runOnIdle { assertEquals(listOf("enable", "reconnect", "prompt"), harness.calls) }
    }

    @Test
    fun deniedAccessDoesNotRetryOrOptIn() {
        val harness = Harness()
        showDiagnostics(harness)
        click(R.string.action_test_connection)
        rule.runOnIdle { harness.deliver(ConsentOutcome.DENIED_TEMPORARY) }
        rule.onNodeWithText(label(R.string.consent_denied_temporary)).assertExists()
        rule.runOnIdle {
            assertEquals(1, harness.consentRequests)
            assertEquals(emptyList<String>(), harness.calls)
        }
    }

    @Test
    fun failedConsentRequestReleasesBothTestButtonsWithoutRunning() {
        val harness = Harness()
        showDiagnostics(harness)
        click(R.string.action_run_test_prompt)
        rule.runOnIdle { harness.deliver(ConsentOutcome.FAILED) }
        rule.onNodeWithText(label(R.string.consent_failed)).assertExists()
        rule.onNodeWithText(label(R.string.action_test_connection)).assertIsEnabled()
        rule.onNodeWithText(label(R.string.action_run_test_prompt)).assertIsEnabled()
        rule.runOnIdle { assertEquals(emptyList<String>(), harness.calls) }
    }

    @Test
    fun failedPreferenceSaveStopsBeforeReconnectAndAllowsRetry() {
        val harness = Harness().apply { saveSucceeds = false }
        showDiagnostics(harness)
        click(R.string.action_test_connection)
        rule.runOnIdle { harness.deliver(ConsentOutcome.GRANTED) }
        rule.onNodeWithText(label(R.string.msg_settings_save_failed)).assertExists()
        rule.runOnIdle {
            assertEquals(listOf("enable"), harness.calls)
            assertEquals(RecognitionPreference.UNDECIDED, harness.preference)
            harness.saveSucceeds = true
        }
        click(R.string.action_test_connection)
        rule.runOnIdle { harness.deliver(ConsentOutcome.ALREADY_APPROVED) }
        rule.runOnIdle { assertEquals(listOf("enable", "enable", "reconnect", "connection"), harness.calls) }
    }

    @Test
    fun reconnectKeepsBothActionsDisabledAndDoesNotChangeThePendingTest() {
        val reconnect = CompletableDeferred<Unit>()
        val harness = Harness().apply { reconnectGate = reconnect }
        showDiagnostics(harness)
        click(R.string.action_test_connection)
        rule.runOnIdle { harness.deliver(ConsentOutcome.GRANTED) }
        rule.onNodeWithText(label(R.string.action_run_test_prompt)).assertIsNotEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, harness.consentRequests)
            assertEquals(listOf("enable", "reconnect"), harness.calls)
            reconnect.complete(Unit)
        }
        rule.runOnIdle { assertEquals(listOf("enable", "reconnect", "connection"), harness.calls) }
    }

    @Test
    fun pendingPromptSurvivesRecreationWhileApprovalIsOpen() {
        val harness = Harness()
        val restoration = StateRestorationTester(rule)
        restoration.setContent { Diagnostics(harness) }
        click(R.string.action_run_test_prompt)
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { harness.deliver(ConsentOutcome.GRANTED) }
        rule.runOnIdle {
            assertEquals(1, harness.consentRequests)
            assertEquals(listOf("enable", "reconnect", "prompt"), harness.calls)
        }
    }

    @Test
    fun missingMindlayerOpensInstallationAndContinuesOnlyAfterItIsInstalled() {
        val harness = Harness().apply { installed = false }
        showDiagnostics(harness)
        click(R.string.action_test_connection)
        rule.runOnIdle {
            assertEquals(1, harness.installRequests)
            assertEquals(0, harness.consentRequests)
            assertEquals(emptyList<String>(), harness.calls)
            harness.installed = true
            harness.resume()
        }
        rule.runOnIdle {
            assertEquals(1, harness.consentRequests)
            harness.deliver(ConsentOutcome.GRANTED)
        }
        rule.runOnIdle { assertEquals(listOf("enable", "reconnect", "connection"), harness.calls) }
    }

    @Test
    fun returningWithoutInstallingReleasesThePendingTest() {
        val harness = Harness().apply { installed = false }
        showDiagnostics(harness)
        click(R.string.action_run_test_prompt)
        rule.runOnIdle { harness.resume() }
        rule.onNodeWithText(label(R.string.consent_service_unavailable)).assertExists()
        rule.onNodeWithText(label(R.string.action_run_test_prompt)).assertIsEnabled()
        rule.runOnIdle {
            assertEquals(0, harness.consentRequests)
            assertEquals(emptyList<String>(), harness.calls)
        }
    }

    @Test
    fun settingsExposesMindlayerApprovalAnOffSwitchAndItsOwnOptions() {
        val harness = Harness()
        showSettings(harness)
        click(R.string.action_google_play)
        rule.runOnIdle {
            assertEquals(1, harness.googlePlayRequests)
            assertEquals(emptyList<String>(), harness.calls)
            assertEquals(RecognitionPreference.UNDECIDED, harness.preference)
        }
        rule.onNodeWithText(label(R.string.label_enhanced_label_recognition)).assertIsOff().performClick()
        rule.runOnIdle {
            assertEquals(1, harness.consentRequests)
            assertEquals(RecognitionPreference.UNDECIDED, harness.preference)
            harness.deliver(ConsentOutcome.GRANTED)
        }
        rule.onNodeWithText(label(R.string.label_enhanced_label_recognition)).assertIsOn()
        click(R.string.action_open_mindlayer)
        rule.runOnIdle { assertEquals(1, harness.openRequests) }
        rule.onNodeWithText(label(R.string.label_enhanced_label_recognition)).performClick().assertIsOff()
        rule.onNodeWithText(label(R.string.consent_granted)).assertDoesNotExist()
        rule.runOnIdle { assertEquals(RecognitionPreference.DISABLED, harness.preference) }
    }

    @Test
    fun permanentBlockOffersMindlayerManagementWithoutEnabling() {
        val harness = Harness()
        showSettings(harness)
        rule.onNodeWithText(label(R.string.label_enhanced_label_recognition)).performClick()
        rule.runOnIdle { harness.deliver(ConsentOutcome.DENIED_PERMANENT) }
        rule.onNodeWithText(label(R.string.consent_denied_permanent)).assertExists()
        click(R.string.action_open_mindlayer)
        rule.runOnIdle {
            assertEquals(1, harness.openRequests)
            assertEquals(emptyList<String>(), harness.calls)
            assertEquals(RecognitionPreference.UNDECIDED, harness.preference)
        }
    }

    @Test
    fun approvedButUnavailableModelsKeepTheOptInAndOfferMindlayerSetup() {
        val harness = Harness().apply { connected = false }
        showSettings(harness)
        rule.onNodeWithText(label(R.string.label_enhanced_label_recognition)).performClick()
        rule.runOnIdle { harness.deliver(ConsentOutcome.GRANTED) }
        rule.onNodeWithText(label(R.string.label_enhanced_label_recognition)).assertIsOn()
        rule.onNodeWithText(label(R.string.consent_still_unavailable)).assertExists()
        rule.onNodeWithText(label(R.string.action_open_mindlayer)).assertIsEnabled()
    }

    @Test
    fun unsupportedDevicesExplainTheRequirementWithoutStartingSetup() {
        val harness = Harness().apply { supported = false; installed = false }
        showSettings(harness)
        rule.onNodeWithText(label(R.string.label_enhanced_label_recognition)).assertIsNotEnabled()
        rule.onNodeWithText(label(R.string.msg_mindlayer_requires_android)).assertExists()
        rule.onNodeWithText(label(R.string.action_mindlayer_google_play)).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(0, harness.installRequests)
            assertEquals(0, harness.consentRequests)
        }
    }

    private fun showDiagnostics(harness: Harness) = rule.setContent { Diagnostics(harness) }

    @Composable
    private fun Diagnostics(harness: Harness) {
        CompositionLocalProvider(LocalLifecycleOwner provides harness) {
            StarlitCoffeeTheme(dynamicColor = false) {
                MindlayerDiagnosticsContent(
                    enabled = true,
                    integrationFlow = { ready, stopped -> harness.integration(ready, stopped) },
                    testConnection = { harness.calls.add("connection"); connected() },
                    runTestPrompt = { harness.calls.add("prompt"); connected() },
                )
            }
        }
    }

    private fun showSettings(harness: Harness) = rule.setContent {
        CompositionLocalProvider(LocalLifecycleOwner provides harness) {
            StarlitCoffeeTheme(dynamicColor = false) {
                val integration = harness.integration({}, {})
                SettingsGroup {
                    MindlayerSettingsContent(
                        preference = harness.preference,
                        enabled = true,
                        installed = harness.installed,
                        supported = harness.supported,
                        integration = integration,
                        onDisable = { harness.preference = RecognitionPreference.DISABLED },
                        onOpenMindlayer = { harness.openRequests++ },
                        onOpenGooglePlay = { harness.googlePlayRequests++ },
                    )
                }
            }
        }
    }

    private fun click(resourceId: Int) = rule.onNodeWithText(label(resourceId)).performClick()
    private fun label(resourceId: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
    private fun connected() = ConnectionTestResult(ConnectionStatus.CONNECTED, null, null, null)

    private class Harness : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        var installed by mutableStateOf(true)
        var preference by mutableStateOf(RecognitionPreference.UNDECIDED)
        var supported = true
        var saveSucceeds = true
        var connected = true
        var reconnectGate: CompletableDeferred<Unit>? = null
        var consentRequests = 0
        var installRequests = 0
        var openRequests = 0
        var googlePlayRequests = 0
        val calls = mutableListOf<String>()
        private var onConsent: (ConsentOutcome) -> Unit = {}

        fun deliver(outcome: ConsentOutcome) = onConsent(outcome)
        fun resume() {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        @Composable
        fun integration(ready: suspend () -> Unit, stopped: () -> Unit) = rememberMindlayerIntegrationFlow(
            supported = supported,
            isInstalled = { installed },
            openInstall = { installRequests++; true },
            enableRecognition = {
                calls.add("enable")
                if (saveSucceeds) preference = RecognitionPreference.ENABLED
                saveSucceeds
            },
            reconnect = { calls.add("reconnect"); reconnectGate?.await(); connected },
            consentFlow = { callback ->
                onConsent = callback
                MindlayerConsentFlow(false) { consentRequests++ }
            },
            onReady = ready,
            onStopped = stopped,
        )
    }
}
