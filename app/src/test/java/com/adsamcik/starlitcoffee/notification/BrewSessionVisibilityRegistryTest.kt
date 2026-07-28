package com.adsamcik.starlitcoffee.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionVisibilityRegistryTest {

    @Test
    fun `session remains visible until every registered screen is disposed`() {
        val sessionId = "visibility-registry-test"
        BrewSessionVisibilityRegistry.unregister(sessionId)

        BrewSessionVisibilityRegistry.register(sessionId)
        BrewSessionVisibilityRegistry.register(sessionId)
        assertTrue(BrewSessionVisibilityRegistry.isVisible(sessionId))

        BrewSessionVisibilityRegistry.unregister(sessionId)
        assertTrue(BrewSessionVisibilityRegistry.isVisible(sessionId))

        BrewSessionVisibilityRegistry.unregister(sessionId)
        assertFalse(BrewSessionVisibilityRegistry.isVisible(sessionId))
    }
}
