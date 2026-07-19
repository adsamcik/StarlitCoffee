package com.adsamcik.starlitcoffee.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MindlayerStartupPolicyTest {
    @Test
    fun `offers connection only after an installed service remains unavailable`() {
        assertTrue(
            shouldOfferMindlayerConnection(
                isInstalled = true,
                connectionAttemptFinished = true,
                isConnected = false,
                offerHandled = false,
            ),
        )
    }

    @Test
    fun `does not offer connection before the automatic attempt finishes`() {
        assertFalse(
            shouldOfferMindlayerConnection(
                isInstalled = true,
                connectionAttemptFinished = false,
                isConnected = false,
                offerHandled = false,
            ),
        )
    }

    @Test
    fun `does not offer connection when service is absent connected or already handled`() {
        assertFalse(
            shouldOfferMindlayerConnection(
                isInstalled = false,
                connectionAttemptFinished = true,
                isConnected = false,
                offerHandled = false,
            ),
        )
        assertFalse(
            shouldOfferMindlayerConnection(
                isInstalled = true,
                connectionAttemptFinished = true,
                isConnected = true,
                offerHandled = false,
            ),
        )
        assertFalse(
            shouldOfferMindlayerConnection(
                isInstalled = true,
                connectionAttemptFinished = true,
                isConnected = false,
                offerHandled = true,
            ),
        )
    }

    @Test
    fun `uses the public Mindlayer Play Store URI`() {
        assertEquals(
            "market://details?id=com.adsamcik.mindlayer",
            MindlayerInstallLink.PLAY_STORE_URI,
        )
    }
}
