package com.adsamcik.starlitcoffee.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.scan.observability.ConnectionStatus
import com.adsamcik.starlitcoffee.scan.observability.ConnectionTestResult
import com.adsamcik.starlitcoffee.scan.observability.MindlayerConnectionTester
import com.adsamcik.starlitcoffee.scan.observability.errorResultFor
import com.adsamcik.starlitcoffee.util.MindlayerAvailability
import com.adsamcik.starlitcoffee.util.MindlayerInstallLink
import com.adsamcik.starlitcoffee.util.RecognitionPreference
import com.adsamcik.starlitcoffee.util.openInstalledMindlayerApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val connectedColor = Color(0xFF4CAF50)
private val connectingColor = Color(0xFFFFC107)
private val disconnectedColor = Color(0xFF9E9E9E)

/** The ordinary Settings control owns opt-in, approval and access to Mindlayer's own options. */
@Composable
fun MindlayerSettingsRow(
    preference: RecognitionPreference,
    enabled: Boolean,
    enableRecognition: suspend () -> Boolean,
    disableRecognition: suspend () -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshOnReturn by rememberSaveable { mutableStateOf(false) }
    val couldNotOpen = stringResource(R.string.consent_service_unavailable)
    val couldNotOpenStore = stringResource(R.string.msg_could_not_open_app_store)
    val integration = rememberMindlayerIntegrationFlow(enableRecognition)
    val refresh by rememberUpdatedState(integration.request)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && refreshOnReturn) {
                refreshOnReturn = false
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    MindlayerSettingsContent(
        preference = preference,
        enabled = enabled,
        installed = rememberMindlayerInstalled(),
        supported = MindlayerAvailability.isSupported(),
        integration = integration,
        onDisable = {
            val app = context.applicationContext as? StarlitCoffeeApp
            scope.launch {
                if (disableRecognition()) app?.disableMindlayerForCurrentSession()
            }
        },
        onOpenMindlayer = {
            val opened = runCatching { openInstalledMindlayerApp(context) }.getOrDefault(false)
            refreshOnReturn = opened
            if (!opened) Toast.makeText(context, couldNotOpen, Toast.LENGTH_LONG).show()
        },
        onOpenGooglePlay = {
            if (!MindlayerInstallLink.open(context)) {
                Toast.makeText(context, couldNotOpenStore, Toast.LENGTH_LONG).show()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MindlayerSettingsContent(
    preference: RecognitionPreference,
    enabled: Boolean,
    installed: Boolean,
    supported: Boolean,
    integration: MindlayerIntegrationFlow,
    onDisable: () -> Unit,
    onOpenMindlayer: () -> Unit,
    onOpenGooglePlay: () -> Unit,
) {
    val recognitionEnabled = preference == RecognitionPreference.ENABLED
    val messageRes = integration.messageRes?.takeUnless {
        !recognitionEnabled && (it == R.string.consent_granted || it == R.string.consent_still_unavailable)
    }
    SettingsSwitchRow(
        title = stringResource(R.string.label_enhanced_label_recognition),
        summary = stringResource(
            messageRes ?: if (supported) R.string.msg_enhanced_label_recognition else R.string.msg_mindlayer_requires_android,
        ),
        checked = recognitionEnabled,
        enabled = enabled && !integration.inProgress && (supported || recognitionEnabled),
        onCheckedChange = { if (it) integration.request() else onDisable() },
    )
    if (supported) {
        FlowRow(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!installed) {
                FilledTonalButton(
                    onClick = integration.request,
                    enabled = enabled && !integration.inProgress,
                ) {
                    Text(stringResource(R.string.action_mindlayer_google_play))
                }
            } else {
                if (recognitionEnabled || integration.messageRes == R.string.consent_denied_permanent) {
                    OutlinedButton(
                        onClick = onOpenMindlayer,
                        enabled = enabled && !integration.inProgress,
                    ) {
                        Text(stringResource(R.string.action_open_mindlayer))
                    }
                }
                TextButton(
                    onClick = onOpenGooglePlay,
                    enabled = enabled && !integration.inProgress,
                ) {
                    Text(stringResource(R.string.action_google_play))
                }
            }
        }
    }
}

private enum class MindlayerDiagnosticTest { CONNECTION, PROMPT }

/** Explicit diagnostic actions also opt in and obtain approval before contacting the engine. */
@Composable
fun MindlayerDiagnosticsCard(enableRecognition: suspend () -> Boolean, enabled: Boolean = true) {
    val context = LocalContext.current
    MindlayerDiagnosticsContent(
        enabled = enabled,
        integrationFlow = { ready, stopped -> rememberMindlayerIntegrationFlow(enableRecognition, ready, stopped) },
        testConnection = { MindlayerConnectionTester.testConnection(context) },
        runTestPrompt = { MindlayerConnectionTester.runTestPrompt(context) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MindlayerDiagnosticsContent(
    enabled: Boolean,
    integrationFlow: @Composable (suspend () -> Unit, () -> Unit) -> MindlayerIntegrationFlow,
    testConnection: suspend () -> ConnectionTestResult,
    runTestPrompt: suspend () -> ConnectionTestResult,
) {
    var result by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var pendingTest by rememberSaveable { mutableStateOf<MindlayerDiagnosticTest?>(null) }

    val integration = integrationFlow(
        {
            val selected = pendingTest
            pendingTest = null
            try {
                result = when (selected) {
                    MindlayerDiagnosticTest.CONNECTION -> testConnection()
                    MindlayerDiagnosticTest.PROMPT -> runTestPrompt()
                    null -> null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                result = errorResultFor(error)
            }
        },
        { pendingTest = null },
    )
    val isLoading = integration.inProgress
    fun requestTest(test: MindlayerDiagnosticTest) {
        if (!enabled || isLoading || pendingTest != null) return
        result = null
        pendingTest = test
        integration.request()
    }

    SettingsGroup {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.label_ai_service),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val (dotColor, statusText) = when (if (isLoading) ConnectionStatus.CONNECTING else result?.status) {
                    ConnectionStatus.CONNECTED -> connectedColor to "Connected"
                    ConnectionStatus.CONNECTING -> connectingColor to "Connecting…"
                    ConnectionStatus.INITIALIZING -> connectingColor to "Initializing…"
                    ConnectionStatus.DISCONNECTED -> disconnectedColor to "Disconnected"
                    ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error to "Error"
                    null -> disconnectedColor to "Not tested"
                }
                Spacer(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Status: $statusText",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            result?.engineInfo?.let { info ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Backend: ${info.backend}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Init time: ${"%.1f".format(info.initTimeSeconds)}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Decode speed: ${"%.1f".format(info.decodeToksPerSec)} tok/s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        requestTest(MindlayerDiagnosticTest.CONNECTION)
                    },
                    enabled = enabled && !isLoading,
                ) {
                    Text(stringResource(R.string.action_test_connection))
                }
                OutlinedButton(
                    onClick = {
                        requestTest(MindlayerDiagnosticTest.PROMPT)
                    },
                    enabled = enabled && !isLoading,
                ) {
                    Text(stringResource(R.string.action_run_test_prompt))
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(if (pendingTest != null) R.string.consent_requesting else R.string.label_testing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            result?.testResult?.let { test ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Prompt: \"${test.prompt}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Response: \"${test.response}\"",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Latency: ${test.latencyMs}ms · Tokens: ${test.tokenCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val message = result?.errorMessage ?: integration.messageRes?.takeUnless { isLoading }?.let { stringResource(it) }
            message?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (result?.status == ConnectionStatus.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
