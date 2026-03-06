package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "brew_logs",
    indices = [
        Index("coffeeBagId"),
        Index("recipeId"),
    ]
)
data class BrewLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val recipeId: Long? = null,
    val coffeeBagId: Long? = null,
    val method: String,
    val doseG: Float,
    val waterG: Float,
    val ratio: Float,
    val grindSetting: String? = null,
    val filterType: String? = null,
    val tasteFeedback: String? = null,
    val rating: Float? = null,
    val freeformNotes: String? = null,
    val brewTimeSeconds: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
