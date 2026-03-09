package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    // --- Levenshtein distance ---

    @Test
    fun `levenshteinDistance ignores case`() {
        assertEquals(1, FuzzyMatcher.levenshteinDistance("Washed", "washd"))
    }

    // --- Candidate matching ---

    @Test
    fun `fuzzyMatch prefers exact length candidate on tie`() {
        val match = FuzzyMatcher.fuzzyMatch(
            input = "Washed",
            candidates = listOf("Washes", "Washed"),
            maxDistance = 1,
            minLength = 5,
        )

        assertEquals("Washed", match)
    }

    @Test
    fun `fuzzyMatch skips candidates shorter than minimum length`() {
        val match = FuzzyMatcher.fuzzyMatch(
            input = "SL2B",
            candidates = listOf("SL28"),
            maxDistance = 1,
            minLength = 5,
        )

        assertNull(match)
    }

    // --- Text tokenization ---

    @Test
    fun `fuzzyMatchInText matches multi word phrases`() {
        val match = FuzzyMatcher.fuzzyMatchInText(
            text = "Origin: Costa Rca, honey processed",
            candidates = listOf("Costa Rica", "Ethiopia"),
            maxDistance = 2,
            minLength = 5,
        )

        assertEquals("Costa Rica", match)
    }

    @Test
    fun `fuzzyMatchInText returns best match across windows`() {
        val match = FuzzyMatcher.fuzzyMatchInText(
            text = "Variety Pik Bourbon and washed process",
            candidates = listOf("Pink Bourbon", "Washed"),
            maxDistance = 2,
            minLength = 5,
        )

        assertTrue(match == "Pink Bourbon" || match == "Washed")
        assertEquals("Washed", match)
    }
}
