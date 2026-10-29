package org.bataydar.evidence.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.bataydar.evidence.data.model.AuditLogEntity
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.FarmerProfileEntity
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity

@Database(
    entities = [
        FarmerProfileEntity::class,
        CropRecordEntity::class,
        GeotaggedEvidenceEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun farmerProfileDao(): FarmerProfileDao
    abstract fun cropRecordDao(): CropRecordDao
    abstract fun geotaggedEvidenceDao(): GeotaggedEvidenceDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bataydar_evidence_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
