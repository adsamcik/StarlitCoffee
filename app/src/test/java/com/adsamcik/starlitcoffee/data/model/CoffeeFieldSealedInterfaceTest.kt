package com.adsamcik.starlitcoffee.data.model

import org.junit.Assert.*
import org.junit.Test

class CoffeeFieldSealedInterfaceTest {

    // --- CoffeeRoastLevel ---

    @Test
    fun `fromString maps known roast level by display name`() {
        val result = CoffeeRoastLevel.fromString("Light")
        assertTrue(result is CoffeeRoastLevel.Known)
        assertEquals(CoffeeRoastLevel.Known.LIGHT, result)
    }

    @Test
    fun `fromString maps roast level alias`() {
        val result = CoffeeRoastLevel.fromString("filter roast")
        assertTrue(result is CoffeeRoastLevel.Known)
        assertEquals(CoffeeRoastLevel.Known.FILTER, result)
    }

    @Test
    fun `fromString returns Other for unknown roast level`() {
        val result = CoffeeRoastLevel.fromString("City+")
        assertTrue(result is CoffeeRoastLevel.Other)
        assertEquals("City+", result.displayName)
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(CoffeeRoastLevel.Known.ESPRESSO, CoffeeRoastLevel.fromString("ESPRESSO"))
        assertEquals(CoffeeRoastLevel.Known.ESPRESSO, CoffeeRoastLevel.fromString("espresso"))
    }

    @Test
    fun `allSearchTerms includes display names and aliases`() {
        val terms = CoffeeRoastLevel.allSearchTerms
        assertTrue(terms.contains("Light"))
        assertTrue(terms.contains("filter roast"))
        assertTrue(terms.contains("espresso roast"))
    }

    // --- CoffeeProcessType ---

    @Test
    fun `fromString maps known process type`() {
        assertEquals(CoffeeProcessType.Known.WASHED, CoffeeProcessType.fromString("Washed"))
        assertEquals(CoffeeProcessType.Known.HONEY, CoffeeProcessType.fromString("honey"))
    }

    @Test
    fun `fromString maps process alias`() {
        val result = CoffeeProcessType.fromString("giling basah")
        assertEquals(CoffeeProcessType.Known.WET_HULLED, result)
    }

    @Test
    fun `unknown process becomes Other`() {
        val result = CoffeeProcessType.fromString("Experimental Lactic")
        assertTrue(result is CoffeeProcessType.Other)
        assertEquals("Experimental Lactic", result.displayName)
    }

    // --- CoffeeVariety ---

    @Test
    fun `fromString maps known variety`() {
        assertEquals(CoffeeVariety.Known.GEISHA, CoffeeVariety.fromString("Geisha"))
    }

    @Test
    fun `fromString maps variety alias Gesha to Geisha`() {
        assertEquals(CoffeeVariety.Known.GEISHA, CoffeeVariety.fromString("Gesha"))
    }

    @Test
    fun `fromString maps multi-word variety`() {
        assertEquals(CoffeeVariety.Known.PINK_BOURBON, CoffeeVariety.fromString("Pink Bourbon"))
    }

    @Test
    fun `unknown variety becomes Other`() {
        val result = CoffeeVariety.fromString("Wush Wush")
        assertTrue(result is CoffeeVariety.Other)
        assertEquals("Wush Wush", result.displayName)
    }

    // --- CoffeeOrigin ---

    @Test
    fun `fromString maps known country`() {
        assertEquals(CoffeeOrigin.Known.ETHIOPIA, CoffeeOrigin.fromString("Ethiopia"))
    }

    @Test
    fun `fromString maps country abbreviation`() {
        assertEquals(CoffeeOrigin.Known.ETHIOPIA, CoffeeOrigin.fromString("ETH"))
    }

    @Test
    fun `abbreviationMap contains expected entries`() {
        val map = CoffeeOrigin.abbreviationMap
        assertEquals("Ethiopia", map["ETH"])
        assertEquals("Colombia", map["COL"])
        assertEquals("Brazil", map["BRA"])
    }

    @Test
    fun `unknown country becomes Other`() {
        val result = CoffeeOrigin.fromString("Hawaii")
        assertTrue(result is CoffeeOrigin.Other)
    }

    // --- CoffeeRegion ---

    @Test
    fun `fromString maps known region`() {
        assertEquals(CoffeeRegion.Known.YIRGACHEFFE, CoffeeRegion.fromString("Yirgacheffe"))
    }

    @Test
    fun `fromString maps region alias Sidama to Sidamo`() {
        assertEquals(CoffeeRegion.Known.SIDAMO, CoffeeRegion.fromString("Sidama"))
    }

    @Test
    fun `unknown region becomes Other`() {
        val result = CoffeeRegion.fromString("Bonga Forest")
        assertTrue(result is CoffeeRegion.Other)
        assertEquals("Bonga Forest", result.displayName)
    }
}
