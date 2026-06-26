package com.adsamcik.starlitcoffee.scan.observability

import com.adsamcik.mindlayer.sdk.MindlayerException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Mindlayer service reports `ENGINE_INITIALIZING` as a retryable, transient
 * signal (the on-device engine is still warming up), not a terminal failure.
 * The diagnostic card must surface it as "Initializing", never a red "Error".
 */
class MindlayerConnectionStatusTest {

    // --- engine initializing is transient, not an error ---

    @Test
    fun `wire message engine_initializing is treated as initializing`() {
        assertTrue(isEngineInitializing(RuntimeException("engine_initializing")))
    }

    @Test
    fun `engine initializing match is case insensitive`() {
        assertTrue(isEngineInitializing(RuntimeException("ENGINE_INITIALIZING")))
    }

    @Test
    fun `typed MindlayerException code name is treated as initializing`() {
        // No wire string in the human message — detection relies on the code.
        val typed = MindlayerException(message = "warming up", codeName = "ENGINE_INITIALIZING")
        assertTrue(isEngineInitializing(typed))
    }

    @Test
    fun `unrelated failure is not treated as initializing`() {
        assertFalse(isEngineInitializing(RuntimeException("connection refused")))
    }

    // --- result classification ---

    @Test
    fun `initializing throwable maps to INITIALIZING status with no error banner`() {
        val result = errorResultFor(RuntimeException("engine_initializing"))
        assertEquals(ConnectionStatus.INITIALIZING, result.status)
        assertNull(result.errorMessage)
    }

    @Test
    fun `genuine failure maps to ERROR status and surfaces the message`() {
        val result = errorResultFor(RuntimeException("boom"))
        assertEquals(ConnectionStatus.ERROR, result.status)
        assertEquals("boom", result.errorMessage)
    }
}
