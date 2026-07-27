package com.adsamcik.starlitcoffee.data.repository

import com.adsamcik.starlitcoffee.data.db.dao.ActiveBrewSessionDao
import com.adsamcik.starlitcoffee.data.db.entity.ActiveBrewSessionEntity
import kotlinx.coroutines.flow.Flow

/** Durable session storage boundary used by recovery, notification, and UI adapters. */
class ActiveBrewSessionRepository(
    private val dao: ActiveBrewSessionDao,
) {
    fun observeSession(sessionId: String): Flow<ActiveBrewSessionEntity?> = dao.observeById(sessionId)

    suspend fun getSession(sessionId: String): ActiveBrewSessionEntity? = dao.getById(sessionId)

    suspend fun getRecoverableSessions(): List<ActiveBrewSessionEntity> = dao.getRecoverable()

    suspend fun create(session: ActiveBrewSessionEntity): Long = dao.insert(session.copy(revision = 0L))

    /**
     * Returns the persisted next revision, or null when another owner already
     * transitioned the session and the caller must reload before retrying.
     */
    suspend fun saveIfCurrent(
        session: ActiveBrewSessionEntity,
        expectedRevision: Long,
    ): ActiveBrewSessionEntity? {
        val next = session.copy(revision = expectedRevision + 1L)
        val updated = dao.updateIfRevision(
            sessionId = next.sessionId,
            expectedRevision = expectedRevision,
            nextRevision = next.revision,
            status = next.status,
            recipeSnapshotVersion = next.recipeSnapshotVersion,
            recipeSnapshotJson = next.recipeSnapshotJson,
            compiledPlanSchemaVersion = next.compiledPlanSchemaVersion,
            compiledPlanJson = next.compiledPlanJson,
            runtimeSchemaVersion = next.runtimeSchemaVersion,
            runtimeJson = next.runtimeJson,
            executionContextSchemaVersion = next.executionContextSchemaVersion,
            executionContextJson = next.executionContextJson,
            currentStageId = next.currentStageId,
            currentStageIndex = next.currentStageIndex,
            startedAtWallClockMillis = next.startedAtWallClockMillis,
            pausedAtWallClockMillis = next.pausedAtWallClockMillis,
            deadlineAtWallClockMillis = next.deadlineAtWallClockMillis,
            scheduledEventToken = next.scheduledEventToken,
            notificationStateJson = next.notificationStateJson,
            lastProcessedEventId = next.lastProcessedEventId,
            completedLogId = next.completedLogId,
            updatedAt = next.updatedAt,
        )
        return next.takeIf { updated == 1 }
    }

    suspend fun delete(sessionId: String) = dao.deleteById(sessionId)
}
