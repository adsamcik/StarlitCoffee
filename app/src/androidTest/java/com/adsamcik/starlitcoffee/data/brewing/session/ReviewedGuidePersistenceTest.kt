package com.adsamcik.starlitcoffee.data.brewing.session

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.brewing.guides.ReviewedMethodGuideLibrary
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockedSessionEngine
import com.adsamcik.starlitcoffee.domain.brewing.session.MonotonicClock
import com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.WallClock
import com.adsamcik.starlitcoffee.ui.guidance.*
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.ReviewedGuideStartFactory
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Own UUID database; no user packs, production sessions or OS alarms are modified. */
class ReviewedGuidePersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val guides get() = ReviewedMethodGuideLibrary.decode(context.assets
        .open(ReviewedMethodGuideLibrary.ASSET_PATH).bufferedReader().use { it.readText() })
    private var monotonic = 100L
    private var wall = 1_000L
    private val gate = P1ExactRecipeReleaseGate(BuiltInP1ExactGuidanceLoadResult.Unavailable("test"),
        InstructionAssetCatalog(emptyList()))

    @Test fun seventeenRunningGuidesRestoreWithFrozenSourcesClocksAndRemindersAndReadAheadHasNoEffects() = runBlocking {
        val name = "reviewed-guides-${UUID.randomUUID()}"
        val requests = guides.map { ReviewedGuideStartFactory.create(it, BrewUiState(), null, 21.3) }
        try {
            val first = open(name)
            try {
                val coordinator = coordinator(first)
                requests.forEach { request ->
                    coordinator.createOrResume(request)
                    val guide = requireNotNull(request.recipe.reviewedGuide)
                    repeat(guide.steps.indexOfFirst { it.id == guide.originStepId }) {
                        coordinator.dispatch(request.sessionId, SessionEvent.ManualAdvance())
                    }
                    val started = active(coordinator.dispatch(request.sessionId, SessionEvent.Start()))
                    assertTrue(guide.id, started.runtime.hasPhysicalClockStarted)
                    coordinator.dispatch(request.sessionId, SessionEvent.SetTimerTarget(
                        requireNotNull(started.runtime.currentStage).instanceId, 600_000L))
                    coordinator.dispatch(request.sessionId, SessionEvent.SetGuidancePaused(true))
                }
            } finally { first.close() }
            monotonic = 5L
            wall = 61_000L
            val second = open(name)
            try {
                val coordinator = coordinator(second)
                requests.forEach { request ->
                    monotonic = 5L
                    wall = 61_000L
                    val restored = active(coordinator.dispatch(request.sessionId, SessionEvent.Restore()))
                    assertEquals(request.recipe, restored.recipe)
                    assertEquals(request.stagePlan, restored.runtime.stagePlan)
                    assertEquals(60_000L, restored.runtime.totalActiveElapsedMillis)
                    assertEquals(1_000L, restored.runtime.userTimer?.originWallClockMillis)
                    assertTrue(restored.runtime.isGuidancePaused)
                    assertEquals(request.physicalClock?.startStageId, restored.runtime.currentStage?.instanceId)
                    val before = second.activeBrewSessionDao().getById(request.sessionId.value)
                    val reader = requireNotNull(resolveBrewReadAheadGuide(restored.recipe,
                        restored.runtime.stagePlan.stages.map { it.definition }, gate, DurableBrewSessionGuidancePreferences()))
                    assertEquals(request.recipe.reviewedGuide?.steps?.size, reader.resolution.content.size)
                    assertEquals(before, second.activeBrewSessionDao().getById(request.sessionId.value))
                    // Expiry alone cannot move a physical stage. Even a day later the user's confirmation is required.
                    monotonic += 86_400_000L
                    wall += 86_400_000L
                    val afterTick = active(coordinator.dispatch(request.sessionId, SessionEvent.Tick()))
                    assertEquals(restored.runtime.currentStage?.instanceId, afterTick.runtime.currentStage?.instanceId)
                    assertEquals(BrewSessionStatus.RUNNING, afterTick.runtime.status)
                    coordinator.dispatch(request.sessionId, SessionEvent.Cancel())
                }
            } finally { second.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun everyReviewedProcedureCanCompleteThroughManualConfirmationsWithoutAnAutomaticMachineAction() = runBlocking {
        val name = "reviewed-completion-${UUID.randomUUID()}"
        val database = open(name)
        try {
            val coordinator = coordinator(database)
            guides.forEach { guide ->
                val request = ReviewedGuideStartFactory.create(guide, BrewUiState(), null, 21.3)
                var session = active(coordinator.createOrResume(request))
                request.stagePlan.stages.forEach { stage ->
                    if (stage.instanceId == request.physicalClock?.startStageId) {
                        session = active(coordinator.dispatch(request.sessionId, SessionEvent.Start()))
                    }
                    assertEquals(stage.instanceId, session.runtime.currentStage?.instanceId)
                    monotonic += 604_800_000L
                    wall += 604_800_000L
                    session = active(coordinator.dispatch(request.sessionId, SessionEvent.ManualAdvance()))
                }
                assertEquals(guide.id, BrewSessionStatus.COMPLETED, session.runtime.status)
                assertNotNull(session.runtime.physicalClock?.endedElapsedMillis)
                assertTrue(session.runtime.stageProgress.all { it.completionKind?.name == "MANUAL" })
            }
        } finally { database.close(); context.deleteDatabase(name) }
    }

    private fun open(name: String) = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
    private fun active(result: BrewSessionOperationResult) = (result as BrewSessionOperationResult.Active).session
    private fun coordinator(database: AppDatabase) = BrewSessionCoordinator(ActiveBrewSessionRepository(database.activeBrewSessionDao()),
        ClockedSessionEngine(MonotonicClock { monotonic }, WallClock { wall }), object : BrewSessionEffectHandler {
            override suspend fun deliver(effect: PendingSessionEffect, session: ActiveBrewSession) = SessionEffectDelivery.Delivered
        })
}
