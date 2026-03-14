package com.adsamcik.starlitcoffee.data.model

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.util.FreshnessInsight

/**
 * Represents a contextual card shown below "Start Brewing" on the home screen.
 * One card at a time, selected by priority.
 */
sealed class HomeContextCard {

    /** Open bag is getting old — nudge to use it. */
    data class FreshnessAlert(
        val bagName: String,
        val insight: FreshnessInsight,
        val brewsRemaining: Int?,
    ) : HomeContextCard()

    /** Recent negative feedback pattern — one actionable tip. */
    data class CoachingTip(
        val tip: String,
        val detail: String,
        val emoji: String,
    ) : HomeContextCard()

    /** Last rated brew summary — reinforcement or reminder. */
    data class LastBrewSummary(
        val brew: BrewLogEntity,
        val bagName: String?,
        val ratingEmoji: String,
    ) : HomeContextCard()

    companion object {
        /**
         * Resolve the highest-priority context card from available data.
         * Priority: freshness alert > coaching tip > last brew summary.
         */
        fun resolve(
            bags: List<com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity>,
            brewLogs: List<BrewLogEntity>,
            selectedBagId: Long?,
        ): HomeContextCard? {
            // 1. Freshness alert for open bags past peak
            val freshnessCard = resolveFreshnessAlert(bags, brewLogs)
            if (freshnessCard != null) return freshnessCard

            // 2. Coaching tip from recent negative feedback
            val coachingCard = resolveCoachingTip(brewLogs)
            if (coachingCard != null) return coachingCard

            // 3. Last rated brew summary
            return resolveLastBrewSummary(brewLogs, bags)
        }

        private fun resolveFreshnessAlert(
            bags: List<com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity>,
            brewLogs: List<BrewLogEntity>,
        ): FreshnessAlert? {
            val openBags = bags.filter { it.status == "OPEN" }
            if (openBags.isEmpty()) return null

            val now = System.currentTimeMillis()
            // Find the oldest open bag that's past peak or mellowing
            val alertBag = openBags
                .filter { it.openedDate != null }
                .sortedBy { it.openedDate }
                .firstOrNull { bag ->
                    val daysSinceOpened = ((now - (bag.openedDate ?: now)) / 86_400_000L).toInt()
                    daysSinceOpened >= 14
                }
                ?: return null

            val insight = com.adsamcik.starlitcoffee.util.CoffeeBagInsights
                .freshnessInsight(alertBag.roastDate)

            val brewsRemaining = if (alertBag.weightG != null && alertBag.weightG > 0f) {
                val avgDose = brewLogs
                    .filter { it.coffeeBagId == alertBag.id }
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.doseG }
                    ?.average()
                    ?.toFloat()
                    ?: 20f
                (alertBag.weightG / avgDose).toInt()
            } else null

            val bagName = alertBag.name +
                (alertBag.roaster?.let { " · $it" } ?: "")

            return FreshnessAlert(
                bagName = bagName,
                insight = insight,
                brewsRemaining = brewsRemaining,
            )
        }

        private fun resolveCoachingTip(
            brewLogs: List<BrewLogEntity>,
        ): CoachingTip? {
            // Look at last 5 rated brews for patterns
            val recentRated = brewLogs
                .filter { it.rating != null && it.tasteFeedback != null }
                .take(5)

            if (recentRated.isEmpty()) return null

            val feedbackCounts = recentRated
                .groupingBy { it.tasteFeedback }
                .eachCount()

            // Pattern: same negative feedback ≥2 times in last 5 brews
            val dominantIssue = feedbackCounts.entries
                .filter { it.key != TasteFeedback.BALANCED.name && it.value >= 2 }
                .maxByOrNull { it.value }

            // Or: most recent brew was negative (single occurrence is still worth a tip)
            val recentIssue = dominantIssue?.key
                ?: recentRated.firstOrNull()
                    ?.takeIf { it.tasteFeedback != TasteFeedback.BALANCED.name }
                    ?.tasteFeedback
                ?: return null

            val isPattern = dominantIssue != null

            return when (recentIssue) {
                TasteFeedback.TOO_BITTER.name -> CoachingTip(
                    tip = if (isPattern) "Your last few cups were bitter" else "Last cup was bitter",
                    detail = "Try grinding a bit coarser or shortening brew time",
                    emoji = "💡",
                )
                TasteFeedback.TOO_SOUR.name -> CoachingTip(
                    tip = if (isPattern) "Your last few cups were sour" else "Last cup was sour",
                    detail = "Try grinding finer or extending brew time",
                    emoji = "💡",
                )
                TasteFeedback.ASTRINGENT.name -> CoachingTip(
                    tip = "Noticing a dry mouthfeel?",
                    detail = "Try a higher dose (20g+) for better bed depth",
                    emoji = "💡",
                )
                TasteFeedback.CLOGGED.name -> CoachingTip(
                    tip = "Brew keeps stalling?",
                    detail = "Grind much coarser and reduce agitation",
                    emoji = "💡",
                )
                else -> null
            }
        }

        private fun resolveLastBrewSummary(
            brewLogs: List<BrewLogEntity>,
            bags: List<com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity>,
        ): LastBrewSummary? {
            val lastRated = brewLogs
                .filter { it.rating != null && it.rating > 0f }
                .maxByOrNull { it.createdAt }
                ?: return null

            val bagName = lastRated.coffeeBagId?.let { bagId ->
                bags.find { it.id == bagId }?.let { bag ->
                    bag.name + (bag.roaster?.let { " · $it" } ?: "")
                }
            }

            val emoji = when {
                lastRated.rating!! >= 4.5f -> "🔥"
                lastRated.rating >= 3.0f -> "👍"
                else -> "👎"
            }

            return LastBrewSummary(
                brew = lastRated,
                bagName = bagName,
                ratingEmoji = emoji,
            )
        }
    }
}
