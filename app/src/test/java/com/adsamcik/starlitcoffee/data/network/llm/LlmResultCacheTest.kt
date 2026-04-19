package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.starlitcoffee.scan.model.FieldContext
import com.adsamcik.starlitcoffee.scan.model.FieldSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmResultCacheTest {

    @Test
    fun `LlmResultCache returns cached result for same key`() {
        val cache = LlmResultCache()
        val result = LlmExtractionResult.Success(fieldCandidates = emptyList(), tokensUsed = 10)
        cache.put("abc123", result)
        assertEquals(result, cache.get("abc123"))
    }

    @Test
    fun `LlmResultCache returns null for unknown key`() {
        val cache = LlmResultCache()
        assertNull(cache.get("missing"))
    }

    @Test
    fun `LlmCacheKey deterministic for identical inputs`() {
        val image = ByteArray(128) { it.toByte() }
        val k1 = LlmCacheKey.compute(image, setOf("name", "roaster"), "hello", emptyMap())
        val k2 = LlmCacheKey.compute(image, setOf("roaster", "name"), "hello", emptyMap())
        // Field order within the set is canonicalised.
        assertEquals(k1, k2)
    }

    @Test
    fun `LlmCacheKey differs when image differs`() {
        val a = ByteArray(16) { 1 }
        val b = ByteArray(16) { 2 }
        assertNotEquals(
            LlmCacheKey.compute(a, emptySet(), null, emptyMap()),
            LlmCacheKey.compute(b, emptySet(), null, emptyMap()),
        )
    }

    @Test
    fun `LlmCacheKey differs when OCR text differs`() {
        val img = ByteArray(8)
        assertNotEquals(
            LlmCacheKey.compute(img, emptySet(), "ocr A", emptyMap()),
            LlmCacheKey.compute(img, emptySet(), "ocr B", emptyMap()),
        )
    }

    @Test
    fun `LlmCacheKey differs when existing fields differ`() {
        val img = ByteArray(8)
        val ef1 = mapOf("name" to FieldContext("Kenya", FieldSource.OCR))
        val ef2 = mapOf("name" to FieldContext("Ethiopia", FieldSource.OCR))
        assertNotEquals(
            LlmCacheKey.compute(img, emptySet(), null, ef1),
            LlmCacheKey.compute(img, emptySet(), null, ef2),
        )
    }

    @Test
    fun `LlmCacheKey output is 64-char hex`() {
        val img = ByteArray(8)
        val key = LlmCacheKey.compute(img, emptySet(), null, emptyMap())
        assertEquals(64, key.length)
        assertEquals(key, key.lowercase())
    }
}
