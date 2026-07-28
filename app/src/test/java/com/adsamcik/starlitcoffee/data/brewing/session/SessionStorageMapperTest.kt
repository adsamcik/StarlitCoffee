package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult
import com.adsamcik.starlitcoffee.domain.brewing.InstructionAssetId
import com.adsamcik.starlitcoffee.domain.brewing.QuantityRole
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
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMassTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageObservationId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageReferenceTargets
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetyMessage
import com.adsamcik.starlitcoffee.domain.brewing.session.StageSafetySeverity
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTargetQualifier
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTemperatureTarget
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeReference
import com.adsamcik.starlitcoffee.domain.brewing.session.StageTimeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        assertTrue(documents.compiledPlanJson.contains("\"reference\":\"STAGE_DURATION\""))
        assertTrue(documents.compiledPlanJson.contains("\"role\":\"BREW_WATER_INPUT\""))
        assertTrue(documents.compiledPlanJson.contains("\"reference\":\"BREW_CUMULATIVE\""))
        assertTrue(documents.compiledPlanJson.contains("\"minimumC\":92.0"))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"STAGE_ALERT\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"SCHEDULE_STAGE_DEADLINE\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"CANCEL_STAGE_DEADLINE\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"FINALIZE_BREW_LOG\""))
        assertTrue(documents.runtimeJson.contains("\"kind\":\"CANCEL_SESSION_WORK\""))
    }

    @Test
    fun `versioned plan and runtime mappers round trip without schema drift`() {
        val state = sampleState()

        val planSnapshot = CompiledStagePlanSnapshotMapperV1.toSnapshot(state.stagePlan)
        val restoredPlan = CompiledStagePlanSnapshotMapperV1.toDomain(planSnapshot)
        val runtimeSnapshot = SessionRuntimeSnapshotMapperV1.toSnapshot(state)
        val restoredRuntime = SessionRuntimeSnapshotMapperV1.toDomain(runtimeSnapshot, restoredPlan)

        assertEquals(CompiledStagePlanSnapshotV1.SCHEMA_VERSION, planSnapshot.schemaVersion)
        assertEquals(SessionRuntimeSnapshotV1.SCHEMA_VERSION, runtimeSnapshot.schemaVersion)
        assertEquals(state.stagePlan, restoredPlan)
        assertEquals(state, restoredRuntime)
    }

    @Test
    fun `set backed actuals serialize deterministically while ID histories retain chronology`() {
        val state = sampleState().withFirstStageActualSetOrder(
            observations = listOf(
                StageObservationId("z_observation"),
                StageObservationId("a_observation"),
            ),
            markers = listOf(
                StageMarkerId("z_marker"),
                StageMarkerId("a_marker"),
            ),
        ).copy(
            processedEventIds = listOf(SessionEventId("z_event"), SessionEventId("a_event")),
            acknowledgedEffectIds = listOf(SessionEffectId("z_effect"), SessionEffectId("a_effect")),
        )
        val stateWithReverseSetInsertion = state.withFirstStageActualSetOrder(
            observations = listOf(
                StageObservationId("a_observation"),
                StageObservationId("z_observation"),
            ),
            markers = listOf(
                StageMarkerId("a_marker"),
                StageMarkerId("z_marker"),
            ),
        )

        val runtimeSnapshot = SessionRuntimeSnapshotMapperV1.toSnapshot(state)

        assertEquals(
            listOf("a_observation", "z_observation"),
            runtimeSnapshot.stageProgress[0].actuals.observationIds,
        )
        assertEquals(
            listOf("a_marker", "z_marker"),
            runtimeSnapshot.stageProgress[0].actuals.markerIds,
        )
        assertEquals(listOf("z_event", "a_event"), runtimeSnapshot.processedEventIds)
        assertEquals(listOf("z_effect", "a_effect"), runtimeSnapshot.acknowledgedEffectIds)
        assertEquals(
            SessionStorageMapper.encode(state, sampleContext()).runtimeJson,
            SessionStorageMapper.encode(stateWithReverseSetInsertion, sampleContext()).runtimeJson,
        )
    }

    @Test
    fun `pre-reference-target V1 plan restores with empty source cues`() {
        val legacyPlanJson =
            """
            {
              "schemaVersion": 1,
              "stagePlanId": "legacy_plan",
              "stagePlanVersion": 1,
              "stages": [
                {
                  "instance": {
                    "sourceStageId": "legacy_stage",
                    "occurrence": 1
                  },
                  "definition": {
                    "stageId": "legacy_stage",
                    "action": "CUSTOM",
                    "contentId": "legacy_content",
                    "completion": { "kind": "MANUAL" }
                  }
                }
              ]
            }
            """.trimIndent()
        val decodedPlan = (
            SessionStorageSnapshotCodec.decodeCompiledPlan(legacyPlanJson)
                as SnapshotDecodeResult.Decoded
            ).value
        val restored = SessionStorageMapper.restore(
            SessionStorageSnapshots(
                compiledPlan = decodedPlan,
                runtime = SessionRuntimeSnapshotV1(
                    sessionId = "legacy_session",
                    status = BrewSessionStatus.READY.name,
                    currentStageIndex = 0,
                    stageProgress = listOf(
                        StageRuntimeProgressSnapshotV1(status = StageRunStatus.PENDING.name),
                    ),
                ),
                executionContext = sampleContext(),
            ),
        ) as SessionStorageRestoreResult.Restored

        assertEquals(
            StageReferenceTargets(),
            restored.value.state.stagePlan.stages.single().definition.referenceTargets,
        )
    }

    @Test
    fun `legacy mass discriminators restore every supported quantity semantic`() {
        fun legacyTarget(kind: String) = StageMassTargetSnapshotV1(
            kind = kind,
            qualifier = StageTargetQualifier.EXACT.name,
            minimumGrams = 100.0,
            maximumGrams = 100.0,
        )

        val restored = StageReferenceTargetsSnapshotMapper.toDomain(
            StageReferenceTargetsSnapshotV1(
                massTargets = listOf(
                    legacyTarget("ADDED_WATER"),
                    legacyTarget("CUMULATIVE_WATER"),
                    legacyTarget("BEVERAGE_YIELD"),
                ),
            ),
        )

        assertEquals(
            listOf(
                QuantityRole.BREW_WATER_INPUT to StageMassReference.STAGE_ADDED,
                QuantityRole.BREW_WATER_INPUT to StageMassReference.BREW_CUMULATIVE,
                QuantityRole.BEVERAGE_YIELD to StageMassReference.RECIPE_TOTAL,
            ),
            restored.massTargets.map { target -> target.role to target.reference },
        )
        assertThrows(IllegalArgumentException::class.java) {
            StageReferenceTargetsSnapshotMapper.toDomain(
                StageReferenceTargetsSnapshotV1(
                    massTargets = listOf(
                        legacyTarget("ADDED_WATER").copy(
                            role = QuantityRole.BREW_WATER_INPUT.name,
                        ),
                    ),
                ),
            )
        }
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
                stage.copy(
                    definition = stage.definition.copy(
                        completion = stage.definition.completion.copy(kind = "FUTURE_MODE"),
                    ),
                )
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
    fun `unknown reference target discriminator is invalid with its raw document retained`() {
        val snapshots = SessionStorageMapper.snapshot(sampleState(), sampleContext())
        val invalidPlan = snapshots.compiledPlan.copy(
            stages = snapshots.compiledPlan.stages.mapIndexed { index, stage ->
                if (index != 0) {
                    stage
                } else {
                    stage.copy(
                        definition = stage.definition.copy(
                            referenceTargets = stage.definition.referenceTargets.copy(
                                timeTargets = stage.definition.referenceTargets.timeTargets.map { target ->
                                    target.copy(reference = "FUTURE_CLOCK")
                                },
                            ),
                        ),
                    )
                }
            },
        )
        val invalidPlanJson = SessionStorageSnapshotCodec.encodeCompiledPlan(invalidPlan)

        val restored = SessionStorageMapper.decodeAndRestore(
            compiledPlanJson = invalidPlanJson,
            runtimeJson = SessionStorageSnapshotCodec.encodeRuntime(snapshots.runtime),
            executionContextJson = SessionStorageSnapshotCodec.encodeExecutionContext(snapshots.executionContext),
        ) as SessionStorageRestoreResult.InvalidDocument

        assertEquals(SessionStorageDocument.COMPILED_PLAN, restored.document)
        assertTrue(restored.reason.contains("Unknown time reference"))
        assertEquals(invalidPlanJson, restored.rawJson)
    }

    @Test
    fun `reference targets without cue IDs receive deterministic legacy identities`() {
        val snapshots = SessionStorageMapper.snapshot(sampleState(), sampleContext())
        val legacyPlan = snapshots.compiledPlan.copy(
            stages = snapshots.compiledPlan.stages.map { stage ->
                stage.copy(
                    definition = stage.definition.copy(
                        referenceTargets = stage.definition.referenceTargets.copy(
                            timeTargets = stage.definition.referenceTargets.timeTargets.map { target ->
                                target.copy(id = null)
                            },
                            massTargets = stage.definition.referenceTargets.massTargets
                                .mapIndexed { index, target ->
                                    target.copy(
                                        id = null,
                                        role = null,
                                        reference = null,
                                        kind = listOf("ADDED_WATER", "CUMULATIVE_WATER")[index],
                                    )
                                },
                        ),
                    ),
                )
            },
        )

        val restored = SessionStorageMapper.restore(
            snapshots.copy(compiledPlan = legacyPlan),
        ) as SessionStorageRestoreResult.Restored

        assertEquals(
            listOf(
                "stage_duration_1",
                "brew_elapsed_at_start_2",
                "brew_elapsed_at_completion_3",
            ),
            restored.value.state.stagePlan.stages.first().definition.referenceTargets.timeTargets
                .map { target -> target.id.value },
        )
        assertEquals(
            listOf(
                "brew_water_input_stage_added_1",
                "brew_water_input_brew_cumulative_2",
            ),
            restored.value.state.stagePlan.stages.first().definition.referenceTargets.massTargets
                .map { target -> target.id.value },
        )
    }

    @Test
    fun `missing root schema is reported as an invalid document`() {
        val decoded = SessionStorageSnapshotCodec.decodeExecutionContext("{}")

        assertTrue(decoded is com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult.Invalid)
    }

    private fun SessionRuntimeState.withFirstStageActualSetOrder(
        observations: List<StageObservationId>,
        markers: List<StageMarkerId>,
    ): SessionRuntimeState = copy(
        stageProgress = stageProgress.mapIndexed { index, progress ->
            if (index == 0) {
                progress.copy(
                    actuals = progress.actuals.copy(
                        observations = observations.toCollection(linkedSetOf()),
                        markers = markers.toCollection(linkedSetOf()),
                    ),
                )
            } else {
                progress
            }
        },
    )

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
                referenceTargets = StageReferenceTargets(
                    timeTargets = listOf(
                        StageTimeTarget(
                            id = StageTargetId("bloom_duration"),
                            reference = StageTimeReference.STAGE_DURATION,
                            qualifier = StageTargetQualifier.APPROXIMATE,
                            minimumMillis = 30_000L,
                        ),
                        StageTimeTarget(
                            id = StageTargetId("bloom_start"),
                            reference = StageTimeReference.BREW_ELAPSED_AT_START,
                            qualifier = StageTargetQualifier.EXACT,
                            minimumMillis = 0L,
                        ),
                        StageTimeTarget(
                            id = StageTargetId("bloom_complete"),
                            reference = StageTimeReference.BREW_ELAPSED_AT_COMPLETION,
                            qualifier = StageTargetQualifier.RANGE,
                            minimumMillis = 30_000L,
                            maximumMillis = 45_000L,
                        ),
                    ),
                    massTargets = listOf(
                        StageMassTarget(
                            id = StageTargetId("bloom_added_water"),
                            role = QuantityRole.BREW_WATER_INPUT,
                            reference = StageMassReference.STAGE_ADDED,
                            qualifier = StageTargetQualifier.EXACT,
                            minimumGrams = 60.0,
                        ),
                        StageMassTarget(
                            id = StageTargetId("bloom_cumulative_water"),
                            role = QuantityRole.BREW_WATER_INPUT,
                            reference = StageMassReference.BREW_CUMULATIVE,
                            qualifier = StageTargetQualifier.EXACT,
                            minimumGrams = 60.0,
                        ),
                    ),
                    temperatureTarget = StageTemperatureTarget(
                        qualifier = StageTargetQualifier.RANGE,
                        minimumC = 92.0,
                        maximumC = 96.0,
                    ),
                ),
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
