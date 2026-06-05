package com.adsamcik.starlitcoffee.data.network.llm

import com.adsamcik.starlitcoffee.scan.model.FieldContext
import java.security.MessageDigest

/**
 * Deterministic cache key for LLM extraction requests.
 *
 * The key must capture everything that influences the LLM response:
 * - the image bytes (primary driver)
 * - the set of fields we asked for (`fieldsNeeded`)
 * - the OCR text (provides grounding context)
 * - the existing fields (change the prompt)
 * - the prompt schema version (so a prompt change busts old cache entries)
 *
 * We use SHA-256 — not `ByteArray.contentHashCode()` which is a 32-bit Int
 * and will collide frequently for large byte arrays (birthday at ~65k entries).
 * A 256-bit hex string is effectively collision-free for our cache size.
 */
internal object LlmCacheKey {

    /**
     * Bump when the system prompt schema or parser semantics change. Two
     * requests with the same image but different [SCHEMA_VERSION] will
     * compute to different cache keys.
     */
    const val SCHEMA_VERSION = "v2-14f-structured-output"

    fun compute(
        imageBytes: ByteArray,
        fieldsNeeded: Set<String>,
        rawOcrText: String?,
        existingFields: Map<String, FieldContext>,
        mode: String = "text",
    ): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(SCHEMA_VERSION.toByteArray(Charsets.UTF_8))
        md.update(0x1F)
        // Modality/prompt mode (text vs vision) — the same image+fields produce
        // different model output for a vision pass, so they must not share a
        // cache entry with the text pass.
        md.update(mode.toByteArray(Charsets.UTF_8))
        md.update(0x1F)
        md.update(imageBytes)
        md.update(0x1F)
        // Canonicalise field ordering so set/map insertion order doesn't
        // produce different keys for equivalent requests.
        md.update(fieldsNeeded.sorted().joinToString(",").toByteArray(Charsets.UTF_8))
        md.update(0x1F)
        md.update((rawOcrText ?: "").toByteArray(Charsets.UTF_8))
        md.update(0x1F)
        val sortedExisting = existingFields.entries
            .sortedBy { it.key }
            .joinToString("|") { (k, v) -> "$k=${v.source}:${v.value}" }
        md.update(sortedExisting.toByteArray(Charsets.UTF_8))

        val digest = md.digest()
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX[(b.toInt() ushr 4) and 0x0F])
            sb.append(HEX[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
