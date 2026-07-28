package com.adsamcik.starlitcoffee.ui.session

import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageObservationId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableBrewSessionFeedbackPolicyTest {

    @Test
    fun `minute cue is withheld for manual and observed stages`() {
        assertFalse(
            shouldPlayDurableMinuteCue(
                status = BrewSessionStatus.RUNNING,
                action = BrewStageAction.ADD_COFFEE,
                completion = BrewStageCompletionPresentation.Manual,
                previousMinute = 1,
                currentMinute = 2,
                enabled = true,
            ),
        )
        assertFalse(
            shouldPlayDurableMinuteCue(
                status = BrewSessionStatus.RUNNING,
                action = BrewStageAction.OBSERVE,
                completion = BrewStageCompletionPresentation.ObservedEvent(
                    observationId = StageObservationId("feedback_test_observed"),
                    alreadyRecorded = false,
                ),
                previousMinute = 1,
                currentMinute = 2,
                enabled = true,
            ),
        )
    }

    @Test
    fun `minute cue remains available for an active timed stage`() {
        assertTrue(
            shouldPlayDurableMinuteCue(
                status = BrewSessionStatus.RUNNING,
                action = BrewStageAction.STEEP,
                completion = BrewStageCompletionPresentation.Countdown(
                    targetElapsedMillis = 120_000L,
                    remainingMillis = 60_000L,
                ),
                previousMinute = 0,
                currentMinute = 1,
                enabled = true,
            ),
        )
    }
}
