package org.bataydar.evidence.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farmer_profiles")
data class FarmerProfileEntity(
    @PrimaryKey
    val farmerId: String,
    val fullName: String,
    val phoneNumber: String,
    val village: String,
    val district: String,
    val state: String,
    val preferredLanguage: String = "en",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
