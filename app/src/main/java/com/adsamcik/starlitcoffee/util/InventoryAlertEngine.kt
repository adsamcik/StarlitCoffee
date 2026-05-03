package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.model.InventoryAlert
import com.adsamcik.starlitcoffee.data.model.InventoryAlertType

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val PEAK_START_DAYS = 7
private const val PEAK_END_DAYS = 21
private const val MELLOWING_END_DAYS = 42
private const val EXPIRY_WARNING_DAYS = 14
private const val STALE_OPEN_DAYS = 30
private const val AGING_SEALED_DAYS = 60
private const val INFERRED_SHELF_LIFE_DAYS = 365
private const val FOCUS_THRESHOLD = 2
private const val DECAF_COVERAGE_WINDOW_DAYS = 30
private const val DECAF_COVERAGE_THRESHOLD = 3
private val AVAILABLE_BAG_STATUSES = setOf("SEALED", "OPEN", "FROZEN")

/**
 * Inventory-level brew alerts (peak window, expiry, decaf coverage, etc.).
 * The fan-out of small helpers reflects the rule set, not poor decomposition.
 */
@Suppress("TooManyFunctions")
object InventoryAlertEngine {

    fun buildAlerts(
        bags: List<CoffeeBagEntity>,
        brewLogs: List<com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
    ): List<InventoryAlert> = buildList {
        addAll(freshnessCountdownAlerts(bags, nowMillis))
        addAll(expiryAlerts(bags, nowMillis))
        addAll(stalenessAlerts(bags, nowMillis))
        addAll(agingSealedAlerts(bags, nowMillis))
        addAll(focusAlerts(bags, nowMillis))
        addAll(decafCoverageAlerts(bags, brewLogs, nowMillis))
    }

    // --- 4a: Freshness countdown ---

    internal fun freshnessCountdownAlerts(
        bags: List<CoffeeBagEntity>,
        nowMillis: Long,
    ): List<InventoryAlert> {
        return bags
            .filter { it.status == "OPEN" && it.roastDate != null }
            .mapNotNull { bag ->
                val daysSinceRoast = ((nowMillis - bag.roastDate!!) / MILLIS_PER_DAY).toInt()
                    .coerceAtLeast(0)
                val name = bagDisplayName(bag)
                freshnessTransitionAlert(name, bag.id, daysSinceRoast)
            }
    }

    private fun freshnessTransitionAlert(
        name: String,
        bagId: Long,
        daysSinceRoast: Int,
    ): InventoryAlert? {
        val daysUntilPeak = PEAK_START_DAYS - daysSinceRoast
        val daysUntilMellowing = PEAK_END_DAYS - daysSinceRoast
        val daysUntilVintage = MELLOWING_END_DAYS - daysSinceRoast

        return when {
            // Approaching peak (within 3 days)
            daysUntilPeak in 1..3 -> InventoryAlert(
                type = InventoryAlertType.FRESHNESS,
                bagName = name,
                message = "$name enters peak in $daysUntilPeak day${plural(daysUntilPeak)}",
                bagId = bagId,
            )
            // Approaching mellowing (within 3 days of leaving peak)
            daysUntilMellowing in 0..3 && daysSinceRoast >= PEAK_START_DAYS -> InventoryAlert(
                type = InventoryAlertType.FRESHNESS,
                bagName = name,
                message = "$name hits mellowing in $daysUntilMellowing day${plural(daysUntilMellowing)} — brew it now",
                bagId = bagId,
            )
            // Approaching vintage (within 3 days of leaving mellowing)
            daysUntilVintage in 0..3 && daysSinceRoast >= PEAK_END_DAYS -> InventoryAlert(
                type = InventoryAlertType.FRESHNESS,
                bagName = name,
                message = "$name hits vintage in $daysUntilVintage day${plural(daysUntilVintage)} — use it soon",
                bagId = bagId,
            )
            else -> null
        }
    }

    // --- 4b: Expiry & staleness alerts ---

    internal fun expiryAlerts(
        bags: List<CoffeeBagEntity>,
        nowMillis: Long,
    ): List<InventoryAlert> {
        val warningThreshold = nowMillis + EXPIRY_WARNING_DAYS * MILLIS_PER_DAY
        return bags
            .filter { it.status != "FINISHED" }
            .mapNotNull { bag ->
                val effectiveExpiry = bag.expiryDate
                    ?: inferredExpiryDate(bag)
                    ?: return@mapNotNull null
                if (effectiveExpiry > warningThreshold) return@mapNotNull null

                val daysLeft = ((effectiveExpiry - nowMillis) / MILLIS_PER_DAY).toInt()
                val name = bagDisplayName(bag)
                val inferred = bag.expiryDate == null

                when {
                    daysLeft < 0 -> InventoryAlert(
                        type = InventoryAlertType.EXPIRY,
                        bagName = name,
                        message = "Expired: $name${if (inferred) " (estimated)" else ""}",
                        bagId = bag.id,
                    )
                    else -> InventoryAlert(
                        type = InventoryAlertType.EXPIRY,
                        bagName = name,
                        message = "Use soon: $name expires in $daysLeft day${plural(daysLeft)}${if (inferred) " (estimated)" else ""}",
                        bagId = bag.id,
                    )
                }
            }
    }

