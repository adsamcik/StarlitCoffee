package com.adsamcik.starlitcoffee.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.StarlitCoffeeApp
import com.adsamcik.starlitcoffee.ui.component.ConsentOutcome
import com.adsamcik.starlitcoffee.ui.component.RecognitionActions
import com.adsamcik.starlitcoffee.ui.component.RecognitionSetupState
import com.adsamcik.starlitcoffee.ui.component.messageRes
import com.adsamcik.starlitcoffee.ui.component.rememberMindlayerConsentFlow
import com.adsamcik.starlitcoffee.util.MindlayerInstallLink
import com.adsamcik.starlitcoffee.util.ModelSetupLaunchOutcome
import com.adsamcik.starlitcoffee.viewmodel.BrewViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ScanRecognitionRecovery(
    val isRetrying: Boolean,
    val actions: RecognitionActions,
)

internal data class RecognitionSetupLaunch(
    val state: RecognitionSetupState,
    val request: () -> Unit,
)

/** Only a successfully opened destination arms a return retry. */
@Composable
internal fun rememberRecognitionSetupLaunch(
    sessionId: String?,
    generationId: String?,
    enabled: Boolean,
    launchSetup: suspend () -> ModelSetupLaunchOutcome,
    onOpened: (String?) -> Unit,
): RecognitionSetupLaunch {
    val scope = rememberCoroutineScope()
    val latestIdentity by rememberUpdatedState(sessionId to generationId)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestLaunch by rememberUpdatedState(launchSetup)
    val latestOnOpened by rememberUpdatedState(onOpened)
    var state by remember(sessionId, generationId) { mutableStateOf(RecognitionSetupState.IDLE) }
    var launchJob by remember(sessionId, generationId) { mutableStateOf<Job?>(null) }
    DisposableEffect(sessionId, generationId, enabled) {
        onDispose { launchJob?.cancel() }
    }
    return RecognitionSetupLaunch(state) {
        if (latestEnabled && state != RecognitionSetupState.OPENING) {
            val requestedIdentity = sessionId to generationId
            state = RecognitionSetupState.OPENING
            launchJob = scope.launch {
                try {
                    val outcome = latestLaunch()
                    if (latestIdentity == requestedIdentity && latestEnabled) {
                        state = when (outcome) {
                            ModelSetupLaunchOutcome.OPENED_SETUP, ModelSetupLaunchOutcome.OPENED_APP -> {
                                latestOnOpened(requestedIdentity.second)
                                RecognitionSetupState.IDLE
                            }
                            ModelSetupLaunchOutcome.UNAVAILABLE -> RecognitionSetupState.UNAVAILABLE
                            ModelSetupLaunchOutcome.FAILED -> RecognitionSetupState.FAILED
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    state = RecognitionSetupState.FAILED
                } finally {
                    if (state == RecognitionSetupState.OPENING) state = RecognitionSetupState.IDLE
                    launchJob = null
                }
            }
        }
    }
}

/** Reconnect before retrying, without reviving a replaced or already-running scan. */
internal suspend fun retryCurrentScanReview(
    requested: ScanReviewData,
    currentData: () -> ScanReviewData,
    reconnect: suspend () -> Unit,
    retry: (String) -> Boolean,
): Boolean {
    val sessionId = requested.sessionId ?: return false
    fun isCurrent(): Boolean = currentData().let {
        it.sessionId == sessionId && it.generationId == requested.generationId && !it.isProcessing
    }
    if (!isCurrent()) return false
    reconnect()
    return isCurrent() && retry(sessionId)
}

/** Shared recovery for new-bag and rescan review; ordinary retry never requests consent. */
@Composable
internal fun rememberScanRecognitionRecovery(
    brewViewModel: BrewViewModel,
    data: ScanReviewData,
    onRetake: () -> Unit,
    enabled: Boolean = true,
): ScanRecognitionRecovery {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val latestData by rememberUpdatedState(data)
    val latestEnabled by rememberUpdatedState(enabled)
    val consentMessages = ConsentOutcome.entries.associateWith { stringResource(it.messageRes()) }
    var isRetrying by remember(data.sessionId) { mutableStateOf(false) }
    var isReconnecting by remember(data.sessionId) { mutableStateOf(false) }
    var consentReview by remember(data.sessionId) { mutableStateOf<ScanReviewData?>(null) }
    var setupReturnPending by rememberSaveable(data.sessionId) { mutableStateOf(false) }
    var setupGeneration by rememberSaveable(data.sessionId) { mutableStateOf<String?>(null) }
    val setupLaunch = rememberRecognitionSetupLaunch(
        sessionId = data.sessionId,
        generationId = data.generationId,
        enabled = enabled && !data.isProcessing,
        launchSetup = brewViewModel::launchMindlayerModelSetup,
        onOpened = { generation ->
            setupGeneration = generation
            setupReturnPending = true
        },
    )
    LaunchedEffect(data.sessionId, data.llmStatus, data.fieldEvidence, data.generationId, data.isProcessing) {
        isRetrying = false
    }

    fun retryReview(requested: ScanReviewData, forceReconnect: Boolean = false) {
        if (!latestEnabled || latestData.isProcessing) return
        if (isRetrying || isReconnecting) return
        isReconnecting = true
        isRetrying = true
        scope.launch {
            var queued = false
            try {
                queued = retryCurrentScanReview(
                    requested = requested,
                    currentData = { latestData.copy(isProcessing = latestData.isProcessing || !latestEnabled) },
                    reconnect = {
                        val app = context.applicationContext as? StarlitCoffeeApp
                        if (forceReconnect) app?.reconnectMindlayer()
                        else app?.reconnectMindlayerIfUnavailable()
                    },
                    retry = brewViewModel::retryBagPhotoLlm,
                )
            } finally {
                if (latestData.sessionId == requested.sessionId) {
                    isReconnecting = false
                    isRetrying = queued
                }
            }
        }
    }

    val consentFlow = rememberMindlayerConsentFlow { outcome ->
        val requested = consentReview
        consentReview = null
        when (outcome) {
            ConsentOutcome.GRANTED, ConsentOutcome.ALREADY_APPROVED -> {
                brewViewModel.enableLabelRecognition()
                if (requested != null) retryReview(requested, forceReconnect = true)
            }
            else -> Toast.makeText(context, consentMessages.getValue(outcome), Toast.LENGTH_LONG).show()
        }
    }
    DisposableEffect(lifecycleOwner, data.sessionId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && setupReturnPending) {
                setupReturnPending = false
                retryReview(latestData.copy(generationId = setupGeneration), forceReconnect = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return ScanRecognitionRecovery(
        isRetrying = isRetrying || isReconnecting || consentFlow.inProgress,
        actions = RecognitionActions(
            onRetry = { retryReview(latestData) },
            onEnable = {
                if (latestEnabled && !consentFlow.inProgress) {
                    consentReview = latestData
                    consentFlow.request()
                }
            },
            onInstall = {
                if (latestEnabled && !MindlayerInstallLink.open(context)) {
                    Toast.makeText(context, R.string.msg_could_not_open_app_store, Toast.LENGTH_LONG).show()
                }
            },
            onSetup = setupLaunch.request,
            onDisable = { if (latestEnabled) brewViewModel.disableLabelRecognition() },
            onRetake = { if (latestEnabled) onRetake() },
            setupState = setupLaunch.state,
        ),
    )
}
