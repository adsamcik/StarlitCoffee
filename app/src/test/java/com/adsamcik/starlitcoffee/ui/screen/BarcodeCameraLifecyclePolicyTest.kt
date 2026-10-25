package com.adsamcik.starlitcoffee.ui.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeCameraLifecyclePolicyTest {

    @Test
    fun `disposal has one owner and permanently deactivates camera`() {
        val policy = BarcodeCameraLifecyclePolicy()

        assertTrue(policy.isActive)
        assertTrue(policy.dispose())
        assertFalse(policy.dispose())

        assertFalse(policy.isActive)
    }
}
