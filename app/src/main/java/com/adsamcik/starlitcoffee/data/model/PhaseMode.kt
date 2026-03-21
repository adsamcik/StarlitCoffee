package com.adsamcik.starlitcoffee.data.model

/**
 * Controls how a brew phase transitions to the next phase.
 */
enum class PhaseMode {
    /**
     * Countdown timer requiring user action. Counts down from
     * [com.adsamcik.starlitcoffee.viewmodel.BrewPhase.durationSeconds], then counts
     * negative (elastic drift) until user taps Done.
     * Used for pour phases where the user must physically pour water.
     */
    TIMED,

    /**
     * Countdown timer that auto-advances. Counts down from
     * [com.adsamcik.starlitcoffee.viewmodel.BrewPhase.durationSeconds] and automatically
     * transitions to the next phase at zero. Used for passive wait phases
     * (e.g. Saturate, Steep) where no user action is needed.
     */
    AUTO_TIMED,

    /**
     * Event-gated. Counts UP (stopwatch style). Waits for user tap to advance.
     * [com.adsamcik.starlitcoffee.viewmodel.BrewPhase.durationSeconds] is a suggested target, not enforced.
     */
    EVENT_GATED,
}
