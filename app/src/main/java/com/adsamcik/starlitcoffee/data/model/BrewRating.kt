package com.adsamcik.starlitcoffee.data.model

import kotlin.math.roundToInt

/**
 * Single canonical rating vocabulary for a brew, used everywhere (post-brew
 * check-in, rating sheet, log detail, notification, share card).
 *
 * The scale is intentionally **4 tiers, positive-skewed**: you rate coffee you
 * brewed yourself, so real outcomes cluster positive. There is one negative
 * anchor ([BAD], which branches into the [TasteIssue] diagnostic follow-up),
 * one neutral ([MEH]), and two positive tiers ([GOOD], [AWESOME]) so the useful
 * distinction — "repeat it" vs "dial this in as a keeper" — has room.
 *
 * Persisted in [com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity.rating]
 * as [storedValue] (1f..4f). Legacy 0-5 continuous values are normalized to
 * these tiers by DB migration 15 -> 16 and, defensively, by [fromStoredValue].
 */
enum class BrewRating(
    val score: Int,
    val emoji: String,
    val tasteFeedback: TasteFeedback?,
    val triggersIssueFollowUp: Boolean,
) {
    BAD(1, "☹️", null, triggersIssueFollowUp = true),
    MEH(2, "😐", null, triggersIssueFollowUp = false),
    GOOD(3, "😀", TasteFeedback.BALANCED, triggersIssueFollowUp = false),
    AWESOME(4, "😋", TasteFeedback.BALANCED, triggersIssueFollowUp = false),
    ;

    /** Value written to the `rating` column. */
    val storedValue: Float get() = score.toFloat()

    companion object {
        /** Tiers ordered worst -> best, for building selectors and distributions. */
        val ordered: List<BrewRating> = listOf(BAD, MEH, GOOD, AWESOME)

        /**
         * Map a stored rating value back to a tier. Returns null for unrated
         * (`null` or `<= 0`). Rounds to the nearest integer tier and clamps into
         * 1..4 so any stray legacy value (e.g. an un-migrated 4.5) still resolves
         * sensibly instead of falling through.
         */
        fun fromStoredValue(value: Float?): BrewRating? {
            if (value == null || value <= 0f) return null
            return when (value.roundToInt().coerceIn(1, 4)) {
                4 -> AWESOME
                3 -> GOOD
                2 -> MEH
                else -> BAD
            }
        }
    }
}
