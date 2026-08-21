package com.adsamcik.starlitcoffee.util

import kotlinx.serialization.Serializable

@Serializable
enum class RecognitionCapability {
    READY,
    AUTHORIZATION_REQUIRED,
    INSTALLATION_REQUIRED,
    ASSET_SETUP_REQUIRED,
    UNSUPPORTED,
    TEMPORARILY_UNAVAILABLE,
}

@Serializable
enum class RecognitionRunState {
    IDLE,
    RUNNING,
    PARTIAL,
    COMPLETE,
    RETRIABLE_FAILURE,
    TERMINAL_NO_RESULT,
}

@Serializable
enum class RecognitionPreference {
    UNDECIDED,
    ENABLED,
    DISABLED,
}

enum class RecognitionStatusText {
    CHECKING_LABEL,
    CHECKING_MORE_DETAILS,
    DETAILS_NEED_REVIEW,
    COULD_NOT_READ_MORE,
}

enum class RecognitionOffer {
    ENABLE,
    INSTALL,
    FINISH_SETUP,
}

enum class RecognitionRecoveryAction {
    RETRY,
    RETAKE,
}

data class RecognitionPresentation(
    val status: RecognitionStatusText? = null,
    val unresolvedCount: Int = 0,
    val offer: RecognitionOffer? = null,
    val recoveryAction: RecognitionRecoveryAction? = null,
    val announceUpdate: Boolean = false,
)

/** Pure mapping that prevents provider and pipeline vocabulary reaching Compose. */
object RecognitionUiStateMapper {
    fun map(
        capability: RecognitionCapability,
        runState: RecognitionRunState,
        preference: RecognitionPreference,
        hasValues: Boolean,
        unresolvedCount: Int,
        announceUpdate: Boolean = false,
    ): RecognitionPresentation {
        val offer = when {
            preference == RecognitionPreference.DISABLED -> null
            capability == RecognitionCapability.AUTHORIZATION_REQUIRED -> RecognitionOffer.ENABLE
            capability == RecognitionCapability.INSTALLATION_REQUIRED -> RecognitionOffer.INSTALL
            capability == RecognitionCapability.ASSET_SETUP_REQUIRED -> RecognitionOffer.FINISH_SETUP
            else -> null
        }
        val status = when (runState) {
            RecognitionRunState.RUNNING -> if (hasValues) {
                RecognitionStatusText.CHECKING_MORE_DETAILS
            } else {
                RecognitionStatusText.CHECKING_LABEL
            }
            RecognitionRunState.PARTIAL -> RecognitionStatusText.CHECKING_MORE_DETAILS
            RecognitionRunState.RETRIABLE_FAILURE -> if (hasValues) {
                RecognitionStatusText.COULD_NOT_READ_MORE
            } else {
                null
            }
            RecognitionRunState.COMPLETE -> if (unresolvedCount > 0) {
                RecognitionStatusText.DETAILS_NEED_REVIEW
            } else {
                null
            }
            RecognitionRunState.IDLE,
            RecognitionRunState.TERMINAL_NO_RESULT,
            -> null
        }
        val recovery = when {
            runState != RecognitionRunState.RETRIABLE_FAILURE -> null
            hasValues -> RecognitionRecoveryAction.RETRY
            else -> RecognitionRecoveryAction.RETAKE
        }
        return RecognitionPresentation(
            status = status,
            unresolvedCount = unresolvedCount,
            offer = offer,
            recoveryAction = recovery,
            announceUpdate = announceUpdate,
        )
    }

    fun fromPipeline(
        pipelineStatus: LlmEnrichmentStatus,
        isProcessing: Boolean,
        hasValues: Boolean,
        unresolvedCount: Int,
        preference: RecognitionPreference,
        mindlayerSupported: Boolean,
        mindlayerInstalled: Boolean,
    ): RecognitionPresentation {
        val capability = when {
            pipelineStatus == LlmEnrichmentStatus.SETUP_REQUIRED ->
                RecognitionCapability.ASSET_SETUP_REQUIRED
            pipelineStatus == LlmEnrichmentStatus.UNAVAILABLE && !mindlayerSupported ->
                RecognitionCapability.UNSUPPORTED
            pipelineStatus == LlmEnrichmentStatus.UNAVAILABLE && !mindlayerInstalled ->
                RecognitionCapability.INSTALLATION_REQUIRED
            pipelineStatus == LlmEnrichmentStatus.UNAVAILABLE ->
                RecognitionCapability.AUTHORIZATION_REQUIRED
            else -> RecognitionCapability.READY
        }
        val runState = when {
            isProcessing && hasValues -> RecognitionRunState.PARTIAL
            isProcessing -> RecognitionRunState.RUNNING
            pipelineStatus == LlmEnrichmentStatus.FAILED ||
                pipelineStatus == LlmEnrichmentStatus.TIMED_OUT -> RecognitionRunState.RETRIABLE_FAILURE
            pipelineStatus == LlmEnrichmentStatus.SUCCEEDED -> RecognitionRunState.COMPLETE
            else -> RecognitionRunState.IDLE
        }
        return map(
            capability = capability,
            runState = runState,
            preference = preference,
            hasValues = hasValues,
            unresolvedCount = unresolvedCount,
        )
    }
}
