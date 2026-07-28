package com.adsamcik.starlitcoffee.domain.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewSessionWallClockReconciliationTest {

    @Test
    fun `wall reconciliation completes an expired stage after a monotonic-clock reset`() {
        val stageId = StageId("reboot_countdown")
        val plan = CompiledStagePlan(
            id = StagePlanId("reboot_reconciliation"),
            version = 1,
            stages = listOf(
                CompiledBrewStage(
                    instanceId = StageInstanceId(stageId, 1),
                    definition = BrewStageDefinition(
                        id = stageId,
                        action = BrewStageAction.STEEP,
                        contentId = StageContentId("reboot_countdown"),
                        completionMode = StageCompletionMode.Countdown(30_000L),
                    ),
                ),
            ),
        )
        val started = SessionReducer.reduce(
            state = SessionRuntimeState.create(SessionId("reboot-session"), plan),
            event = SessionEvent.Start(),
            now = SessionClockReading(monotonicMillis = 500_000L, wallClockMillis = 1_000L),
        ).state

        val reconciled = SessionReducer.reduce(
            state = started,
            event = SessionEvent.Reconcile(),
            now = SessionClockReading(monotonicMillis = 50L, wallClockMillis = 31_000L),
        ).state

        assertEquals(BrewSessionStatus.COMPLETED, reconciled.status)
        assertEquals(30_000L, reconciled.totalActiveElapsedMillis)
        assertEquals(
            ClockReconciliationKind.WALL_CLOCK_FORWARD,
            reconciled.lastClockReconciliation?.kind,
        )
    }
}
