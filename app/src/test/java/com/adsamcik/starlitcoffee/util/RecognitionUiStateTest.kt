package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionUiStateTest {
    @Test
    fun `partial values remain editable while more details are checked`() {
        val presentation = RecognitionUiStateMapper.map(
            capability = RecognitionCapability.READY,
            runState = RecognitionRunState.PARTIAL,
            preference = RecognitionPreference.UNDECIDED,
            hasValues = true,
            unresolvedCount = 2,
        )

        assertEquals(RecognitionStatusText.CHECKING_MORE_DETAILS, presentation.status)
        assertNull(presentation.recoveryAction)
    }

    @Test
    fun `failure keeps values and offers retry`() {
        val presentation = RecognitionUiStateMapper.map(
            capability = RecognitionCapability.TEMPORARILY_UNAVAILABLE,
            runState = RecognitionRunState.RETRIABLE_FAILURE,
            preference = RecognitionPreference.ENABLED,
            hasValues = true,
            unresolvedCount = 1,
        )

        assertEquals(RecognitionStatusText.COULD_NOT_READ_MORE, presentation.status)
        assertEquals(RecognitionRecoveryAction.RETRY, presentation.recoveryAction)
    }

    @Test
    fun `missing optional runtime is contextual and never blocks basic values`() {
        val presentation = RecognitionUiStateMapper.map(
            capability = RecognitionCapability.INSTALLATION_REQUIRED,
            runState = RecognitionRunState.COMPLETE,
            preference = RecognitionPreference.UNDECIDED,
            hasValues = true,
            unresolvedCount = 1,
        )

        assertEquals(RecognitionOffer.INSTALL, presentation.offer)
        assertEquals(RecognitionStatusText.DETAILS_NEED_REVIEW, presentation.status)
    }

    @Test
    fun `disabled enrichment never offers setup`() {
        val presentation = RecognitionUiStateMapper.map(
            capability = RecognitionCapability.INSTALLATION_REQUIRED,
            runState = RecognitionRunState.COMPLETE,
            preference = RecognitionPreference.DISABLED,
            hasValues = true,
            unresolvedCount = 0,
        )

        assertNull(presentation.offer)
        assertNull(presentation.status)
    }

    @Test
    fun `unsupported runtime never offers installation or retry`() {
        val presentation = RecognitionUiStateMapper.fromPipeline(
            pipelineStatus = LlmEnrichmentStatus.UNAVAILABLE,
            isProcessing = false,
            hasValues = true,
            unresolvedCount = 1,
            preference = RecognitionPreference.UNDECIDED,
            mindlayerSupported = false,
            mindlayerInstalled = false,
        )

        assertNull(presentation.offer)
        assertNull(presentation.recoveryAction)
    }
}
