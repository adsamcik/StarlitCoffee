package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingSnapshotCodec
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult
import com.adsamcik.starlitcoffee.data.brewing.snapshot.StageActualSnapshotV1
import com.adsamcik.starlitcoffee.data.db.entity.ActiveBrewSessionEntity
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.domain.brewing.session.StageActuals
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionKind
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageMarkerId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageObservationId
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRunStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.StageRuntimeProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionCompletionPlannerTest {

    @Test
    fun `completion plan preserves immutable session data in versioned and legacy log fields`() {
        val session = completedSession(
            recipe = recipe(
                methodFamilyId = "future_family",
                brewerProfileId = "future_profile",
            ),
            context = executionContext(
                methodLabel = "Experimental brewer",
                doseG = 20.0,
                waterG = 310.0,
                ratio = 15.5,
                grindLabel = "6.5",
                filterLabel = "future_mesh",
                isDecaf = true,
                notes = "Record exact session values",
            ),
        )
        val bag = coffeeBag(
            id = 7L,
            status = "SEALED",
            weightG = 30f,
            grindSetting = "5.5",
        )

        val plan = BrewSessionCompletionPlanner.plan(session, currentCoffeeBag = bag)

        assertEquals("future_family", plan.brewRecord.recipe.methodFamilyId)
        assertEquals("future_profile", plan.brewRecord.recipe.brewerProfileId)
        assertEquals("session-42", plan.brewRecord.sourceSessionId)
        assertEquals(COMPLETED_AT, plan.brewRecord.completedAtWallClockMillis)
        assertEquals(
            listOf(
                StageActualSnapshotV1(
                    stageInstanceId = "bloom_1",
                    elapsedActiveMillis = 30_000L,
                    addedAmountG = 50.0,
                    cumulativeAmountG = 50.0,
                    observationIds = listOf("bloom_seen"),
                    markerIds = listOf("bloom_marked"),
                    completionKind = "MANUAL",
                ),
                StageActualSnapshotV1(
                    stageInstanceId = "pour_1",
                    elapsedActiveMillis = 60_500L,
                    beverageYieldG = 270.0,
                    observationIds = listOf("drawdown_seen"),
                    markerIds = listOf("serve_marked"),
                    completionKind = "MEASURED",
                ),
            ),
            plan.brewRecord.stageActuals,
        )

        val log = plan.brewLog
        assertEquals(12L, log.recipeId)
        assertEquals(7L, log.coffeeBagId)
        assertEquals("Experimental brewer", log.method)
        assertEquals("future_family", log.methodFamilyId)
        assertEquals("future_profile", log.brewerProfileId)
        assertEquals(1, log.snapshotVersion)
        assertEquals("session-42", log.sourceSessionId)
        assertEquals(20f, log.doseG)
        assertEquals(310f, log.waterG)
        assertEquals(15.5f, log.ratio)
        assertEquals("6.5", log.grindSetting)
        assertEquals("future_mesh", log.filterType)
        assertTrue(log.isDecaf)
        assertEquals("Record exact session values", log.freeformNotes)
        assertEquals(90, log.brewTimeSeconds)
        assertEquals(COMPLETED_AT, log.createdAt)
        val storedRecord = BrewingSnapshotCodec.decodeRecord(requireNotNull(log.brewSnapshotJson))
        assertEquals(plan.brewRecord, (storedRecord as SnapshotDecodeResult.Decoded).value)

        assertEquals(1, plan.coffeeBagUpdates.size)
        val updatedBag = plan.coffeeBagUpdates.single()
        assertEquals("OPEN", updatedBag.status)
        assertEquals(COMPLETED_AT, updatedBag.openedDate)
        assertEquals(10f, updatedBag.weightG)
        assertEquals("6.5", updatedBag.grindSetting)
        assertEquals(null, plan.rotatedToCoffeeBagId)
    }

    @Test
    fun `completion plan finishes a depleted bag and opens the supplied next sealed bag`() {
        val session = completedSession(
            context = executionContext(doseG = 20.0, grindLabel = "7.0"),
        )
        val current = coffeeBag(id = 7L, status = "SEALED", weightG = 15f)
        val next = coffeeBag(id = 8L, status = "SEALED", weightG = 250f)

        val plan = BrewSessionCompletionPlanner.plan(
            session = session,
            currentCoffeeBag = current,
            nextSealedCoffeeBag = next,
        )

        assertEquals(8L, plan.rotatedToCoffeeBagId)
        assertEquals(2, plan.coffeeBagUpdates.size)
        val depleted = plan.coffeeBagUpdates[0]
        assertEquals(7L, depleted.id)
        assertEquals("FINISHED", depleted.status)
        assertEquals(0f, depleted.weightG)
        assertEquals(COMPLETED_AT, depleted.openedDate)
        assertEquals("7.0", depleted.grindSetting)
        val rotated = plan.coffeeBagUpdates[1]
        assertEquals(8L, rotated.id)
        assertEquals("OPEN", rotated.status)
        assertEquals(250f, rotated.weightG)
        assertEquals(COMPLETED_AT, rotated.openedDate)
        assertEquals("7.0", rotated.grindSetting)
    }

    @Test
    fun `completion plan never mutates a bag that does not match the durable selection`() {
        val session = completedSession()

        val plan = BrewSessionCompletionPlanner.plan(
            session = session,
            currentCoffeeBag = coffeeBag(id = 99L, status = "SEALED", weightG = 20f),
            nextSealedCoffeeBag = coffeeBag(id = 8L, status = "SEALED", weightG = 250f),
        )

        assertTrue(plan.coffeeBagUpdates.isEmpty())
        assertEquals(null, plan.rotatedToCoffeeBagId)
        assertEquals(7L, plan.brewLog.coffeeBagId)
    }

    @Test
    fun `completion plan refuses a session whose log was already recorded`() {
        val session = completedSession(completedLogId = 500L)

        val failure = runCatching {
            BrewSessionCompletionPlanner.plan(session, currentCoffeeBag = coffeeBag(id = 7L))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    private fun completedSession(
        recipe: BrewRecipeSnapshotV1 = recipe(),
        context: SessionExecutionContextSnapshotV1 = executionContext(),
        completedLogId: Long? = null,
    ): RestoredActiveBrewSession {
        val stagePlan = CompiledStagePlan(
            id = StagePlanId("completion_test"),
            version = 1,
            stages = listOf(
                compiledStage("bloom", BrewStageAction.BLOOM),
                compiledStage("pour", BrewStageAction.POUR),
            ),
        )
        val runtime = SessionRuntimeState(
            sessionId = SessionId("session-42"),
            stagePlan = stagePlan,
            status = BrewSessionStatus.COMPLETED,
            currentStageIndex = null,
            stageProgress = listOf(
                StageRuntimeProgress(
                    status = StageRunStatus.COMPLETED,
                    elapsedActiveMillis = 30_000L,
                    completedAtWallClockMillis = COMPLETED_AT - 60_500L,
                    completionKind = StageCompletionKind.MANUAL,
                    actuals = StageActuals(
                        addedAmountGrams = 50.0,
                        cumulativeAmountGrams = 50.0,
                        observations = setOf(StageObservationId("bloom_seen")),
                        markers = setOf(StageMarkerId("bloom_marked")),
                    ),
                ),
                StageRuntimeProgress(
                    status = StageRunStatus.COMPLETED,
                    elapsedActiveMillis = 60_500L,
                    completedAtWallClockMillis = COMPLETED_AT,
                    completionKind = StageCompletionKind.MEASURED,
                    actuals = StageActuals(
                        beverageYieldGrams = 270.0,
                        observations = setOf(StageObservationId("drawdown_seen")),
                        markers = setOf(StageMarkerId("serve_marked")),
                    ),
                ),
            ),
            totalActiveElapsedMillis = 90_500L,
            startedAtWallClockMillis = COMPLETED_AT - 90_500L,
            endedAtWallClockMillis = COMPLETED_AT,
            updatedAtWallClockMillis = COMPLETED_AT,
            revision = 4L,
        )
        return RestoredActiveBrewSession(
            entity = ActiveBrewSessionEntity(
                sessionId = "session-42",
                status = BrewSessionStatus.COMPLETED.name,
                recipeSnapshotVersion = 1,
                recipeSnapshotJson = "{}",
                compiledPlanSchemaVersion = 1,
                compiledPlanJson = "{}",
                runtimeSchemaVersion = 1,
                runtimeJson = "{}",
                completedLogId = completedLogId,
                revision = 4L,
                createdAt = COMPLETED_AT - 90_500L,
                updatedAt = COMPLETED_AT,
            ),
            recipe = recipe,
            runtime = runtime,
            executionContext = context,
        )
    }

    private fun compiledStage(
        stageId: String,
        action: BrewStageAction,
    ): CompiledBrewStage {
        val sourceStageId = StageId(stageId)
        return CompiledBrewStage(
            instanceId = StageInstanceId(sourceStageId, occurrence = 1),
            definition = BrewStageDefinition(
                id = sourceStageId,
                action = action,
                contentId = StageContentId("${stageId}_instruction"),
                completionMode = StageCompletionMode.Manual,
            ),
        )
    }

    private fun recipe(
        methodFamilyId: String = "manual_gravity",
        brewerProfileId: String = "v60_02",
    ): BrewRecipeSnapshotV1 = BrewRecipeSnapshotV1(
        methodFamilyId = methodFamilyId,
        brewerProfileId = brewerProfileId,
        equipment = EquipmentConfigurationSnapshotV1(brewerProfileId = brewerProfileId),
        quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 20.0, brewWaterInputG = 300.0),
        ratioDefinition = RatioDefinitionSnapshotV1(
            numerator = "BREW_WATER_INPUT",
            denominator = "DRY_COFFEE_DOSE",
        ),
        ratioValue = 15.0,
        outputModel = OutputModelSnapshotV1(kind = "BREW_WATER_MINUS_RETENTION"),
    )

    private fun executionContext(
        methodLabel: String = "V60 02",
        doseG: Double = 20.0,
        waterG: Double = 300.0,
        ratio: Double = 15.0,
        grindLabel: String? = "Medium-fine",
        filterLabel: String? = "Paper",
        isDecaf: Boolean = false,
        notes: String? = "Session note",
    ): SessionExecutionContextSnapshotV1 = SessionExecutionContextSnapshotV1(
        coffeeBagId = 7L,
        sourceRecipeId = 12L,
        logPresentation = BrewLogPresentationContextSnapshotV1(
            methodLabel = methodLabel,
            doseG = doseG,
            waterG = waterG,
            ratio = ratio,
            grindLabel = grindLabel,
            filterLabel = filterLabel,
            isDecaf = isDecaf,
            notes = notes,
        ),
    )

    private fun coffeeBag(
        id: Long,
        status: String = "OPEN",
        weightG: Float? = null,
        grindSetting: String? = null,
    ): CoffeeBagEntity = CoffeeBagEntity(
        id = id,
        name = "Night Owl",
        roaster = "Starlit",
        status = status,
        weightG = weightG,
        grindSetting = grindSetting,
        createdAt = 1L,
    )

    private companion object {
        const val COMPLETED_AT = 1_700_000_000_000L
    }
}
