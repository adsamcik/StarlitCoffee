package com.adsamcik.starlitcoffee.scan

import kotlinx.coroutines.withTimeoutOrNull

/** One absolute budget shared by every stage of a bag scan. */
class ScanDeadline private constructor(
    val expiresAtEpochMs: Long,
    private val nowEpochMs: () -> Long,
) {
    val remainingMillis: Long
        get() = (expiresAtEpochMs - nowEpochMs()).coerceAtLeast(0L)

    suspend fun <T : Any> run(block: suspend () -> T): T {
        val remaining = remainingMillis
        if (remaining == 0L) throw ScanDeadlineExceededException()
        return withTimeoutOrNull(remaining) { block() }
            ?: throw ScanDeadlineExceededException()
    }

    companion object {
        const val DEFAULT_BUDGET_MS: Long = 5L * 60L * 1_000L

        fun startingNow(
            budgetMillis: Long = DEFAULT_BUDGET_MS,
            nowEpochMs: () -> Long = System::currentTimeMillis,
        ): ScanDeadline = fromStartedAt(nowEpochMs(), budgetMillis, nowEpochMs)

        fun fromStartedAt(
            startedAtEpochMs: Long,
            budgetMillis: Long = DEFAULT_BUDGET_MS,
            nowEpochMs: () -> Long = System::currentTimeMillis,
        ): ScanDeadline {
            require(budgetMillis > 0L) { "Scan budget must be positive" }
            val safeStart = startedAtEpochMs.takeIf { it > 0L } ?: nowEpochMs()
            return ScanDeadline(
                expiresAtEpochMs = safeStart + budgetMillis,
                nowEpochMs = nowEpochMs,
            )
        }
    }
}

class ScanDeadlineExceededException : Exception("Bag scan reached its five-minute deadline")
