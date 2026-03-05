package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flavor_tags",
    foreignKeys = [
        ForeignKey(
            entity = BrewLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["brewLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("brewLogId")],
)
data class FlavorTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val brewLogId: Long,
    val descriptor: String,
    val intensity: Int? = null,
)
