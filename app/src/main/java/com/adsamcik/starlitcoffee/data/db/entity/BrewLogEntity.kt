package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "brew_logs",
    indices = [
        Index("coffeeBagId"),
        Index("recipeId"),
        Index(value = ["sourceSessionId"], unique = true),
    ]
)
data class BrewLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val recipeId: Long? = null,
    val coffeeBagId: Long? = null,
    val method: String,
    val methodFamilyId: String? = null,
    val brewerProfileId: String? = null,
    val snapshotVersion: Int? = null,
    val brewSnapshotJson: String? = null,
    val sourceSessionId: String? = null,
    val doseG: Float,
    val waterG: Float,
    val ratio: Float,
    val grindSetting: String? = null,
    val filterType: String? = null,
    val isDecaf: Boolean = false,
    val tasteFeedback: String? = null,
    val rating: Float? = null,
    val freeformNotes: String? = null,
    val brewTimeSeconds: Int? = null,
    /** Frozen estimate shown when this brew was planned. */
    val expectedBeverageOutputG: Float? = null,
    /** Optional scale reading reported after brewing. Stored only as a complete pair. */
    val measuredWaterInputG: Float? = null,
    val measuredBeverageOutputG: Float? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
