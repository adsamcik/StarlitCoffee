package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_barcode_stems",
    indices = [Index("prefix", unique = true)],
)
data class UserBarcodeStemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prefix: String,
    val roasterName: String,
    val observationCount: Int = 1,
    val confidence: String = "LOW",
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
)
