package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.ActiveClockAnchor
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockReconciliation
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockReconciliationKind
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEffectId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageActuals
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertKind
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertPolicy
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionKind
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageEquipmentRequirement
import com.adsamcik.starlitcoffee.domain.brewing.session.StageEquipmentStateId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMarkerId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageObservationId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStorageMapperTest {

    @Test
    fun `separate storage documents round trip compiled plan runtime effects and context`() {
        val state = sampleState()
        val context = sampleContext()

        val documents = SessionStorageMapper.encode(state, context)
        val restored = SessionStorageMapper.decodeAndRestore(
            compiledPlanJson = documents.compiledPlanJson,
            runtimeJson = documents.runtimeJson,
            executionContextJson = documents.executionContextJson,
        ) as SessionStorageRestoreResult.Restored

        assertEquals(state, restored.value.state)
        assertEquals(context, restored.value.executionContext)
        assertTrue(documents.compiledPlanJson.contains("\"kind\":\"COUNTDOWN\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"STAGE_ALERT\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"SCHEDULE_STAGE_DEADLINE\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"CANCEL_STAGE_DEADLINE\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"FINALIZE_BREW_LOG\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"CANCEL_SESSION_WORK\""))
    }

    @Test
    fun `future runtime schema remains inspectable and is not defaulted`() {
        val documents = SessionStorageMapper.encode(sampleState(), sampleContext())

        val restored = SessionStorageMapper.decodeAndRestore(
            compiledPlanJson = documents.compiledPlanJson,
            runtimeJson = "{\"schemaVersion\":2}",
            executionContextJson = documents.executionContextJson,
        ) as SessionStorageRestoreResult.UnsupportedDocument

        assertEquals(SessionStorageDocument.RUNTIME, restored.document)
        assertEquals(2, restored.schemaVersion)
        assertEquals("{\"schemaVersion\":2}", restored.rawJson)
    }

    @Test
    fun `unknown completion discriminator is invalid with its raw document retained`() {
        val snapshots = SessionStorageMapper.snapshot(sampleState(), sampleContext())
        val invalidPlan = snapshots.compiledPlan.copy(
            stages = snapshots.compiledPlan.stages.map { stage ->
                stage.copy(definition = stage.definition.copy(completion = stage.definition.completion.copy(kind = "FUTURE_MODE")))
            },
        )
        val invalidPlanJson = SessionStorageSnapshotCodec.encodeCompiledPlan(invalidPlan)

        val restored = SessionStorageMapper.decodeAndRestore(
            compiledPlanJson = invalidPlanJson,
            runtimeJson = SessionStorageSnapshotCodec.encodeRuntime(snapshots.runtime),
            executionContextJson = SessionStorageSnapshotCodec.encodeExecutionContext(snapshots.executionContext),
        ) as SessionStorageRestoreResult.InvalidDocument

        assertEquals(SessionStorageDocument.COMPILED_PLAN, restored.document)
        assertTrue(restored.reason.contains("Unknown stage completion discriminator"))
        assertEquals(invalidPlanJson, restored.rawJson)
    }

    @Test
    fun `missing root schema is reported as an invalid document`() {
        val decoded = SessionStorageSnapshotCodec.decodeExecutionContext("{}")

        assertTrue(decoded is com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult.Invalid)
    }

    private fun sampleState(): SessionRuntimeState {
        val sessionId = SessionId("session-storage-1")
        val bloom = CompiledBrewStage(
            instanceId = StageInstanceId(StageId("bloom"), 1),
            definition = BrewStageDefinition(
                id = StageId("bloom"),
                action = BrewStageAction.BLOOM,
                contentId = StageContentId("bloom_instruction"),
                instructionAssetId = InstructionAssetId("bloom_pour"),
                requiresIllustration = true,
                safetyMessages = listOf(
                    StageSafetyMessage("hot_water", StageSafetySeverity.WARNING),
                ),
                equipmentRequirement = StageEquipmentRequirement(StageEquipmentStateId("filter_rinsed")),
                completionMode = StageCompletionMode.Countdown(30_000L),
                alertPolicy = StageAlertPolicy(alertOnStart = true, alertOnCompletion = true),
            ),
        )
        val serve = CompiledBrewStage(
            instanceId = StageInstanceId(StageId("serve"), 1),
            definition = BrewStageDefinition(
                id = StageId("serve"),
                action = BrewStageAction.SERVE,
                contentId = StageContentId("serve_instruction"),
                completionMode = StageCompletionMode.ExternalMarker(StageMarkerId("served")),
                isSkippable = true,
            ),
        )
        val plan = CompiledStagePlan(
            id = StagePlanId("storage_test_plan"),
            version = 3,
            stages = listOf(bloom, serve),
        )

        return SessionRuntimeState(
            sessionId = sessionId,
            stagePlan = plan,
            status = BrewSessionStatus.RUNNING,
            currentStageIndex = 1,
            stageProgress = listOf(
                StageRuntimeProgress(
                    status = StageRunStatus.COMPLETED,
                    elapsedActiveMillis = 30_000L,
                    startedAtWallClockMillis = 1_000L,
                    completedAtWallClockMillis = 31_000L,
                    completionKind = StageCompletionKind.AUTOMATIC,
                    actuals = StageActuals(
                        addedAmountGrams = 60.0,
                        observations = setOf(StageObservationId("bloom_expanded")),
                    ),
                ),
                StageRuntimeProgress(
                    status = StageRunStatus.ACTIVE,
                    elapsedActiveMillis = 5_000L,
                    startedAtWallClockMillis = 31_000L,
                    actuals = StageActuals(
                        cumulativeAmountGrams = 300.0,
                        beverageYieldGrams = 250.0,
                        observations = setOf(StageObservationId("last_drip")),
                        markers = setOf(StageMarkerId("machine_signal")),
                    ),
                ),
            ),
            totalActiveElapsedMillis = 35_000L,
            activeClockAnchor = ActiveClockAnchor(monotonicMillis = 350L, wallClockMillis = 36_000L),
            startedAtWallClockMillis = 1_000L,
            updatedAtWallClockMillis = 36_000L,
            revision = 7L,
            processedEventIds = listOf(SessionEventId("start"), SessionEventId("record_bloom")),
            pendingEffects = listOf(
                PendingSessionEffect.StageAlert(
                    effectId = SessionEffectId("effect-alert"),
                    sessionId = sessionId,
                    stageInstanceId = bloom.instanceId,
                    kind = StageAlertKind.STARTED,
                ),
                PendingSessionEffect.ScheduleStageDeadline(
                    effectId = SessionEffectId("effect-schedule"),
                    sessionId = sessionId,
                    stageInstanceId = bloom.instanceId,
                    scheduleToken = "bloom-deadline",
                    dueAtWallClockMillis = 31_000L,
                ),
                PendingSessionEffect.CancelStageDeadline(
                    effectId = SessionEffectId("effect-cancel-deadline"),
                    sessionId = sessionId,
                    scheduleToken = "bloom-deadline",
                ),
                PendingSessionEffect.FinalizeBrewLog(
                    effectId = SessionEffectId("effect-finalize-log"),
                    sessionId = sessionId,
                ),
                PendingSessionEffect.CancelSessionWork(
                    effectId = SessionEffectId("effect-cancel-session"),
                    sessionId = sessionId,
                ),
            ),
            acknowledgedEffectIds = listOf(SessionEffectId("effect-acknowledged")),
            lastClockReconciliation = ClockReconciliation(
                kind = ClockReconciliationKind.MONOTONIC_TICK,
                observedDeltaMillis = 5_000L,
                appliedDeltaMillis = 5_000L,
            ),
        )
    }

    private fun sampleContext(): SessionExecutionContextSnapshotV1 = SessionExecutionContextSnapshotV1(
        coffeeBagId = 42L,
        sourceRecipeId = 99L,
        logPresentation = BrewLogPresentationContextSnapshotV1(
            methodLabel = "V60 02",
            doseG = 20.0,
            waterG = 300.0,
            ratio = 15.0,
            grindLabel = "Medium-fine",
            filterLabel = "Paper",
            notes = "Drawdown was even.",
        ),
    )
}
