package org.bataydar.evidence.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crop_records")
data class CropRecordEntity(
    @PrimaryKey
    val recordId: String,
    val farmerId: String,
    val fieldName: String,
    val village: String,
    val district: String,
    val state: String,
    val cropType: String,
    val sowingDate: String, // YYYY-MM-DD
    val fieldDescription: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val lastSyncTimestamp: Long? = null,
    
    // Optional Landlord details
    val landlordName: String? = null,
    val landlordPhone: String? = null,
    val landlordConfirmationStatus: LandlordStatus = LandlordStatus.NOT_REQUESTED,
    val landlordConfirmationDate: Long? = null,
    val landlordConfirmationNotes: String? = null
)
