package com.adsamcik.starlitcoffee.domain.brewing.session

/**
 * Monotonic time is used only while the current process owns a running session.
 * It must not be serialized as an epoch timestamp or compared after a reboot.
 */
fun interface MonotonicClock {
    fun nowMillis(): Long
}

/** Persisted timestamps use this wall clock so a session can be restored later. */
fun interface WallClock {
    fun nowMillis(): Long
}

/** A single reducer input captured from both clock domains at the same boundary. */
data class SessionClockReading(
    val monotonicMillis: Long,
    val wallClockMillis: Long,
) {
    init {
        require(monotonicMillis >= 0L) { "Monotonic time cannot be negative" }
    }
}

/**
 * Small adapter that injects clocks at the edge while leaving [SessionReducer]
 * completely deterministic and side-effect free.
 */
class ClockedSessionEngine(
    private val monotonicClock: MonotonicClock,
    private val wallClock: WallClock,
) {
    fun now(): SessionClockReading = SessionClockReading(
        monotonicMillis = monotonicClock.nowMillis(),
        wallClockMillis = wallClock.nowMillis(),
    )

    fun reduce(
        state: SessionRuntimeState,
        event: SessionEvent,
    ): SessionTransition = SessionReducer.reduce(state, event, now())
}
