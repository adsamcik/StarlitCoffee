package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewSessionPrimaryActionTest {

    @Test
    fun `selects the current start pause or resume phase action`() {
        assertEquals(
            BrewSessionPrimaryAction.START,
            primaryBrewSessionAction(actions(canStart = true)),
        )
        assertEquals(
            BrewSessionPrimaryAction.PAUSE,
            primaryBrewSessionAction(actions(canPause = true)),
        )
        assertEquals(
            BrewSessionPrimaryAction.RESUME,
            primaryBrewSessionAction(actions(canResume = true)),
        )
    }

    @Test
    fun `does not make secondary session controls sticky`() {
        assertNull(
            primaryBrewSessionAction(
                actions(canSkip = true, canCancel = true),
            ),
        )
    }

    private fun actions(
        canStart: Boolean = false,
        canPause: Boolean = false,
        canResume: Boolean = false,
        canSkip: Boolean = false,
        canCancel: Boolean = false,
    ) = BrewSessionActionAvailability(
        canStart = canStart,
        canPause = canPause,
        canResume = canResume,
        canManualAdvance = false,
        canSkip = canSkip,
        canCancel = canCancel,
        canFinish = false,
        canRecordActual = false,
    )
}
