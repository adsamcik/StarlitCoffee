package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockedSessionEngine
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.MonotonicClock
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.WallClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionCoordinatorTest {

    @Test
    fun `waiting for physical start persists no extraction clock or effects`() = runTest {
        val dao = FakeActiveBrewSessionDao()
        val handler = RecordingEffectHandler(dao, SessionEffectDelivery.Delivered)
        val coordinator = coordinator(dao, handler)
        val request = startRequest(SessionId("awaiting-first-water"))
        val created = coordinator.createOrResume(request, startImmediately = false)
            as BrewSessionOperationResult.Active
        assertEquals(BrewSessionStatus.READY, created.session.runtime.status)
        assertEquals(null, created.session.runtime.startedAtWallClockMillis)
        assertEquals(null, created.session.runtime.activeClockAnchor)
        assertEquals(0L, created.session.runtime.totalActiveElapsedMillis)
        assertTrue(handler.delivered.isEmpty())
        assertEquals(listOf("insert:0"), dao.operations)
        val resumed = coordinator.createOrResume(request, startImmediately = false)
            as BrewSessionOperationResult.Active
        assertEquals(created.session, resumed.session)
        assertEquals(1, dao.operations.size)
        val started = coordinator.dispatch(request.sessionId, SessionEvent.Start())
            as BrewSessionOperationResult.Active
        assertEquals(BrewSessionStatus.RUNNING, started.session.runtime.status)
        assertEquals(1_000L, started.session.runtime.startedAtWallClockMillis)
        assertEquals(1, handler.delivered.size)
    }

    @Test
    fun `discarding a prepared session does not log coffee or start a clock`() = runTest {
        val dao = FakeActiveBrewSessionDao()
        val handler = RecordingEffectHandler(dao, SessionEffectDelivery.Delivered)
        val coordinator = coordinator(dao, handler)
        val request = startRequest(SessionId("discard-preparation"))
        coordinator.createOrResume(request, startImmediately = false)
        val cancelled = coordinator.dispatch(request.sessionId, SessionEvent.Cancel())
            as BrewSessionOperationResult.Active
        assertEquals(BrewSessionStatus.CANCELLED, cancelled.session.runtime.status)
        assertEquals(null, cancelled.session.runtime.startedAtWallClockMillis)
        assertEquals(0L, cancelled.session.runtime.totalActiveElapsedMillis)
        assertTrue(handler.delivered.none { it is PendingSessionEffect.FinalizeBrewLog })
        assertTrue(handler.delivered.none { it is PendingSessionEffect.ScheduleStageDeadline })
    }

    @Test
    fun `start transition is durable before effect delivery and acknowledgement`() = runTest {
        val dao = FakeActiveBrewSessionDao()
        val handler = RecordingEffectHandler(dao, SessionEffectDelivery.Delivered)
        val sessionId = SessionId("coordinator-delivered")
        val coordinator = coordinator(dao, handler)

        val result = coordinator.createOrResume(startRequest(sessionId))

        val active = result as BrewSessionOperationResult.Active
        val persisted = requireNotNull(dao.current(sessionId.value))
        val restored = ActiveBrewSessionEntityMapper.restore(persisted)
            as ActiveBrewSessionRestoreResult.Restored
        assertEquals(2L, active.session.runtime.revision)
        assertTrue(active.session.runtime.pendingEffects.isEmpty())
        assertEquals(active.session.runtime, restored.value.runtime)
        assertTrue(restored.value.runtime.acknowledgedEffectIds.isNotEmpty())
        assertEquals(
            listOf("insert:0", "cas:0->1", "deliver:1", "cas:1->2"),
            dao.operations,
        )
        assertEquals(1, handler.delivered.size)
        assertTrue(handler.delivered.single() is PendingSessionEffect.StageAlert)
    }

    @Test
    fun `deferred effect stays in the persisted outbox for recovery`() = runTest {
        val dao = FakeActiveBrewSessionDao()
        val handler = RecordingEffectHandler(
            dao = dao,
            delivery = SessionEffectDelivery.Deferred("notification permission unavailable"),
        )
        val sessionId = SessionId("coordinator-deferred")
        val coordinator = coordinator(dao, handler)

        val result = coordinator.createOrResume(startRequest(sessionId))

        val pending = result as BrewSessionOperationResult.PendingEffect
        val persisted = requireNotNull(dao.current(sessionId.value))
        val restored = ActiveBrewSessionEntityMapper.restore(persisted)
            as ActiveBrewSessionRestoreResult.Restored
        assertEquals("notification permission unavailable", pending.reason)
        assertEquals(1L, restored.value.runtime.revision)
        assertEquals(listOf(pending.effect.effectId), restored.value.runtime.pendingEffects.map { it.effectId })
        assertFalse(restored.value.runtime.acknowledgedEffectIds.contains(pending.effect.effectId))
        assertEquals(listOf("insert:0", "cas:0->1", "deliver:1"), dao.operations)
        assertNotNull(persisted)
    }

    private fun coordinator(
        dao: FakeActiveBrewSessionDao,
        handler: BrewSessionEffectHandler,
    ): BrewSessionCoordinator = BrewSessionCoordinator(
        sessionRepository = ActiveBrewSessionRepository(dao),
        clockedEngine = ClockedSessionEngine(
            monotonicClock = MonotonicClock { 100L },
            wallClock = WallClock { 1_000L },
        ),
        effectHandler = handler,
    )

    private fun startRequest(sessionId: SessionId): BrewSessionStartRequest = BrewSessionStartRequest(
        sessionId = sessionId,
        recipe = ActiveBrewSessionTestFixtures.recipe(),
        stagePlan = ActiveBrewSessionTestFixtures.plan(),
        executionContext = ActiveBrewSessionTestFixtures.executionContext(),
    )

    private class RecordingEffectHandler(
        private val dao: FakeActiveBrewSessionDao,
        private val delivery: SessionEffectDelivery,
    ) : BrewSessionEffectHandler {
        val delivered = mutableListOf<PendingSessionEffect>()

        override suspend fun deliver(
            effect: PendingSessionEffect,
            session: ActiveBrewSession,
        ): SessionEffectDelivery {
            val durable = dao.current(session.runtime.sessionId.value)
            assertNotNull("The outbox effect must be saved before delivery", durable)
            val persisted = requireNotNull(durable)
            val restored = ActiveBrewSessionEntityMapper.restore(persisted)
                as ActiveBrewSessionRestoreResult.Restored
            assertEquals(session.runtime.revision, persisted.revision)
            assertEquals(effect.effectId, restored.value.runtime.pendingEffects.first().effectId)
            dao.operations += "deliver:${persisted.revision}"
            delivered += effect
            return delivery
        }
    }
}
