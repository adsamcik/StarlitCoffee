package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.UserBarcodeStemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserBarcodeStemDao {
    @Query(
        "SELECT * FROM user_barcode_stems " +
            "WHERE :barcode LIKE prefix || '%' " +
            "ORDER BY LENGTH(prefix) DESC LIMIT 1",
    )
    suspend fun findStemForBarcode(barcode: String): UserBarcodeStemEntity?

    @Query("SELECT * FROM user_barcode_stems ORDER BY observationCount DESC")
    fun getAllStems(): Flow<List<UserBarcodeStemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stem: UserBarcodeStemEntity): Long

    @Query(
        "UPDATE user_barcode_stems SET " +
            "observationCount = observationCount + 1, " +
            "lastSeenAt = :now, " +
            "confidence = CASE WHEN observationCount >= 1 THEN 'MEDIUM' ELSE confidence END " +
            "WHERE prefix = :prefix",
    )
    suspend fun incrementObservation(prefix: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_barcode_stems WHERE prefix = :prefix")
    suspend fun deleteStem(prefix: String)

    @Query(
        "SELECT * FROM user_barcode_stems " +
            "WHERE prefix LIKE :partialBarcode || '%' " +
            "ORDER BY LENGTH(prefix) ASC, observationCount DESC",
    )
    suspend fun findStemsByPartialBarcode(partialBarcode: String): List<UserBarcodeStemEntity>
}
