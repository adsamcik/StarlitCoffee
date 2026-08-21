package com.adsamcik.starlitcoffee.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningLibraryAvailabilityTest {
    @Test
    fun `packaged profiles make the library available`() {
        assertEquals(
            LearningLibraryAvailability.AVAILABLE,
            resolveLearningLibraryAvailability(
                hasProfiles = true,
            ),
        )
    }

    @Test
    fun `a genuine content failure is unavailable`() {
        assertEquals(
            LearningLibraryAvailability.UNAVAILABLE,
            resolveLearningLibraryAvailability(
                hasProfiles = false,
            ),
        )
    }
}
