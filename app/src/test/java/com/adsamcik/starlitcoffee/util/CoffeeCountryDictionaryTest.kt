package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CoffeeCountryDictionaryTest {

    // --- GS1 prefix lookup ---

    @Test
    fun `byGs1Prefix returns Czech dictionary for prefix 859`() {
        val dict = CoffeeCountryDictionaries.byGs1Prefix(859)
        assertNotNull(dict)
        assertEquals("CZ", dict!!.countryCode)
        assertEquals(Locale("cs"), dict.locale)
    }

    @Test
    fun `byGs1Prefix returns German dictionary for prefix range 400 to 440`() {
        assertEquals("DE", CoffeeCountryDictionaries.byGs1Prefix(400)?.countryCode)
        assertEquals("DE", CoffeeCountryDictionaries.byGs1Prefix(420)?.countryCode)
        assertEquals("DE", CoffeeCountryDictionaries.byGs1Prefix(440)?.countryCode)
    }

    @Test
    fun `byGs1Prefix returns Italian dictionary for prefix range 800 to 839`() {
        assertEquals("IT", CoffeeCountryDictionaries.byGs1Prefix(800)?.countryCode)
        assertEquals("IT", CoffeeCountryDictionaries.byGs1Prefix(839)?.countryCode)
    }

    @Test
    fun `byGs1Prefix returns Polish dictionary for prefix 590`() {
        assertEquals("PL", CoffeeCountryDictionaries.byGs1Prefix(590)?.countryCode)
    }

    @Test
    fun `byGs1Prefix returns null for unknown prefix`() {
        assertNull(CoffeeCountryDictionaries.byGs1Prefix(12))
        assertNull(CoffeeCountryDictionaries.byGs1Prefix(789))
    }

    // --- Region name lookup ---

    @Test
    fun `byRegionName maps BarcodeInsights region names to dictionaries`() {
        assertEquals("CZ", CoffeeCountryDictionaries.byRegionName("Czech Republic")?.countryCode)
        assertEquals("DE", CoffeeCountryDictionaries.byRegionName("Germany")?.countryCode)
        assertEquals("IT", CoffeeCountryDictionaries.byRegionName("Italy")?.countryCode)
        assertEquals("PL", CoffeeCountryDictionaries.byRegionName("Poland")?.countryCode)
        assertEquals("FR", CoffeeCountryDictionaries.byRegionName("France")?.countryCode)
        assertEquals("ES", CoffeeCountryDictionaries.byRegionName("Spain")?.countryCode)
        assertEquals("DK", CoffeeCountryDictionaries.byRegionName("Denmark")?.countryCode)
        assertEquals("SE", CoffeeCountryDictionaries.byRegionName("Sweden")?.countryCode)
        assertEquals("NO", CoffeeCountryDictionaries.byRegionName("Norway")?.countryCode)
        assertEquals("FI", CoffeeCountryDictionaries.byRegionName("Finland")?.countryCode)
        assertEquals("NL", CoffeeCountryDictionaries.byRegionName("Netherlands")?.countryCode)
        assertEquals("PT", CoffeeCountryDictionaries.byRegionName("Portugal")?.countryCode)
        assertEquals("SK", CoffeeCountryDictionaries.byRegionName("Slovakia")?.countryCode)
        assertEquals("JP", CoffeeCountryDictionaries.byRegionName("Japan")?.countryCode)
        assertEquals("KR", CoffeeCountryDictionaries.byRegionName("South Korea")?.countryCode)
        assertNull(CoffeeCountryDictionaries.byRegionName("Australia"))
        assertNull(CoffeeCountryDictionaries.byRegionName(null))
    }

    // --- Locale inference from barcode ---

    @Test
    fun `localeFromBarcode infers Czech locale from 859 barcode`() {
        assertEquals(Locale("cs"), CoffeeCountryDictionaries.localeFromBarcode("8594206180014"))
    }

    @Test
    fun `localeFromBarcode infers German locale from 400 barcode`() {
        assertEquals(Locale.GERMAN, CoffeeCountryDictionaries.localeFromBarcode("4001234567890"))
    }

    @Test
    fun `localeFromBarcode returns null for unknown prefix`() {
        assertNull(CoffeeCountryDictionaries.localeFromBarcode("0123456789012"))
    }

    @Test
    fun `localeFromBarcode returns null for non 13 digit codes`() {
        assertNull(CoffeeCountryDictionaries.localeFromBarcode("859420618001"))
    }

    // --- Section labels ---

    @Test
    fun `Czech dictionary has expected section labels for key fields`() {
        val labels = CoffeeCountryDictionaries.CZECH.sectionLabels
        assertTrue(labels.origin.any { it.contains("Původ") })
        assertTrue(labels.roaster.any { it.contains("Pražírna") })
        assertTrue(labels.tastingNotes.any { it.contains("Chuť") })
        assertTrue(labels.expiryDate.any { it.contains("Spotřebujte") })
    }

    @Test
    fun `German dictionary has expected section labels`() {
        val labels = CoffeeCountryDictionaries.GERMAN.sectionLabels
        assertTrue(labels.origin.any { it.contains("Herkunft") })
        assertTrue(labels.roaster.any { it.contains("Rösterei") })
        assertTrue(labels.expiryDate.any { it.contains("MHD") })
    }

    // --- allSectionLabels with country hint ---

    @Test
    fun `allSectionLabels puts hinted country labels first`() {
        val labels = CoffeeCountryDictionaries.allSectionLabels(
            fieldSelector = CountrySectionLabels::origin,
            countryHint = CoffeeCountryDictionaries.CZECH,
        )
        assertTrue(labels.isNotEmpty())
        assertEquals("Původ", labels.first())
        assertTrue(labels.any { it == "Origin" })
        assertTrue(labels.any { it == "Herkunft" })
    }

    @Test
    fun `allSectionLabels without hint returns all labels with english first`() {
        val labels = CoffeeCountryDictionaries.allSectionLabels(
            fieldSelector = CountrySectionLabels::roaster,
        )
        assertTrue(labels.isNotEmpty())
        assertTrue(labels.contains("Pražírna"))
        assertTrue(labels.contains("Rösterei"))
        assertTrue(labels.contains("Roastery"))
    }

    @Test
    fun `allSectionLabels deduplicates across dictionaries`() {
        val labels = CoffeeCountryDictionaries.allSectionLabels(
            fieldSelector = CountrySectionLabels::farm,
        )
        val fincaCount = labels.count { it == "Finca" }
        assertEquals("Finca should appear only once despite being in multiple dictionaries", 1, fincaCount)
    }
}
