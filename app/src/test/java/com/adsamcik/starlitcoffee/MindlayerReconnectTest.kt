package com.adsamcik.starlitcoffee

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MindlayerReconnectTest {
    @Test
    fun `failed connection never reports provider availability as success`() = runTest {
        var availabilityChecked = false

        val connected = awaitSuccessfulMindlayerReconnect(
            awaitConnection = { error("connection failed") },
            checkAvailability = {
                availabilityChecked = true
                true
            },
        )

        assertFalse(connected)
        assertFalse(availabilityChecked)
    }

    @Test
    fun `successful connection returns actual provider availability`() = runTest {
        assertTrue(
            awaitSuccessfulMindlayerReconnect(
                awaitConnection = {},
                checkAvailability = { true },
            ),
        )
        assertFalse(
            awaitSuccessfulMindlayerReconnect(
                awaitConnection = {},
                checkAvailability = { false },
            ),
        )
    }

    @Test(expected = CancellationException::class)
    fun `connection cancellation is rethrown`() = runTest {
        awaitSuccessfulMindlayerReconnect(
            awaitConnection = { throw CancellationException("cancelled") },
            checkAvailability = { true },
        )
    }
}
