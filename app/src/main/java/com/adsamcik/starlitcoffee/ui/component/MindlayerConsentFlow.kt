package com.adsamcik.starlitcoffee.ui.component

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.adsamcik.mindlayer.sdk.ConsentRequestResult
import com.adsamcik.mindlayer.sdk.MindlayerConsent
import com.adsamcik.starlitcoffee.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Terminal outcome of a Mindlayer consent request, mapped from
 * [ConsentRequestResult] plus the consent-screen activity result. Callers turn
 * these into localized messages and, on [GRANTED]/[ALREADY_APPROVED], re-bind
 * the Mindlayer clients.
 */
enum class ConsentOutcome {
    /** User approved on the consent screen. */
    GRANTED,

    /** App was already approved — no screen shown. */
    ALREADY_APPROVED,

    /** User dismissed/cancelled the consent screen. */
    DECLINED,

    /** Temporarily denied; will be askable again later. */
    DENIED_TEMPORARY,

    /** Permanently blocked; user must unblock from the Mindlayer dashboard. */
    DENIED_PERMANENT,

    /** Mindlayer service not installed / not bindable. */
    SERVICE_UNAVAILABLE,

    /** Any other failure. */
    FAILED,
}

/** Reactive handle for a consent request: whether one is in flight + a trigger. */
@Stable
class MindlayerConsentFlow internal constructor(
    val inProgress: Boolean,
    val request: () -> Unit,
)

/** Localized message for an outcome, for a toast/snackbar. */
@StringRes
fun ConsentOutcome.messageRes(): Int = when (this) {
    ConsentOutcome.GRANTED -> R.string.consent_granted
    ConsentOutcome.ALREADY_APPROVED -> R.string.consent_already_approved
    ConsentOutcome.DECLINED -> R.string.consent_declined
    ConsentOutcome.DENIED_TEMPORARY -> R.string.consent_denied_temporary
    ConsentOutcome.DENIED_PERMANENT -> R.string.consent_denied_permanent
    ConsentOutcome.SERVICE_UNAVAILABLE -> R.string.consent_service_unavailable
    ConsentOutcome.FAILED -> R.string.consent_failed
}

/**
 * Remember a single-flight Mindlayer consent flow.
 *
 * Implements the canonical SDK pattern (see Mindlayer `SDK_INTEGRATION.md`):
 * call [MindlayerConsent.requestConsent] off the main thread; if `Available`,
 * launch the returned `IntentSender` via `StartIntentSenderForResult`; report
 * the outcome to [onOutcome]. A request already in flight is ignored, so a
 * double tap (or scan + settings both triggering) can't open duplicate
 * consent screens.
 */
@Composable
fun rememberMindlayerConsentFlow(onOutcome: (ConsentOutcome) -> Unit): MindlayerConsentFlow {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onOutcome)
    val inProgress = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        inProgress.value = false
        callback.value(
            if (result.resultCode == Activity.RESULT_OK) ConsentOutcome.GRANTED
            else ConsentOutcome.DECLINED,
        )
    }

    val request = remember(launcher) {
        request@{
            if (inProgress.value) return@request
            inProgress.value = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { MindlayerConsent.requestConsent(appContext) }
                when (result) {
                    // Keep inProgress = true until the launcher result arrives.
                    is ConsentRequestResult.Available ->
                        launcher.launch(IntentSenderRequest.Builder(result.intentSender).build())

                    ConsentRequestResult.AlreadyApproved -> {
                        inProgress.value = false
                        callback.value(ConsentOutcome.ALREADY_APPROVED)
                    }

                    is ConsentRequestResult.Denied -> {
                        inProgress.value = false
                        callback.value(
                            if (result.untilEpochMs == null) ConsentOutcome.DENIED_PERMANENT
                            else ConsentOutcome.DENIED_TEMPORARY,
                        )
                    }

                    ConsentRequestResult.ServiceUnavailable -> {
                        inProgress.value = false
                        callback.value(ConsentOutcome.SERVICE_UNAVAILABLE)
                    }

                    is ConsentRequestResult.Failed -> {
                        inProgress.value = false
                        callback.value(ConsentOutcome.FAILED)
                    }
                }
            }
            Unit
        }
    }

    return MindlayerConsentFlow(inProgress = inProgress.value, request = request)
}
