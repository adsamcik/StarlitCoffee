package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewSessionPrimaryActionTest {

    @Test
    fun `waiting stage pauses the physical clock and remains resumable`() {
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
        assertEquals(SessionEvent.Pause::class, BrewSessionPrimaryAction.PAUSE.event()::class)
        assertEquals(SessionEvent.Resume::class, BrewSessionPrimaryAction.RESUME.event()::class)
    }

    @Test
    fun `physical confirmation stays primary while the clock is running`() {
        assertEquals(BrewSessionPrimaryAction.COMPLETE_STEP,
            primaryBrewSessionAction(actions(canPause = true, canManualAdvance = true)))
        assertEquals(BrewSessionPrimaryAction.FINISH,
            primaryBrewSessionAction(actions(canPause = true, canManualAdvance = true, canFinish = true)))
        assertEquals(SessionEvent.ManualAdvance::class, BrewSessionPrimaryAction.COMPLETE_STEP.event()::class)
        assertEquals(SessionEvent.Finish::class, BrewSessionPrimaryAction.FINISH.event()::class)
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
        canManualAdvance: Boolean = false,
        canFinish: Boolean = false,
    ) = BrewSessionActionAvailability(
        canStart = canStart,
        canPause = canPause,
        canResume = canResume,
        canManualAdvance = canManualAdvance,
        canSkip = canSkip,
        canCancel = canCancel,
        canFinish = canFinish,
        canRecordActual = false,
    )
}
