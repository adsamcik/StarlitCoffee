package com.adsamcik.starlitcoffee.notification

import com.adsamcik.starlitcoffee.data.work.BagReviewContext
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
        DeepLinkBus.consumeBagAnalysisReady()
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

    @Test
    fun `bag analysis deep link preserves originating work id`() = runTest {
        DeepLinkBus.postBagAnalysisReady("9f8b49ea-4407-4129-a34f-472f8654f875")

        assertEquals(
            "9f8b49ea-4407-4129-a34f-472f8654f875",
            DeepLinkBus.pendingBagAnalysis.first()?.workId,
        )
    }

    @Test
    fun `bag analysis deep link preserves rescan review context`() = runTest {
        val reviewContext = BagReviewContext.rescan(42L)

        DeepLinkBus.postBagAnalysisReady(
            "9f8b49ea-4407-4129-a34f-472f8654f875",
            reviewContext,
        )

        assertEquals(reviewContext, DeepLinkBus.pendingBagAnalysis.first()?.reviewContext)
    }

    @Test
    fun `blank bag analysis work id is ignored`() = runTest {
        DeepLinkBus.postBagAnalysisReady(" ")

        assertNull(DeepLinkBus.pendingBagAnalysis.first())
    }

    @Test
    fun `consume bag analysis clears work id`() = runTest {
        DeepLinkBus.postBagAnalysisReady("9f8b49ea-4407-4129-a34f-472f8654f875")

        DeepLinkBus.consumeBagAnalysisReady()

        assertNull(DeepLinkBus.pendingBagAnalysis.first())
    }
}
