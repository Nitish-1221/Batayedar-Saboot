package org.bataydar.evidence.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "geotagged_evidence",
    foreignKeys = [
        ForeignKey(
            entity = CropRecordEntity::class,
            parentColumns = ["recordId"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["recordId"])]
)
data class GeotaggedEvidenceEntity(
    @PrimaryKey
    val evidenceId: String,
    val recordId: String,
    val originalPhotoPath: String,
    val watermarkedPhotoPath: String? = null,
    val photoSha256: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyMeters: Float,
    val gpsFixAgeMs: Long,
    val capturedAtUtc: Long,
    val capturedAtLocalFormatted: String,
    val deviceModel: String,
    val provider: String = "gps",
    val notes: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
