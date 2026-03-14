package com.adsamcik.starlitcoffee.data.model

/**
 * Simplified post-brew rating for the check-in card.
 * Maps to a star rating and optional taste feedback for the brew log.
 */
enum class QuickRating(
    val emoji: String,
    val label: String,
    val starRating: Float,
    val tasteFeedback: TasteFeedback?,
) {
    GREAT("🔥", "Great", 5f, TasteFeedback.BALANCED),
    GOOD("👍", "Good", 3.5f, TasteFeedback.BALANCED),
    NOT_GREAT("👎", "Not great", 2f, null),
}
