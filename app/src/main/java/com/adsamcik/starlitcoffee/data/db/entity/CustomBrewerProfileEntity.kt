package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Built-in profiles stay in the checked-in catalogue; only user-owned profiles persist here. */
@Entity(
    tableName = "custom_brewer_profiles",
    indices = [Index("methodFamilyId")],
)
data class CustomBrewerProfileEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val methodFamilyId: String,
    val schemaVersion: Int,
    val profileJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
