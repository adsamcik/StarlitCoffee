package com.adsamcik.starlitcoffee.util

import org.junit.Test
import org.junit.Assert.*

class OcrFieldExtractorTest {

    // --- Beansmith's Ethiopia Gedeb (real bag sample) ---

    private val frontText = """
        BEANSMITH'S
        COFFEE ROASTERY
        ETHIOPIA GEDEB
        HEIRLOOM - WASHED
        LESNÍ JAHODA, ŠVESTKA, YUZU, ZELENÝ ČAJ
        FILTER
    """.trimIndent()

    private val backText = """
        LOOKING FOR SPECIALTY BEAUTY
        Jsme malé součást velkého příběhu výběrové kávy
        WWW.BEANSMITHS.COM
        DATUM PRAŽENÍ 09.12.25
        PR-15976
        ETH GEDEB
        SPOTŘEBUJTE DO 07.06.2026
        +FILTER 250g
        8 594206 183060
        100% RECYCLABLE
    """.trimIndent()

    @Test
    fun `front of bag extracts origin Ethiopia`() {
        val result = OcrFieldExtractor.extractFields(frontText)
        // Regex matches case-insensitively; returns original case from text
        assertTrue("Should find Ethiopia", result.origin!!.equals("Ethiopia", ignoreCase = true))
    }

    @Test
    fun `front of bag extracts variety Heirloom`() {
        val result = OcrFieldExtractor.extractFields(frontText)
        assertNotNull("Variety should be extracted", result.variety)
        assertTrue("Should contain Heirloom", result.variety!!.contains("Heirloom", ignoreCase = true))
    }

    @Test
    fun `front of bag extracts process washed`() {
        val result = OcrFieldExtractor.extractFields(frontText)
        assertNotNull("Process should be extracted", result.processType)
        assertTrue("Should be washed", result.processType!!.contains("washed", ignoreCase = true))
    }

    @Test
    fun `front of bag attempts tasting notes extraction`() {
        val result = OcrFieldExtractor.extractFields(frontText)
        // Czech tasting notes without a label — tests comma-separated line regex
        assertNotNull("Tasting notes should be extracted from comma-separated line", result.tastingNotes)
    }

    @Test
    fun `back of bag extracts roast date`() {
        val result = OcrFieldExtractor.extractFields(backText)
        assertNotNull("Roast date should be extracted", result.roastDate)
    }

    @Test
    fun `merged extraction captures available fields and reports gaps`() {
        val front = OcrFieldExtractor.extractFields(frontText)
        val back = OcrFieldExtractor.extractFields(backText)

        val merged = OcrFieldExtractor.OcrExtractionResult(
            name = front.name ?: back.name,
            roaster = front.roaster ?: back.roaster,
            origin = front.origin ?: back.origin,
            region = front.region ?: back.region,
            farm = front.farm ?: back.farm,
            variety = front.variety ?: back.variety,
            processType = front.processType ?: back.processType,
            altitude = front.altitude ?: back.altitude,
            tastingNotes = front.tastingNotes ?: back.tastingNotes,
            roastLevel = front.roastLevel ?: back.roastLevel,
            roastDate = front.roastDate ?: back.roastDate,
            expiryDate = front.expiryDate ?: back.expiryDate,
            weight = front.weight ?: back.weight,
        )

        // Fields that regex CAN extract
        assertTrue("Origin should be Ethiopia", merged.origin!!.equals("Ethiopia", ignoreCase = true))
        assertNotNull("Variety should be extracted", merged.variety)
        assertNotNull("Process should be extracted", merged.processType)
        assertNotNull("Roast date should be extracted", merged.roastDate)
        assertNotNull("Region should be Gedeb", merged.region)
        assertTrue("Region should be Gedeb", merged.region!!.contains("Gedeb", ignoreCase = true))
        assertNotNull("Roast level should be filter", merged.roastLevel)
        assertNotNull("Weight should be 250g", merged.weight)
        assertTrue("Weight should be 250g", merged.weight!!.contains("250"))
        assertNotNull("Tasting notes should be extracted", merged.tastingNotes)

        // Roaster extraction via roastery label regex
        assertNotNull("Roaster should be extracted via roastery keyword", merged.roaster)

        // Barcode extraction from text
        val barcodeFromBack = OcrFieldExtractor.extractBarcodeFromText(backText)
        assertNotNull("Should extract barcode from OCR text", barcodeFromBack)
        assertEquals("8594206183060", barcodeFromBack)
    }

