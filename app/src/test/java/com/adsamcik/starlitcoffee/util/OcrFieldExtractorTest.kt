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
        println("Tasting notes from front: ${result.tastingNotes ?: "NOT EXTRACTED"}")
    }

    @Test
    fun `back of bag extracts roast date`() {
        val result = OcrFieldExtractor.extractFields(backText)
        println("Roast date from back: ${result.roastDate ?: "NOT EXTRACTED"}")
        assertNotNull("Roast date should be extracted", result.roastDate)
    }

    @Test
    fun `merged extraction captures available fields and reports gaps`() {
        val front = OcrFieldExtractor.extractFields(frontText)
        val back = OcrFieldExtractor.extractFields(backText)

        val merged = OcrFieldExtractor.OcrExtractionResult(
            roaster = front.roaster ?: back.roaster,
            origin = front.origin ?: back.origin,
            region = front.region ?: back.region,
            variety = front.variety ?: back.variety,
            processType = front.processType ?: back.processType,
            altitude = front.altitude ?: back.altitude,
            tastingNotes = front.tastingNotes ?: back.tastingNotes,
            roastLevel = front.roastLevel ?: back.roastLevel,
            roastDate = front.roastDate ?: back.roastDate,
            weight = front.weight ?: back.weight,
        )

        println("=== MERGED EXTRACTION RESULTS ===")
        println("Roaster:       ${merged.roaster ?: "MISSED"}")
        println("Origin:        ${merged.origin ?: "MISSED"}")
        println("Region:        ${merged.region ?: "MISSED"}")
        println("Variety:       ${merged.variety ?: "MISSED"}")
        println("Process:       ${merged.processType ?: "MISSED"}")
        println("Altitude:      ${merged.altitude ?: "N/A"}")
        println("Tasting notes: ${merged.tastingNotes ?: "MISSED"}")
        println("Roast level:   ${merged.roastLevel ?: "MISSED"}")
        println("Roast date:    ${merged.roastDate ?: "MISSED"}")
        println("Weight:        ${merged.weight ?: "MISSED"}")

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
}
