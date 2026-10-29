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
    @Query("SELECT * FROM crop_records ORDER BY createdAt DESC")
    fun getAllRecordsWithEvidenceFlow(): Flow<List<RecordWithEvidence>>

    @Transaction
    @Query("SELECT * FROM crop_records WHERE recordId = :recordId LIMIT 1")
    suspend fun getRecordWithEvidence(recordId: String): RecordWithEvidence?

    @Transaction
    @Query("SELECT * FROM crop_records WHERE recordId = :recordId LIMIT 1")
    fun getRecordWithEvidenceFlow(recordId: String): Flow<RecordWithEvidence?>

    @Query("SELECT COUNT(*) FROM crop_records")
    fun getTotalFieldsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crop_records WHERE landlordConfirmationStatus = 'REQUESTED'")
    fun getAwaitingLandlordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM crop_records WHERE syncStatus = 'PENDING_SYNC'")
    fun getPendingSyncCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM crop_records ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 5): Flow<List<RecordWithEvidence>>

    @Query("SELECT * FROM crop_records WHERE syncStatus = 'PENDING_SYNC' OR syncStatus = 'SYNC_FAILED'")
    suspend fun getPendingRecords(): List<CropRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CropRecordEntity)

    @Update
    suspend fun updateRecord(record: CropRecordEntity)

    @Delete
    suspend fun deleteRecord(record: CropRecordEntity)

    @Query("DELETE FROM crop_records WHERE recordId = :recordId")
    suspend fun deleteRecordById(recordId: String)

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
