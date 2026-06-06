package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.scan.observability.ConnectionStatus
import com.adsamcik.starlitcoffee.scan.observability.ConnectionTestResult
import com.adsamcik.starlitcoffee.scan.observability.MindlayerConnectionTester
import kotlinx.coroutines.launch

private val connectedColor = Color(0xFF4CAF50)
private val connectingColor = Color(0xFFFFC107)
private val disconnectedColor = Color(0xFF9E9E9E)
private val errorColor = Color(0xFFF44336)

@Composable
fun MindlayerSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val consent = rememberMindlayerConsentFlow { outcome ->
        when (outcome) {
            ConsentOutcome.GRANTED, ConsentOutcome.ALREADY_APPROVED -> scope.launch {
                val app = context.applicationContext as? StarlitCoffeeApp
                val ok = app?.reconnectMindlayer() ?: false
                Toast.makeText(
                    context,
                    context.getString(
                        if (ok) R.string.consent_granted else R.string.consent_still_unavailable,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                isLoading = true
                result = MindlayerConnectionTester.testConnection(context)
                isLoading = false
            }
            else -> Toast.makeText(context, context.getString(outcome.messageRes()), Toast.LENGTH_LONG).show()
        }
    }

    // Auto-test connection on first composition
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isLoading = true
        result = MindlayerConnectionTester.testConnection(context)
        isLoading = false
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.label_ai_service),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Status row
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (dotColor, statusText) = when (result?.status) {
                    ConnectionStatus.CONNECTED -> connectedColor to "Connected"
                    ConnectionStatus.CONNECTING -> connectingColor to "Connecting…"
                    ConnectionStatus.DISCONNECTED -> disconnectedColor to "Disconnected"
                    ConnectionStatus.ERROR -> errorColor to "Error"
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

            // Engine info
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

            Spacer(modifier = Modifier.height(12.dp))

            // Consent — grant Mindlayer access so the AI service can be used.
            FilledTonalButton(
                onClick = consent.request,
                enabled = !consent.inProgress && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (consent.inProgress) R.string.consent_requesting else R.string.action_enable_ai,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            result = MindlayerConnectionTester.testConnection(context)
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                ) {
                    Text(stringResource(R.string.action_test_connection))
                }
                FilledTonalButton(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            result = MindlayerConnectionTester.runTestPrompt(context)
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                ) {
                    Text(stringResource(R.string.action_run_test_prompt))
                }
            }

            // Loading indicator
            if (isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.label_testing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Test result
            result?.testResult?.let { test ->
                Spacer(modifier = Modifier.height(12.dp))
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
                    text = "Latency: ${test.latencyMs}ms | Tokens: ${test.tokenCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Error message
            result?.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠ $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = errorColor,
                )
            }
        }
    }
}
