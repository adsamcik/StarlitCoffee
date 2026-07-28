package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockedSessionEngine
import com.adsamcik.starlitcoffee.domain.brewing.session.MonotonicClock
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionId
import com.adsamcik.starlitcoffee.domain.brewing.session.WallClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionStatusNotifierTest {

    @Test
    fun `publishes only an exactly restored running snapshot`() = runTest {
        val sessionId = SessionId("status-running")
        val repository = repositoryWithStartedSession(sessionId)
        val notifier = RecordingStatusNotifier()

        publishRestoredBrewSessionStatus(sessionId, repository, notifier)

        assertEquals(listOf(sessionId), notifier.published.map { it.runtime.sessionId })
        assertEquals(BrewSessionStatus.RUNNING, notifier.published.single().runtime.status)
        assertTrue(notifier.cleared.isEmpty())
    }

    @Test
    fun `clears stale status for paused and missing sessions`() = runTest {
        val sessionId = SessionId("status-paused")
        val repository = repositoryWithStartedSession(sessionId)
        val coordinator = coordinatorFor(repository)
        coordinator.dispatch(sessionId, SessionEvent.Pause())
        val notifier = RecordingStatusNotifier()

        publishRestoredBrewSessionStatus(sessionId, repository, notifier)
        publishRestoredBrewSessionStatus(SessionId("status-missing"), repository, notifier)

        assertTrue(notifier.published.isEmpty())
        assertEquals(listOf(sessionId, SessionId("status-missing")), notifier.cleared)
    }

    private suspend fun repositoryWithStartedSession(sessionId: SessionId): ActiveBrewSessionRepository {
        val dao = FakeActiveBrewSessionDao()
        val repository = ActiveBrewSessionRepository(dao)
        val coordinator = coordinatorFor(repository)
        coordinator.createOrResume(
            BrewSessionStartRequest(
                sessionId = sessionId,
                recipe = ActiveBrewSessionTestFixtures.recipe(),
                stagePlan = ActiveBrewSessionTestFixtures.plan(alertOnStart = false),
                executionContext = ActiveBrewSessionTestFixtures.executionContext(),
            ),
        )
        return repository
    }

    private fun coordinatorFor(repository: ActiveBrewSessionRepository): BrewSessionCoordinator =
        BrewSessionCoordinator(
            sessionRepository = repository,
            clockedEngine = ClockedSessionEngine(
                monotonicClock = MonotonicClock { 100L },
                wallClock = WallClock { 1_000L },
            ),
            effectHandler = object : BrewSessionEffectHandler {
                override suspend fun deliver(
                    effect: PendingSessionEffect,
                    session: ActiveBrewSession,
                ): SessionEffectDelivery = SessionEffectDelivery.Delivered
            },
        )

    private class RecordingStatusNotifier : BrewSessionStatusNotifier {
        val published = mutableListOf<ActiveBrewSession>()
        val cleared = mutableListOf<SessionId>()

        override fun publish(session: ActiveBrewSession) {
            published += session
        }

        override fun clear(sessionId: SessionId) {
            cleared += sessionId
        }
    }
}
