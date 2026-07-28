package com.adsamcik.starlitcoffee.data.brewing.session

import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewQuantitiesSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.BrewRecipeSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.EquipmentConfigurationSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.OutputModelSnapshotV1
import com.adsamcik.starlitcoffee.data.brewing.snapshot.RatioDefinitionSnapshotV1
import com.adsamcik.starlitcoffee.data.db.dao.ActiveBrewSessionDao
import com.adsamcik.starlitcoffee.data.db.entity.ActiveBrewSessionEntity
import com.adsamcik.starlitcoffee.domain.brewing.StageContentId
import com.adsamcik.starlitcoffee.domain.brewing.StageId
import com.adsamcik.starlitcoffee.domain.brewing.StagePlanId
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageAction
import com.adsamcik.starlitcoffee.domain.brewing.session.BrewStageDefinition
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledBrewStage
import com.adsamcik.starlitcoffee.domain.brewing.session.CompiledStagePlan
import com.adsamcik.starlitcoffee.domain.brewing.session.StageAlertPolicy
import com.adsamcik.starlitcoffee.domain.brewing.session.StageCompletionMode
import com.adsamcik.starlitcoffee.domain.brewing.session.StageInstanceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal object ActiveBrewSessionTestFixtures {

    fun recipe(): BrewRecipeSnapshotV1 = BrewRecipeSnapshotV1(
        methodFamilyId = "manual_gravity",
        brewerProfileId = "v60_02",
        equipment = EquipmentConfigurationSnapshotV1(
            brewerProfileId = "v60_02",
        ),
        quantities = BrewQuantitiesSnapshotV1(
            dryCoffeeDoseG = 20.0,
            brewWaterInputG = 300.0,
        ),
        ratioDefinition = RatioDefinitionSnapshotV1(
            numerator = "BREW_WATER_INPUT",
            denominator = "DRY_COFFEE_DOSE",
        ),
        ratioValue = 15.0,
        outputModel = OutputModelSnapshotV1(kind = "BREW_WATER_MINUS_RETENTION"),
    )

    fun executionContext(): SessionExecutionContextSnapshotV1 = SessionExecutionContextSnapshotV1(
        coffeeBagId = 7L,
        sourceRecipeId = 12L,
        logPresentation = BrewLogPresentationContextSnapshotV1(
            methodLabel = "V60 02",
            doseG = 20.0,
            waterG = 300.0,
            ratio = 15.0,
            grindLabel = "Medium-fine",
            filterLabel = "Paper",
            notes = "Persistent session test",
        ),
    )

    fun plan(
        completionMode: StageCompletionMode = StageCompletionMode.Manual,
        alertOnStart: Boolean = true,
    ): CompiledStagePlan {
        val definition = BrewStageDefinition(
            id = StageId("brew"),
            action = BrewStageAction.POUR,
            contentId = StageContentId("brew_instruction"),
            completionMode = completionMode,
            alertPolicy = StageAlertPolicy(alertOnStart = alertOnStart),
        )
        return CompiledStagePlan(
            id = StagePlanId("durable_session_test"),
            version = 1,
            stages = listOf(
                CompiledBrewStage(
                    instanceId = StageInstanceId(definition.id, 1),
                    definition = definition,
                ),
            ),
        )
    }
}

/** A deterministic in-memory implementation that also records durable write ordering. */
internal class FakeActiveBrewSessionDao : ActiveBrewSessionDao {
    private val sessions = linkedMapOf<String, ActiveBrewSessionEntity>()
    private val flows = mutableMapOf<String, MutableStateFlow<ActiveBrewSessionEntity?>>()
    private val recoverableSessionsFlow = MutableStateFlow<List<ActiveBrewSessionEntity>>(emptyList())

    val operations = mutableListOf<String>()

    fun current(sessionId: String): ActiveBrewSessionEntity? = sessions[sessionId]

