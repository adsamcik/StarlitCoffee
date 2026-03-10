package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import com.adsamcik.starlitcoffee.data.model.TasteFeedback
import java.util.Locale

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

enum class FreshnessPhase(
    val displayName: String,
    val shortLabel: String,
    val rankWeight: Float,
) {
    DEGASSING(
        displayName = "Degassing",
        shortLabel = "Resting",
        rankWeight = 0.58f,
    ),
    PEAK(
        displayName = "Peak",
        shortLabel = "Peak",
        rankWeight = 1.0f,
    ),
    MELLOWING(
        displayName = "Mellowing",
        shortLabel = "Mellowing",
        rankWeight = 0.78f,
    ),
    VINTAGE(
        displayName = "Vintage",
        shortLabel = "Vintage",
        rankWeight = 0.34f,
    ),
    UNKNOWN(
        displayName = "Unknown",
        shortLabel = "Unknown",
        rankWeight = 0.42f,
    ),
}

data class FreshnessInsight(
    val phase: FreshnessPhase,
    val daysSinceRoast: Int?,
    val ringProgress: Float,
    val score: Float,
    val headline: String,
    val coachText: String,
    val windowText: String,
)

enum class GrindOutcomeTag(val label: String) {
    WORKED("Worked"),
    TOO_FINE("Too fine"),
    TOO_COARSE("Too coarse"),
    UNKNOWN("Untested"),
}

data class GrindOutcomeEntry(
    val brewLogId: Long,
    val grindSetting: String,
    val outcome: GrindOutcomeTag,
    val rating: Float?,
    val timestamp: Long,
)

data class GrindInsight(
    val lastGrindSetting: String?,
    val bestGrindSetting: String?,
    val bestRating: Float?,
    val recentOutcomes: List<GrindOutcomeEntry>,
    val adjustmentHint: String?,
    val summary: String,
)

data class SensorySnapshot(
    val topChips: List<String>,
    val bagNoteChips: List<String>,
    val averageRating: Float?,
    val summary: String,
    val totalTaggedBrews: Int,
)

data class RankedBagSuggestion(
    val bag: CoffeeBagEntity,
    val freshness: FreshnessInsight,
    val grindInsight: GrindInsight,
    val sensorySnapshot: SensorySnapshot,
    val score: Float,
    val reasons: List<String>,
)

object CoffeeBagInsights {

