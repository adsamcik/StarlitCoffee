package com.adsamcik.starlitcoffee.notification

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkBusTest {

    @After
    fun tearDown() {
        // Clear shared state between tests — the bus is process-scoped.
        DeepLinkBus.consumeBrewLogDetail()
    }

    @Test
    fun `pendingBrewLogId starts null`() = runTest {
        DeepLinkBus.consumeBrewLogDetail()
        assertNull(DeepLinkBus.pendingBrewLogId.first())
    }

    @Test
    fun `postBrewLogDetail with positive id stores value`() = runTest {
        DeepLinkBus.postBrewLogDetail(42L)
        assertEquals(42L, DeepLinkBus.pendingBrewLogId.first())
    }

    @Test
    fun `postBrewLogDetail ignores zero and negative ids`() = runTest {
        DeepLinkBus.postBrewLogDetail(0L)
        assertNull(DeepLinkBus.pendingBrewLogId.first())

        DeepLinkBus.postBrewLogDetail(-7L)
        assertNull(DeepLinkBus.pendingBrewLogId.first())
    }

    @Test
    fun `consumeBrewLogDetail clears stored id`() = runTest {
        DeepLinkBus.postBrewLogDetail(99L)
        DeepLinkBus.consumeBrewLogDetail()
        assertNull(DeepLinkBus.pendingBrewLogId.first())
    }

    @Test
    fun `postBrewLogDetail overwrites a previous pending id`() = runTest {
        DeepLinkBus.postBrewLogDetail(1L)
        DeepLinkBus.postBrewLogDetail(2L)
        assertEquals(2L, DeepLinkBus.pendingBrewLogId.first())
    }
}
