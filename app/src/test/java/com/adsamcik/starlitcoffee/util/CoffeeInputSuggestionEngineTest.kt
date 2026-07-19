package com.adsamcik.starlitcoffee.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoffeeInputSuggestionEngineTest {

    private val origins = listOf(
        CoffeeInputSuggestion("Ethiopia", aliases = listOf("ETH")),
        CoffeeInputSuggestion("Colombia"),
        CoffeeInputSuggestion("Costa Rica"),
        CoffeeInputSuggestion("Côte d'Ivoire"),
    )

    @Test
    fun `exact match ranks before prefix and fuzzy matches`() {
        val ranked = CoffeeInputSuggestionEngine.rank(
            input = "Colombia",
            suggestions = origins + CoffeeInputSuggestion("Colombian"),
        )

        assertEquals("Colombia", ranked.first())
    }

    @Test
    fun `prefix match ranks before contained match`() {
        val ranked = CoffeeInputSuggestionEngine.rank(
            input = "cost",
            suggestions = listOf(
                CoffeeInputSuggestion("Acosta Estate"),
                CoffeeInputSuggestion("Costa Rica"),
            ),
        )

        assertEquals(listOf("Costa Rica", "Acosta Estate"), ranked)
    }

    @Test
    fun `search folds case punctuation and diacritics`() {
        val ranked = CoffeeInputSuggestionEngine.rank(
            input = "cote d ivoire",
            suggestions = origins,
        )

        assertEquals("Côte d'Ivoire", ranked.first())
    }

    @Test
    fun `alias and fuzzy matches return canonical values`() {
        assertEquals(
            "Ethiopia",
            CoffeeInputSuggestionEngine.rank("eth", origins).first(),
        )
        assertEquals(
            "Ethiopia",
            CoffeeInputSuggestionEngine.rank("ethiopa", origins).first(),
        )
    }

    @Test
    fun `fuzzy matching tolerates a typo in an incomplete prefix`() {
        val ranked = CoffeeInputSuggestionEngine.rank(
            input = "yirgch",
            suggestions = listOf(CoffeeInputSuggestion("Yirgacheffe")),
        )

        assertEquals(listOf("Yirgacheffe"), ranked)
    }

    @Test
    fun `fuzzy matching checks each word prefix`() {
        val ranked = CoffeeInputSuggestionEngine.rank(
            input = "bourbn",
            suggestions = listOf(CoffeeInputSuggestion("Pink Bourbon")),
        )

        assertEquals(listOf("Pink Bourbon"), ranked)
    }

    @Test
    fun `recency breaks equal relevance ties`() {
        val suggestions = CoffeeInputSuggestionEngine.merge(
            recentValues = listOf("Pink Bourbon", "Red Bourbon"),
            libraryValues = listOf(
                CoffeeInputSuggestion("Yellow Bourbon"),
                CoffeeInputSuggestion("Red Bourbon"),
                CoffeeInputSuggestion("Pink Bourbon"),
            ),
        )

        val ranked = CoffeeInputSuggestionEngine.rank("bourbon", suggestions)

        assertEquals(listOf("Pink Bourbon", "Red Bourbon", "Yellow Bourbon"), ranked)
    }

    @Test
    fun `merge deduplicates normalized values and preserves recent spelling`() {
        val suggestions = CoffeeInputSuggestionEngine.merge(
            recentValues = listOf("Nariño", "nariño", "Narino"),
            libraryValues = listOf(CoffeeInputSuggestion("Narino", aliases = listOf("NAR"))),
        )

        assertEquals(1, suggestions.size)
        assertEquals("Nariño", suggestions.single().value)
        assertEquals(listOf("NAR"), suggestions.single().aliases)
    }

    @Test
    fun `merge deduplicates localized values through shared aliases`() {
        val suggestions = CoffeeInputSuggestionEngine.merge(
            recentValues = emptyList(),
            libraryValues = listOf(
                CoffeeInputSuggestion("Etiopie", aliases = listOf("Ethiopia", "ETH")),
                CoffeeInputSuggestion("Ethiopia", aliases = listOf("Ethiopian")),
            ),
        )

        assertEquals(1, suggestions.size)
        assertEquals("Etiopie", suggestions.single().value)
    }

    @Test
    fun `merge collapses every history value connected by a library alias`() {
        val suggestions = CoffeeInputSuggestionEngine.merge(
            recentValues = listOf("Jamaica", "Jamaican"),
            libraryValues = listOf(
                CoffeeInputSuggestion("Jamaica", aliases = listOf("Jamaican")),
            ),
        )

        assertEquals(1, suggestions.size)
        assertEquals("Jamaica", suggestions.single().value)
    }

    @Test
    fun `merge keeps canonical library value searchable when recent spelling wins`() {
        val suggestions = CoffeeInputSuggestionEngine.merge(
            recentValues = listOf("Côte d'Ivoire"),
            libraryValues = listOf(
                CoffeeInputSuggestion("Ivory Coast", aliases = listOf("Côte d'Ivoire")),
            ),
        )

        assertEquals(
            "Côte d'Ivoire",
            CoffeeInputSuggestionEngine.rank("ivory", suggestions).single(),
        )
    }

    @Test
    fun `vocabulary only origin resolves to canonical country`() {
        val resolved = CoffeeInputSuggestionEngine.resolveCanonicalOrigin(
            input = "JAM",
            vocabularyOrigins = listOf(
                CoffeeVocabularyEntry("Jamaica", aliases = listOf("JAM")),
            ),
            locale = Locale.ENGLISH,
        )

        assertEquals("Jamaica", resolved)
    }

    @Test
    fun `known origin resolves to stable canonical country name across locales`() {
        val resolved = CoffeeInputSuggestionEngine.resolveCanonicalOrigin(
            input = "Congo - Kinshasa",
            vocabularyOrigins = emptyList(),
            locale = Locale.ENGLISH,
        )

        assertEquals("DR Congo", resolved)
    }

    @Test
    fun `compound origin does not acquire broader canonical identity for region filtering`() {
        val resolved = CoffeeInputSuggestionEngine.resolveCanonicalOrigin(
            input = "Ethiopia / Kenya",
            vocabularyOrigins = emptyList(),
            locale = Locale.ENGLISH,
        )

        assertNull(resolved)
    }

    @Test
    fun `accepting a suggestion preserves arbitrary single value input semantics`() {
        assertEquals(
            "Custom Experimental",
            CoffeeInputSuggestionEngine.accept(
                currentValue = "anything the user typed",
                suggestion = "Custom Experimental",
                multiValue = false,
            ),
        )
    }

    @Test
    fun `multi value ranking and acceptance operate on active comma separated token`() {
        val suggestions = listOf(
            CoffeeInputSuggestion("Blueberry"),
            CoffeeInputSuggestion("Blackberry"),
        )

        val ranked = CoffeeInputSuggestionEngine.rank(
            input = "Jasmine, blue",
            suggestions = suggestions,
            multiValue = true,
        )

        assertEquals("Blueberry", ranked.first())
        assertEquals(
            "Jasmine, Blueberry",
            CoffeeInputSuggestionEngine.accept(
                currentValue = "Jasmine, blue",
                suggestion = "Blueberry",
                multiValue = true,
            ),
        )
    }

    @Test
    fun `multi value acceptance removes active token when suggestion duplicates earlier token`() {
        assertEquals(
            "Geisha",
            CoffeeInputSuggestionEngine.accept(
                currentValue = "Geisha, Bourbon",
                suggestion = "geisha",
                multiValue = true,
            ),
        )
    }

    @Test
    fun `multi value ranking uses token around actual cursor`() {
        val value = "Jasmine, blue, Cocoa"
        val ranked = CoffeeInputSuggestionEngine.rank(
            input = value,
            suggestions = listOf(
                CoffeeInputSuggestion("Blueberry"),
                CoffeeInputSuggestion("Cocoa nib"),
            ),
            multiValue = true,
            cursorIndex = value.indexOf("blue") + 2,
        )

        assertEquals("Blueberry", ranked.first())
    }

    @Test
    fun `multi value acceptance replaces middle token and returns cursor after replacement`() {
        val value = "Jasmine, blue, Cocoa"
        val accepted = CoffeeInputSuggestionEngine.acceptWithSelection(
            currentValue = value,
            suggestion = "Blueberry",
            multiValue = true,
            cursorIndex = value.indexOf("blue") + 2,
        )

        assertEquals("Jasmine, Blueberry, Cocoa", accepted.value)
        assertEquals("Jasmine, Blueberry".length, accepted.cursorIndex)
    }

    @Test
    fun `duplicate suggestion removes middle partial token without deleting later values`() {
        val value = "Geisha, gei, Bourbon"
        val accepted = CoffeeInputSuggestionEngine.acceptWithSelection(
            currentValue = value,
            suggestion = "Geisha",
            multiValue = true,
            cursorIndex = value.indexOf("gei", startIndex = 1) + 2,
        )

        assertEquals("Geisha, Bourbon", accepted.value)
        assertEquals("Geisha".length, accepted.cursorIndex)
    }
}