    internal fun stalenessAlerts(
        bags: List<CoffeeBagEntity>,
        nowMillis: Long,
    ): List<InventoryAlert> {
        val staleThreshold = nowMillis - STALE_OPEN_DAYS * MILLIS_PER_DAY
        return bags
            .filter { bag ->
                bag.status == "OPEN" &&
                    bag.openedDate != null &&
                    bag.openedDate < staleThreshold &&
                    (bag.weightG ?: 0f) > 50f
            }
            .map { bag ->
                val daysOpen = ((nowMillis - bag.openedDate!!) / MILLIS_PER_DAY).toInt()
                val name = bagDisplayName(bag)
                InventoryAlert(
                    type = InventoryAlertType.STALENESS,
                    bagName = name,
                    message = "Forgotten bag: $name opened $daysOpen days ago",
                    bagId = bag.id,
                )
            }
    }

    internal fun agingSealedAlerts(
        bags: List<CoffeeBagEntity>,
        nowMillis: Long,
    ): List<InventoryAlert> {
        val agingThreshold = nowMillis - AGING_SEALED_DAYS * MILLIS_PER_DAY
        return bags
            .filter { bag ->
                bag.status == "SEALED" &&
                    bag.roastDate != null &&
                    bag.roastDate < agingThreshold
            }
            .map { bag ->
                val name = bagDisplayName(bag)
                InventoryAlert(
                    type = InventoryAlertType.AGING_SEALED,
                    bagName = name,
                    message = "$name aging sealed — open or freeze it",
                    bagId = bag.id,
                )
            }
    }

    // --- 4d: Focus mode ---

    internal fun focusAlerts(
        bags: List<CoffeeBagEntity>,
        nowMillis: Long,
    ): List<InventoryAlert> {
        val openBags = bags.filter { it.status == "OPEN" }
        if (openBags.size <= FOCUS_THRESHOLD) return emptyList()

        val ranked = openBags.sortedWith(
            compareByDescending<CoffeeBagEntity> { bag ->
                bag.roastDate?.let {
                    CoffeeBagInsights.freshnessInsight(it, nowMillis).score
                } ?: FreshnessPhase.UNKNOWN.rankWeight
            }.thenBy { it.weightG ?: Float.MAX_VALUE },
        )

        val topBag = ranked.first()
        val name = bagDisplayName(topBag)
        val freshness = CoffeeBagInsights.freshnessInsight(topBag.roastDate, nowMillis)
        val brewsLeft = topBag.weightG?.let { (it / 20f).toInt() }

        val detail = buildString {
            append("Focus on $name — ${freshness.phase.shortLabel.lowercase()} freshness")
            if (brewsLeft != null && brewsLeft > 0) {
                append(", ~$brewsLeft brew${plural(brewsLeft)} left")
            }
            append(". Your other ${openBags.size - 1} bag${plural(openBags.size - 1)} can wait.")
        }

        return listOf(
            InventoryAlert(
                type = InventoryAlertType.FOCUS,
                bagName = name,
                message = detail,
                bagId = topBag.id,
            ),
        )
    }

    // --- Decaf coverage ---

    /**
     * Fires when the user has been brewing decaf recently but has no decaf bags available
     * (SEALED, OPEN, or FROZEN — FROZEN counts because the user can defrost). This signals
     * they're about to run out of decaf options and should stock up.
     *
     * Requires ≥3 decaf brew logs within the coverage window to avoid false positives from
     * a single one-off decaf brew.
     */
    internal fun decafCoverageAlerts(
        bags: List<CoffeeBagEntity>,
        brewLogs: List<com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity>,
        nowMillis: Long,
    ): List<InventoryAlert> {
        val windowStart = nowMillis - DECAF_COVERAGE_WINDOW_DAYS * MILLIS_PER_DAY
        val recentDecafBrews = brewLogs.count { it.isDecaf && it.createdAt >= windowStart }
        if (recentDecafBrews < DECAF_COVERAGE_THRESHOLD) return emptyList()

        val availableDecaf = bags.count { bag ->
            bag.isDecaf && bag.status in AVAILABLE_BAG_STATUSES
        }
        if (availableDecaf > 0) return emptyList()

        return listOf(
            InventoryAlert(
                type = InventoryAlertType.DECAF_COVERAGE,
                bagName = "",
                message = "You've brewed decaf $recentDecafBrews times recently — no decaf bags on hand",
                bagId = null,
            ),
        )
    }

    // --- Helpers ---

    private fun inferredExpiryDate(bag: CoffeeBagEntity): Long? {
        if (bag.status != "SEALED" || bag.roastDate == null) return null
        return bag.roastDate + INFERRED_SHELF_LIFE_DAYS * MILLIS_PER_DAY
    }

    private fun bagDisplayName(bag: CoffeeBagEntity): String =
        bag.name + (bag.roaster?.let { " · $it" } ?: "")

    private fun plural(count: Int): String = if (count == 1) "" else "s"
}
