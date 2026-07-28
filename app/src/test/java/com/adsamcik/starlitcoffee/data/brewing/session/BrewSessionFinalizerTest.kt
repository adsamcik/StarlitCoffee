package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewingSnapshotCodec
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.SnapshotDecodeResult
import com.adsamcik.starlitcoffee.data.db.entity.CoffeeBagEntity
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.data.repository.TransactionRunner
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionClockReading
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEventId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionReducer
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionRuntimeState
import com.adsamcik.starlitcoffee.testutil.FakeBrewLogDao
import com.adsamcik.starlitcoffee.testutil.FakeCoffeeBagDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionFinalizerTest {

    @Test
    fun `finalization creates an immutable session log rotates inventory and acknowledges its outbox`() = runTest {
        val fixture = finalizationFixture()

        val delivery = fixture.finalizer.deliver(fixture.effect, fixture.session)

        assertEquals(SessionEffectDelivery.AcknowledgedAtomically, delivery)
        val log = requireNotNull(fixture.brewLogDao.getBySourceSessionId(fixture.sessionId.value))
        assertEquals(1, fixture.brewLogDao.getAll().first().size)
        assertEquals(fixture.context.sourceRecipeId, log.recipeId)
        assertEquals(fixture.context.coffeeBagId, log.coffeeBagId)
        assertEquals("Experimental brewer", log.method)
        assertEquals("future_family", log.methodFamilyId)
        assertEquals("future_profile", log.brewerProfileId)
        assertEquals(fixture.sessionId.value, log.sourceSessionId)
        assertEquals(20f, log.doseG)
        assertEquals(310f, log.waterG)
        assertEquals(15.5f, log.ratio)
        assertEquals("6.5", log.grindSetting)
        assertEquals("future_mesh", log.filterType)
        assertTrue(log.isDecaf)
        assertEquals("Record exact session values", log.freeformNotes)

        val record = (BrewingSnapshotCodec.decodeRecord(requireNotNull(log.brewSnapshotJson))
            as SnapshotDecodeResult.Decoded).value
        assertEquals(fixture.recipe, record.recipe)
        assertEquals(fixture.sessionId.value, record.sourceSessionId)
        assertEquals(fixture.runtime.endedAtWallClockMillis, record.completedAtWallClockMillis)
        assertEquals(
            fixture.runtime.stageProgress.single().elapsedActiveMillis,
            record.stageActuals.single().elapsedActiveMillis,
        )
        assertEquals(
            fixture.runtime.stageProgress.single().completionKind?.name,
            record.stageActuals.single().completionKind,
        )

        val depleted = requireNotNull(fixture.coffeeBagDao.getByIdOnce(fixture.selectedBagId))
        val rotated = requireNotNull(fixture.coffeeBagDao.getByIdOnce(fixture.nextBagId))
        assertEquals("FINISHED", depleted.status)
        assertEquals(0f, depleted.weightG)
        assertEquals("6.5", depleted.grindSetting)
        assertEquals("OPEN", rotated.status)
        assertEquals(250f, rotated.weightG)
        assertEquals("6.5", rotated.grindSetting)

        val persisted = requireNotNull(fixture.sessionDao.current(fixture.sessionId.value))
        val restored = ActiveBrewSessionEntityMapper.restore(persisted)
            as ActiveBrewSessionRestoreResult.Restored
        assertEquals(log.id, persisted.completedLogId)
        assertFalse(restored.value.runtime.pendingEffects.any { it.effectId == fixture.effect.effectId })
        assertTrue(restored.value.runtime.acknowledgedEffectIds.contains(fixture.effect.effectId))
    }

    @Test
    fun `retry after a committed log does not duplicate the record or consume inventory twice`() = runTest {
        val fixture = finalizationFixture()
        val restored = ActiveBrewSessionEntityMapper.restore(fixture.entity)
            as ActiveBrewSessionRestoreResult.Restored
        val selectedBeforeFirstAttempt = requireNotNull(
            fixture.coffeeBagDao.getByIdOnce(fixture.selectedBagId),
        )
        val nextBeforeFirstAttempt = requireNotNull(
            fixture.coffeeBagDao.getByIdOnce(fixture.nextBagId),
        )
        val planned = BrewSessionCompletionPlanner.plan(
            session = restored.value,
            currentCoffeeBag = selectedBeforeFirstAttempt,
            nextSealedCoffeeBag = nextBeforeFirstAttempt,
        )
        val existingLogId = fixture.brewLogDao.insertIfSourceSessionIsNew(planned.brewLog)
        for (bag in planned.coffeeBagUpdates) {
            fixture.coffeeBagDao.update(bag)
        }
        val selectedAfterCommittedLog = requireNotNull(
            fixture.coffeeBagDao.getByIdOnce(fixture.selectedBagId),
        )
        val nextAfterCommittedLog = requireNotNull(
            fixture.coffeeBagDao.getByIdOnce(fixture.nextBagId),
        )

        val delivery = fixture.finalizer.deliver(fixture.effect, fixture.session)

        assertEquals(SessionEffectDelivery.AcknowledgedAtomically, delivery)
        assertEquals(existingLogId, requireNotNull(
            fixture.brewLogDao.getBySourceSessionId(fixture.sessionId.value),
        ).id)
        assertEquals(1, fixture.brewLogDao.getAll().first().size)
        assertEquals(selectedAfterCommittedLog, fixture.coffeeBagDao.getByIdOnce(fixture.selectedBagId))
        assertEquals(nextAfterCommittedLog, fixture.coffeeBagDao.getByIdOnce(fixture.nextBagId))

        val persisted = requireNotNull(fixture.sessionDao.current(fixture.sessionId.value))
        val durable = ActiveBrewSessionEntityMapper.restore(persisted)
            as ActiveBrewSessionRestoreResult.Restored
        assertEquals(existingLogId, persisted.completedLogId)
        assertFalse(durable.value.runtime.pendingEffects.any { it.effectId == fixture.effect.effectId })
        assertTrue(durable.value.runtime.acknowledgedEffectIds.contains(fixture.effect.effectId))
    }

    private suspend fun finalizationFixture(): FinalizationFixture {
        val sessionId = SessionId("finalizer-session")
        val recipe = BrewRecipeSnapshotV1(
            methodFamilyId = "future_family",
            brewerProfileId = "future_profile",
            equipment = EquipmentConfigurationSnapshotV1(brewerProfileId = "future_profile"),
            quantities = BrewQuantitiesSnapshotV1(dryCoffeeDoseG = 20.0, brewWaterInputG = 310.0),
            ratioDefinition = RatioDefinitionSnapshotV1(
                numerator = "BREW_WATER_INPUT",
                denominator = "DRY_COFFEE_DOSE",
            ),
            ratioValue = 15.5,
            outputModel = OutputModelSnapshotV1(kind = "BREW_WATER_MINUS_RETENTION"),
        )
        val coffeeBagDao = FakeCoffeeBagDao()
        val selectedBagId = coffeeBagDao.insert(
            CoffeeBagEntity(
                name = "Night Owl",
                roaster = "Starlit",
                status = "OPEN",
                weightG = 15f,
                grindSetting = "5.5",
                createdAt = 1L,
            ),
        )
        val nextBagId = coffeeBagDao.insert(
            CoffeeBagEntity(
                name = "Night Owl",
                roaster = "Starlit",
                status = "SEALED",
                weightG = 250f,
                createdAt = 2L,
            ),
        )
        val context = SessionExecutionContextSnapshotV1(
            coffeeBagId = selectedBagId,
            sourceRecipeId = 12L,
            logPresentation = BrewLogPresentationContextSnapshotV1(
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
        val runtime = completedRuntime(sessionId)
        val entity = ActiveBrewSessionEntityMapper.create(
            recipe = recipe,
            runtime = runtime,
            executionContext = context,
            nowWallClockMillis = requireNotNull(runtime.endedAtWallClockMillis),
        )
        val sessionDao = FakeActiveBrewSessionDao()
        sessionDao.insert(entity)
        val brewLogDao = FakeBrewLogDao()
        val effect = runtime.pendingEffects.filterIsInstance<PendingSessionEffect.FinalizeBrewLog>()
            .single()
        val session = ActiveBrewSession(recipe, runtime, context)
        val finalizer = BrewSessionFinalizer(
            transactionRunner = TransactionRunner.Direct,
            sessionRepository = ActiveBrewSessionRepository(sessionDao),
            brewLogDao = brewLogDao,
            coffeeBagDao = coffeeBagDao,
            nowWallClockMillis = { 3_000L },
        )
        return FinalizationFixture(
            finalizer = finalizer,
            session = session,
            sessionId = sessionId,
            effect = effect,
            recipe = recipe,
            context = context,
            runtime = runtime,
            entity = entity,
            sessionDao = sessionDao,
            brewLogDao = brewLogDao,
            coffeeBagDao = coffeeBagDao,
            selectedBagId = selectedBagId,
            nextBagId = nextBagId,
        )
    }

    private fun completedRuntime(sessionId: SessionId): SessionRuntimeState {
        val initial = SessionRuntimeState.create(
            sessionId = sessionId,
            stagePlan = ActiveBrewSessionTestFixtures.plan(alertOnStart = false),
        )
        val started = SessionReducer.reduce(
            state = initial,
            event = SessionEvent.Start(SessionEventId("start")),
            now = SessionClockReading(monotonicMillis = 0L, wallClockMillis = 1_000L),
        ).state
        return SessionReducer.reduce(
            state = started,
            event = SessionEvent.Finish(SessionEventId("finish")),
            now = SessionClockReading(monotonicMillis = 2_000L, wallClockMillis = 3_000L),
        ).state
    }

    private data class FinalizationFixture(
        val finalizer: BrewSessionFinalizer,
        val session: ActiveBrewSession,
        val sessionId: SessionId,
        val effect: PendingSessionEffect.FinalizeBrewLog,
        val recipe: BrewRecipeSnapshotV1,
        val context: SessionExecutionContextSnapshotV1,
        val runtime: SessionRuntimeState,
        val entity: com.adsamcik.starlitcoffee.data.db.entity.ActiveBrewSessionEntity,
        val sessionDao: FakeActiveBrewSessionDao,
        val brewLogDao: FakeBrewLogDao,
        val coffeeBagDao: FakeCoffeeBagDao,
        val selectedBagId: Long,
        val nextBagId: Long,
    )
}
