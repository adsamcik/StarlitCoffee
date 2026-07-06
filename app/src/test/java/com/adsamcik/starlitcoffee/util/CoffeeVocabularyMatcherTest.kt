package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeVocabularyMatcherTest {

    private val vocabulary = CoffeeFilterVocabulary(
        origins = listOf(
            CoffeeVocabularyEntry("Ethiopia", listOf("ETH")),
            CoffeeVocabularyEntry("Costa Rica"),
            CoffeeVocabularyEntry("Colombia"),
            CoffeeVocabularyEntry("Kenya"),
        ),
        varieties = listOf(
            CoffeeVocabularyEntry("Geisha", listOf("Gesha")),
            CoffeeVocabularyEntry("Java"),
            CoffeeVocabularyEntry("Bourbon"),
        ),
        processTypes = listOf(
            CoffeeVocabularyEntry("Washed"),
            CoffeeVocabularyEntry("Anaerobic Natural"),
        ),
        roastLevels = listOf(CoffeeVocabularyEntry("Light", listOf("Light Roast"))),
        tastingNotes = listOf(
            CoffeeVocabularyEntry("Milk Chocolate"),
            CoffeeVocabularyEntry("Blueberry"),
            CoffeeVocabularyEntry("Jasmine"),
        ),
    )

    // --- Whole-token & phrase matching ---

    @Test
    fun `matches whole-word origin and process tokens in ocr text`() {
        val hints = CoffeeVocabularyMatcher.match(
            "Single origin coffee from Ethiopia. Washed process.",
            vocabulary,
        )

        assertTrue(hints.origins.contains("Ethiopia"))
        assertTrue(hints.processTypes.contains("Washed"))
    }

    @Test
    fun `matches multi-word phrase across whitespace`() {
        val hints = CoffeeVocabularyMatcher.match("Lovingly grown in Costa Rica", vocabulary)

        assertTrue(hints.origins.contains("Costa Rica"))
        assertFalse(hints.origins.contains("Kenya"))
    }

    @Test
    fun `matches multi-word tasting notes`() {
        val hints = CoffeeVocabularyMatcher.match(
            "Tasting notes: milk chocolate, blueberry, jasmine",
            vocabulary,
        )

        assertTrue(hints.tastingNotes.contains("Milk Chocolate"))
        assertTrue(hints.tastingNotes.contains("Blueberry"))
        assertTrue(hints.tastingNotes.contains("Jasmine"))
    }

    // --- Alias & abbreviation matching ---

    @Test
    fun `matches variety via alias`() {
        val hints = CoffeeVocabularyMatcher.match("Panama Gesha, lot 42", vocabulary)

        assertTrue(hints.varieties.contains("Geisha"))
    }

    @Test
    fun `matches origin via short abbreviation token`() {
        val hints = CoffeeVocabularyMatcher.match("ORIGIN: ETH  ALT: 1900m", vocabulary)

        assertTrue(hints.origins.contains("Ethiopia"))
    }

    // --- OCR-tolerant fuzzy matching ---

    @Test
    fun `tolerates a single-character ocr error via fuzzy match`() {
        // "Ethiopa" is one deletion away from "Ethiopia".
        val hints = CoffeeVocabularyMatcher.match("beans from ethiopa region", vocabulary)

        assertTrue(hints.origins.contains("Ethiopia"))
    }

    @Test
    fun `does not fuzzy-match short terms inside longer words`() {
        // "Java" (4 chars) must only match as a whole token, never inside "javanese".
        val insideWord = CoffeeVocabularyMatcher.match("a javanese folk dance", vocabulary)
        val wholeToken = CoffeeVocabularyMatcher.match("variety: Java", vocabulary)

        assertFalse(insideWord.varieties.contains("Java"))
        assertTrue(wholeToken.varieties.contains("Java"))
    }

    // --- Ranking & caps ---

    @Test
    fun `phrase match outranks token match when capped`() {
        val hints = CoffeeVocabularyMatcher.match(
            "Costa Rica and Kenya",
            vocabulary,
            maxPerField = 1,
        )

        assertEquals(listOf("Costa Rica"), hints.origins)
    }

    // --- Empty inputs ---

    @Test
    fun `blank ocr text yields empty hints`() {
        assertEquals(KnownFieldValues.EMPTY, CoffeeVocabularyMatcher.match("   ", vocabulary))
    }

    @Test
    fun `empty vocabulary yields empty hints`() {
        val hints = CoffeeVocabularyMatcher.match("Ethiopia Washed", CoffeeFilterVocabulary.EMPTY)

        assertEquals(KnownFieldValues.EMPTY, hints)
    }

    // --- Merge ---

    @Test
    fun `merge places boost values first and dedupes case-insensitively`() {
        val base = KnownFieldValues(origins = listOf("Colombia", "Kenya"))
        val boost = KnownFieldValues(origins = listOf("Ethiopia", "colombia"))

        val merged = CoffeeVocabularyMatcher.merge(base, boost)

        assertEquals(listOf("Ethiopia", "colombia", "Kenya"), merged.origins)
    }

    @Test
    fun `merge preserves base non-vocabulary fields`() {
        val base = KnownFieldValues(
            names = listOf("Monarch"),
            roasters = listOf("Onyx"),
            farms = listOf("La Esperanza"),
        )
        val boost = KnownFieldValues(varieties = listOf("Geisha"))

        val merged = CoffeeVocabularyMatcher.merge(base, boost)

        assertEquals(listOf("Monarch"), merged.names)
        assertEquals(listOf("Onyx"), merged.roasters)
        assertEquals(listOf("La Esperanza"), merged.farms)
        assertEquals(listOf("Geisha"), merged.varieties)
    }

    // --- Scale & determinism ---

    @Test
    fun `handles a large ocr blob and selects across all fields`() {
        val noise = (1..400).joinToString(" ") { "lorem$it ipsum dolor sit amet" }
        val ocr = buildString {
            append(noise)
            append("\nOrigin: Ethiopia — Washed process — variety Gesha\n")
            append("Roast: Light. Notes: milk chocolate, blueberry, jasmine.\n")
            append(noise)
        }

        val hints = CoffeeVocabularyMatcher.match(ocr, vocabulary)

        assertTrue(hints.origins.contains("Ethiopia"))
        assertTrue(hints.processTypes.contains("Washed"))
        assertTrue(hints.varieties.contains("Geisha"))
        assertTrue(hints.roastLevels.contains("Light"))
        assertTrue(hints.tastingNotes.contains("Milk Chocolate"))
        assertTrue(hints.tastingNotes.contains("Blueberry"))
    }

    @Test
    fun `respects the per-field cap`() {
        val ocr = "Ethiopia Colombia Kenya Costa Rica"

        val hints = CoffeeVocabularyMatcher.match(ocr, vocabulary, maxPerField = 2)

        assertEquals(2, hints.origins.size)
    }

    @Test
    fun `repeated calls return identical results`() {
        val ocr = "Ethiopa washd gesha, notes: bluberry & jasmin"

        val first = CoffeeVocabularyMatcher.match(ocr, vocabulary)
        val second = CoffeeVocabularyMatcher.match(ocr, vocabulary)

        assertEquals(first, second)
    }
}
