package com.adsamcik.starlitcoffee.data.model

/**
 * Controls how a brew phase transitions to the next phase.
 */
enum class PhaseMode {
    /**
     * Countdown timer. Counts down from [com.adsamcik.starlitcoffee.viewmodel.BrewPhase.durationSeconds].
     * Does NOT auto-advance at zero — counts negative (elastic drift) until user taps Done.
     */
    TIMED,

    /**
     * Event-gated. Counts UP (stopwatch style). Waits for user tap to advance.
     * [com.adsamcik.starlitcoffee.viewmodel.BrewPhase.durationSeconds] is a suggested target, not enforced.
     */
    EVENT_GATED,
}
