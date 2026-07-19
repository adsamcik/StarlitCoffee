package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFieldSupportTest {

    @Test
    fun `buildFieldEvidence tags live scan values as consensus evidence`() {
        val evidence = ScanFieldSupport.buildFieldEvidence(
            mapOf(
                "name" to "Night Shift",
                "isDecaf" to "Decaf",
            ),
        )

        assertEquals(BagFieldSourceType.CONSENSUS, evidence.getValue("name").sourceType)
        assertEquals(BagFieldConfidence.MEDIUM, evidence.getValue("isDecaf").confidence)
    }

    @Test
    fun `buildPrefill preserves decaf from resolved fields`() {
        val prefill = ScanFieldSupport.buildPrefill(
            mapOf(
                "name" to "Night Shift",
                "isDecaf" to "Decaf",
            ),
        )

        assertEquals("Night Shift", prefill.name)
        assertTrue(prefill.isDecaf == true)
    }

    @Test
    fun `buildDraft parses scanned decaf weight and dates`() {
        val draft = ScanFieldSupport.buildDraft(
            mapOf(
                "name" to "Night Shift",
                "origin" to "Colombia",
                "farm" to "El Paraiso",
                "altitude" to "1900 masl",
                "weight" to "250g",
                "roastDate" to "2026-02-20",
                "expiryDate" to "20.03.2026",
                "isDecaf" to "Decaf",
            ),
        )!!

        assertEquals("Night Shift", draft.name)
        assertEquals("Colombia", draft.origin)
        assertEquals("El Paraiso", draft.farm)
        assertEquals("1900 masl", draft.altitude)
        assertEquals(250f, draft.weightG!!, 0.01f)
        assertEquals(DateParser.parse("2026-02-20"), draft.roastDateMillis)
        assertEquals(DateParser.parse("20.03.2026"), draft.expiryDateMillis)
        assertTrue(draft.isDecaf)
    }
}
