package com.adsamcik.starlitcoffee.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bag_scan_evidence",
    foreignKeys = [
        ForeignKey(
            entity = CoffeeBagEntity::class,
            parentColumns = ["id"],
            childColumns = ["bagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bagId")],
)
data class BagScanEvidenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bagId: Long,
    val fieldName: String,
    val resolvedValue: String,
    val confidence: String,
    val sourceType: String,
    val rawOcrText: String? = null,
    val supportingText: String? = null,
    val scanTimestamp: Long = System.currentTimeMillis(),
)
