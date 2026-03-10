package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.db.entity.BrewLogEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.db.entity.FlavorTagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeBagInsightsTest {

    // --- Freshness system ---

    @Test
    fun `freshness insight marks early bags as degassing`() {
        val now = 1_000L * 60 * 60 * 24 * 20

        val insight = CoffeeBagInsights.freshnessInsight(
            roastDateMillis = now - DAYS * 3,
            nowMillis = now,
        )

        assertEquals(FreshnessPhase.DEGASSING, insight.phase)
        assertTrue(insight.coachText.contains("rest", ignoreCase = true))
    }

    @Test
    fun `freshness insight marks day ten bags as peak`() {
        val now = 1_000L * 60 * 60 * 24 * 30

        val insight = CoffeeBagInsights.freshnessInsight(
            roastDateMillis = now - DAYS * 10,
            nowMillis = now,
        )

        assertEquals(FreshnessPhase.PEAK, insight.phase)
        assertTrue(insight.windowText.contains("7-21"))
    }

    @Test
    fun `freshness score stays ordered across phase boundaries`() {
        val now = DAYS * 80
        val daySix = CoffeeBagInsights.freshnessInsight(
            roastDateMillis = now - DAYS * 6,
            nowMillis = now,
        )
        val daySeven = CoffeeBagInsights.freshnessInsight(
            roastDateMillis = now - DAYS * 7,
            nowMillis = now,
        )
        val dayFortyTwo = CoffeeBagInsights.freshnessInsight(
            roastDateMillis = now - DAYS * 42,
            nowMillis = now,
        )
        val dayFortyThree = CoffeeBagInsights.freshnessInsight(
            roastDateMillis = now - DAYS * 43,
            nowMillis = now,
        )

        assertTrue(daySeven.score > daySix.score)
        assertTrue(dayFortyTwo.score > dayFortyThree.score)
    }

    // --- Sensory snapshot ---

    @Test
    fun `sensory snapshot favors repeated brew descriptors before bag text`() {
        val bag = bag(
            tastingNotes = "floral, honey, citrus",
        )
        val brewLogs = listOf(
            log(id = 1L, rating = 4.5f),
            log(id = 2L, rating = 4.0f),
        )
        val flavorTags = listOf(
            tag(brewLogId = 1L, descriptor = "Citrus"),
            tag(brewLogId = 1L, descriptor = "Berry"),
            tag(brewLogId = 2L, descriptor = "Citrus"),
        )

        val snapshot = CoffeeBagInsights.buildSensorySnapshot(
            bag = bag,
            brewLogs = brewLogs,
            flavorTags = flavorTags,
        )

        assertEquals(listOf("Citrus", "Berry", "Floral"), snapshot.topChips.take(3))
        assertEquals(4.25f, snapshot.averageRating ?: 0f, 0.01f)
    }

    // --- Grind intelligence ---

    @Test
    fun `grind insight keeps best grind and suggests finer after sour cup`() {
        val bag = bag(grindSetting = "6.2")
        val brewLogs = listOf(
            log(
                id = 1L,
                grindSetting = "6.0",
                rating = 4.5f,
                tasteFeedback = "BALANCED",
                createdAt = 100L,
            ),
            log(
                id = 2L,
                grindSetting = "6.3",
                rating = 3.0f,
                tasteFeedback = "TOO_SOUR",
                createdAt = 200L,
            ),
        )

        val insight = CoffeeBagInsights.buildGrindInsight(
            bag = bag,
            brewLogs = brewLogs,
        )

        assertEquals("6.3", insight.lastGrindSetting)
        assertEquals("6.0", insight.bestGrindSetting)
        assertEquals(GrindOutcomeTag.TOO_COARSE, insight.recentOutcomes.first().outcome)
        assertTrue(insight.adjustmentHint.orEmpty().contains("finer", ignoreCase = true))
    }

    // --- Brew picker ranking ---

    @Test
    fun `bag ranking favors peak bag with enough coffee and positive grind history`() {
        val now = DAYS * 40
        val bags = listOf(
            bag(
                id = 1L,
                name = "Peak Bag",
                roastDate = now - DAYS * 10,
                weightG = 240f,
            ),
            bag(
                id = 2L,
                name = "Vintage Bag",
                roastDate = now - DAYS * 70,
                weightG = 240f,
            ),
        )
        val brewLogs = listOf(
            log(
                id = 1L,
                coffeeBagId = 1L,
                grindSetting = "5.8",
                rating = 4.5f,
                tasteFeedback = "BALANCED",
                createdAt = now - DAYS,
            ),
            log(
                id = 2L,
                coffeeBagId = 2L,
                grindSetting = "6.2",
                rating = 3.0f,
                tasteFeedback = "TOO_BITTER",
                createdAt = now - DAYS,
            ),
        )

        val ranked = CoffeeBagInsights.rankBagsForBrew(
            bags = bags,
            brewLogs = brewLogs,
            targetDoseG = 20f,
            nowMillis = now,
        )

        assertEquals(1L, ranked.first().bag.id)
        assertTrue(ranked.first().reasons.any { it.contains("Peak", ignoreCase = true) })
    }

    @Test
    fun `bag ranking carries brew flavor tags into sensory chips`() {
        val now = DAYS * 50
        val bags = listOf(
            bag(
                id = 1L,
                roastDate = now - DAYS * 12,
                tastingNotes = "Chocolate",
            ),
        )
        val brewLogs = listOf(
            log(
                id = 1L,
                coffeeBagId = 1L,
                rating = 4.0f,
                createdAt = now - DAYS,
            ),
        )
        val flavorTags = listOf(
            tag(brewLogId = 1L, descriptor = "Berry"),
            tag(brewLogId = 1L, descriptor = "Citrus"),
        )

        val ranked = CoffeeBagInsights.rankBagsForBrew(
            bags = bags,
            brewLogs = brewLogs,
            flavorTags = flavorTags,
            targetDoseG = 20f,
            nowMillis = now,
        )

        assertEquals(listOf("Berry", "Citrus", "Chocolate"), ranked.first().sensorySnapshot.topChips)
    }

    @Test
    fun `bag ranking penalizes bags without enough coffee for the brew`() {
        val now = DAYS * 55
        val ranked = CoffeeBagInsights.rankBagsForBrew(
            bags = listOf(
                bag(
                    id = 1L,
                    name = "Enough",
                    roastDate = now - DAYS * 12,
                    weightG = 30f,
                ),
                bag(
                    id = 2L,
                    name = "Too low",
                    roastDate = now - DAYS * 12,
                    weightG = 12f,
                ),
            ),
            brewLogs = emptyList(),
            targetDoseG = 20f,
            nowMillis = now,
        )

        assertEquals("Enough", ranked.first().bag.name)
        assertEquals("Too low", ranked.last().bag.name)
    }

    private fun bag(
        id: Long = 1L,
        name: String = "Bag",
        roastDate: Long? = null,
        tastingNotes: String? = null,
        grindSetting: String? = null,
        weightG: Float? = 250f,
    ) = CoffeeBagEntity(
        id = id,
        name = name,
        roastDate = roastDate,
        tastingNotes = tastingNotes,
        grindSetting = grindSetting,
        weightG = weightG,
        initialWeightG = weightG,
    )

    private fun log(
        id: Long,
        coffeeBagId: Long? = 1L,
        grindSetting: String? = null,
        rating: Float? = null,
        tasteFeedback: String? = null,
        createdAt: Long = 0L,
    ) = BrewLogEntity(
        id = id,
        coffeeBagId = coffeeBagId,
        method = "PULSAR",
        doseG = 20f,
        waterG = 340f,
        ratio = 17f,
        grindSetting = grindSetting,
        rating = rating,
        tasteFeedback = tasteFeedback,
        createdAt = createdAt,
    )

    private fun tag(
        brewLogId: Long,
        descriptor: String,
    ) = FlavorTagEntity(
        brewLogId = brewLogId,
        descriptor = descriptor,
    )

    private companion object {
        const val DAYS = 24L * 60 * 60 * 1000
    }
}
