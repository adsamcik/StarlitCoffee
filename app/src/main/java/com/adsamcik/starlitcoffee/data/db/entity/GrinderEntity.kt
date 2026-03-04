package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_grinders")
data class GrinderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val brand: String,
    val model: String,
    val isManual: Boolean,
    val scaleType: String,
    val clicksPerRotation: Int? = null,
    val calibrationStyle: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
