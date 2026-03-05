package com.adsamcik.starlitcoffee.data.model

/**
 * Typed identity for brew phases. Replaces string-based phase name matching
 * (e.g., `name == "Bloom"`, `startsWith("Pour")`) with compiler-checked enums.
 */
enum class PhaseType(
    val displayName: String,
    val emoji: String,
) {
    BLOOM(
        displayName = "Bloom",
        emoji = "🌱",
    ),
    POUR(
        displayName = "Pour",
        emoji = "💧",
    ),
    DRAIN_AND_REFILL(
        displayName = "Drain & Refill",
        emoji = "🔄",
    ),
    DRAWDOWN(
        displayName = "Drawdown",
        emoji = "⏬",
    ),
}
