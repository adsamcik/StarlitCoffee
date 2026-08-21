package com.adsamcik.starlitcoffee.data.network.ocr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FallbackOcrServiceTest {
    @Test
    fun `primary result wins without invoking fallback`() = runTest {
        var fallbackCalls = 0

        val result = runWithFallback(
            primaryAvailable = { true },
            primaryCall = { "mindlayer" },
            fallbackCall = { fallbackCalls++; "bundled" },
        )

        assertEquals("mindlayer", result)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `bundled result is used when primary is unavailable or fails`() = runTest {
        assertEquals(
            "bundled",
            runWithFallback(
                primaryAvailable = { false },
                primaryCall = { error("must not run") },
                fallbackCall = { "bundled" },
            ),
        )
        assertEquals(
            "bundled",
            runWithFallback<String>(
                primaryAvailable = { true },
                primaryCall = { error("temporary failure") },
                fallbackCall = { "bundled" },
            ),
        )
    }

    @Test
    fun `cancellation is never converted into fallback`() = runTest {
        try {
            runWithFallback<String>(
                primaryAvailable = { true },
                primaryCall = { throw CancellationException("cancelled") },
                fallbackCall = { fail("fallback must not run after cancellation"); null },
            )
            fail("expected cancellation")
        } catch (_: CancellationException) {
            // Expected: structured cancellation propagates unchanged.
        }
    }
}
