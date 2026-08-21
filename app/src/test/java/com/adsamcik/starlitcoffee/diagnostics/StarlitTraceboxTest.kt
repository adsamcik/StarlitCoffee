package com.adsamcik.starlitcoffee.diagnostics

import dev.tracebox.api.CaptureKind
import dev.tracebox.api.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarlitTraceboxTest {
    @Test
    fun `recognizes only the dedicated Tracebox handler process`() {
        assertTrue(isTraceboxHandlerProcessName("com.adsamcik.starlitcoffee:tracebox_handler"))
        assertTrue(isTraceboxHandlerProcessName("com.adsamcik.starlitcoffee.debug:tracebox_handler"))
        assertFalse(isTraceboxHandlerProcessName("com.adsamcik.starlitcoffee"))
        assertFalse(isTraceboxHandlerProcessName("com.adsamcik.starlitcoffee:worker"))
        assertFalse(isTraceboxHandlerProcessName(null))
    }

    @Test
    fun `default policy enables the complete local capture profile without Logcat mirroring`() {
        val policy = StarlitTracebox.defaultPolicy

        assertTrue(policy.enabled)
        assertEquals(LogLevel.INFO, policy.minimumLogLevel)
        assertFalse(policy.mirrorToLogcat)
        assertEquals(
            CaptureKind.entries.toSet() - CaptureKind.RUST_PANIC,
            StarlitTracebox.supportedCaptureKinds,
        )
        assertEquals(StarlitTracebox.supportedCaptureKinds, policy.captures)
    }

    @Test
    fun `native capture is offered only to supported 64 bit processes`() {
        assertTrue(isTraceboxNativeCaptureSupported(true, arrayOf("arm64-v8a", "armeabi-v7a")))
        assertTrue(isTraceboxNativeCaptureSupported(true, arrayOf("x86_64", "x86")))
        assertFalse(isTraceboxNativeCaptureSupported(false, arrayOf("arm64-v8a")))
        assertFalse(isTraceboxNativeCaptureSupported(true, arrayOf("armeabi-v7a", "x86")))
        assertFalse(isTraceboxNativeCaptureSupported(true, emptyArray()))
    }
}
