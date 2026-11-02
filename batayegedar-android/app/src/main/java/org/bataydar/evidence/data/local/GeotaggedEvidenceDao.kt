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

    @Query("SELECT * FROM geotagged_evidence WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingEvidence(): List<GeotaggedEvidenceEntity>

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE syncStatus != 'SYNCED'")
    fun getUnsyncedEvidenceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedEvidenceCountDirect(): Int

    @Query("SELECT COUNT(*) FROM geotagged_evidence")
    suspend fun getTotalEvidenceCountDirect(): Int

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE syncStatus = 'SYNCED'")
    suspend fun getSyncedEvidenceCountDirect(): Int

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE syncStatus = 'SYNC_FAILED'")
    suspend fun getFailedEvidenceCountDirect(): Int

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE recordId = :recordId AND syncStatus != 'SYNCED'")
    suspend fun getUnsyncedEvidenceCountForRecord(recordId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: GeotaggedEvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidences(evidences: List<GeotaggedEvidenceEntity>)

    @Query("DELETE FROM geotagged_evidence")
    suspend fun deleteAllEvidence()

    @Update
    suspend fun updateEvidence(evidence: GeotaggedEvidenceEntity)

    @Delete
    suspend fun deleteEvidence(evidence: GeotaggedEvidenceEntity)

    @Query("DELETE FROM geotagged_evidence WHERE evidenceId = :evidenceId")
    suspend fun deleteEvidenceById(evidenceId: String)

    @Query("UPDATE geotagged_evidence SET syncStatus = :syncStatus WHERE evidenceId = :evidenceId")
    suspend fun updateSyncStatus(evidenceId: String, syncStatus: SyncStatus)
}
