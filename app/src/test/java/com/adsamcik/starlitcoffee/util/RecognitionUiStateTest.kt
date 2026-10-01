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

    @Test
    fun `generic unavailability respects enabled undecided and disabled preferences`() {
        val expectations = listOf(
            Triple(RecognitionPreference.ENABLED, null, RecognitionRecoveryAction.RETRY),
            Triple(RecognitionPreference.UNDECIDED, RecognitionOffer.ENABLE, null),
            Triple(RecognitionPreference.DISABLED, null, null),
        )

        expectations.forEach { (preference, offer, recovery) ->
            val presentation = fromPipeline(LlmEnrichmentStatus.UNAVAILABLE, preference)

            assertEquals("Offer for $preference", offer, presentation.offer)
            assertEquals("Recovery for $preference", recovery, presentation.recoveryAction)
        }
        assertEquals(
            RecognitionCapability.TEMPORARILY_UNAVAILABLE,
            RecognitionUiStateMapper.capabilityFromPipeline(
                pipelineStatus = LlmEnrichmentStatus.UNAVAILABLE,
                preference = RecognitionPreference.ENABLED,
                mindlayerSupported = true,
                mindlayerInstalled = true,
            ),
        )
    }

    @Test
    fun `explicit authorization and setup offer the needed action without retry`() {
        val expectations = listOf(
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED to RecognitionOffer.ENABLE,
            LlmEnrichmentStatus.SETUP_REQUIRED to RecognitionOffer.FINISH_SETUP,
        )

        expectations.forEach { (status, offer) ->
            RecognitionPreference.entries.forEach { preference ->
                val presentation = fromPipeline(
                    status = status,
                    preference = preference,
                    fallbackCapability = RecognitionCapability.READY,
                    fallbackRunState = RecognitionRunState.RETRIABLE_FAILURE,
                )

                assertEquals(
                    "$status offer for $preference",
                    offer.takeUnless { preference == RecognitionPreference.DISABLED },
                    presentation.offer,
                )
                assertNull("$status must not retry before recovery", presentation.recoveryAction)
            }
        }
    }

    @Test
    fun `unsupported and missing runtime never retry terminal failures`() {
        val statuses = listOf(
            LlmEnrichmentStatus.UNAVAILABLE,
            LlmEnrichmentStatus.FAILED,
            LlmEnrichmentStatus.TIMED_OUT,
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED,
            LlmEnrichmentStatus.SETUP_REQUIRED,
        )

        statuses.forEach { status ->
            RecognitionPreference.entries.forEach { preference ->
                val unsupported = fromPipeline(
                    status = status,
                    preference = preference,
                    supported = false,
                    installed = false,
                    fallbackRunState = RecognitionRunState.RETRIABLE_FAILURE,
                )
                val missing = fromPipeline(
                    status = status,
                    preference = preference,
                    installed = false,
                    fallbackRunState = RecognitionRunState.RETRIABLE_FAILURE,
                )

                assertNull("Unsupported $status offer for $preference", unsupported.offer)
                assertNull("Unsupported $status recovery for $preference", unsupported.recoveryAction)
                assertEquals(
                    "Missing $status offer for $preference",
                    RecognitionOffer.INSTALL.takeUnless { preference == RecognitionPreference.DISABLED },
                    missing.offer,
                )
                assertNull("Missing $status recovery for $preference", missing.recoveryAction)
            }
        }
    }

    @Test
    fun `durable unavailable result with values retries instead of appearing complete`() {
        RecognitionCapability.entries.forEach { retainedCapability ->
            val presentation = fromPipeline(
                status = LlmEnrichmentStatus.UNAVAILABLE,
                fallbackCapability = retainedCapability,
                fallbackRunState = RecognitionRunState.COMPLETE,
            )

            assertEquals(RecognitionStatusText.COULD_NOT_READ_MORE, presentation.status)
            assertEquals(RecognitionRecoveryAction.RETRY, presentation.recoveryAction)
            assertNull(presentation.offer)
        }
    }

    @Test
    fun `new failure clears retained blocking capability so legitimate retry remains available`() {
        listOf(LlmEnrichmentStatus.FAILED, LlmEnrichmentStatus.TIMED_OUT).forEach { status ->
            listOf(RecognitionCapability.AUTHORIZATION_REQUIRED, RecognitionCapability.ASSET_SETUP_REQUIRED).forEach { retained ->
                val presentation = fromPipeline(
                    status = status,
                    fallbackCapability = retained,
                    fallbackRunState = RecognitionRunState.COMPLETE,
                )

                assertNull(presentation.offer)
                assertEquals(RecognitionRecoveryAction.RETRY, presentation.recoveryAction)
            }
        }
    }

    @Test
    fun `active processing outranks retained failure and blocks premature recovery offers`() {
        val statuses = listOf(
            LlmEnrichmentStatus.UNAVAILABLE,
            LlmEnrichmentStatus.FAILED,
            LlmEnrichmentStatus.TIMED_OUT,
            LlmEnrichmentStatus.AUTHORIZATION_REQUIRED,
            LlmEnrichmentStatus.SETUP_REQUIRED,
        )

        statuses.forEach { status ->
            RecognitionPreference.entries.forEach { preference ->
                listOf(false, true).forEach { hasValues ->
                    val presentation = fromPipeline(
                        status = status,
                        preference = preference,
                        isProcessing = true,
                        hasValues = hasValues,
                        fallbackCapability = RecognitionCapability.AUTHORIZATION_REQUIRED,
                        fallbackRunState = RecognitionRunState.RETRIABLE_FAILURE,
                    )

                    assertEquals(
                        if (hasValues) RecognitionStatusText.CHECKING_MORE_DETAILS else RecognitionStatusText.CHECKING_LABEL,
                        presentation.status,
                    )
                    assertNull(presentation.offer)
                    assertNull(presentation.recoveryAction)
                }
            }
        }
    }

    @Test
    fun `success clears retained authorization setup and failure state`() {
        RecognitionCapability.entries.forEach { retainedCapability ->
            val presentation = fromPipeline(
                status = LlmEnrichmentStatus.SUCCEEDED,
                fallbackCapability = retainedCapability,
                fallbackRunState = RecognitionRunState.RETRIABLE_FAILURE,
            )

            assertEquals(RecognitionStatusText.DETAILS_NEED_REVIEW, presentation.status)
            assertNull(presentation.offer)
            assertNull(presentation.recoveryAction)
        }
        assertEquals(
            RecognitionCapability.READY,
            RecognitionUiStateMapper.capabilityFromPipeline(
                pipelineStatus = LlmEnrichmentStatus.SUCCEEDED,
                preference = RecognitionPreference.ENABLED,
                mindlayerSupported = true,
                mindlayerInstalled = true,
                fallback = RecognitionCapability.AUTHORIZATION_REQUIRED,
            ),
        )
    }

    @Test
    fun `not run preserves durable setup and running state without offering first scan opt-in`() {
        val initial = fromPipeline(LlmEnrichmentStatus.NOT_RUN, RecognitionPreference.UNDECIDED)
        val setup = fromPipeline(
            status = LlmEnrichmentStatus.NOT_RUN,
            fallbackCapability = RecognitionCapability.ASSET_SETUP_REQUIRED,
        )
        val partial = fromPipeline(
            status = LlmEnrichmentStatus.NOT_RUN,
            fallbackRunState = RecognitionRunState.PARTIAL,
        )

        assertNull(initial.offer)
        assertNull(initial.recoveryAction)
        assertEquals(RecognitionOffer.FINISH_SETUP, setup.offer)
        assertEquals(RecognitionStatusText.CHECKING_MORE_DETAILS, partial.status)
        assertNull(partial.offer)
        assertNull(partial.recoveryAction)
    }

    @Test
    fun `temporary unavailability without usable values offers retry rather than retake`() {
        val presentation = fromPipeline(LlmEnrichmentStatus.UNAVAILABLE, hasValues = false)

        assertNull(presentation.offer)
        assertNull(presentation.status)
        assertEquals(RecognitionRecoveryAction.RETRY, presentation.recoveryAction)
    }

    @Test
    fun `failed or timed out reading without usable values offers retake`() {
        listOf(LlmEnrichmentStatus.FAILED, LlmEnrichmentStatus.TIMED_OUT).forEach { status ->
            val presentation = fromPipeline(
                status = status,
                hasValues = false,
                fallbackCapability = RecognitionCapability.TEMPORARILY_UNAVAILABLE,
            )

            assertNull(presentation.offer)
            assertEquals(RecognitionRecoveryAction.RETAKE, presentation.recoveryAction)
        }
    }

    @Test
    fun `disabled preference suppresses all offers and recovery actions`() {
        RecognitionCapability.entries.forEach { capability ->
            listOf(false, true).forEach { hasValues ->
                val presentation = RecognitionUiStateMapper.map(
                    capability = capability,
                    runState = RecognitionRunState.RETRIABLE_FAILURE,
                    preference = RecognitionPreference.DISABLED,
                    hasValues = hasValues,
                    unresolvedCount = 1,
                )

                assertNull(presentation.offer)
                assertNull(presentation.recoveryAction)
            }
        }
    }

    private fun fromPipeline(
        status: LlmEnrichmentStatus,
        preference: RecognitionPreference = RecognitionPreference.ENABLED,
        isProcessing: Boolean = false,
        hasValues: Boolean = true,
        supported: Boolean = true,
        installed: Boolean = true,
        fallbackCapability: RecognitionCapability = RecognitionCapability.READY,
        fallbackRunState: RecognitionRunState = RecognitionRunState.IDLE,
    ) = RecognitionUiStateMapper.fromPipeline(
        pipelineStatus = status,
        isProcessing = isProcessing,
        hasValues = hasValues,
        unresolvedCount = 1,
        preference = preference,
        mindlayerSupported = supported,
        mindlayerInstalled = installed,
        fallbackCapability = fallbackCapability,
        fallbackRunState = fallbackRunState,
    )
}
