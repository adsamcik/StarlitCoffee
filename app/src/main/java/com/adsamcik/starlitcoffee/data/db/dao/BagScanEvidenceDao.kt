package com.adsamcik.starlitcoffee.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.adsamcik.starlitcoffee.data.db.entity.BagScanEvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BagScanEvidenceDao {
    @Insert
    suspend fun insertAll(evidence: List<BagScanEvidenceEntity>)

    @Query("SELECT * FROM bag_scan_evidence WHERE bagId = :bagId ORDER BY scanTimestamp DESC")
    fun getEvidenceForBag(bagId: Long): Flow<List<BagScanEvidenceEntity>>

    @Query("SELECT * FROM bag_scan_evidence WHERE bagId = :bagId ORDER BY scanTimestamp DESC")
    suspend fun getEvidenceForBagOnce(bagId: Long): List<BagScanEvidenceEntity>

    @Query(
        """
        DELETE FROM bag_scan_evidence 
        WHERE bagId = :bagId 
        AND scanTimestamp NOT IN (
            SELECT DISTINCT scanTimestamp 
            FROM bag_scan_evidence 
            WHERE bagId = :bagId 
            ORDER BY scanTimestamp DESC 
            LIMIT 2
        )
        """,
    )
    suspend fun pruneOldEvidence(bagId: Long)

    @Query("DELETE FROM bag_scan_evidence WHERE bagId = :bagId")
    suspend fun deleteForBag(bagId: Long)
}
