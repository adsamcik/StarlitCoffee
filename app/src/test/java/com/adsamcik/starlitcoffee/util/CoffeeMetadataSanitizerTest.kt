package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field-contract sanitiser tests. These pin the deterministic clean-up that
 * runs on raw LLM/OCR extractions before they reach the review chips and the
 * saved bag — the on-device model routinely misfiles correctly-read tokens on
 * bilingual / structured labels (the "Tumbaga Decaf" corpus bag is the
 * motivating case: region="Kolumbie", process="Decaf", weight="250gC1000g").
 */
class CoffeeMetadataSanitizerTest {

    // --- Rule 0: generic/non-value guards (bare species, bean-form process) ---

    @Test
    fun `bare Arabica in origin is dropped not relocated`() {
        // Real hallucination observed on-device: the model reported the bag's
        // origin as "Arabica" (a species, never a country) when ground truth
        // was not visible on the label at all.
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = "Arabica", region = null, processType = null,
            roastLevel = null, variety = null, weight = null, isDecaf = null,
        )
        assertNull(result.origin)
        assertTrue(
            result.corrections.any {
                it.field == "origin" && it.action == ScanFieldCorrectionAction.DROPPED
            },
        )
    }

    @Test
    fun `bare Robusta in variety is dropped`() {
        // Bare species names don't distinguish a cultivar the way "Bourbon"
        // or "Geisha" do — nearly all specialty coffee is Arabica already.
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = null,
            roastLevel = null, variety = "Robusta", weight = null, isDecaf = null,
        )
        assertNull(result.variety)
    }

    @Test
    fun `a real cultivar in variety is preserved`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = null,
            roastLevel = null, variety = "Geisha", weight = null, isDecaf = null,
        )
        assertEquals("Geisha", result.variety)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `whole bean in process is dropped as bean-form not processing method`() {
        // Real hallucination observed on-device: process="Whole Bean" when
        // ground truth was not visible — a packaging descriptor, not a
        // washed/natural/honey processing method.
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = "Whole Bean",
            roastLevel = null, variety = null, weight = null, isDecaf = null,
        )
        assertNull(result.processType)
        assertTrue(
            result.corrections.any {
                it.field == "processType" && it.action == ScanFieldCorrectionAction.DROPPED
            },
        )
    }

    @Test
    fun `ground in process is dropped as bean-form`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = "Ground",
            roastLevel = null, variety = null, weight = null, isDecaf = null,
        )
        assertNull(result.processType)
    }

    // --- Rule 1: decaf displacement ---

    @Test
    fun `bare decaf marker in process is cleared and flags isDecaf`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = "Colombia",
            region = null,
            processType = "Decaf",
            roastLevel = null,
            variety = null,
            weight = null,
            isDecaf = null,
        )
        assertNull(result.processType)
        assertEquals(true, result.isDecaf)
        assertTrue(
            result.corrections.any {
                it.field == "processType" && it.action == ScanFieldCorrectionAction.FLAGGED_DECAF
            },
        )
    }

    @Test
    fun `real process value is preserved`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = "Washed",
            roastLevel = null, variety = null, weight = null, isDecaf = null,
        )
        assertEquals("Washed", result.processType)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `explicit non-decaf is preserved when no marker present`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = "Natural",
            roastLevel = null, variety = null, weight = null, isDecaf = false,
        )
        assertEquals(false, result.isDecaf)
    }

    // --- Rule 2: cross-field reclassification ---

    @Test
    fun `country name in region duplicating origin is dropped`() {
        // The motivating bug: origin already "Colombia", region "Kolumbie"
        // (Czech for Colombia) — a duplicate, not a sub-region.
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = "Colombia",
            region = "Kolumbie",
            processType = null, roastLevel = null, variety = null,
            weight = null, isDecaf = null,
        )
        assertEquals("Colombia", result.origin)
        assertNull(result.region)
        assertTrue(
            result.corrections.any {
                it.field == "region" && it.action == ScanFieldCorrectionAction.DROPPED
            },
        )
    }

    @Test
    fun `country name in region is relocated to an empty origin`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null,
            region = "Kolumbie",
            processType = null, roastLevel = null, variety = null,
            weight = null, isDecaf = null,
        )
        assertEquals("Kolumbie", result.origin)
        assertNull(result.region)
        assertTrue(
            result.corrections.any {
                it.field == "region" &&
                    it.action == ScanFieldCorrectionAction.RELOCATED &&
                    it.to == "origin"
            },
        )
    }

    @Test
    fun `english country name in region duplicating origin is dropped`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = "Colombia", region = "Colombia",
            processType = null, roastLevel = null, variety = null,
            weight = null, isDecaf = null,
        )
        assertNull(result.region)
    }

    @Test
    fun `genuine free-text region is preserved`() {
        // "Tumbaga" is the real sub-region and is in no dictionary — it must
        // pass through untouched.
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = "Colombia", region = "Tumbaga",
            processType = null, roastLevel = null, variety = null,
            weight = null, isDecaf = null,
        )
        assertEquals("Tumbaga", result.region)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `known sub-region is preserved`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = "Colombia", region = "Huila",
            processType = null, roastLevel = null, variety = null,
            weight = null, isDecaf = null,
        )
        assertEquals("Huila", result.region)
    }

    @Test
    fun `espresso roast level is preserved`() {
        // Espresso IS a valid roast intent — it must not be treated as noise.
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = null,
            roastLevel = "Espresso", variety = null, weight = null, isDecaf = null,
        )
        assertEquals("Espresso", result.roastLevel)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `roast term in process slot is relocated to empty roast level`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = "Espresso",
            roastLevel = null, variety = null, weight = null, isDecaf = null,
        )
        assertEquals("Espresso", result.roastLevel)
        assertNull(result.processType)
    }

    // --- Rule 3: weight format recovery ---

    @Test
    fun `garbled merged weight recovers the first valid token`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = null,
            roastLevel = null, variety = null, weight = "250gC1000g", isDecaf = null,
        )
        assertEquals("250g", result.weight)
        assertTrue(
            result.corrections.any {
                it.field == "weight" && it.action == ScanFieldCorrectionAction.NORMALIZED
            },
        )
    }

    @Test
    fun `valid weight is preserved unchanged`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = null,
            roastLevel = null, variety = null, weight = "250g", isDecaf = null,
        )
        assertEquals("250g", result.weight)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `unparseable weight is dropped`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = null, region = null, processType = null,
            roastLevel = null, variety = null, weight = "no weight here", isDecaf = null,
        )
        assertNull(result.weight)
        assertTrue(
            result.corrections.any {
                it.field == "weight" && it.action == ScanFieldCorrectionAction.DROPPED
            },
        )
    }

    // --- Combined: the full motivating extraction ---

    @Test
    fun `full Tumbaga decaf extraction is corrected end to end`() {
        val result = CoffeeMetadataNormalizer.sanitizeExtraction(
            origin = "Colombia",
            region = "Kolumbie",
            processType = "Decaf",
            roastLevel = "Espresso",
            variety = null,
            weight = "250gC1000g",
            isDecaf = null,
        )
        assertEquals("Colombia", result.origin)
        assertNull(result.region)
        assertNull(result.processType)
        assertEquals("Espresso", result.roastLevel)
        assertEquals("250g", result.weight)
        assertEquals(true, result.isDecaf)
    }

    // --- classifyControlledValue ---

    @Test
    fun `classify maps a czech country alias to origin only`() {
        assertEquals(setOf("origin"), CoffeeMetadataNormalizer.classifyControlledValue("Kolumbie"))
    }

    @Test
    fun `classify returns empty for unknown free text`() {
        assertTrue(CoffeeMetadataNormalizer.classifyControlledValue("Tumbaga").isEmpty())
    }

    // --- sanitizeDate ---

    @Test
    fun `full ISO date is preserved`() {
        assertEquals("2026-05-28", CoffeeMetadataNormalizer.sanitizeDate("2026-05-28"))
    }

    @Test
    fun `year-month date is preserved`() {
        assertEquals("2026-08", CoffeeMetadataNormalizer.sanitizeDate("2026-08"))
    }

    @Test
    fun `relative date phrase is rejected`() {
        // Real hallucination observed on-device: expiryDate reported as
        // "3 months from roast date" — a relative description, not a date.
        assertNull(CoffeeMetadataNormalizer.sanitizeDate("3 months from roast date"))
    }

    @Test
    fun `garbled non-date text is rejected`() {
        assertNull(CoffeeMetadataNormalizer.sanitizeDate("best before"))
    }

    @Test
    fun `blank and null are rejected`() {
        assertNull(CoffeeMetadataNormalizer.sanitizeDate(""))
        assertNull(CoffeeMetadataNormalizer.sanitizeDate("   "))
        assertNull(CoffeeMetadataNormalizer.sanitizeDate(null))
    }

    @Test
    fun `european dd-mm-yyyy date is preserved`() {
        // DateParser already recognizes this format (common on EU labels);
        // sanitizeDate must delegate to it rather than reject anything that
        // isn't strict ISO, or it would regress real, previously-working dates.
        assertEquals("20.03.2026", CoffeeMetadataNormalizer.sanitizeDate("20.03.2026"))
    }

    @Test
    fun `month name date is preserved`() {
        assertEquals("January 15, 2026", CoffeeMetadataNormalizer.sanitizeDate("January 15, 2026"))
    }
}
