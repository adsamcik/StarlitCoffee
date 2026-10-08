package com.adsamcik.starlitcoffee.notification

import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertKind
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertPolicy
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAdvanceConstraint
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionStageAlertPolicyTest {

    @Test
    fun `transition from before background boundary is not replayed`() {
        val fixture = lateCountdownReconciliation()

        assertFalse(
            shouldPublishDurableBrewStageAlert(
                effect = fixture.completionAlert,
                runtime = fixture.runtime,
                backgroundedAtWallClockMillis = 60_000L,
            ),
        )
        assertFalse(
            shouldPublishDurableBrewStageAlert(
                effect = fixture.startedAlert,
                runtime = fixture.runtime,
                backgroundedAtWallClockMillis = 60_000L,
            ),
        )
    }

    @Test
    fun `transition after background boundary remains eligible regardless of delivery delay`() {
        val fixture = lateCountdownReconciliation()

        assertTrue(
            shouldPublishDurableBrewStageAlert(
                effect = fixture.completionAlert,
                runtime = fixture.runtime,
                backgroundedAtWallClockMillis = 20_000L,
            ),
        )
        assertTrue(
            shouldPublishDurableBrewStageAlert(
                effect = fixture.startedAlert,
                runtime = fixture.runtime,
                backgroundedAtWallClockMillis = 20_000L,
            ),
        )
    }

    @Test
    fun `missing background boundary preserves worker-only notification delivery`() {
        val fixture = lateCountdownReconciliation()

        assertTrue(
            shouldPublishDurableBrewStageAlert(
                effect = fixture.completionAlert,
                runtime = fixture.runtime,
                backgroundedAtWallClockMillis = null,
            ),
        )
    }

    @Test
    fun `later reconciliation does not move an earlier recorded stage boundary`() {
        val plan = plan(
            stage(
                id = "bloom",
                completionMode = StageCompletionMode.Immediate,
                advanceConstraint = StageAdvanceConstraint(notBeforeBrewElapsedMillis = 30_000L),
            ),
            stage("pour", StageCompletionMode.Countdown(60_000L)),
        )
        val started = SessionReducer.reduce(
            SessionRuntimeState.create(SESSION_ID, plan),
            SessionEvent.Start(),
            SessionClockReading(monotonicMillis = 0L, wallClockMillis = 1_000L),
        ).state
        val bloomCompleted = SessionReducer.reduce(
            started,
            SessionEvent.Reconcile(),
            SessionClockReading(monotonicMillis = 30_000L, wallClockMillis = 31_000L),
        ).state
        val laterReconciliation = SessionReducer.reduce(
            bloomCompleted,
            SessionEvent.Reconcile(),
            SessionClockReading(monotonicMillis = 91_000L, wallClockMillis = 92_000L),
        ).state
        val pourStarted = laterReconciliation.pendingEffects
            .filterIsInstance<PendingSessionEffect.StageAlert>()
            .single { effect ->
                effect.kind == StageAlertKind.STARTED && effect.stageInstanceId == plan.stages[1].instanceId
            }

        assertTrue(
            shouldPublishDurableBrewStageAlert(
                effect = pourStarted,
                runtime = laterReconciliation,
                backgroundedAtWallClockMillis = 20_000L,
            ),
        )
    }

    private fun lateCountdownReconciliation(): ReconciliationFixture {
        val plan = plan(
            stage("bloom", StageCompletionMode.Countdown(30_000L)),
            stage("pour", StageCompletionMode.Manual),
        )
        val initial = SessionRuntimeState.create(SESSION_ID, plan)
        val started = SessionReducer.reduce(
            initial,
            SessionEvent.Start(),
            SessionClockReading(monotonicMillis = 0L, wallClockMillis = 1_000L),
        ).state
        val reconciled = SessionReducer.reduce(
            started,
            SessionEvent.Reconcile(),
            SessionClockReading(monotonicMillis = 91_000L, wallClockMillis = 92_000L),
        ).state
        val alerts = reconciled.pendingEffects.filterIsInstance<PendingSessionEffect.StageAlert>()
        return ReconciliationFixture(
            runtime = reconciled,
            completionAlert = alerts.single { effect -> effect.kind == StageAlertKind.COMPLETED },
            startedAlert = alerts.single { effect ->
                effect.kind == StageAlertKind.STARTED && effect.stageInstanceId == plan.stages[1].instanceId
            },
        )
    }

    private fun plan(vararg definitions: BrewStageDefinition): CompiledStagePlan = CompiledStagePlan(
        id = StagePlanId("alert_policy_test"),
        version = 1,
        stages = definitions.mapIndexed { index, definition ->
            CompiledBrewStage(
                instanceId = StageInstanceId(definition.id, index + 1),
                definition = definition,
            )
        },
    )

    private fun stage(
        id: String,
        completionMode: StageCompletionMode,
        advanceConstraint: StageAdvanceConstraint = StageAdvanceConstraint(),
    ): BrewStageDefinition = BrewStageDefinition(
        id = StageId(id),
        action = BrewStageAction.CUSTOM,
        contentId = StageContentId("${id}_content"),
        completionMode = completionMode,
        advanceConstraint = advanceConstraint,
        alertPolicy = StageAlertPolicy(alertOnStart = true),
    )

    private data class ReconciliationFixture(
        val runtime: SessionRuntimeState,
        val completionAlert: PendingSessionEffect.StageAlert,
        val startedAlert: PendingSessionEffect.StageAlert,
    )

    private companion object {
        val SESSION_ID = SessionId("stage-alert-policy-test")
    }
}
