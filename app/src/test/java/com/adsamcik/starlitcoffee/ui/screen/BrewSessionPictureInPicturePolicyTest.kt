package com.adsamcik.starlitcoffee.ui.screen

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.ui.session.ActiveBrewSessionPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewSessionAccessibilityPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewSessionLiveRegion
import com.adsamcik.starlitcoffee.ui.session.BrewSessionStageProgressPresentation
import com.adsamcik.starlitcoffee.ui.session.BrewStageCompletionPresentation
import com.adsamcik.starlitcoffee.ui.session.CurrentBrewStagePresentation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionPictureInPicturePolicyTest {

    @Test
    fun `PiP is offered only for a running bounded time stage`() {
        assertTrue(
            shouldOfferPictureInPicture(
                presentation(
                    BrewStageCompletionPresentation.Countdown(
                        targetElapsedMillis = 60_000L,
                        remainingMillis = 60_000L,
                    ),
                ),
            ),
        )
        assertTrue(
            shouldOfferPictureInPicture(
                presentation(
                    BrewStageCompletionPresentation.ElapsedRange(
                        minimumElapsedMillis = 0L,
                        maximumElapsedMillis = 60_000L,
                        minimumRemainingMillis = 0L,
                        maximumRemainingMillis = 60_000L,
                    ),
                ),
            ),
        )
        assertFalse(shouldOfferPictureInPicture(presentation(BrewStageCompletionPresentation.Manual)))
        assertFalse(
            shouldOfferPictureInPicture(
                presentation(
                    BrewStageCompletionPresentation.Countdown(
                        targetElapsedMillis = 21 * 60_000L,
                        remainingMillis = 21 * 60_000L,
                    ),
                ),
            ),
        )
        assertFalse(
            shouldOfferPictureInPicture(
                presentation(BrewStageCompletionPresentation.Manual, BrewSessionStatus.PAUSED),
            ),
        )
    }

    private fun presentation(
        completion: BrewStageCompletionPresentation,
        status: BrewSessionStatus = BrewSessionStatus.RUNNING,
    ): ActiveBrewSessionPresentation.Available {
        val progress = BrewSessionStageProgressPresentation(
            completedStageCount = 0,
            skippedStageCount = 0,
            finishedStageCount = 0,
            totalStageCount = 1,
            currentStageNumber = 1,
        )
        val stage = CurrentBrewStagePresentation(
            stageInstanceId = StageInstanceId(StageId("pip_test_stage"), 1),
            action = BrewStageAction.STEEP,
            contentId = StageContentId("pip_test_content"),
            instructionAssetId = null,
            requiresIllustration = false,
            runStatus = StageRunStatus.ACTIVE,
            elapsedActiveMillis = 0L,
            completion = completion,
            safetyMessages = emptyList(),
        )
        return ActiveBrewSessionPresentation.Available(
            sessionId = "pip-test",
            status = status,
            totalActiveElapsedMillis = 0L,
            stageProgress = progress,
            currentStage = stage,
            safetyMessages = emptyList(),
            actions = com.adsamcik.starlitcoffee.ui.session.BrewSessionActionAvailability(
                canStart = false,
                canPause = false,
                canResume = false,
                canManualAdvance = false,
                canSkip = false,
                canCancel = false,
                canFinish = false,
                canRecordActual = false,
            ),
            accessibility = BrewSessionAccessibilityPresentation(
                liveRegion = BrewSessionLiveRegion.POLITE,
                status = status,
                stageProgress = progress,
                currentStageAction = stage.action,
                currentStageContentId = stage.contentId,
                totalActiveElapsedMillis = 0L,
                currentStageElapsedMillis = 0L,
                countdownRemainingMillis = null,
                safetyMessages = emptyList(),
            ),
        )
    }
}
