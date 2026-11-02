package org.bataydar.evidence.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.bataydar.evidence.data.model.AuditLogEntity
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.DeletionTombstoneEntity
import org.bataydar.evidence.data.model.FarmerProfileEntity
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity

@Database(
    entities = [
        FarmerProfileEntity::class,
        CropRecordEntity::class,
        GeotaggedEvidenceEntity::class,
        AuditLogEntity::class,
        DeletionTombstoneEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun farmerProfileDao(): FarmerProfileDao
    abstract fun cropRecordDao(): CropRecordDao
    abstract fun geotaggedEvidenceDao(): GeotaggedEvidenceDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun deletionTombstoneDao(): DeletionTombstoneDao

    suspend fun clearUserData() {
        farmerProfileDao().deleteAll()
        cropRecordDao().deleteAllRecords()
        geotaggedEvidenceDao().deleteAllEvidence()
        deletionTombstoneDao().deleteAllTombstones()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `deletion_tombstones` (
                        `recordUuid` TEXT NOT NULL,
                        `recordType` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `firebaseUid` TEXT NOT NULL,
                        `cloudinaryPublicId` TEXT,
                        `deletedAt` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`recordUuid`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bataydar_evidence_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
