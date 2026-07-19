package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.util.shouldOfferMindlayerConnection
import kotlinx.coroutines.launch

/**
 * Tries the detected Mindlayer service before asking for consent. The prompt is
 * handled at most once per app launch so a declined consent never blocks setup.
 */
@Composable
fun MindlayerStartupConnectionPrompt(
    isMindlayerInstalled: Boolean,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? StarlitCoffeeApp
    val scope = rememberCoroutineScope()
    var connectionAttemptFinished by rememberSaveable { mutableStateOf(false) }
    var isConnected by rememberSaveable { mutableStateOf(false) }
    var offerHandled by rememberSaveable { mutableStateOf(false) }

    val consent = rememberMindlayerConsentFlow { outcome ->
        offerHandled = true
        if (outcome == ConsentOutcome.GRANTED || outcome == ConsentOutcome.ALREADY_APPROVED) {
            scope.launch {
                isConnected = app?.reconnectMindlayer() == true
                connectionAttemptFinished = true
            }
        }
    }

    LaunchedEffect(isMindlayerInstalled) {
        if (!isMindlayerInstalled) {
            connectionAttemptFinished = false
            isConnected = false
            return@LaunchedEffect
        }

        isConnected = app?.reconnectMindlayer() == true
        connectionAttemptFinished = true
    }

    if (
        shouldOfferMindlayerConnection(
            isInstalled = isMindlayerInstalled,
            connectionAttemptFinished = connectionAttemptFinished,
            isConnected = isConnected,
            offerHandled = offerHandled,
        )
    ) {
        AlertDialog(
            onDismissRequest = { offerHandled = true },
            title = { Text(stringResource(R.string.label_ai_service)) },
            text = { Text(stringResource(R.string.msg_mindlayer_connection_offer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        offerHandled = true
                        consent.request()
                    },
                    enabled = !consent.inProgress,
                ) {
                    Text(stringResource(R.string.action_connect_mindlayer))
                }
            },
            dismissButton = {
                TextButton(onClick = { offerHandled = true }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
