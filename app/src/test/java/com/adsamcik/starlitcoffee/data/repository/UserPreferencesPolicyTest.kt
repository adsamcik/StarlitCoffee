package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.model.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesPolicyTest {

    @Test
    fun `disabled requested default is normalized into enabled methods`() {
        val selection = normalizeMethodSelection(
            enabledMethods = setOf(BrewMethod.V60, BrewMethod.AEROPRESS),
            requestedDefault = BrewMethod.PULSAR,
        )

        assertTrue(selection.enabledMethods.contains(selection.defaultMethod))
        assertEquals(BrewMethod.V60, selection.defaultMethod)
    }

    @Test
    fun `empty enabled methods normalizes to Pulsar`() {
        val selection = normalizeMethodSelection(
            enabledMethods = emptySet(),
            requestedDefault = BrewMethod.V60,
        )

        assertEquals(setOf(BrewMethod.PULSAR), selection.enabledMethods)
        assertEquals(BrewMethod.PULSAR, selection.defaultMethod)
    }
}
