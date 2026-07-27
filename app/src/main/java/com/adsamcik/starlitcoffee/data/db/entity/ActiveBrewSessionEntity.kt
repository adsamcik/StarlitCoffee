package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One durable in-progress brew. Recipe, compiled plan, and runtime each carry
 * their own explicit schema version so recovery never needs to infer meaning
 * from a later catalogue revision.
 */
@Entity(
    tableName = "active_brew_sessions",
    indices = [
        Index("status"),
        Index("updatedAt"),
    ],
)
data class ActiveBrewSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val status: String,
    val recipeSnapshotVersion: Int,
    val recipeSnapshotJson: String,
    val compiledPlanSchemaVersion: Int,
    val compiledPlanJson: String,
    val runtimeSchemaVersion: Int,
    val runtimeJson: String,
    val executionContextSchemaVersion: Int? = null,
    val executionContextJson: String? = null,
    val currentStageId: String? = null,
    val currentStageIndex: Int? = null,
    val startedAtWallClockMillis: Long? = null,
    val pausedAtWallClockMillis: Long? = null,
    val deadlineAtWallClockMillis: Long? = null,
    val scheduledEventToken: String? = null,
    val notificationStateJson: String? = null,
    val lastProcessedEventId: String? = null,
    val completedLogId: Long? = null,
    val revision: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
