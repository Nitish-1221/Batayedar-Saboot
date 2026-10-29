package org.bataydar.evidence.data.local

import androidx.room.TypeConverter
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return try {
            SyncStatus.valueOf(value)
        } catch (e: Exception) {
            SyncStatus.LOCAL_ONLY
        }
    }

    @TypeConverter
    fun fromLandlordStatus(status: LandlordStatus): String = status.name

    @TypeConverter
    fun toLandlordStatus(value: String): LandlordStatus {
        return try {
            LandlordStatus.valueOf(value)
        } catch (e: Exception) {
            LandlordStatus.NOT_REQUESTED
        }
    }
}
