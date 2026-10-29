package org.bataydar.evidence.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity
import org.bataydar.evidence.data.model.SyncStatus

@Dao
interface GeotaggedEvidenceDao {

    @Query("SELECT COUNT(*) FROM geotagged_evidence")
    fun getTotalEvidenceCount(): Flow<Int>

    @Query("SELECT * FROM geotagged_evidence WHERE recordId = :recordId ORDER BY capturedAtUtc DESC")
    fun getEvidenceForRecordFlow(recordId: String): Flow<List<GeotaggedEvidenceEntity>>

    @Query("SELECT * FROM geotagged_evidence WHERE recordId = :recordId ORDER BY capturedAtUtc DESC")
    suspend fun getEvidenceForRecord(recordId: String): List<GeotaggedEvidenceEntity>

    @Query("SELECT * FROM geotagged_evidence WHERE evidenceId = :evidenceId LIMIT 1")
    suspend fun getEvidenceById(evidenceId: String): GeotaggedEvidenceEntity?

    @Query("SELECT * FROM geotagged_evidence WHERE syncStatus = 'PENDING_SYNC' OR syncStatus = 'SYNC_FAILED'")
    suspend fun getPendingEvidence(): List<GeotaggedEvidenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: GeotaggedEvidenceEntity)

    @Update
    suspend fun updateEvidence(evidence: GeotaggedEvidenceEntity)

    @Delete
    suspend fun deleteEvidence(evidence: GeotaggedEvidenceEntity)

    @Query("DELETE FROM geotagged_evidence WHERE evidenceId = :evidenceId")
    suspend fun deleteEvidenceById(evidenceId: String)

    @Query("UPDATE geotagged_evidence SET syncStatus = :syncStatus WHERE evidenceId = :evidenceId")
    suspend fun updateSyncStatus(evidenceId: String, syncStatus: SyncStatus)
}
