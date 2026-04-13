package com.adsamcik.starlitcoffee.data.network.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmResultCacheTest {

    @Test
    fun `LlmResultCache returns cached result for same hash`() {
        val cache = LlmResultCache()
        val result = LlmExtractionResult.Success(fieldCandidates = emptyList(), tokensUsed = 10)
        cache.put(12345, result)
        assertEquals(result, cache.get(12345))
    }

    @Test
    fun `LlmResultCache returns null for unknown hash`() {
        val cache = LlmResultCache()
        assertNull(cache.get(99999))
    }
}
