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
}
