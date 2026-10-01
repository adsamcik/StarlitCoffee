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

enum class RecognitionAnnouncement {
    CHECKING,
    READY_TO_REVIEW,
    NO_RESULT,
    COULD_NOT_READ_MORE,
    RETRY,
    RETAKE,
    ENABLE,
    INSTALL,
    FINISH_SETUP,
}

data class RecognitionPresentation(
    val status: RecognitionStatusText? = null,
    val unresolvedCount: Int = 0,
    val offer: RecognitionOffer? = null,
    val recoveryAction: RecognitionRecoveryAction? = null,
    val announcement: RecognitionAnnouncement? = null,
)

/** Pure mapping that prevents provider and pipeline vocabulary reaching Compose. */
object RecognitionUiStateMapper {
    fun map(
        capability: RecognitionCapability,
        runState: RecognitionRunState,
        preference: RecognitionPreference,
        hasValues: Boolean,
        unresolvedCount: Int,
    ): RecognitionPresentation {
        val isRunning = runState == RecognitionRunState.RUNNING || runState == RecognitionRunState.PARTIAL
        val offer = when {
            preference == RecognitionPreference.DISABLED || isRunning -> null
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
        val recovery = recoveryFor(capability, runState, preference, hasValues)
        return RecognitionPresentation(
            status = status,
            unresolvedCount = unresolvedCount,
            offer = offer,
            recoveryAction = recovery,
            announcement = announcementFor(runState, hasValues, offer, recovery),
        )
    }

    private fun announcementFor(
        runState: RecognitionRunState,
        hasValues: Boolean,
        offer: RecognitionOffer?,
        recovery: RecognitionRecoveryAction?,
    ): RecognitionAnnouncement? = when {
        runState == RecognitionRunState.RUNNING || runState == RecognitionRunState.PARTIAL ->
            RecognitionAnnouncement.CHECKING
        offer == RecognitionOffer.ENABLE -> RecognitionAnnouncement.ENABLE
        offer == RecognitionOffer.INSTALL -> RecognitionAnnouncement.INSTALL
        offer == RecognitionOffer.FINISH_SETUP -> RecognitionAnnouncement.FINISH_SETUP
        recovery == RecognitionRecoveryAction.RETRY -> RecognitionAnnouncement.RETRY
        recovery == RecognitionRecoveryAction.RETAKE -> RecognitionAnnouncement.RETAKE
        runState == RecognitionRunState.RETRIABLE_FAILURE -> RecognitionAnnouncement.COULD_NOT_READ_MORE
        runState == RecognitionRunState.COMPLETE && hasValues -> RecognitionAnnouncement.READY_TO_REVIEW
        runState == RecognitionRunState.COMPLETE || runState == RecognitionRunState.TERMINAL_NO_RESULT ->
            RecognitionAnnouncement.NO_RESULT
        else -> null
    }

    private fun recoveryFor(
        capability: RecognitionCapability,
        runState: RecognitionRunState,
        preference: RecognitionPreference,
        hasValues: Boolean,
    ): RecognitionRecoveryAction? = when {
        runState != RecognitionRunState.RETRIABLE_FAILURE -> null
        preference == RecognitionPreference.DISABLED -> null
        capability != RecognitionCapability.READY &&
            capability != RecognitionCapability.TEMPORARILY_UNAVAILABLE -> null
        capability == RecognitionCapability.TEMPORARILY_UNAVAILABLE -> RecognitionRecoveryAction.RETRY
        hasValues -> RecognitionRecoveryAction.RETRY
        else -> RecognitionRecoveryAction.RETAKE
    }

    fun capabilityFromPipeline(
        pipelineStatus: LlmEnrichmentStatus,
        preference: RecognitionPreference,
        mindlayerSupported: Boolean,
        mindlayerInstalled: Boolean,
        fallback: RecognitionCapability = RecognitionCapability.READY,
    ): RecognitionCapability = when {
        pipelineStatus == LlmEnrichmentStatus.SUCCEEDED -> RecognitionCapability.READY
        pipelineStatus == LlmEnrichmentStatus.NOT_RUN -> fallback
        !mindlayerSupported -> RecognitionCapability.UNSUPPORTED
        !mindlayerInstalled -> RecognitionCapability.INSTALLATION_REQUIRED
        pipelineStatus == LlmEnrichmentStatus.AUTHORIZATION_REQUIRED -> RecognitionCapability.AUTHORIZATION_REQUIRED
        pipelineStatus == LlmEnrichmentStatus.SETUP_REQUIRED -> RecognitionCapability.ASSET_SETUP_REQUIRED
        pipelineStatus == LlmEnrichmentStatus.FAILED || pipelineStatus == LlmEnrichmentStatus.TIMED_OUT ->
            RecognitionCapability.READY
        pipelineStatus == LlmEnrichmentStatus.UNAVAILABLE && preference == RecognitionPreference.UNDECIDED ->
            RecognitionCapability.AUTHORIZATION_REQUIRED
        else -> RecognitionCapability.TEMPORARILY_UNAVAILABLE
    }

    fun fromPipeline(
        pipelineStatus: LlmEnrichmentStatus,
        isProcessing: Boolean,
        hasValues: Boolean,
        unresolvedCount: Int,
        preference: RecognitionPreference,
        mindlayerSupported: Boolean,
        mindlayerInstalled: Boolean,
        fallbackCapability: RecognitionCapability = RecognitionCapability.READY,
        fallbackRunState: RecognitionRunState = RecognitionRunState.IDLE,
    ): RecognitionPresentation {
        val capability = capabilityFromPipeline(
            pipelineStatus = pipelineStatus,
            preference = preference,
            mindlayerSupported = mindlayerSupported,
            mindlayerInstalled = mindlayerInstalled,
            fallback = fallbackCapability,
        )
        val runState = when {
            isProcessing && hasValues -> RecognitionRunState.PARTIAL
            isProcessing -> RecognitionRunState.RUNNING
            pipelineStatus == LlmEnrichmentStatus.FAILED ||
                pipelineStatus == LlmEnrichmentStatus.TIMED_OUT -> RecognitionRunState.RETRIABLE_FAILURE
            pipelineStatus == LlmEnrichmentStatus.UNAVAILABLE &&
                capability == RecognitionCapability.TEMPORARILY_UNAVAILABLE -> RecognitionRunState.RETRIABLE_FAILURE
            pipelineStatus == LlmEnrichmentStatus.SUCCEEDED -> RecognitionRunState.COMPLETE
            else -> fallbackRunState
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
