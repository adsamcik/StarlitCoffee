package com.adsamcik.starlitcoffee.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class BagReviewEnrichmentTest {
    @Test
    fun `explicitly leaving an already blank field empty protects it from late recognition`() {
        val initial = BagFormSnapshot(name = "Coffee")
        val incoming = initial.copy(originCountry = "Brazil", roaster = "Roaster")

        val merged = mergeBagFormEnrichment(initial, initial, incoming, protectedFields = setOf("origin"))

        assertEquals("", merged.originCountry)
        assertEquals("Roaster", merged.roaster)
    }

    @Test
    fun `explicit value stays protected even when it happens to equal the previous automatic value`() {
        val initial = BagFormSnapshot(name = "My coffee", weight = "250")
        val incoming = initial.copy(name = "Different coffee", weight = "750", farm = "Farm")

        val merged = mergeBagFormEnrichment(initial, initial, incoming, protectedFields = setOf("name", "weight"))

        assertEquals("My coffee", merged.name)
        assertEquals("250", merged.weight)
        assertEquals("Farm", merged.farm)
    }
}
