package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.ActiveBrewSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveBrewSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ActiveBrewSessionEntity): Long

    /**
     * Atomically writes a reducer transition only when the caller's loaded
     * revision is still current. This prevents a worker and a visible screen
     * from silently overwriting one another after process recovery.
     */
    @Query(
        """
        UPDATE active_brew_sessions
        SET status = :status,
            recipeSnapshotVersion = :recipeSnapshotVersion,
            recipeSnapshotJson = :recipeSnapshotJson,
            compiledPlanSchemaVersion = :compiledPlanSchemaVersion,
            compiledPlanJson = :compiledPlanJson,
            runtimeSchemaVersion = :runtimeSchemaVersion,
            runtimeJson = :runtimeJson,
            executionContextSchemaVersion = :executionContextSchemaVersion,
            executionContextJson = :executionContextJson,
            currentStageId = :currentStageId,
            currentStageIndex = :currentStageIndex,
            startedAtWallClockMillis = :startedAtWallClockMillis,
            pausedAtWallClockMillis = :pausedAtWallClockMillis,
            deadlineAtWallClockMillis = :deadlineAtWallClockMillis,
            scheduledEventToken = :scheduledEventToken,
            notificationStateJson = :notificationStateJson,
            lastProcessedEventId = :lastProcessedEventId,
            completedLogId = :completedLogId,
            revision = :nextRevision,
            updatedAt = :updatedAt
        WHERE sessionId = :sessionId AND revision = :expectedRevision
        """,
    )
    // Room binds this flat optimistic-lock update directly to named SQL columns; a wrapper
    // object would hide the query contract without reducing the database API.
    @Suppress("LongParameterList")
    suspend fun updateIfRevision(
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
    ): Int

    @Query("SELECT * FROM active_brew_sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): ActiveBrewSessionEntity?

    @Query(
        "SELECT * FROM active_brew_sessions WHERE status IN ('READY', 'RUNNING', 'PAUSED') " +
            "OR (status = 'COMPLETED' AND completedLogId IS NULL) ORDER BY updatedAt DESC",
    )
    suspend fun getRecoverable(): List<ActiveBrewSessionEntity>

    /**
     * Keeps the calculator's contextual resume action in sync with durable
     * sessions that can still progress or still need their completion logged.
     */
    @Query(
        "SELECT * FROM active_brew_sessions WHERE status IN ('READY', 'RUNNING', 'PAUSED') " +
            "OR (status = 'COMPLETED' AND completedLogId IS NULL) ORDER BY updatedAt DESC",
    )
    fun observeRecoverable(): Flow<List<ActiveBrewSessionEntity>>

    @Query("SELECT * FROM active_brew_sessions WHERE sessionId = :sessionId")
    fun observeById(sessionId: String): Flow<ActiveBrewSessionEntity?>

    @Delete
    suspend fun delete(session: ActiveBrewSessionEntity)

    @Query("DELETE FROM active_brew_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: String)
}
