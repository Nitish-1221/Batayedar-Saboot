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

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    fun getTotalEvidenceCount(ownerUid: String = ""): Flow<Int>

    @Query("SELECT * FROM geotagged_evidence WHERE recordId = :recordId AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid) ORDER BY capturedAtUtc DESC")
    fun getEvidenceForRecordFlow(recordId: String, ownerUid: String = ""): Flow<List<GeotaggedEvidenceEntity>>

    @Query("SELECT * FROM geotagged_evidence WHERE recordId = :recordId AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid) ORDER BY capturedAtUtc DESC")
    suspend fun getEvidenceForRecord(recordId: String, ownerUid: String = ""): List<GeotaggedEvidenceEntity>

    @Query("SELECT * FROM geotagged_evidence WHERE evidenceId = :evidenceId AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid) LIMIT 1")
    suspend fun getEvidenceById(evidenceId: String, ownerUid: String = ""): GeotaggedEvidenceEntity?

    @Query("SELECT * FROM geotagged_evidence WHERE syncStatus != 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getPendingEvidence(ownerUid: String = ""): List<GeotaggedEvidenceEntity>

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE syncStatus != 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    fun getUnsyncedEvidenceCount(ownerUid: String = ""): Flow<Int>

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE syncStatus != 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getUnsyncedEvidenceCountDirect(ownerUid: String = ""): Int

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getTotalEvidenceCountDirect(ownerUid: String = ""): Int

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE syncStatus = 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getSyncedEvidenceCountDirect(ownerUid: String = ""): Int

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE syncStatus = 'SYNC_FAILED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getFailedEvidenceCountDirect(ownerUid: String = ""): Int

    @Query("SELECT COUNT(*) FROM geotagged_evidence WHERE recordId = :recordId AND syncStatus != 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getUnsyncedEvidenceCountForRecord(recordId: String, ownerUid: String = ""): Int

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

    @Query("DELETE FROM geotagged_evidence WHERE evidenceId = :evidenceId AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun deleteEvidenceById(evidenceId: String, ownerUid: String = "")

    @Query("UPDATE geotagged_evidence SET syncStatus = :syncStatus WHERE evidenceId = :evidenceId")
    suspend fun updateSyncStatus(evidenceId: String, syncStatus: SyncStatus)
}
