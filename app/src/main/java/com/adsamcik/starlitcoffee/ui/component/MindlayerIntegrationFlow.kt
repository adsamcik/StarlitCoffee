package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.util.MindlayerAvailability
import com.adsamcik.starlitcoffee.util.MindlayerInstallLink
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Stable
internal class MindlayerIntegrationFlow(
    val inProgress: Boolean,
    val messageRes: Int?,
    val request: () -> Unit,
)

/** Settings and explicit diagnostic tests share the same install, consent and opt-in path. */
@Composable
internal fun rememberMindlayerIntegrationFlow(
    enableRecognition: suspend () -> Boolean,
    onReady: suspend () -> Unit = {},
    onStopped: () -> Unit = {},
): MindlayerIntegrationFlow {
    val context = LocalContext.current
    val app = context.applicationContext as? StarlitCoffeeApp
    return rememberMindlayerIntegrationFlow(
        supported = MindlayerAvailability.isSupported(),
        isInstalled = { MindlayerAvailability.isInstalled(context) },
        openInstall = { MindlayerInstallLink.open(context) },
        enableRecognition = {
            enableRecognition().also { saved ->
                if (saved) app?.enableMindlayerForCurrentSession()
            }
        },
        reconnect = { app?.reconnectMindlayerIfUnavailable() ?: false },
        consentFlow = { onOutcome -> rememberMindlayerConsentFlow(onOutcome) },
        onReady = onReady,
        onStopped = onStopped,
    )
}

/** Injectable platform boundaries let UI tests exercise the actual approval continuation. */
@Composable
internal fun rememberMindlayerIntegrationFlow(
    supported: Boolean,
    isInstalled: () -> Boolean,
    openInstall: () -> Boolean,
    enableRecognition: suspend () -> Boolean,
    reconnect: suspend () -> Boolean,
    consentFlow: @Composable ((ConsentOutcome) -> Unit) -> MindlayerConsentFlow,
    onReady: suspend () -> Unit,
    onStopped: () -> Unit,
): MindlayerIntegrationFlow {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var waitingForInstall by rememberSaveable { mutableStateOf(false) }
    var waitingForConsent by rememberSaveable { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var messageRes by remember { mutableStateOf<Int?>(null) }

    val consent = consentFlow { outcome ->
        if (waitingForConsent) {
            waitingForConsent = false
            if (outcome == ConsentOutcome.GRANTED || outcome == ConsentOutcome.ALREADY_APPROVED) {
                isConnecting = true
                scope.launch {
                    var enabled = false
                    try {
                        enabled = enableRecognition()
                        if (enabled) {
                            messageRes = if (reconnect()) R.string.consent_granted else R.string.consent_still_unavailable
                            onReady()
                        } else {
                            messageRes = R.string.msg_settings_save_failed
                            onStopped()
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Tracebox.log.error(error, LogTemplate.of("Mindlayer integration setup failed"))
                        messageRes = if (enabled) R.string.consent_still_unavailable else R.string.msg_settings_save_failed
                        onStopped()
                    } finally {
                        isConnecting = false
                    }
                }
            } else {
                messageRes = outcome.messageRes()
                onStopped()
            }
        }
    }

    fun requestConsent() {
        waitingForConsent = true
        consent.request()
    }

    val resumeInstall by rememberUpdatedState {
        if (waitingForInstall) {
            waitingForInstall = false
            if (isInstalled()) {
                requestConsent()
            } else {
                messageRes = R.string.consent_service_unavailable
                onStopped()
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeInstall()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val inProgress = waitingForInstall || waitingForConsent || isConnecting
    return MindlayerIntegrationFlow(
        inProgress = inProgress,
        messageRes = if (waitingForConsent || isConnecting) R.string.consent_requesting else messageRes,
        request = {
            // Read the mutable flags here as well as disabling controls; rapid taps can precede recomposition.
            if (!waitingForInstall && !waitingForConsent && !isConnecting) {
                messageRes = null
                when {
                    !supported -> {
                        messageRes = R.string.msg_mindlayer_requires_android
                        onStopped()
                    }
                    isInstalled() -> requestConsent()
                    else -> {
                        waitingForInstall = openInstall()
                        if (!waitingForInstall) {
                            messageRes = R.string.msg_could_not_open_app_store
                            onStopped()
                        }
                    }
                }
            }
        },
    )
}
