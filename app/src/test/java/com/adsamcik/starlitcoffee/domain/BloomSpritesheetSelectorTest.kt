package com.adsamcik.starlitcoffee.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BloomSpritesheetSelectorTest {

    // --- Eligibility / disabled flowers ---

    @Test
    fun `returns null when every flower is disabled`() {
        val weights = BloomSpritesheetIds.associateWith { 0 }
        val picked = pickWeightedBloomSpritesheetId(weights)
        assertNull(picked)
    }

    @Test
    fun `picks the only enabled flower deterministically`() {
        val weights = BloomSpritesheetIds.associateWith { id ->
            if (id == "rose") 1 else 0
        }
        repeat(20) {
            assertEquals("rose", pickWeightedBloomSpritesheetId(weights))
        }
    }

    @Test
    fun `disabled flower with high display count never inflates other flowers' boost`() {
        // rose disabled but historically popular (100 displays) — must not
        // make the rest of the field disproportionately attractive.
        val weights = BloomSpritesheetIds.associateWith { id ->
            if (id == "rose") 0 else 1
        }
        val displayCounts = mapOf("rose" to 100)

        // With rose excluded from "max", every other flower has count 0 and
        // max=0, so all extras=0 and the picker becomes uniform random over
        // the remaining flowers. A simple sanity check: many trials should
        // hit several distinct flowers.
        val random = Random(1234)
        val seen = mutableSetOf<String>()
        repeat(200) {
            val id = pickWeightedBloomSpritesheetId(weights, displayCounts, random = random)
            assertNotNull(id)
            seen.add(id!!)
        }
        assertTrue("Expected several distinct flowers, got $seen", seen.size >= 5)
        assertTrue("Disabled flower must never be picked", "rose" !in seen)
    }

    // --- Rotation / weighting math ---

    @Test
    fun `flower at max display count gets only its base weight`() {
        // 2 flowers total, one shown 5 times, the other 0; multiplier = 2.
        // Leader weight = 1 + 0 = 1; underdog weight = 1 + 5*2 = 11.
        val weights = BloomSpritesheetIds.associateWith { id ->
            when (id) {
                "rose", "lotus" -> 1
                else -> 0
            }
        }
        val displayCounts = mapOf("rose" to 5)

        // Probabilistic: across many trials, lotus should appear far more often.
        var roseHits = 0
        var lotusHits = 0
        val random = Random(42)
        repeat(1_000) {
            val id = pickWeightedBloomSpritesheetId(weights, displayCounts, random = random)
            when (id) {
                "rose" -> roseHits++
                "lotus" -> lotusHits++
            }
        }

        // Theoretical: rose gets 1/12 ≈ 8.3%, lotus gets 11/12 ≈ 91.7%.
        // Allow a generous tolerance window — main goal is "lotus dominates".
        assertTrue(
            "Lotus should dominate (got rose=$roseHits, lotus=$lotusHits)",
            lotusHits > roseHits * 5,
        )
    }

    @Test
    fun `equal display counts fall back to uniform user-weighted selection`() {
        // All counts equal -> max - count = 0 for everyone -> additive boost
        // disappears -> distribution is the same as the legacy weighted random.
        val weights = BloomSpritesheetIds.associateWith { 1 }
        val displayCounts = BloomSpritesheetIds.associateWith { 7 }

        val random = Random(99)
        val hits = mutableMapOf<String, Int>()
        val trials = 10_000
        repeat(trials) {
            val id = pickWeightedBloomSpritesheetId(weights, displayCounts, random = random)
            hits[id!!] = (hits[id] ?: 0) + 1
        }

        // All flowers split 10k trials. Keep a broad tolerance so the test
        // covers weighting regressions without depending on the exact list size.
        val expectedPerFlower = trials.toDouble() / BloomSpritesheetIds.size
        val lowerBound = expectedPerFlower * 0.55
        val upperBound = expectedPerFlower * 1.45
        hits.values.forEach { count ->
            assertTrue("Distribution skewed: $hits", count.toDouble() in lowerBound..upperBound)
        }
        assertEquals(BloomSpritesheetIds.size, hits.size)
    }

    @Test
    fun `multiplier of zero disables rotation boost`() {
        // mult = 0 collapses to legacy weighted random — counts are ignored.
        val weights = BloomSpritesheetIds.associateWith { 1 }
        val displayCounts = mapOf("rose" to 100)

        var roseHits = 0
        val random = Random(7)
        repeat(2_000) {
            val id = pickWeightedBloomSpritesheetId(
                weights = weights,
                displayCounts = displayCounts,
                rotationMultiplier = 0,
                random = random,
            )
            if (id == "rose") roseHits++
        }
        // Equal chance among all flowers. Allow generous bounds.
        val expectedRoseHits = 2_000.0 / BloomSpritesheetIds.size
        assertTrue(
            "Rose should still appear with equal weighting (got $roseHits)",
            roseHits.toDouble() in (expectedRoseHits * 0.45)..(expectedRoseHits * 1.8),
        )
    }

    @Test
    fun `negative display counts are clamped to zero`() {
        // Defensive: malformed data must not yield negative weights.
        val weights = BloomSpritesheetIds.associateWith { id ->
            if (id == "rose" || id == "lotus") 1 else 0
        }
        val displayCounts = mapOf("rose" to -5, "lotus" to 0)

        // Both treated as count 0 -> uniform within rose/lotus.
        val random = Random(0)
        repeat(10) {
            val id = pickWeightedBloomSpritesheetId(weights, displayCounts, random = random)
            assertTrue("Picked $id", id == "rose" || id == "lotus")
        }
    }

    // --- Behavioural property: simulated rotation evens out ---

    @Test
    fun `feedback loop converges to roughly even distribution over many brews`() {
        // Simulate the production loop: pick -> increment count -> pick again.
        // After enough brews, every flower's count should be within a small
        // factor of every other's, even though picks are still random.
        val weights = BloomSpritesheetIds.associateWith { 1 }
        val counts = BloomSpritesheetIds.associateWith { 0 }.toMutableMap()
        val random = Random(20251102)
        // Convergence is asymptotic: the rotation bounds the absolute spread
        // between flowers to a small constant, so the spread relative to the
        // mean shrinks as the brew count grows. With ~40 flowers, a few
        // hundred brews leaves normal sampling outliers above the threshold;
        // "many brews" needs to be comfortably larger than the flower count.
        val brews = 2000

        repeat(brews) {
            val picked = pickWeightedBloomSpritesheetId(weights, counts, random = random)
            assertNotNull(picked)
            counts[picked!!] = (counts[picked] ?: 0) + 1
        }

        val total = counts.values.sum()
        assertEquals(brews, total)

        val mean = total.toDouble() / BloomSpritesheetIds.size
        val maxCount = counts.values.max()
        val minCount = counts.values.min()

        // The leader and the laggard should be within ~30% of the mean —
        // far tighter than uniform-random's expected spread for this sample size.
        assertTrue(
            "Distribution too uneven: counts=$counts, mean=$mean, min=$minCount, max=$maxCount",
            maxCount <= mean * 1.3 && minCount >= mean * 0.7,
        )
    }
}
