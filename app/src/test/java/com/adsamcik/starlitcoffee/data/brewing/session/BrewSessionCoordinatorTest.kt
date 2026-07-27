package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockedSessionEngine
import com.adsamcik.starlitcoffee.domain.brewing.session.MonotonicClock
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.WallClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionCoordinatorTest {

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
