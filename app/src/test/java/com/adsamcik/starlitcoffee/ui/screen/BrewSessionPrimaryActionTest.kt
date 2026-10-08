package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewSessionPrimaryActionTest {

    @Test
    fun `waiting stage pauses only guidance and physical pause remains resumable`() {
        assertEquals(
            BrewSessionPrimaryAction.START,
            primaryBrewSessionAction(actions(canStart = true)),
        )
        assertEquals(
            BrewSessionPrimaryAction.PAUSE_GUIDANCE,
            primaryBrewSessionAction(actions(canPause = true)),
        )
        assertEquals(
            BrewSessionPrimaryAction.RESUME,
            primaryBrewSessionAction(actions(canResume = true)),
        )
        assertEquals(BrewSessionPrimaryAction.RESUME_GUIDANCE,
            primaryBrewSessionAction(actions(canPause = true), isGuidancePaused = true))
        assertEquals(true, (BrewSessionPrimaryAction.PAUSE_GUIDANCE.event() as SessionEvent.SetGuidancePaused).paused)
        assertEquals(false, (BrewSessionPrimaryAction.RESUME_GUIDANCE.event() as SessionEvent.SetGuidancePaused).paused)
    }

    @Test
    fun `physical confirmation stays primary while guidance is paused`() {
        assertEquals(BrewSessionPrimaryAction.COMPLETE_STEP,
            primaryBrewSessionAction(actions(canPause = true, canManualAdvance = true), isGuidancePaused = true))
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
