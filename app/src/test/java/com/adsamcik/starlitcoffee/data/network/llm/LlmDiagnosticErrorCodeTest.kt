package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.shared.MindlayerErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmDiagnosticErrorCodeTest {

    @Test
    fun `known wire code is kept regardless of private message and code name`() {
        val failure = MindlayerException(
            message = "PRIVATE_PROMPT_OR_LABEL_CONTENT",
            code = MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT,
            codeName = "PRIVATE_ARBITRARY_CODE_NAME",
        )

        assertEquals(MindlayerErrorCode.INPUT_EXCEEDS_CONTEXT, knownLlmDiagnosticErrorCode(failure))
    }

    @Test
    fun `unmapped wire code cannot be made known by a forged name`() {
        val failure = MindlayerException(
            message = "PRIVATE_EXCEPTION_MESSAGE",
            code = Int.MAX_VALUE,
            codeName = "INPUT_EXCEEDS_CONTEXT",
        )

        assertNull(knownLlmDiagnosticErrorCode(failure))
    }

    @Test
    fun `unknown sentinel cannot be made known by a forged name`() {
        val failure = MindlayerException(
            message = "PRIVATE_EXCEPTION_MESSAGE",
            code = MindlayerErrorCode.UNKNOWN,
            codeName = "INPUT_EXCEEDS_CONTEXT",
        )

        assertNull(knownLlmDiagnosticErrorCode(failure))
    }

    @Test
    fun `plain exceptions and absent failures have no diagnostic code`() {
        assertNull(knownLlmDiagnosticErrorCode(IllegalStateException("INPUT_EXCEEDS_CONTEXT: PRIVATE_CONTENT")))
        assertNull(knownLlmDiagnosticErrorCode(null))
    }
}
