package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MindlayerStartupPolicyTest {
    @Test
    fun `uses the public Mindlayer Play Store URI`() {
        assertEquals(
            "market://details?id=com.adsamcik.mindlayer",
            MindlayerInstallLink.PLAY_STORE_URI,
        )
    }
}
