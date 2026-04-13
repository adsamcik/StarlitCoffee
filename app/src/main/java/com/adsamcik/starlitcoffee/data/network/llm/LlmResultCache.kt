package com.adsamcik.starlitcoffee.data.network.llm

/**
 * In-memory LRU cache keyed by image content hash.
 * Prevents duplicate LLM API calls for the same bag label.
 */
class LlmResultCache(private val maxSize: Int = 20) {
    private val cache = LinkedHashMap<Int, CacheEntry>(maxSize, 0.75f, true)
    private val expiryMs = 10 * 60 * 1000L // 10 minutes

    data class CacheEntry(
        val result: LlmExtractionResult.Success,
        val timestamp: Long = System.currentTimeMillis(),
    )

    fun get(imageHash: Int): LlmExtractionResult.Success? {
        val entry = cache[imageHash] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > expiryMs) {
            cache.remove(imageHash)
            return null
        }
        return entry.result
    }

    fun put(imageHash: Int, result: LlmExtractionResult.Success) {
        if (cache.size >= maxSize) {
            val oldest = cache.keys.first()
            cache.remove(oldest)
        }
        cache[imageHash] = CacheEntry(result)
    }

    fun clear() {
        cache.clear()
    }
}
