package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_recipes")
data class SavedRecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val coffeeName: String? = null,
    val roaster: String? = null,
    val roastLevel: String? = null,
    val processType: String? = null,
    val method: String,
    val ratio: Float,
    val doseG: Float,
    val waterG: Float,
    val grinderId: String? = null,
    val grindSetting: String? = null,
    val filterType: String? = null,
    val isDecaf: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
