package org.bataydar.evidence.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "farmer_profiles",
    indices = [Index(value = ["ownerFirebaseUid"])]
)
data class FarmerProfileEntity(
    @PrimaryKey
    val farmerId: String,
    val ownerFirebaseUid: String = "",
    val fullName: String,
    val phoneNumber: String,
    val village: String,
    val district: String,
    val state: String,
    val preferredLanguage: String = "en",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
