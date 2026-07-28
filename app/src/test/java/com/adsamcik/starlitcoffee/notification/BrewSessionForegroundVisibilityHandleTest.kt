package com.adsamcik.starlitcoffee.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionForegroundVisibilityHandleTest {

    @Test
    fun `paused composition does not suppress the matching session alert`() {
        val sessionId = "foreground-visibility-handle-test"
        BrewSessionVisibilityRegistry.unregister(sessionId)
        val handle = BrewSessionVisibilityRegistry.foregroundHandle(sessionId)

        assertFalse(BrewSessionVisibilityRegistry.isVisible(sessionId))

        handle.onResumed()
        assertTrue(BrewSessionVisibilityRegistry.isVisible(sessionId))

        handle.onPaused()
        assertFalse(BrewSessionVisibilityRegistry.isVisible(sessionId))

        handle.onResumed()
        handle.dispose()
        assertFalse(BrewSessionVisibilityRegistry.isVisible(sessionId))
    }
}
