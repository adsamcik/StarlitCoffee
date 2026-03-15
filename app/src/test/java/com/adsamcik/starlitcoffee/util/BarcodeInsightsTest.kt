package com.adsamcik.starlitcoffee.util

import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeInsightsTest {

    @Test
    fun `normalize barcode strips separators and rejects unsupported lengths`() {
        assertEquals("8594206180014", BarcodeInsights.normalizeBarcode("859 4206-180014"))
        assertEquals("12345678", BarcodeInsights.normalizeBarcode("1234-5678"))
        assertEquals("01234567890123", BarcodeInsights.normalizeBarcode("01 234567 890123"))
        assertEquals(null, BarcodeInsights.normalizeBarcode("1234567"))
        assertEquals(null, BarcodeInsights.normalizeBarcode("123456789012345"))
        assertEquals(null, BarcodeInsights.normalizeBarcode("abc"))
    }

    @Test
    fun `gs1 issuer region maps supported prefixes`() {
        assertEquals("Czech Republic", BarcodeInsights.gs1IssuerRegion("8594206180014")?.regionName)
        assertEquals("Germany", BarcodeInsights.gs1IssuerRegion("4001234567890")?.regionName)
        assertEquals("Italy", BarcodeInsights.gs1IssuerRegion("8001234567890")?.regionName)
        assertEquals("Poland", BarcodeInsights.gs1IssuerRegion("5901234567890")?.regionName)
        assertEquals(null, BarcodeInsights.gs1IssuerRegion("0123456789012"))
        assertEquals(null, BarcodeInsights.gs1IssuerRegion("859420618001"))
    }

    @Test
    fun `build local match candidates keeps stable bag fields and canonical ids`() {
        val matchedBag = CoffeeBagEntity(
            name = "Beansmith's Core",
            roaster = "Beansmith's",
            origin = "Brazil",
            region = "Cerrado",
            farm = "Producer",
            roastLevel = "Medium",
            processType = "Natural",
            variety = "Catuai",
            altitude = "1200-1400 m",
            tastingNotes = "Chocolate, hazelnut",
            roastDate = 1735689600000L,
            notes = "Personal notes",
            weightG = 250f,
            barcode = "8594206180014",
        )

        val candidates = BarcodeInsights.buildLocalMatchCandidates(matchedBag)

        assertEquals(
            setOf(
                "name",
                "roaster",
                "origin",
                "region",
                "roastLevel",
                "processType",
                "variety",
                "tastingNotes",
                "weight",
            ),
            candidates.map { it.fieldName }.toSet(),
        )
        assertTrue(candidates.all { it.sourceType == BagFieldSourceType.LOCAL_BARCODE_MATCH })
        assertTrue(candidates.none { it.value == "2025-01-01" || it.value == "Personal notes" })
        assertEquals("250", candidates.first { it.fieldName == "weight" }.value)
        assertEquals("BRAZIL", candidates.first { it.fieldName == "origin" }.canonicalKey)
        assertEquals("CERRADO", candidates.first { it.fieldName == "region" }.canonicalKey)
        assertEquals("MEDIUM", candidates.first { it.fieldName == "roastLevel" }.canonicalKey)
        assertEquals("NATURAL", candidates.first { it.fieldName == "processType" }.canonicalKey)
        assertEquals("CATUAI", candidates.first { it.fieldName == "variety" }.canonicalKey)
        assertEquals("chocolate,hazelnut", candidates.first { it.fieldName == "tastingNotes" }.canonicalKey)
    }

    @Test
    fun `build local match candidates includes decaf evidence`() {
        val matchedBag = CoffeeBagEntity(
            name = "Night Shift",
            roaster = "Beansmith's",
            isDecaf = true,
        )

        val candidates = BarcodeInsights.buildLocalMatchCandidates(matchedBag)

        val decafCandidate = candidates.first { it.fieldName == "isDecaf" }
        assertEquals("Decaf", decafCandidate.value)
        assertEquals("true", decafCandidate.canonicalKey)
    }

    @Test
    fun `build local match candidates localizes canonical metadata for requested locale`() {
        val matchedBag = CoffeeBagEntity(
            name = "Vacation Bag",
            roaster = "Beansmith's",
            origin = "Etiopie",
            originId = "ETHIOPIA",
            region = "Guji",
            regionId = "GUJI",
            roastLevel = "Světlé",
            roastLevelIds = "LIGHT",
            processType = "Lavado",
            processTypeId = "WASHED",
            variety = "Gesha",
            varietyIds = "GEISHA",
            tastingNotes = "Lesní jahoda, Zelený čaj",
            tasteNoteIds = "green_tea,wild_strawberry",
        )

        val candidates = BarcodeInsights.buildLocalMatchCandidates(matchedBag, locale = Locale("da"))

        assertEquals("ETHIOPIA", candidates.first { it.fieldName == "origin" }.canonicalKey)
        assertEquals("Lys", candidates.first { it.fieldName == "roastLevel" }.value)
        assertEquals("Světlé", candidates.first { it.fieldName == "roastLevel" }.rawValue)
        assertEquals("Vasket", candidates.first { it.fieldName == "processType" }.value)
        assertEquals("Lavado", candidates.first { it.fieldName == "processType" }.rawValue)
        assertEquals("Grøn te, Skovjordbær", candidates.first { it.fieldName == "tastingNotes" }.value)
        assertEquals("Lesní jahoda, Zelený čaj", candidates.first { it.fieldName == "tastingNotes" }.rawValue)
    }

    @Test
    fun `build local match candidates fall back to default labels for unsupported locale`() {
        val matchedBag = CoffeeBagEntity(
            name = "Vacation Bag",
            roaster = "Beansmith's",
            origin = "Etiopie",
            originId = "ETHIOPIA",
            roastLevel = "Světlé",
            roastLevelIds = "LIGHT",
            processType = "Lavado",
            processTypeId = "WASHED",
            tastingNotes = "Lesní jahoda, Zelený čaj",
            tasteNoteIds = "green_tea,wild_strawberry",
        )

        val candidates = BarcodeInsights.buildLocalMatchCandidates(matchedBag, locale = Locale("fr"))

        assertEquals("Light", candidates.first { it.fieldName == "roastLevel" }.value)
        assertEquals("Washed", candidates.first { it.fieldName == "processType" }.value)
        assertEquals("Green tea, Wild strawberry", candidates.first { it.fieldName == "tastingNotes" }.value)
    }

    @Test
    fun `observed barcode stem infers unique roaster from repeated public family`() {
        val match = BarcodeInsights.findObservedStemMatch("8594206183909")
        val candidates = BarcodeInsights.buildObservedStemCandidates(match)

        assertEquals("859420618", match?.stem)
        assertEquals(listOf("Beansmith's"), match?.brands)
        assertEquals("Czech Republic", match?.gs1Region)
        assertEquals(1, candidates.size)
        assertEquals("roaster", candidates.single().fieldName)
        assertEquals("Beansmith's", candidates.single().value)
        assertEquals(BagFieldSourceType.OBSERVED_BARCODE_STEM, candidates.single().sourceType)
        assertEquals(BagFieldConfidence.MEDIUM, candidates.single().confidenceHint)
    }

    @Test
    fun `gs1Region is null for non country specific stems`() {
        val match = BarcodeInsights.findObservedStemMatch("7965549851234")
        assertNotNull(match)
        assertEquals("Upraženo", match?.roasterCandidate)
        assertEquals(null, match?.gs1Region)
    }

    @Test
    fun `observed barcode stem supports gtin14 listings with leading zero`() {
        val match = BarcodeInsights.findObservedStemMatch("08594213864310")
        val candidates = BarcodeInsights.buildObservedStemCandidates(match)

        assertEquals("859421386", match?.stem)
        assertEquals("Pražírna Káva Monro", candidates.single().value)
    }

    @Test
    fun `observed barcode stem now infers fathers roastery from repeated retail family`() {
        val match = BarcodeInsights.findObservedStemMatch("8594211300995")
        val candidates = BarcodeInsights.buildObservedStemCandidates(match)

        assertEquals("859421130", match?.stem)
        assertEquals("Father's Coffee Roastery", candidates.single().value)
        assertEquals(BagFieldConfidence.MEDIUM, candidates.single().confidenceHint)
    }

    @Test
    fun `observed barcode stem infers kavy pitel from repeated official family`() {
        val match = BarcodeInsights.findObservedStemMatch("8594205571234")
        val candidates = BarcodeInsights.buildObservedStemCandidates(match)

        assertEquals("859420557", match?.stem)
        assertEquals("Kávy Pitel", candidates.single().value)
        assertEquals(BagFieldConfidence.MEDIUM, candidates.single().confidenceHint)
    }

    @Test
    fun `nordbeans now produces candidate after horizont disagreement retracted`() {
        val match = BarcodeInsights.findObservedStemMatch("8594200940690")
        val candidates = BarcodeInsights.buildObservedStemCandidates(match)

        assertEquals("859420094", match?.stem)
        assertEquals("Nordbeans", candidates.single().value)
        assertEquals(BagFieldConfidence.MEDIUM, candidates.single().confidenceHint)
        assertTrue(match!!.note.contains("Horizont"))
    }

    @Test
    fun `doubleshot 8 digit stem now produces candidate`() {
        val match = BarcodeInsights.findObservedStemMatch("8595688214235")
        val candidates = BarcodeInsights.buildObservedStemCandidates(match)

        assertEquals("85956882", match?.stem)
        assertEquals("Doubleshot", candidates.single().value)
        assertEquals(BagFieldConfidence.MEDIUM, candidates.single().confidenceHint)
    }

    @Test
    fun `fathers note honestly reflects capsule only evidence`() {
        val match = BarcodeInsights.findObservedStemMatch("8594211300537")
        assertNotNull(match)
        assertTrue(match!!.note.contains("capsule"))
        assertTrue(match.note.contains("Bag-format barcodes have not been independently verified"))
    }

    @Test
    fun `shared observed stem stays hint only`() {
        val match = BarcodeInsights.findObservedStemMatch("8595722207049")
        val hints = BarcodeInsights.buildBarcodeReviewHints(
            barcode = "8595722207049",
            matchedBag = null,
            observedStemMatch = match,
        )

        assertEquals("859572220", match?.stem)
        assertTrue(BarcodeInsights.buildObservedStemCandidates(match).isEmpty())
        assertTrue(hints.any { it.message.contains("Garage Coffee") && it.message.contains("Fixi Coffee") })
    }

    @Test
    fun `new retail families add candidates while mixed prefix data stays ignored`() {
        val frolikMatch = BarcodeInsights.findObservedStemMatch("8594021130225")
        val coffeespotMatch = BarcodeInsights.findObservedStemMatch("8594222222095")
        val fiftybeansMatch = BarcodeInsights.findObservedStemMatch("8594226860040")

        assertEquals("859402113", frolikMatch?.stem)
        assertEquals("Frolíkova káva", BarcodeInsights.buildObservedStemCandidates(frolikMatch).single().value)

        assertEquals("859422222", coffeespotMatch?.stem)
        assertEquals("Coffeespot", BarcodeInsights.buildObservedStemCandidates(coffeespotMatch).single().value)

        assertEquals("859422686", fiftybeansMatch?.stem)
        assertEquals("Fiftybeans", BarcodeInsights.buildObservedStemCandidates(fiftybeansMatch).single().value)

        assertEquals(null, BarcodeInsights.findObservedStemMatch("8003303062055"))
    }

    @Test
    fun `barcode review hints explain 12 digit codes without inventing a leading zero`() {
        val hints = BarcodeInsights.buildBarcodeReviewHints(
            barcode = "747180340235",
            matchedBag = null,
            observedStemMatch = null,
        )

        assertEquals(1, hints.size)
        assertTrue(hints.single().message.contains("12 digits"))
        assertTrue(hints.single().message.contains("leading zero"))
    }

    @Test
    fun `barcode review hints include saved bag reuse gs1 caveat and observed stem note`() {
        val matchedBag = CoffeeBagEntity(
            name = "Beansmith's Core",
            roaster = "Beansmith's",
            barcode = "8594206180014",
        )

        val hints = BarcodeInsights.buildBarcodeReviewHints(
            barcode = "8594206180014",
            matchedBag = matchedBag,
        )

        assertEquals(3, hints.size)
        assertTrue(hints.any { it.message.contains("Exact barcode match found") })
        assertTrue(hints.any { it.message.contains("GS1 Czech Republic") })
        assertTrue(hints.any { it.message.contains("Beansmith's products") })
    }
}
