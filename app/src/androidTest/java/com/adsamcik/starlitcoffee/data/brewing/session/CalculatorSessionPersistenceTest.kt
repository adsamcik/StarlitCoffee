package com.adsamcik.starlitcoffee.data.brewing.session

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.adsamcik.starlitcoffee.data.db.AppDatabase
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.CalcToken
import com.adsamcik.starlitcoffee.data.repository.ActiveBrewSessionRepository
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewSessionStatus
import com.adsamcik.starlitcoffee.domain.brewing.session.ClockedSessionEngine
import com.adsamcik.starlitcoffee.domain.brewing.session.MonotonicClock
import com.adsamcik.starlitcoffee.domain.brewing.session.SessionEvent
import com.adsamcik.starlitcoffee.domain.brewing.session.WallClock
import com.adsamcik.starlitcoffee.viewmodel.BrewUiState
import com.adsamcik.starlitcoffee.viewmodel.CalcUiState
import com.adsamcik.starlitcoffee.viewmodel.CalculatorBrewSessionStartFactory
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises Android Room on disk and new runtime owners; scheduling/notification delivery is tested separately. */
class CalculatorSessionPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun allMethodCustomRemindersRetainOriginThroughEditAndRoomRecreation() = runBlocking<Unit> {
        val name = "custom-timer-session-test-${UUID.randomUUID()}"
        val requests = BrewMethod.entries.map(::request)
        try {
            open(name).useForTest { database ->
                val coordinator = coordinator(database, monotonic = 100L, wall = 1_000L)
                requests.forEach { request ->
                    val created = coordinator.createOrResume(request) as BrewSessionOperationResult.Active
                    coordinator.dispatch(request.sessionId, SessionEvent.SetTimerTarget(
                        requireNotNull(created.session.runtime.currentStage).instanceId, 120_000L))
                    coordinator.dispatch(request.sessionId, SessionEvent.SetGuidancePaused(true))
                }
            }
            open(name).useForTest { database ->
                val coordinator = coordinator(database, monotonic = 5L, wall = 31_000L)
                requests.forEach { request ->
                    val restored = coordinator.dispatch(request.sessionId, SessionEvent.Restore())
                        as BrewSessionOperationResult.Active
                    val timer = restored.session.runtime.userTimer
                    // A completed recipe-controlled bloom legitimately removes its stage-bound reminder.
                    assertTrue(timer != null)
                    assertEquals(1_000L, timer!!.originWallClockMillis)
                    assertEquals(121_000L, timer.deadlineAtWallClockMillis)
                    assertEquals(90_000L, timer.remainingMillis(30_000L))
                    val edited = coordinator.dispatch(request.sessionId, SessionEvent.SetTimerTarget(
                        timer.stageInstanceId, 180_000L)) as BrewSessionOperationResult.Active
                    assertEquals(2L, edited.session.runtime.userTimer?.revision)
                    assertEquals(181_000L, edited.session.runtime.userTimer?.deadlineAtWallClockMillis)
                    assertTrue(edited.session.runtime.isGuidancePaused)
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun guidePauseRestoresFromRoomWhileAllMethodPhysicalClocksContinue() = runBlocking<Unit> {
        val name = "guide-paused-session-test-${UUID.randomUUID()}"
        val requests = BrewMethod.entries.map(::request)
        try {
            open(name).useForTest { database ->
                val coordinator = coordinator(database, monotonic = 100L, wall = 1_000L)
                requests.forEach { request ->
                    coordinator.createOrResume(request)
                    coordinator.dispatch(request.sessionId, SessionEvent.SetGuidancePaused(true))
                }
            }
            open(name).useForTest { database ->
                val restored = coordinator(database, monotonic = 5L, wall = 61_000L)
                    .reconcileRecoverableSessions().map { it as BrewSessionOperationResult.Active }
                    .associateBy { it.session.runtime.sessionId }
                assertEquals(requests.size, restored.size)
                requests.forEach { request ->
                    val session = requireNotNull(restored[request.sessionId]).session
                    assertTrue(session.runtime.isGuidancePaused)
                    assertEquals(BrewSessionStatus.RUNNING, session.runtime.status)
                    assertEquals(60_000L, session.runtime.totalActiveElapsedMillis)
                    assertEquals(1_000L, session.runtime.startedAtWallClockMillis)
                    assertEquals(request.recipe, session.recipe)
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun everyCalculatorMethodRestoresItsPhysicalClockAndFrozenSetupFromRoom() = runBlocking<Unit> {
        val name = "calculator-session-test-${UUID.randomUUID()}"
        val requests = BrewMethod.entries.associateWith(::request)
        try {
            open(name).useForTest { database ->
                val coordinator = coordinator(database, monotonic = 100L, wall = 1_000L)
                requests.forEach { (method, request) ->
                    val created = coordinator.createOrResume(request, startImmediately = !method.hasBloom)
                        as BrewSessionOperationResult.Active
                    assertEquals(if (method.hasBloom) BrewSessionStatus.READY else BrewSessionStatus.RUNNING,
                        created.session.runtime.status)
                    if (method.hasBloom) {
                        assertNull(created.session.runtime.startedAtWallClockMillis)
                        coordinator.dispatch(request.sessionId, SessionEvent.Start())
                    }
                }
            }
            // A reopened database and a new coordinator have no ViewModel or prior monotonic anchor.
            open(name).useForTest { database ->
                val restored = coordinator(database, monotonic = 5L, wall = 6_500L)
                    .reconcileRecoverableSessions().map { it as BrewSessionOperationResult.Active }
                    .associateBy { it.session.runtime.sessionId }
                assertEquals(BrewMethod.entries.size, restored.size)
                requests.values.forEach { request ->
                    val session = requireNotNull(restored[request.sessionId]).session
                    assertEquals(request.recipe, session.recipe)
                    assertEquals(request.executionContext, session.executionContext)
                    assertEquals(request.stagePlan, session.runtime.stagePlan)
                    assertEquals(1_000L, session.runtime.startedAtWallClockMillis)
                    assertEquals(5_500L, session.runtime.totalActiveElapsedMillis)
                    assertEquals(BrewSessionStatus.RUNNING, session.runtime.status)
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun unstartedBloomSurvivesReopeningWithoutCountingPreparationTime() = runBlocking<Unit> {
        val name = "prepared-session-test-${UUID.randomUUID()}"
        val request = request(BrewMethod.CHEMEX)
        try {
            open(name).useForTest { database ->
                coordinator(database, monotonic = 100L, wall = 1_000L)
                    .createOrResume(request, startImmediately = false)
            }
            open(name).useForTest { database ->
                val coordinator = coordinator(database, monotonic = 5L, wall = 101_000L)
                val resumed = coordinator.createOrResume(request, startImmediately = false)
                    as BrewSessionOperationResult.Active
                assertEquals(BrewSessionStatus.READY, resumed.session.runtime.status)
                assertEquals(0L, resumed.session.runtime.totalActiveElapsedMillis)
                assertNull(resumed.session.runtime.startedAtWallClockMillis)
                val started = coordinator.dispatch(request.sessionId, SessionEvent.Start())
                    as BrewSessionOperationResult.Active
                assertEquals(101_000L, started.session.runtime.startedAtWallClockMillis)
                assertEquals(0L, started.session.runtime.totalActiveElapsedMillis)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun open(name: String): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name).build()

    private inline fun AppDatabase.useForTest(block: (AppDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private fun coordinator(database: AppDatabase, monotonic: Long, wall: Long) = BrewSessionCoordinator(
        sessionRepository = ActiveBrewSessionRepository(database.activeBrewSessionDao()),
        clockedEngine = ClockedSessionEngine(MonotonicClock { monotonic }, WallClock { wall }),
        effectHandler = object : BrewSessionEffectHandler {
            override suspend fun deliver(effect: com.adsamcik.starlitcoffee.domain.brewing.session.PendingSessionEffect,
                session: ActiveBrewSession) = SessionEffectDelivery.Delivered
        },
    )

    private fun request(method: BrewMethod): BrewSessionStartRequest = requireNotNull(
        CalculatorBrewSessionStartFactory().create(
            calculator = CalcUiState(
                brewMethod = method,
                tokens = listOf(CalcToken.Number("20")),
                previewDoseG = 20f,
                previewWaterMl = 20f * method.defaultRatio,
                previewBeverageG = 20f * method.defaultRatio - 2f,
                ratio = method.defaultRatio,
                hasValidExpression = true,
            ),
            preparation = BrewUiState(method = method, effectiveBloomDurationSeconds = 45),
            selectedCoffeeBagId = 91L,
        ),
    )
}
