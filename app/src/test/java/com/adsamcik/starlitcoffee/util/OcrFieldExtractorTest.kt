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
    fun `extractFields recognizes multilingual origin aliases`() {
        val result = OcrFieldExtractor.extractFields(
            """
            Etiopie Guji
            Gesha
            Lavado
            """.trimIndent(),
        )

        assertNotNull("Origin should be extracted from localized alias", result.origin)
        assertTrue(result.origin!!.equals("Etiopie", ignoreCase = true))
        assertNotNull("Region should still be extracted", result.region)
        assertTrue(result.region!!.contains("Guji", ignoreCase = true))
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

    // --- Fuzzy field extraction ---

    @Test
    fun `extractFields falls back to fuzzy origin matching`() {
        val result = OcrFieldExtractor.extractFields("Ethiopa Gedeb\nHeirloom")
        assertNotNull("Should fuzzy match Ethiopia", result.origin)
        assertEquals("Ethiopia", result.origin)
    }

    @Test
    fun `extractFields falls back to fuzzy process matching`() {
        val result = OcrFieldExtractor.extractFields("Colombia Huila\nWahsed")
        assertNotNull("Should fuzzy match Washed", result.processType)
        assertEquals("Washed", result.processType)
    }

    @Test
    fun `extractFields marks regex and abbreviation matches as high confidence`() {
        val result = OcrFieldExtractor.extractFields("PNG Sigri Estate\nWashed\n250g")

        assertEquals(BagFieldConfidence.HIGH, result.fieldConfidence["origin"])
        assertEquals(BagFieldConfidence.HIGH, result.fieldConfidence["processType"])
        assertEquals(BagFieldConfidence.HIGH, result.fieldConfidence["weight"])
    }

    @Test
    fun `extractFields marks history matches as high confidence`() {
        val result = OcrFieldExtractor.extractFields(
            rawText = "Producer: Juan Rodriguez\nLimited release",
            knownFields = KnownFieldValues(
                farms = listOf("Juan Rodriguez"),
                names = listOf("Limited release"),
            ),
        )

        assertEquals(BagFieldConfidence.HIGH, result.fieldConfidence["farm"])
        assertEquals(BagFieldConfidence.HIGH, result.fieldConfidence["name"])
    }

    @Test
    fun `extractFields marks fuzzy matches medium and derived name low confidence`() {
        val result = OcrFieldExtractor.extractFields("Ethiopa Gedeb\nWahsed")

        assertEquals(BagFieldConfidence.MEDIUM, result.fieldConfidence["origin"])
        assertEquals(BagFieldConfidence.MEDIUM, result.fieldConfidence["processType"])
        assertEquals(BagFieldConfidence.LOW, result.fieldConfidence["name"])
    }

    // --- Farm block consumed ---

    @Test
    fun `farm label block is consumed by known fields`() {
        assertTrue(OcrFieldExtractor.isBlockConsumedByKnownFields("Farm: La Esperanza"))
    }

    // --- Multi-signal scoring ---

    @Test
    fun `known roaster from DB gets matched even without keyword`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("DOUBLESHOT", heightPx = 80, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("ETHIOPIA SIDAMO", heightPx = 60, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Washed", heightPx = 25, topPx = 250),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(
            blocks,
            knownRoasters = listOf("Doubleshot"),
        )
        assertEquals("DOUBLESHOT", result.roaster)
        assertNotEquals("DOUBLESHOT", result.name)
    }

    @Test
    fun `known name from DB gets matched`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("TIM WENDELBOE", heightPx = 80, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("FINCA TAMANA", heightPx = 60, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Colombia", heightPx = 30, topPx = 250),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(
            blocks,
            knownNames = listOf("Finca Tamana"),
        )
        assertNotNull(result.name)
        assertTrue(result.name!!.contains("TAMANA", ignoreCase = true))
    }

    @Test
    fun `all caps text scores higher for brand detection`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("PROUD MARY", heightPx = 70, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("Golden Child Blend", heightPx = 70, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Ethiopia", heightPx = 30, topPx = 250),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(blocks)
        assertEquals("PROUD MARY", result.roaster)
    }

    @Test
    fun `block scoring uses fuzzy roaster match when OCR is slightly wrong`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("DOUBLESH0T", heightPx = 80, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("ETHIOPIA SIDAMO", heightPx = 60, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Washed", heightPx = 25, topPx = 250),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(
            blocks,
            knownRoasters = listOf("Doubleshot"),
        )

        assertEquals("DOUBLESH0T", result.roaster)
    }

    @Test
    fun `block scoring uses fuzzy name match when OCR is slightly wrong`() {
        val blocks = listOf(
            OcrFieldExtractor.OcrTextBlock("TIM WENDELBOE", heightPx = 80, topPx = 50),
            OcrFieldExtractor.OcrTextBlock("FINCA TAMNAA", heightPx = 60, topPx = 150),
            OcrFieldExtractor.OcrTextBlock("Colombia", heightPx = 30, topPx = 250),
        )
        val result = OcrFieldExtractor.extractFieldsFromBlocks(
            blocks,
            knownNames = listOf("Finca Tamana"),
        )

        assertEquals("FINCA TAMNAA", result.name)
    }

    // --- Czech language ---

    @Test
    fun `extracts Czech praný as Washed`() {
        val result = OcrFieldExtractor.extractFields("Etiopie Yirgacheffe\nPraný")
        assertNotNull(result.processType)
        assertTrue(result.processType!!.contains("praný", ignoreCase = true))
    }

    @Test
    fun `extracts Czech světlý as Light roast`() {
        val result = OcrFieldExtractor.extractFields("Kolumbie\nSvětlý")
        assertNotNull(result.roastLevel)
        assertTrue(result.roastLevel!!.contains("světlý", ignoreCase = true))
    }

    @Test
    fun `extracts Czech tasting notes label`() {
        val text = "Chuťové poznámky: borůvka, jasmín, med"
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Should extract Czech-labeled tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("borůvka"))
    }

    @Test
    fun `extracts Czech roast date label`() {
        val text = "Datum pražení: 15.01.2026"
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Should extract Czech roast date", result.roastDate)
    }

    // --- Danish language ---

    @Test
    fun `extracts Danish vasket as Washed`() {
        val result = OcrFieldExtractor.extractFields("Kenya\nVasket")
        assertNotNull(result.processType)
    }

    @Test
    fun `extracts Danish lys ristet as Light roast`() {
        val result = OcrFieldExtractor.extractFields("Ethiopia\nLys ristet")
        assertNotNull(result.roastLevel)
    }

    @Test
    fun `extracts Danish smagsnoter label`() {
        val text = "Smagsnoter: bær, chokolade, blomst"
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Should extract Danish tasting notes", result.tastingNotes)
    }

    @Test
    fun `extracts Danish ristet date label`() {
        val text = "Ristet: 15.01.2026"
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Should extract Danish roast date", result.roastDate)
    }

    @Test
    fun `extracts Danish bedst før expiry label`() {
        val text = "Bedst før: 15.06.2026"
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Should extract Danish expiry date", result.expiryDate)
    }

    // --- Additional multilingual edge cases ---

    @Test
    fun `extracts German origin with Italian process and roast aliases`() {
        val result = OcrFieldExtractor.extractFields(
            """
            Äthiopien Guji
            Lavato
            Chiaro
            """.trimIndent(),
        )

        assertEquals("Äthiopien", result.origin)
        assertNotNull(result.processType)
        assertTrue(result.processType!!.contains("Lavato", ignoreCase = true))
        assertNotNull(result.roastLevel)
        assertTrue(result.roastLevel!!.contains("Chiaro", ignoreCase = true))
    }

    @Test
    fun `extracts Spanish notas de cata label`() {
        val result = OcrFieldExtractor.extractFields(
            "Notas de cata: fresa silvestre, yuzu, té verde",
        )

        assertNotNull("Should extract Spanish tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("fresa silvestre", ignoreCase = true))
    }

    @Test
    fun `extracts Italian note di degustazione label`() {
        val result = OcrFieldExtractor.extractFields(
            "Note di degustazione: pesca, miele, bergamotto",
        )

        assertNotNull("Should extract Italian tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("bergamotto", ignoreCase = true))
    }

    @Test
    fun `extracts German gerostet roast date label`() {
        val result = OcrFieldExtractor.extractFields("Geröstet: 15.01.2026")

        assertEquals("15.01.2026", result.roastDate)
    }

    @Test
    fun `extracts French a consommer avant expiry label`() {
        val result = OcrFieldExtractor.extractFields("À consommer avant: 15.06.2026")

        assertEquals("15.06.2026", result.expiryDate)
    }

    // --- Country hint wiring ---

    @Test
    fun `country hint extracts Czech section labels that match dictionary keywords`() {
        val text = "Původ: Etiopie\nZpracování: Praný\nStupeň pražení: Světlé"
        val result = OcrFieldExtractor.extractFields(
            text,
            countryHint = CoffeeCountryDictionaries.CZECH,
        )
        assertEquals("Etiopie", result.origin)
    }

    @Test
    fun `country hint extracts German tasting notes label from dictionary`() {
        val text = "Geschmacksnoten: Schokolade, Haselnuss, Karamell"
        val result = OcrFieldExtractor.extractFields(
            text,
            countryHint = CoffeeCountryDictionaries.GERMAN,
        )
        assertNotNull("Should extract German-labeled tasting notes with hint", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("Schokolade"))
    }

    @Test
    fun `country hint extracts Polish tasting notes label`() {
        val text = "Nuty smakowe: czekolada, karmel, orzechy"
        val result = OcrFieldExtractor.extractFields(
            text,
            countryHint = CoffeeCountryDictionaries.POLISH,
        )
        assertNotNull("Should extract Polish-labeled tasting notes with hint", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("czekolada"))
    }

    @Test
    fun `extraction works without country hint using default labels`() {
        val text = "Tasting notes: blueberry, jasmine, honey"
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Should extract English tasting notes without hint", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("blueberry"))
    }

    @Test
    fun `country hint extracts Czech farm label from dictionary`() {
        val text = "Farma: El Paraiso\nOdrůda: Caturra"
        val result = OcrFieldExtractor.extractFields(
            text,
            countryHint = CoffeeCountryDictionaries.CZECH,
        )
        assertEquals("El Paraiso", result.farm)
    }

    // --- Nordbeans Guatemala Severka (real Czech bag) ---

    private val nordbeansfront = """
        COFFEE NORDBEANS
        Severka
        GUATEMALA
        MANDLE - MLÉČNÁ ČOKOLÁDA - JABLKA
        Ideální káva pro vaši domácí přípravu
    """.trimIndent()

    private val nordbeansBack = """
        Káva se srdcem v horách
        pražená zrnková káva - hmotnost
        250 g
        složení
        100% káva
        uchovávejte nejlépe v původním obalu při pokojové teplotě do
        25°C
        upražili jsme:
        13.01.2026
        minimální trvanlivost:
        15.07.2026
        Severka / Rohlík
        8 594200 941437
        Pražená zrnková káva - 250g
        výrobce: NORDBEANS s. r. o.
        adresa: 1. máje 868/11, 460 07 Liberec
        NORDBEANS
        nordbeans.cz
    """.trimIndent()

    @Test
    fun `Nordbeans front extracts origin Guatemala`() {
        val result = OcrFieldExtractor.extractFields(nordbeansfront)
        assertNotNull("Origin should be Guatemala", result.origin)
        assertTrue(result.origin!!.contains("Guatemala", ignoreCase = true))
    }

    @Test
    fun `Nordbeans front extracts dash-delimited tasting notes`() {
        val result = OcrFieldExtractor.extractFields(nordbeansfront)
        assertNotNull("Should extract dash-separated tasting notes", result.tastingNotes)
        assertTrue(
            "Should contain MANDLE",
            result.tastingNotes!!.contains("MANDLE", ignoreCase = true),
        )
        assertTrue(
            "Should contain JABLKA",
            result.tastingNotes!!.contains("JABLKA", ignoreCase = true),
        )
    }

    @Test
    fun `Nordbeans back extracts roast date from uprazili jsme label`() {
        val result = OcrFieldExtractor.extractFields(nordbeansBack)
        assertNotNull("Should extract roast date from 'upražili jsme:' label", result.roastDate)
        assertEquals("13.01.2026", result.roastDate)
    }

    @Test
    fun `Nordbeans back extracts expiry date from minimalni trvanlivost label`() {
        val result = OcrFieldExtractor.extractFields(nordbeansBack)
        assertNotNull("Should extract expiry date from 'minimální trvanlivost:'", result.expiryDate)
        assertEquals("15.07.2026", result.expiryDate)
    }

    @Test
    fun `Nordbeans back extracts weight 250g`() {
        val result = OcrFieldExtractor.extractFields(nordbeansBack)
        assertNotNull("Should extract weight", result.weight)
        assertTrue(result.weight!!.contains("250"))
    }

    @Test
    fun `Nordbeans back extracts barcode from OCR text`() {
        val barcode = OcrFieldExtractor.extractBarcodeFromText(nordbeansBack)
        assertNotNull("Should extract barcode", barcode)
        assertEquals("8594200941437", barcode)
    }

    @Test
    fun `Nordbeans merged extraction captures both dates and tasting notes`() {
        val front = OcrFieldExtractor.extractFields(nordbeansfront)
        val back = OcrFieldExtractor.extractFields(nordbeansBack)

        val merged = OcrFieldExtractor.OcrExtractionResult(
            name = front.name ?: back.name,
            roaster = front.roaster ?: back.roaster,
            origin = front.origin ?: back.origin,
            region = front.region ?: back.region,
            variety = front.variety ?: back.variety,
            processType = front.processType ?: back.processType,
            altitude = front.altitude ?: back.altitude,
            tastingNotes = front.tastingNotes ?: back.tastingNotes,
            roastLevel = front.roastLevel ?: back.roastLevel,
            roastDate = front.roastDate ?: back.roastDate,
            expiryDate = front.expiryDate ?: back.expiryDate,
            weight = front.weight ?: back.weight,
        )

        assertNotNull("Origin should be Guatemala", merged.origin)
        assertNotNull("Tasting notes should be extracted", merged.tastingNotes)
        assertNotNull("Roast date should be 13.01.2026", merged.roastDate)
        assertNotNull("Expiry date should be 15.07.2026", merged.expiryDate)
        assertNotNull("Weight should be extracted", merged.weight)
    }

    // --- Merrybeans Colombia Tumbaga Decaf (real Czech bag) ---

    private val merrybeansBack = """
        Původ: Kolumbie Tumbaga decaf
        Metoda zpracování: promytá (sugarcane decaf)
        Chuťový profil: smetana, vanilka, mandarinky
        Odrůda kávovníku: smíšené odrůdy
        Nadmořská výška: 1400-2100 m n.m.
        Pražení: filtr espresso
        Pražená zrnková káva od výrobce Merrybeans s.r.o.
        Skladujte v suchu při pokojové teplotě.
        Datum pražení: 12.12.2025
        Minimální trvanlivost: 12.6.2026
        Hmotnost: 250g
    """.trimIndent()

    @Test
    fun `Merrybeans extracts decaf marker from Czech bag`() {
        val result = OcrFieldExtractor.extractFields(merrybeansBack)

        assertTrue(result.isDecaf == true)
        assertEquals(BagFieldConfidence.HIGH, result.fieldConfidence["isDecaf"])
    }

    @Test
    fun `Merrybeans extracts tasting notes from Chutovy profil label`() {
        val result = OcrFieldExtractor.extractFields(merrybeansBack)
        assertNotNull("Should extract tasting notes from 'Chuťový profil:'", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("smetana", ignoreCase = true))
        assertTrue(result.tastingNotes!!.contains("vanilka", ignoreCase = true))
    }

    @Test
    fun `Merrybeans extracts roast date from Datum prazeni label`() {
        val result = OcrFieldExtractor.extractFields(merrybeansBack)
        assertNotNull("Should extract roast date", result.roastDate)
        assertEquals("12.12.2025", result.roastDate)
    }

    @Test
    fun `Merrybeans extracts expiry from Minimalni trvanlivost label`() {
        val result = OcrFieldExtractor.extractFields(merrybeansBack)
        assertNotNull("Should extract expiry date from 'Minimální trvanlivost:'", result.expiryDate)
    }

    @Test
    fun `Merrybeans extracts altitude with Czech m n m unit`() {
        val result = OcrFieldExtractor.extractFields(merrybeansBack)
        assertNotNull("Should extract altitude with Czech unit", result.altitude)
        assertTrue(
            "Altitude should contain 1400",
            result.altitude!!.contains("1400"),
        )
    }

    @Test
    fun `Merrybeans extracts weight 250g`() {
        val result = OcrFieldExtractor.extractFields(merrybeansBack)
        assertNotNull("Should extract weight", result.weight)
        assertTrue(result.weight!!.contains("250"))
    }

    @Test
    fun `Merrybeans extracts origin with Czech country hint`() {
        val result = OcrFieldExtractor.extractFields(
            merrybeansBack,
            countryHint = CoffeeCountryDictionaries.CZECH,
        )
        assertNotNull("Should extract origin from 'Původ:' label", result.origin)
    }

    @Test
    fun `Merrybeans extracts variety from Odruda kavovniku label with Czech hint`() {
        val result = OcrFieldExtractor.extractFields(
            merrybeansBack,
            countryHint = CoffeeCountryDictionaries.CZECH,
        )
        // The variety section label "Odrůda kávovníku" is in the Czech dictionary
        // The full text after the label is "smíšené odrůdy" — extracted as raw value
        // But "Odrůda" by itself is also in the variety regex via search terms
        // What matters: the variety field or at least some extraction happens
        assertNotNull("Should extract variety info from Czech variety label", result.variety)
    }

    // --- Dash delimiter edge cases ---

    @Test
    fun `dash delimiter does not false-positive on two-item lines`() {
        val text = "HEIRLOOM - WASHED"
        val result = OcrFieldExtractor.extractFields(text)
        // Two-item dash line should NOT be treated as tasting notes
        // (it should be matched as variety + process)
        assertNotNull("Should extract variety", result.variety)
        assertNotNull("Should extract process", result.processType)
    }

    @Test
    fun `dash delimiter extracts three or more items as tasting notes`() {
        val text = "Jahoda - Čokoláda - Ořechy"
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Should extract dash-separated tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("Jahoda", ignoreCase = true))
        assertTrue(result.tastingNotes!!.contains("Čokoláda", ignoreCase = true))
        assertTrue(result.tastingNotes!!.contains("Ořechy", ignoreCase = true))
    }

    @Test
    fun `en-dash delimiter extracts tasting notes`() {
        val text = "Mandel – Milchschokolade – Äpfel"
        val result = OcrFieldExtractor.extractFields(text)
        assertNotNull("Should extract en-dash-separated tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("Mandel", ignoreCase = true))
    }

    // --- French language ---

    @Test
    fun `extracts French notes de degustation label`() {
        val result = OcrFieldExtractor.extractFields(
            "Notes de dégustation: cerise, chocolat noir, jasmin",
        )
        assertNotNull("Should extract French tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("cerise", ignoreCase = true))
    }

    @Test
    fun `extracts French torrefie roast date`() {
        val result = OcrFieldExtractor.extractFields("Torréfié le: 10.02.2026")
        assertNotNull("Should extract French roast date", result.roastDate)
    }

    @Test
    fun `extracts French DLUO expiry`() {
        val result = OcrFieldExtractor.extractFields("DLUO: 10.08.2026")
        assertNotNull("Should extract French DLUO expiry", result.expiryDate)
    }

    @Test
    fun `extracts French origin with country hint`() {
        val text = "Origine: Éthiopie\nVariété: Heirloom\nTraitement: Lavé"
        val result = OcrFieldExtractor.extractFields(
            text,
            countryHint = CoffeeCountryDictionaries.FRENCH,
        )
        assertEquals("Éthiopie", result.origin)
    }

    // --- Portuguese language ---

    @Test
    fun `extracts Portuguese tasting notes label`() {
        val result = OcrFieldExtractor.extractFields(
            "Notas de degustação: cacau, caramelo, frutas vermelhas",
        )
        assertNotNull("Should extract Portuguese tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("cacau", ignoreCase = true))
    }

    @Test
    fun `extracts Portuguese validade expiry`() {
        val result = OcrFieldExtractor.extractFields("Validade: 15.09.2026")
        assertNotNull("Should extract Portuguese expiry", result.expiryDate)
    }

    // --- Dutch language ---

    @Test
    fun `extracts Dutch smaaknotities label`() {
        val result = OcrFieldExtractor.extractFields(
            "Smaaknotities: bessen, chocolade, noten",
        )
        assertNotNull("Should extract Dutch tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("bessen", ignoreCase = true))
    }

    @Test
    fun `extracts Dutch THT expiry`() {
        val result = OcrFieldExtractor.extractFields("THT: 20.07.2026")
        assertNotNull("Should extract Dutch THT expiry", result.expiryDate)
    }

    // --- Swedish language ---

    @Test
    fun `extracts Swedish smaknoter label`() {
        val result = OcrFieldExtractor.extractFields(
            "Smaknoter: blåbär, choklad, honung",
        )
        assertNotNull("Should extract Swedish tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("choklad", ignoreCase = true))
    }

    @Test
    fun `extracts Swedish bast fore expiry`() {
        val result = OcrFieldExtractor.extractFields("Bäst före: 15.06.2026")
        assertNotNull("Should extract Swedish expiry", result.expiryDate)
    }

    @Test
    fun `extracts Swedish rostad roast date`() {
        val result = OcrFieldExtractor.extractFields("Rostad: 15.01.2026")
        assertNotNull("Should extract Swedish roast date", result.roastDate)
    }

    // --- Norwegian language ---

    @Test
    fun `extracts Norwegian smaksnotater label`() {
        val result = OcrFieldExtractor.extractFields(
            "Smaksnotater: bær, sjokolade, sitrus",
        )
        assertNotNull("Should extract Norwegian tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("sjokolade", ignoreCase = true))
    }

    @Test
    fun `extracts Norwegian best for expiry`() {
        val result = OcrFieldExtractor.extractFields("Best før: 15.06.2026")
        assertNotNull("Should extract Norwegian expiry", result.expiryDate)
    }

    // --- Finnish language ---

    @Test
    fun `extracts Finnish makumuistiinpanot label`() {
        val result = OcrFieldExtractor.extractFields(
            "Makumuistiinpanot: mustikka, suklaa, hunaja",
            countryHint = CoffeeCountryDictionaries.FINNISH,
        )
        assertNotNull("Should extract Finnish tasting notes", result.tastingNotes)
    }

    @Test
    fun `extracts Finnish parasta ennen expiry`() {
        val result = OcrFieldExtractor.extractFields("Parasta ennen: 15.06.2026")
        assertNotNull("Should extract Finnish expiry", result.expiryDate)
    }

    @Test
    fun `extracts Finnish paahdettu roast date`() {
        val result = OcrFieldExtractor.extractFields("Paahdettu: 15.01.2026")
        assertNotNull("Should extract Finnish roast date", result.roastDate)
    }

    // --- Slovak language ---

    @Test
    fun `extracts Slovak tasting notes with country hint`() {
        val text = "Chuťový profil: maliny, čokoláda, med"
        val result = OcrFieldExtractor.extractFields(
            text,
            countryHint = CoffeeCountryDictionaries.SLOVAK,
        )
        assertNotNull("Should extract Slovak tasting notes", result.tastingNotes)
        assertTrue(result.tastingNotes!!.contains("maliny", ignoreCase = true))
    }

    @Test
    fun `extracts Slovak spotrebujte do expiry`() {
        val result = OcrFieldExtractor.extractFields("Spotrebujte do: 15.06.2026")
        assertNotNull("Should extract Slovak expiry", result.expiryDate)
    }

    // --- Japanese language ---

    @Test
    fun `extracts Japanese tasting notes with country hint`() {
        val text = "テイスティングノート: ブルーベリー, チョコレート, はちみつ"
        val result = OcrFieldExtractor.extractFields(
            text,
            countryHint = CoffeeCountryDictionaries.JAPANESE,
        )
        assertNotNull("Should extract Japanese tasting notes", result.tastingNotes)
    }

    @Test
    fun `extracts Japanese expiry label`() {
        val result = OcrFieldExtractor.extractFields("賞味期限: 15.06.2026")
        assertNotNull("Should extract Japanese expiry", result.expiryDate)
    }

    // --- Korean language ---

    @Test
    fun `extracts Korean tasting notes with country hint`() {
        val text = "테이스팅 노트: 블루베리, 초콜릿, 꿀"
        val result = OcrFieldExtractor.extractFields(
            text,
            countryHint = CoffeeCountryDictionaries.KOREAN,
        )
        assertNotNull("Should extract Korean tasting notes", result.tastingNotes)
    }

    @Test
    fun `extracts Korean expiry label`() {
        val result = OcrFieldExtractor.extractFields("유통기한: 15.06.2026")
        assertNotNull("Should extract Korean expiry", result.expiryDate)
    }

    // --- GS1 region mapping ---

    @Test
    fun `GS1 prefix maps to France for 300-379`() {
        val region = BarcodeInsights.gs1IssuerRegion("3012345678901")
        assertNotNull("Should detect French GS1 prefix", region)
        assertEquals("France", region!!.regionName)
    }

    @Test
    fun `GS1 prefix maps to Sweden for 730-739`() {
        val region = BarcodeInsights.gs1IssuerRegion("7301234567890")
        assertNotNull("Should detect Swedish GS1 prefix", region)
        assertEquals("Sweden", region!!.regionName)
    }

    @Test
    fun `GS1 prefix maps to Spain for 840-849`() {
        val region = BarcodeInsights.gs1IssuerRegion("8401234567890")
        assertNotNull("Should detect Spanish GS1 prefix", region)
        assertEquals("Spain", region!!.regionName)
    }

    @Test
    fun `GS1 prefix maps to Japan for 450-459`() {
        val region = BarcodeInsights.gs1IssuerRegion("4501234567890")
        assertNotNull("Should detect Japanese GS1 prefix", region)
        assertEquals("Japan", region!!.regionName)
    }
}
