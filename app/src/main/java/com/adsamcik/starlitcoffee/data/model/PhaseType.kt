package com.adsamcik.starlitcoffee.data.model

/**
 * Typed identity for brew phases. Replaces string-based phase name matching
 * (e.g., `name == "Bloom"`, `startsWith("Pour")`) with compiler-checked enums.
 */
enum class PhaseType {
    BLOOM,
    POUR,
    DRAIN_AND_REFILL,
    DRAWDOWN,
}
