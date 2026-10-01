package com.adsamcik.starlitcoffee.domain.scandiagnostics

import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

enum class ScanDiagnosticMode { ADD_NEW, RESCAN, UNKNOWN }

/** Immutable correlation for concurrent scan passes; never contains paths or label data. */
class ScanDiagnosticContext(
    val sessionKey: Long? = null,
    val generationKey: Long? = null,
    val workKey: Long? = null,
    val photoCount: Int = 0,
    val mode: ScanDiagnosticMode = ScanDiagnosticMode.UNKNOWN,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ScanDiagnosticContext>
}

/** Only app-generated UUIDs qualify; arbitrary identifiers are never made public. */
fun scanCorrelationKey(identifier: String?): Long? {
    if (identifier?.length != UUID_TEXT_LENGTH) return null
    val uuid = runCatching { UUID.fromString(identifier) }.getOrNull() ?: return null
    if (!uuid.toString().equals(identifier, ignoreCase = true)) return null
    return uuid.mostSignificantBits xor uuid.leastSignificantBits
}

private const val UUID_TEXT_LENGTH = 36