    fun freshnessInsight(
        roastDateMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
    ): FreshnessInsight {
        if (roastDateMillis == null) {
            return FreshnessInsight(
                phase = FreshnessPhase.UNKNOWN,
                daysSinceRoast = null,
                ringProgress = 0.25f,
                score = FreshnessPhase.UNKNOWN.rankWeight,
                headline = "Add roast date",
                coachText = "Roast date unlocks freshness coaching and smarter bag ranking.",
                windowText = "Peak window: usually days 7-21 off roast",
            )
        }

        val daysSinceRoast = ((nowMillis - roastDateMillis) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
        return when {
            daysSinceRoast < 7 -> FreshnessInsight(
                phase = FreshnessPhase.DEGASSING,
                daysSinceRoast = daysSinceRoast,
                ringProgress = (daysSinceRoast / 7f).coerceIn(0f, 1f),
                score = 0.58f + ((daysSinceRoast / 7f) * 0.24f),
                headline = "Still settling",
                coachText = "Let it rest a little longer if you want more clarity and sweetness.",
                windowText = "Peak window: days 7-21 off roast",
            )

            daysSinceRoast <= 21 -> FreshnessInsight(
                phase = FreshnessPhase.PEAK,
                daysSinceRoast = daysSinceRoast,
                ringProgress = 1f,
                score = 1f - (((daysSinceRoast - 7) / 14f).coerceIn(0f, 1f) * 0.08f),
                headline = "Best window right now",
                coachText = "This bag is in the sweet spot. Great time to dial it in.",
                windowText = "Peak window: days 7-21 off roast",
            )

            daysSinceRoast <= 42 -> FreshnessInsight(
                phase = FreshnessPhase.MELLOWING,
                daysSinceRoast = daysSinceRoast,
                ringProgress = 1f - ((daysSinceRoast - 21) / 21f).coerceIn(0f, 1f) * 0.35f,
                score = 0.90f - (((daysSinceRoast - 21) / 21f).coerceIn(0f, 1f) * 0.46f),
                headline = "Still brewing well",
                coachText = "Expect a softer cup than peak week. Lean on grind and dose to keep it lively.",
                windowText = "Peak window has passed, but the bag should still taste good.",
            )

            else -> FreshnessInsight(
                phase = FreshnessPhase.VINTAGE,
                daysSinceRoast = daysSinceRoast,
                ringProgress = 0.28f,
                score = (0.42f - (((daysSinceRoast - 42) / 120f) * 0.26f)).coerceAtLeast(0.08f),
                headline = "Past prime",
                coachText = "Use this bag soon, freeze extras, or expect a flatter cup profile.",
                windowText = "Beyond the main peak window",
            )
        }
    }

    fun buildSensorySnapshot(
        bag: CoffeeBagEntity,
        brewLogs: List<BrewLogEntity>,
        flavorTags: List<FlavorTagEntity>,
    ): SensorySnapshot {
        val brewIds = brewLogs.map { it.id }.toSet()
        val descriptorCounts = linkedMapOf<String, Int>()
        flavorTags
            .filter { it.brewLogId in brewIds }
            .forEach { tag ->
                val label = normalizeChip(tag.descriptor)
                descriptorCounts[label] = (descriptorCounts[label] ?: 0) + 1
            }

        val topDescriptorChips = descriptorCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

        val bagNoteChips = parseTastingNotes(CoffeeMetadataNormalizer.bagDisplayTastingNotes(bag))
        val topChips = (topDescriptorChips + bagNoteChips).distinct().take(6)
        val ratings = brewLogs.mapNotNull { it.rating }
        val averageRating = ratings.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        val summary = when {
            topChips.isNotEmpty() -> topChips.take(3).joinToString(" • ")
            averageRating != null -> "Average cup: ${"%.1f".format(Locale.US, averageRating)} stars"
            else -> "No sensory notes yet"
        }

        return SensorySnapshot(
            topChips = topChips,
            bagNoteChips = bagNoteChips,
            averageRating = averageRating,
            summary = summary,
            totalTaggedBrews = flavorTags.filter { it.brewLogId in brewIds }.map { it.brewLogId }.distinct().size,
        )
    }

    fun buildGrindInsight(
        bag: CoffeeBagEntity,
        brewLogs: List<BrewLogEntity>,
    ): GrindInsight {
        val grindLogs = brewLogs
            .filter { !it.grindSetting.isNullOrBlank() }
            .sortedByDescending { it.createdAt }

        val recentOutcomes = grindLogs.take(3).map { log ->
            GrindOutcomeEntry(
                brewLogId = log.id,
                grindSetting = log.grindSetting.orEmpty(),
                outcome = outcomeFor(log.tasteFeedback),
                rating = log.rating,
                timestamp = log.createdAt,
            )
        }

        val bestLog = grindLogs
            .filter { it.rating != null }
            .maxWithOrNull(compareBy<BrewLogEntity> { it.rating ?: 0f }.thenBy { it.createdAt })

        val lastGrind = grindLogs.firstOrNull()?.grindSetting ?: bag.grindSetting
        val adjustmentHint = recentOutcomes.firstOrNull()?.let { latest ->
            when (latest.outcome) {
                GrindOutcomeTag.WORKED -> "Last balanced cup was at ${latest.grindSetting}."
                GrindOutcomeTag.TOO_COARSE -> "Last cup skewed sour. Go a bit finer than ${latest.grindSetting}."
                GrindOutcomeTag.TOO_FINE -> "Last cup ran heavy. Go a bit coarser than ${latest.grindSetting}."
                GrindOutcomeTag.UNKNOWN -> "Start near ${latest.grindSetting} and adjust by taste."
            }
        } ?: bag.grindSetting?.let { "Start near $it and adjust by taste." }

        val summary = when {
            bestLog?.grindSetting != null && bestLog.rating != null ->
                "Best so far: ${bestLog.grindSetting} (${bestLog.rating} stars)"

            lastGrind != null -> "Last used: $lastGrind"
            else -> "No grind history yet"
        }

        return GrindInsight(
            lastGrindSetting = lastGrind,
            bestGrindSetting = bestLog?.grindSetting,
            bestRating = bestLog?.rating,
            recentOutcomes = recentOutcomes,
            adjustmentHint = adjustmentHint,
            summary = summary,
        )
    }

    fun rankBagsForBrew(
        bags: List<CoffeeBagEntity>,
        brewLogs: List<BrewLogEntity>,
        flavorTags: List<FlavorTagEntity> = emptyList(),
        targetDoseG: Float,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<RankedBagSuggestion> {
        return bags.map { bag ->
            val bagLogs = brewLogs.filter { it.coffeeBagId == bag.id }
            val freshness = freshnessInsight(bag.roastDate, nowMillis)
            val grindInsight = buildGrindInsight(bag, bagLogs)
            val sensorySnapshot = buildSensorySnapshot(
                bag = bag,
                brewLogs = bagLogs,
                flavorTags = flavorTags,
            )

            val weightScore = weightScore(bag.weightG, targetDoseG)
            val grindScore = when (grindInsight.recentOutcomes.firstOrNull()?.outcome) {
                GrindOutcomeTag.WORKED -> 0.32f
                GrindOutcomeTag.UNKNOWN, null -> if (grindInsight.bestGrindSetting != null) 0.16f else 0f
                GrindOutcomeTag.TOO_FINE, GrindOutcomeTag.TOO_COARSE -> 0.08f
            }
            val statusScore = when (bag.status) {
                "OPEN" -> 0.14f
                "SEALED" -> 0.08f
                "FROZEN" -> -0.08f
                else -> 0f
            }

            val reasons = buildList {
                add("${freshness.phase.displayName} freshness")
                bag.weightG?.let { add("${"%.0f".format(Locale.US, it)}g left") }
                when (grindInsight.recentOutcomes.firstOrNull()?.outcome) {
                    GrindOutcomeTag.WORKED -> add("Last cup worked at ${grindInsight.recentOutcomes.first().grindSetting}")
                    GrindOutcomeTag.TOO_COARSE -> add("Last cup wanted a finer grind")
                    GrindOutcomeTag.TOO_FINE -> add("Last cup wanted a coarser grind")
                    GrindOutcomeTag.UNKNOWN, null -> {
                        if (grindInsight.bestGrindSetting != null) {
                            add("Best grind: ${grindInsight.bestGrindSetting}")
                        }
                    }
                }
            }

            RankedBagSuggestion(
                bag = bag,
                freshness = freshness,
                grindInsight = grindInsight,
                sensorySnapshot = sensorySnapshot,
                score = freshness.score + weightScore + grindScore + statusScore,
                reasons = reasons,
            )
        }.sortedWith(
            compareByDescending<RankedBagSuggestion> { it.score }
                .thenByDescending { it.bag.weightG ?: 0f }
                .thenByDescending { it.bag.roastDate ?: Long.MIN_VALUE },
        )
    }

    fun parseTastingNotes(tastingNotes: String?): List<String> {
        if (tastingNotes.isNullOrBlank()) return emptyList()
        return tastingNotes
            .split(",", ";", "/", "\n")
            .map { normalizeChip(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalizeChip(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        val lower = trimmed.lowercase(Locale.getDefault())
        return lower.replaceFirstChar { first ->
            if (first.isLowerCase()) {
                first.titlecase(Locale.getDefault())
            } else {
                first.toString()
            }
        }
    }

    private fun weightScore(weightG: Float?, targetDoseG: Float): Float {
        if (weightG == null || targetDoseG <= 0f) return 0.05f
        return when {
            weightG < targetDoseG -> -1.2f
            weightG < targetDoseG * 2f -> 0.1f
            weightG < targetDoseG * 5f -> 0.25f
            else -> 0.42f
        }
    }

    private fun outcomeFor(rawFeedback: String?): GrindOutcomeTag {
        val feedback = rawFeedback?.let {
            runCatching { TasteFeedback.valueOf(it) }.getOrNull()
        } ?: return GrindOutcomeTag.UNKNOWN

        return when (feedback) {
            TasteFeedback.BALANCED -> GrindOutcomeTag.WORKED
            TasteFeedback.TOO_SOUR -> GrindOutcomeTag.TOO_COARSE
            TasteFeedback.TOO_BITTER,
            TasteFeedback.ASTRINGENT,
            TasteFeedback.CLOGGED,
            -> GrindOutcomeTag.TOO_FINE
        }
    }
}
