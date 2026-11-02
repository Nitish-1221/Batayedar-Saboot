package org.bataydar.evidence.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.bataydar.evidence.data.model.DeletionTombstoneEntity
import org.bataydar.evidence.data.model.SyncStatus

@Dao
interface DeletionTombstoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: DeletionTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstones(tombstones: List<DeletionTombstoneEntity>)

    @Query("SELECT * FROM deletion_tombstones WHERE syncStatus = 'DELETION_PENDING' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid OR firebaseUid = :ownerUid)")
    suspend fun getPendingDeletions(ownerUid: String = ""): List<DeletionTombstoneEntity>

    @Query("SELECT COUNT(*) FROM deletion_tombstones WHERE syncStatus = 'DELETION_PENDING' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid OR firebaseUid = :ownerUid)")
    suspend fun getPendingDeletionsCount(ownerUid: String = ""): Int

    @Query("SELECT COUNT(*) FROM deletion_tombstones WHERE syncStatus = 'DELETION_PENDING' AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid OR firebaseUid = :ownerUid)")
    fun getPendingDeletionsCountFlow(ownerUid: String = ""): Flow<Int>

    @Query("SELECT * FROM deletion_tombstones WHERE recordUuid = :uuid AND (:ownerUid = '' OR ownerFirebaseUid = :ownerUid OR firebaseUid = :ownerUid) LIMIT 1")
    suspend fun getTombstone(uuid: String, ownerUid: String = ""): DeletionTombstoneEntity?

    @Query("SELECT recordUuid FROM deletion_tombstones WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid OR firebaseUid = :ownerUid)")
    suspend fun getAllTombstoneUuids(ownerUid: String = ""): List<String>

    @Query("UPDATE deletion_tombstones SET syncStatus = :status WHERE recordUuid = :uuid")
    suspend fun updateSyncStatus(uuid: String, status: SyncStatus)

    @Query("DELETE FROM deletion_tombstones WHERE recordUuid = :uuid")
    suspend fun deleteTombstone(uuid: String)

    @Query("DELETE FROM deletion_tombstones")
    suspend fun deleteAllTombstones()
}
