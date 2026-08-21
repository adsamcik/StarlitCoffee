package com.adsamcik.starlitcoffee.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.scan.observability.ConnectionStatus
import com.adsamcik.starlitcoffee.scan.observability.ConnectionTestResult
import com.adsamcik.starlitcoffee.scan.observability.MindlayerConnectionTester
import com.adsamcik.starlitcoffee.util.MindlayerInstallLink
import kotlinx.coroutines.launch

private val connectedColor = Color(0xFF4CAF50)
private val connectingColor = Color(0xFFFFC107)
private val disconnectedColor = Color(0xFF9E9E9E)

/**
 * The user-facing Mindlayer setup surface. Diagnostics deliberately live in
 * [MindlayerDiagnosticsCard], so a debug build does not turn a simple consent
 * action into a wall of implementation detail.
 */
@Composable
fun MindlayerSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mindlayerInstalled = rememberMindlayerInstalled()
    val couldNotOpenAppStore = stringResource(R.string.msg_could_not_open_app_store)
    val consentGrantedMessage = stringResource(R.string.consent_granted)
    val consentUnavailableMessage = stringResource(R.string.consent_still_unavailable)
    val consentMessages = ConsentOutcome.entries.associateWith { outcome ->
        stringResource(outcome.messageRes())
    }
    var isLoading by remember { mutableStateOf(false) }

    val consent = rememberMindlayerConsentFlow { outcome ->
        when (outcome) {
            ConsentOutcome.GRANTED, ConsentOutcome.ALREADY_APPROVED -> scope.launch {
                isLoading = true
                try {
                    val app = context.applicationContext as? StarlitCoffeeApp
                    val connected = app?.reconnectMindlayer() ?: false
                    Toast.makeText(
                        context,
                        if (connected) consentGrantedMessage else consentUnavailableMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                } finally {
                    isLoading = false
                }
            }
            else -> Toast.makeText(context, consentMessages.getValue(outcome), Toast.LENGTH_LONG).show()
        }
    }

    SettingsGroup {
        Column(modifier = Modifier.padding(20.dp)) {
            if (mindlayerInstalled) {
                FilledTonalButton(
                    onClick = consent.request,
                    enabled = !consent.inProgress && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (consent.inProgress || isLoading) {
                                R.string.consent_requesting
                            } else {
                                R.string.action_enable_ai
                            },
                        ),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.msg_mindlayer_install_recommendation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = {
                        if (!MindlayerInstallLink.open(context)) {
                            Toast.makeText(
                                context,
                                couldNotOpenAppStore,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_mindlayer_google_play))
                }
            }
        }
    }
}

/** Debug-only connection controls, kept separate from ordinary AI setup. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MindlayerDiagnosticsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun runTest(block: suspend () -> ConnectionTestResult) {
        isLoading = true
        scope.launch {
            try {
                result = block()
            } finally {
                isLoading = false
            }
        }
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
                val (dotColor, statusText) = when (result?.status) {
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
                        runTest { MindlayerConnectionTester.testConnection(context) }
                    },
                    enabled = !isLoading,
                ) {
                    Text(stringResource(R.string.action_test_connection))
                }
                OutlinedButton(
                    onClick = {
                        runTest { MindlayerConnectionTester.runTestPrompt(context) }
                    },
                    enabled = !isLoading,
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
                        text = stringResource(R.string.label_testing),
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

            result?.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
