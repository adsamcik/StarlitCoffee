package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.mindlayer.sdk.ConnectionState
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.shared.MindlayerErrorCode
import com.adsamcik.starlitcoffee.util.RecognitionCapability

/** A bind failure is not proof that the user needs to approve the app. */
internal fun mindlayerConnectionCapability(state: ConnectionState): RecognitionCapability =
    if (state == ConnectionState.REJECTED_NOT_APPROVED) {
        RecognitionCapability.AUTHORIZATION_REQUIRED
    } else {
        RecognitionCapability.TEMPORARILY_UNAVAILABLE
    }

/** Classify the SDK's stable error contract, never a message or caller-supplied code name. */
internal fun mindlayerRecoveryCapability(error: Exception, state: ConnectionState): RecognitionCapability {
    val typed = when (error) {
        is MindlayerException -> error
        is SecurityException -> MindlayerException.fromAidlSecurityException(error)
        else -> null
    }
    return when (typed?.code) {
        MindlayerErrorCode.CONSENT_REQUIRED,
        MindlayerErrorCode.ALLOWLIST_REVOKED,
        -> RecognitionCapability.AUTHORIZATION_REQUIRED
        MindlayerErrorCode.MODEL_MISSING -> RecognitionCapability.ASSET_SETUP_REQUIRED
        // These auth/integration failures are not an invitation to consent again.
        MindlayerErrorCode.CONSENT_DENIED,
        MindlayerErrorCode.IDENTITY_UNKNOWN,
        -> RecognitionCapability.TEMPORARILY_UNAVAILABLE
        MindlayerErrorCode.PERMISSION_DENIED -> mindlayerConnectionCapability(state)
        else -> if (typed == null && error is SecurityException) {
            // The SDK preserves unprefixed service auth-gate exceptions.
            RecognitionCapability.AUTHORIZATION_REQUIRED
        } else {
            RecognitionCapability.TEMPORARILY_UNAVAILABLE
        }
    }
}

internal fun mindlayerUnavailableResult(
    error: Exception,
    state: ConnectionState,
): LlmExtractionResult.Unavailable = LlmExtractionResult.Unavailable(
    reason = "Mindlayer service not available: ${error.message}",
    capability = mindlayerRecoveryCapability(error, state),
)

internal fun mindlayerFailureResult(
    error: Exception,
    state: ConnectionState,
    description: String,
    retryable: Boolean,
): LlmExtractionResult {
    val capability = mindlayerRecoveryCapability(error, state)
    return if (capability == RecognitionCapability.AUTHORIZATION_REQUIRED ||
        capability == RecognitionCapability.ASSET_SETUP_REQUIRED
    ) {
        LlmExtractionResult.Unavailable("$description: ${error.message}", capability)
    } else {
        LlmExtractionResult.Failed(
            error = "$description: ${error.message}",
            retryable = retryable,
            retryAfterMs = (error as? MindlayerException)?.retryAfterMs,
        )
    }
}