    override suspend fun insert(session: ActiveBrewSessionEntity): Long {
        check(session.sessionId !in sessions) { "Duplicate test session ${session.sessionId}" }
        sessions[session.sessionId] = session
        flowFor(session.sessionId).value = session
        publishRecoverableSessions()
        operations += "insert:${session.revision}"
        return 1L
    }

    @Suppress("LongParameterList")
    override suspend fun updateIfRevision(
        sessionId: String,
        expectedRevision: Long,
        nextRevision: Long,
        status: String,
        recipeSnapshotVersion: Int,
        recipeSnapshotJson: String,
        compiledPlanSchemaVersion: Int,
        compiledPlanJson: String,
        runtimeSchemaVersion: Int,
        runtimeJson: String,
        executionContextSchemaVersion: Int?,
        executionContextJson: String?,
        currentStageId: String?,
        currentStageIndex: Int?,
        startedAtWallClockMillis: Long?,
        pausedAtWallClockMillis: Long?,
        deadlineAtWallClockMillis: Long?,
        scheduledEventToken: String?,
        notificationStateJson: String?,
        lastProcessedEventId: String?,
        completedLogId: Long?,
        updatedAt: Long,
    ): Int {
        val current = sessions[sessionId]
        if (current == null || current.revision != expectedRevision) {
            operations += "cas_conflict:$expectedRevision"
            return 0
        }
        val next = current.copy(
            status = status,
            recipeSnapshotVersion = recipeSnapshotVersion,
            recipeSnapshotJson = recipeSnapshotJson,
            compiledPlanSchemaVersion = compiledPlanSchemaVersion,
            compiledPlanJson = compiledPlanJson,
            runtimeSchemaVersion = runtimeSchemaVersion,
            runtimeJson = runtimeJson,
            executionContextSchemaVersion = executionContextSchemaVersion,
            executionContextJson = executionContextJson,
            currentStageId = currentStageId,
            currentStageIndex = currentStageIndex,
            startedAtWallClockMillis = startedAtWallClockMillis,
            pausedAtWallClockMillis = pausedAtWallClockMillis,
            deadlineAtWallClockMillis = deadlineAtWallClockMillis,
            scheduledEventToken = scheduledEventToken,
            notificationStateJson = notificationStateJson,
            lastProcessedEventId = lastProcessedEventId,
            completedLogId = completedLogId,
            revision = nextRevision,
            updatedAt = updatedAt,
        )
        sessions[sessionId] = next
        flowFor(sessionId).value = next
        publishRecoverableSessions()
        operations += "cas:$expectedRevision->$nextRevision"
        return 1
    }

    override suspend fun getById(sessionId: String): ActiveBrewSessionEntity? = sessions[sessionId]

    override suspend fun getRecoverable(): List<ActiveBrewSessionEntity> = recoverableSessions()

    override fun observeRecoverable(): Flow<List<ActiveBrewSessionEntity>> = recoverableSessionsFlow

    override fun observeById(sessionId: String): Flow<ActiveBrewSessionEntity?> = flowFor(sessionId)

    override suspend fun delete(session: ActiveBrewSessionEntity) {
        deleteById(session.sessionId)
    }

    override suspend fun deleteById(sessionId: String) {
        sessions.remove(sessionId)
        flowFor(sessionId).value = null
        publishRecoverableSessions()
    }

    private fun flowFor(sessionId: String): MutableStateFlow<ActiveBrewSessionEntity?> =
        flows.getOrPut(sessionId) { MutableStateFlow(sessions[sessionId]) }

    private fun recoverableSessions(): List<ActiveBrewSessionEntity> = sessions.values
        .filter { session ->
            session.status in setOf("READY", "RUNNING", "PAUSED") ||
                (session.status == "COMPLETED" && session.completedLogId == null)
        }
        .sortedByDescending(ActiveBrewSessionEntity::updatedAt)

    private fun publishRecoverableSessions() {
        recoverableSessionsFlow.value = recoverableSessions()
    }
}
