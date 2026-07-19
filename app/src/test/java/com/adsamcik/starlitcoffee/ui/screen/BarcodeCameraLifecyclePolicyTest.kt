package com.adsamcik.starlitcoffee.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeCameraLifecyclePolicyTest {

    @Test
    fun `active camera setup continues without releasing resources`() {
        val policy = BarcodeCameraLifecyclePolicy()
        var released = false

        val continued = policy.continueOrRelease { released = true }

        assertTrue(continued)
        assertFalse(released)
    }

    @Test
    fun `disposed camera setup releases every later resource`() {
        val policy = BarcodeCameraLifecyclePolicy()
        policy.dispose()
        var released = false

        val continued = policy.continueOrRelease { released = true }

        assertFalse(continued)
        assertTrue(released)
        assertFalse(policy.isActive)
    }

    @Test
    fun `camera lifecycle disposal is one way`() {
        val policy = BarcodeCameraLifecyclePolicy()

        policy.dispose()
        policy.dispose()

        assertFalse(policy.isActive)
    }
}
