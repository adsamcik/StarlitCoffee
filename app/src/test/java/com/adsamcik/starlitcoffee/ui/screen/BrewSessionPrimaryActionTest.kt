package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
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

    @Test
    fun `confirmation names the current brewing task without inventing a custom task`() {
        assertEquals(R.string.action_brew_pour_done, brewStepConfirmationLabel(BrewStageAction.POUR))
        assertEquals(R.string.action_brew_bloom_done, brewStepConfirmationLabel(BrewStageAction.BLOOM))
        assertEquals(R.string.action_brew_coffee_added, brewStepConfirmationLabel(BrewStageAction.ADD_COFFEE))
        assertEquals(R.string.action_brew_step_done, brewStepConfirmationLabel(BrewStageAction.CUSTOM))
        assertEquals(R.string.action_brew_step_done, brewStepConfirmationLabel(null))
    }

    @Test
    fun `saved combined Pulsar pour confirms pouring without changing its stage action`() {
        assertEquals(R.string.action_brew_pour_done, brewStepConfirmationLabel(
            BrewStageAction.OBSERVE, StageContentId("reviewed_pulsar_v1_open_and_pulse"), "pulsar_standard"))
        assertEquals(R.string.action_brew_checked, brewStepConfirmationLabel(
            BrewStageAction.OBSERVE, StageContentId("reviewed_pulsar_v1_open_and_pulse"), "unknown"))
        assertEquals(R.string.action_brew_checked, brewStepConfirmationLabel(
            BrewStageAction.OBSERVE, StageContentId("custom_observation")))
        assertEquals(R.string.action_brew_step_done, brewStepConfirmationLabel(
            BrewStageAction.CUSTOM, StageContentId("reviewed_pulsar_v1_open_and_pulse"), "pulsar_standard"))
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
