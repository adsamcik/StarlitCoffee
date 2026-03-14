package com.adsamcik.starlitcoffee.data.model

/**
 * Follow-up when user rates a brew as "Not great".
 * Maps to TasteFeedback for storage + a human-readable suggestion.
 */
enum class TasteIssue(
    val emoji: String,
    val label: String,
    val tasteFeedback: TasteFeedback,
    val suggestion: String,
) {
    TOO_BITTER("😬", "Too bitter", TasteFeedback.TOO_BITTER, "Try grinding coarser or shorter brew time"),
    TOO_SOUR("🍋", "Too sour", TasteFeedback.TOO_SOUR, "Try grinding finer or longer brew time"),
    TOO_WEAK("💧", "Too weak", TasteFeedback.TOO_SOUR, "Try a stronger ratio or more coffee"),
    TOO_STRONG("💪", "Too strong", TasteFeedback.TOO_BITTER, "Try a lighter ratio or less coffee"),
}
