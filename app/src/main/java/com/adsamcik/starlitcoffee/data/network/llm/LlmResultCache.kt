package com.adsamcik.starlitcoffee.data.network.llm

/**
 * In-memory LRU cache keyed by a SHA-256 content hash (see [LlmCacheKey]).
 * Prevents duplicate LLM calls for the same request (same image +
 * fields-needed + OCR + existing-fields + schema version).
 *
 * Keys are hex-encoded 256-bit digests rather than `ByteArray.contentHashCode()`
 * (a 32-bit Int) — 32-bit keys collide at ~65k entries, which was a real
 * correctness risk for multi-pass live scans that hash the same bag many
 * times with slightly different contexts.
 *
 * ## Thread safety
 *
 * All public methods are `@Synchronized`. The backing [LinkedHashMap] is
 * built with `accessOrder = true` so `get()` itself reorders entries — this
 * is what makes the LRU eviction policy work but also makes concurrent
 * unsynchronised access genuinely unsafe (not just stale-read territory:
 * `LinkedHashMap` can corrupt its internal structure under concurrent
 * mutations).
 *
 * The need surfaced when `LlmEscalationCoordinator` started serializing
 * **provider** calls through a [kotlinx.coroutines.sync.Mutex] but kept
 * cache reads/writes outside that lock. Two simultaneous escalations could
 * both read-miss → wait on the mutex → run extraction sequentially → call
 * [put] from different threads concurrently as their respective
 * `withContext(Dispatchers.IO)` blocks return.
 */
class LlmResultCache(private val maxSize: Int = 20) {
    private val cache = LinkedHashMap<String, CacheEntry>(maxSize, 0.75f, true)
    private val expiryMs = 10 * 60 * 1000L // 10 minutes

    data class CacheEntry(
        val result: LlmExtractionResult.Success,
        val timestamp: Long = System.currentTimeMillis(),
    )

    @Synchronized
    fun get(key: String): LlmExtractionResult.Success? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > expiryMs) {
            cache.remove(key)
            return null
        }
        return entry.result
    }

    @Synchronized
    fun put(key: String, result: LlmExtractionResult.Success) {
        if (cache.size >= maxSize) {
            val oldest = cache.keys.first()
            cache.remove(oldest)
        }
        cache[key] = CacheEntry(result)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
