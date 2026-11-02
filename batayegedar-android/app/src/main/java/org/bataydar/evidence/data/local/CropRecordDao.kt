package org.bataydar.evidence.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.data.model.SyncStatus

@Dao
interface CropRecordDao {

    @Transaction
    @Query("SELECT * FROM crop_records WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid) ORDER BY createdAt DESC")
    fun getAllRecordsWithEvidenceFlow(ownerUid: String = ""): Flow<List<RecordWithEvidence>>

    @Transaction
    @Query("SELECT * FROM crop_records WHERE recordId = :recordId AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid) LIMIT 1")
    suspend fun getRecordWithEvidence(recordId: String, ownerUid: String = ""): RecordWithEvidence?

    @Transaction
    @Query("SELECT * FROM crop_records WHERE recordId = :recordId AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid) LIMIT 1")
    fun getRecordWithEvidenceFlow(recordId: String, ownerUid: String = ""): Flow<RecordWithEvidence?>

    @Query("SELECT COUNT(*) FROM crop_records WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    fun getTotalFieldsCount(ownerUid: String = ""): Flow<Int>

    @Query("SELECT COUNT(*) FROM crop_records WHERE landlordConfirmationStatus = 'REQUESTED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    fun getAwaitingLandlordCount(ownerUid: String = ""): Flow<Int>

    @Query("SELECT COUNT(*) FROM crop_records WHERE syncStatus != 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    fun getPendingSyncCount(ownerUid: String = ""): Flow<Int>

    @Query("SELECT COUNT(*) FROM crop_records WHERE syncStatus != 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getPendingSyncCountDirect(ownerUid: String = ""): Int

    @Query("SELECT COUNT(*) FROM crop_records WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getTotalRecordsCountDirect(ownerUid: String = ""): Int

    @Query("SELECT COUNT(*) FROM crop_records WHERE syncStatus = 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getSyncedRecordsCountDirect(ownerUid: String = ""): Int

    @Query("SELECT COUNT(*) FROM crop_records WHERE syncStatus = 'SYNC_FAILED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getFailedRecordsCountDirect(ownerUid: String = ""): Int

    @Transaction
    @Query("SELECT * FROM crop_records WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid) ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 5, ownerUid: String = ""): Flow<List<RecordWithEvidence>>

    @Query("SELECT * FROM crop_records WHERE recordId = :recordId AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid) LIMIT 1")
    suspend fun getRecordById(recordId: String, ownerUid: String = ""): CropRecordEntity?

    @Query("SELECT * FROM crop_records WHERE syncStatus != 'SYNCED' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun getPendingRecords(ownerUid: String = ""): List<CropRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CropRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<CropRecordEntity>)

    @Query("DELETE FROM crop_records")
    suspend fun deleteAllRecords()

    @Query("DELETE FROM crop_records WHERE farmerId != :currentFarmerId AND ownerFirebaseUid != :currentFarmerId")
    suspend fun deleteOtherFarmerRecords(currentFarmerId: String)

    @Update
    suspend fun updateRecord(record: CropRecordEntity)

    @Delete
    suspend fun deleteRecord(record: CropRecordEntity)

    @Query("DELETE FROM crop_records WHERE recordId = :recordId AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid)")
    suspend fun deleteRecordById(recordId: String, ownerUid: String = "")

    @Query("UPDATE crop_records SET landlordConfirmationStatus = :status, landlordConfirmationDate = :date, landlordConfirmationNotes = :notes, updatedAt = :updatedAt WHERE recordId = :recordId")
    suspend fun updateLandlordStatus(
        recordId: String,
        status: LandlordStatus,
        date: Long?,
        notes: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE crop_records SET syncStatus = :syncStatus, lastSyncTimestamp = :timestamp, updatedAt = :updatedAt WHERE recordId = :recordId")
    suspend fun updateSyncStatus(
        recordId: String,
        syncStatus: SyncStatus,
        timestamp: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )
}
