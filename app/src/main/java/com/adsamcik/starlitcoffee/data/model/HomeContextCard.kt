package com.adsamcik.starlitcoffee.data.model

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.util.FreshnessInsight
import com.adsamcik.starlitcoffee.util.FreshnessPhase

/**
 * Represents a contextual card shown below "Start Brewing" on the home screen.
 * One card at a time, selected by priority.
 */
sealed class HomeContextCard {

    /** Age-aware brewing wisdom for the selected bag. */
    data class BagAgeWisdom(
        val bagName: String,
        val daysSinceRoast: Int,
        val phase: FreshnessPhase,
        val headline: String,
        val grindAdvice: String,
        val brewTip: String,
    ) : HomeContextCard()

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
         * Priority: selected-bag wisdom > old-bag freshness > coaching > summary.
         */
        fun resolve(
            bags: List<com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity>,
            brewLogs: List<BrewLogEntity>,
            selectedBagId: Long?,
        ): HomeContextCard? {
            // 1. Age wisdom for selected bag (if roast date known)
            val wisdomCard = resolveBagAgeWisdom(bags, selectedBagId)
            if (wisdomCard != null) return wisdomCard

            // 2. Freshness alert for aging open bags
            val freshnessCard = resolveFreshnessAlert(bags, brewLogs)
            if (freshnessCard != null) return freshnessCard

            // 3. Coaching tip from recent negative feedback
            val coachingCard = resolveCoachingTip(brewLogs)
            if (coachingCard != null) return coachingCard

            // 4. Last rated brew summary
            return resolveLastBrewSummary(brewLogs, bags)
        }

        private fun resolveBagAgeWisdom(
            bags: List<com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity>,
            selectedBagId: Long?,
        ): BagAgeWisdom? {
            if (selectedBagId == null) return null
            val bag = bags.find { it.id == selectedBagId } ?: return null
            if (bag.roastDate == null) return null

            val insight = com.adsamcik.starlitcoffee.util.CoffeeBagInsights
                .freshnessInsight(bag.roastDate)
            // Only show for non-peak phases — peak doesn't need advice
            if (insight.phase == FreshnessPhase.PEAK || insight.phase == FreshnessPhase.UNKNOWN) {
                return null
            }

            val days = insight.daysSinceRoast ?: return null
            val bagName = bag.name + (bag.roaster?.let { " · $it" } ?: "")

            val (headline, grindAdvice, brewTip) = when (insight.phase) {
                FreshnessPhase.DEGASSING -> Triple(
                    "Day $days — beans are still degassing",
                    "Expect an active bloom with lots of CO₂",
                    when {
                        days < 3 -> "Consider waiting a few more days for best results"
                        else -> "Brew-ready soon — bloom may be vigorous, extend steep 10-15s"
                    },
                )
                FreshnessPhase.MELLOWING -> Triple(
                    "Day $days — past peak, still good",
                    "Try grinding 1-2 clicks finer to compensate",
                    if (days > 35) "Dose up slightly (1-2g) for more body"
                    else "Lean on grind and dose to keep it lively",
                )
                FreshnessPhase.VINTAGE -> Triple(
                    "Day $days — well past prime",
                    "Grind noticeably finer, expect less sweetness",
                    "Use this bag up soon — freeze any extras",
                )
                else -> return null
            }

            return BagAgeWisdom(
                bagName = bagName,
                daysSinceRoast = days,
                phase = insight.phase,
                headline = headline,
                grindAdvice = grindAdvice,
                brewTip = brewTip,
            )
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
