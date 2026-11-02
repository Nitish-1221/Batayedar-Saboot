package org.bataydar.evidence.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.bataydar.evidence.data.model.FarmerProfileEntity

@Dao
interface FarmerProfileDao {
    @Query("SELECT * FROM farmer_profiles WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid OR farmerId = :ownerUid) LIMIT 1")
    fun getProfileFlow(ownerUid: String = ""): Flow<FarmerProfileEntity?>

    @Query("SELECT * FROM farmer_profiles WHERE (:ownerUid = '' OR ownerFirebaseUid = :ownerUid OR farmerId = :ownerUid) LIMIT 1")
    suspend fun getProfile(ownerUid: String = ""): FarmerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: FarmerProfileEntity)

    @Update
    suspend fun update(profile: FarmerProfileEntity)

    @Query("DELETE FROM farmer_profiles")
    suspend fun deleteAll()
}