    @Test
    fun `text-only extractFields derives name from origin and region`() {
        val result = OcrFieldExtractor.extractFields(frontText)
        assertNotNull("Name should be derived from origin+region", result.name)
        assertTrue(
            "Name should contain Ethiopia",
            result.name!!.contains("Ethiopia", ignoreCase = true),
        )
        assertTrue(
            "Name should contain Gedeb",
            result.name!!.contains("Gedeb", ignoreCase = true),
        )
    }

    // --- Block-based (spatial) extraction ---

    @Test
    fun `extractFieldsFromBlocks identifies roaster from largest block`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("SQUARE MILE", heightPx = 80, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("LA ESPERANZA", heightPx = 60, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Colombia, Huila", heightPx = 30, topPx = 250),
            OcrFieldExtractor.OcrTextBlock("Caturra", heightPx = 25, topPx = 310),
            OcrFieldExtractor.OcrTextBlock("Washed", heightPx = 25, topPx = 350),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(blocks)
        assertEquals("SQUARE MILE", result.roaster)
    }

    @Test
    fun `extractFieldsFromBlocks identifies name from second largest block`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("SQUARE MILE", heightPx = 80, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("LA ESPERANZA", heightPx = 60, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Colombia, Huila", heightPx = 30, topPx = 250),
            OcrFieldExtractor.OcrTextBlock("Caturra", heightPx = 25, topPx = 310),
            OcrFieldExtractor.OcrTextBlock("Washed", heightPx = 25, topPx = 350),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(blocks)
        assertEquals("LA ESPERANZA", result.name)
    }

    @Test
    fun `extractFieldsFromBlocks consumes known-field blocks`() {
        // All blocks are known fields — no name or roaster candidates
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("Ethiopia", heightPx = 40, topPx = 100),
            OcrFieldExtractor.OcrTextBlock("Yirgacheffe", heightPx = 35, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Washed", heightPx = 25, topPx = 200),
            OcrFieldExtractor.OcrTextBlock("Heirloom", heightPx = 25, topPx = 250),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(blocks)
        // Name falls back to origin+region
        assertNotNull("Name should fallback to origin+region", result.name)
        assertTrue(result.name!!.contains("Ethiopia", ignoreCase = true))
        assertTrue(result.name!!.contains("Yirgacheffe", ignoreCase = true))
    }

    @Test
    fun `extractFieldsFromBlocks uses keyword roaster when present`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("BEANSMITH'S\nCOFFEE ROASTERY", heightPx = 80, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("ETHIOPIA GEDEB", heightPx = 50, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("HEIRLOOM - WASHED", heightPx = 30, topPx = 250),
            OcrFieldExtractor.OcrTextBlock("FILTER", heightPx = 25, topPx = 310),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(blocks)
        assertNotNull("Roaster should be detected via keyword", result.roaster)
        assertTrue(
            "Roaster should contain Beansmith",
            result.roaster!!.contains("BEANSMITH", ignoreCase = true),
        )
    }

    @Test
    fun `extractFieldsFromBlocks still extracts all known fields`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("TIM WENDELBOE", heightPx = 80, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("FINCA TAMANA", heightPx = 60, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Colombia", heightPx = 30, topPx = 250),
            OcrFieldExtractor.OcrTextBlock("Caturra", heightPx = 25, topPx = 310),
            OcrFieldExtractor.OcrTextBlock("Washed", heightPx = 25, topPx = 350),
            OcrFieldExtractor.OcrTextBlock("1800 masl", heightPx = 20, topPx = 400),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(blocks)
        assertEquals("TIM WENDELBOE", result.roaster)
        assertEquals("FINCA TAMANA", result.name)
        assertTrue(result.origin!!.equals("Colombia", ignoreCase = true))
        assertTrue(result.variety!!.contains("Caturra", ignoreCase = true))
        assertTrue(result.processType!!.contains("Washed", ignoreCase = true))
        assertNotNull("Altitude should be extracted", result.altitude)
    }

    @Test
    fun `extractFieldsFromBlocks filters small fine-print blocks`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("PROUD MARY", heightPx = 100, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("GOLDEN CHILD", heightPx = 70, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Ethiopia Guji", heightPx = 40, topPx = 250),
            OcrFieldExtractor.OcrTextBlock("100% Arabica", heightPx = 15, topPx = 500),
            OcrFieldExtractor.OcrTextBlock("www.proudmarycoffee.com.au", heightPx = 12, topPx = 550),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(blocks)
        assertEquals("PROUD MARY", result.roaster)
        assertEquals("GOLDEN CHILD", result.name)
    }

    // --- isBlockConsumedByKnownFields ---

    @Test
    fun `known-field block Ethiopia Gedeb is consumed`() {
        assertTrue(OcrFieldExtractor.isBlockConsumedByKnownFields("Ethiopia Gedeb"))
    }

    @Test
    fun `known-field block Heirloom - Washed is consumed`() {
        assertTrue(OcrFieldExtractor.isBlockConsumedByKnownFields("HEIRLOOM - WASHED"))
    }

    @Test
    fun `known-field block FILTER is consumed`() {
        assertTrue(OcrFieldExtractor.isBlockConsumedByKnownFields("FILTER"))
    }

    @Test
    fun `known-field block 250g is consumed`() {
        assertTrue(OcrFieldExtractor.isBlockConsumedByKnownFields("250g"))
    }

    @Test
    fun `unknown block SQUARE MILE is not consumed`() {
        assertFalse(OcrFieldExtractor.isBlockConsumedByKnownFields("SQUARE MILE"))
    }

    @Test
    fun `block with only noise words is consumed`() {
        assertTrue(OcrFieldExtractor.isBlockConsumedByKnownFields("Single Origin Coffee"))
    }

    // --- Multiple roast levels ---

    @Test
    fun `extracts multiple roast levels when both filter and espresso present`() {
        val text = """
            Ethiopia Gedeb
            Heirloom
            Washed
            Filter / Espresso
        """.trimIndent()
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Roast level should be extracted", result.roastLevel)
        assertTrue(
            "Should contain filter",
            result.roastLevel!!.contains("filter", ignoreCase = true),
        )
        assertTrue(
            "Should contain espresso",
            result.roastLevel!!.contains("espresso", ignoreCase = true),
        )
    }

    @Test
    fun `does not duplicate filter when filter roast and filter both appear`() {
        val text = """
            Filter Roast
            +FILTER 250g
        """.trimIndent()
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Roast level should be extracted", result.roastLevel)
        assertTrue(
            "Should contain filter roast",
            result.roastLevel!!.contains("filter roast", ignoreCase = true),
        )
        // Should NOT have a redundant standalone "filter"
        val parts = result.roastLevel!!.split(",").map { it.trim().lowercase() }
        assertFalse(
            "Should not have redundant standalone 'filter'",
            parts.contains("filter"),
        )
    }

    @Test
    fun `single roast level still works`() {
        val text = "Ethiopia Yirgacheffe\nLight"
        val result = OcrFieldExtractor.extractFields(text)
        assertEquals("Light", result.roastLevel)
    }

    // --- Weight improvements ---

    @Test
    fun `extracts weight in kilograms`() {
        val result = OcrFieldExtractor.extractFields("Net weight: 1kg")
        assertEquals("1kg", result.weight)
    }

    @Test
    fun `extracts weight in pounds`() {
        val result = OcrFieldExtractor.extractFields("12oz / 1lb bag")
        assertNotNull(result.weight)
    }

    @Test
    fun `extracts decimal kilogram weight`() {
        val result = OcrFieldExtractor.extractFields("2.5kg premium bag")
        assertEquals("2.5kg", result.weight)
    }

    // --- Tasting notes delimiters ---

    @Test
    fun `extracts tasting notes with middot delimiter`() {
        val result = OcrFieldExtractor.extractFields("Chocolate · Caramel · Hazelnut")
        assertNotNull("Should extract middot-separated notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("Chocolate", ignoreCase = true))
        assertTrue(result.tastingNotes!!.contains("Caramel", ignoreCase = true))
    }

    @Test
    fun `extracts tasting notes with bullet delimiter`() {
        val result = OcrFieldExtractor.extractFields("Blueberry • Jasmine • Honey")
        assertNotNull("Should extract bullet-separated notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("Blueberry", ignoreCase = true))
    }

    @Test
    fun `extracts tasting notes with pipe delimiter`() {
        val result = OcrFieldExtractor.extractFields("Stone fruit | Dark chocolate | Citrus")
        assertNotNull("Should extract pipe-separated notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("chocolate", ignoreCase = true))
    }

    // --- Process type synonyms ---

    @Test
    fun `extracts sun-dried as Natural`() {
        val result = OcrFieldExtractor.extractFields("Ethiopia Yirgacheffe\nSun-dried on raised beds")
        assertNotNull("Should extract sun-dried process", result.processType)
        assertTrue(result.processType!!.contains("sun-dried", ignoreCase = true))
    }

    @Test
    fun `extracts sundried as Natural`() {
        val result = OcrFieldExtractor.extractFields("Kenya AA\nSundried")
        assertNotNull(result.processType)
    }

    // --- Farm extraction ---

    @Test
    fun `extracts farm from labeled text`() {
        val result = OcrFieldExtractor.extractFields("Farm: La Esperanza\nColombia Huila\nWashed")
        assertNotNull("Should extract farm", result.farm)
        assertTrue(result.farm!!.contains("Esperanza", ignoreCase = true))
    }

    @Test
    fun `extracts finca from labeled text`() {
        val result = OcrFieldExtractor.extractFields("Finca: El Paraiso\nHonduras\nNatural")
        assertNotNull("Should extract finca", result.farm)
        assertTrue(result.farm!!.contains("Paraiso", ignoreCase = true))
    }

    @Test
    fun `extracts producer from labeled text`() {
        val result = OcrFieldExtractor.extractFields("Producer: Juan Rodriguez\nColombia")
        assertNotNull("Should extract producer", result.farm)
    }

    // --- Origin abbreviations ---

    @Test
    fun `extracts PNG abbreviation as Papua New Guinea`() {
        val result = OcrFieldExtractor.extractFields("PNG Sigri Estate\nWashed")
        assertNotNull("Should extract PNG as Papua New Guinea", result.origin)
        assertEquals("Papua New Guinea", result.origin)
    }

    @Test
    fun `extracts SAL abbreviation as El Salvador`() {
        val result = OcrFieldExtractor.extractFields("SAL Pacamara\nHoney process")
        assertNotNull(result.origin)
        assertEquals("El Salvador", result.origin)
    }

    // --- Roast level aliases ---

    @Test
    fun `extracts cinnamon roast level`() {
        val result = OcrFieldExtractor.extractFields("Ethiopia\nCinnamon roast\nHeirloom")
        assertNotNull(result.roastLevel)
        assertTrue(result.roastLevel!!.contains("cinnamon", ignoreCase = true))
    }

    @Test
    fun `extracts city roast level`() {
        val result = OcrFieldExtractor.extractFields("Colombia\nCity roast")
        assertNotNull(result.roastLevel)
    }

    // --- Farm block consumed ---

    @Test
    fun `farm label block is consumed by known fields`() {
        assertTrue(OcrFieldExtractor.isBlockConsumedByKnownFields("Farm: La Esperanza"))
    }
}
