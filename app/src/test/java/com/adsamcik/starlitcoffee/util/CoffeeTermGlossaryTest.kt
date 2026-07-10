package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeTermGlossaryTest {

    // --- integrity ---

    @Test
    fun `every entry has a non-blank source and english`() {
        CoffeeTermGlossary.entries.forEach {
            assertTrue("blank source", it.source.isNotBlank())
            assertTrue("blank english for ${it.source}", it.english.isNotBlank())
            assertTrue("blank normalized source for ${it.source}", it.normalizedSource.isNotBlank())
        }
    }

    @Test
    fun `normalized source terms are unique`() {
        val normalized = CoffeeTermGlossary.entries.map { it.normalizedSource }
        assertEquals(normalized.size, normalized.toSet().size)
    }

    // --- matching ---

    @Test
    fun `detects a czech term inside a realistic label with diacritics`() {
        val ocr = "Chuťové tóny: angrešt, borůvky, hruška"
        val english = CoffeeTermGlossary.matches(ocr).map { it.english }
        assertTrue(english.contains("gooseberry"))
        assertTrue(english.contains("pear"))
    }

    @Test
    fun `detects a nordic berry term`() {
        val ocr = "Smaksnotater: bringebær, mørk sjokolade"
        val english = CoffeeTermGlossary.matches(ocr).map { it.english }
        assertTrue(english.contains("raspberry"))
    }

    @Test
    fun `matching is diacritic-insensitive`() {
        val withDiacritics = CoffeeTermGlossary.matches("angrešt").map { it.english }
        val without = CoffeeTermGlossary.matches("angrest").map { it.english }
        assertEquals(listOf("gooseberry"), withDiacritics)
        assertEquals(listOf("gooseberry"), without)
    }

    @Test
    fun `matches only whole words, not substrings`() {
        // "lingon" must not fire inside an unrelated longer token.
        assertTrue(CoffeeTermGlossary.matches("prelingonation station").isEmpty())
        // But the whole word does fire.
        assertEquals(listOf("lingonberry"), CoffeeTermGlossary.matches("notes: lingon").map { it.english })
    }

    @Test
    fun `plain english text yields no glossary hits`() {
        assertTrue(CoffeeTermGlossary.matches("Ethiopia Washed, blueberry, dark chocolate").isEmpty())
    }

    @Test
    fun `blank text yields no hits`() {
        assertTrue(CoffeeTermGlossary.matches("   ").isEmpty())
    }

    @Test
    fun `longer phrase suppresses its sub-phrase entry`() {
        // "hořká čokoláda" (dark chocolate) must win over the bare "hořká" (bitter).
        val hits = CoffeeTermGlossary.matches("Chuťové tóny: hořká čokoláda")
        assertEquals(listOf("dark chocolate"), hits.map { it.english })
    }

    @Test
    fun `bare hořká still maps to bitter when it stands alone`() {
        val hits = CoffeeTermGlossary.matches("Profil: hořká")
        assertTrue(hits.map { it.english }.contains("bitter"))
        assertFalse(hits.map { it.english }.contains("dark chocolate"))
    }
}
