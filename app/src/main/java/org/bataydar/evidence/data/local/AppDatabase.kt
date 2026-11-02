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
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `crop_records` ADD COLUMN `ownerFirebaseUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `crop_records` SET `ownerFirebaseUid` = `farmerId` WHERE `ownerFirebaseUid` = ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_crop_records_ownerFirebaseUid` ON `crop_records` (`ownerFirebaseUid`)")

                db.execSQL("ALTER TABLE `geotagged_evidence` ADD COLUMN `ownerFirebaseUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `geotagged_evidence` SET `ownerFirebaseUid` = COALESCE((SELECT `ownerFirebaseUid` FROM `crop_records` WHERE `crop_records`.`recordId` = `geotagged_evidence`.`recordId`), '') WHERE `ownerFirebaseUid` = ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_geotagged_evidence_ownerFirebaseUid` ON `geotagged_evidence` (`ownerFirebaseUid`)")

                db.execSQL("ALTER TABLE `farmer_profiles` ADD COLUMN `ownerFirebaseUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `farmer_profiles` SET `ownerFirebaseUid` = `farmerId` WHERE `ownerFirebaseUid` = ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_farmer_profiles_ownerFirebaseUid` ON `farmer_profiles` (`ownerFirebaseUid`)")

                db.execSQL("ALTER TABLE `audit_logs` ADD COLUMN `ownerFirebaseUid` TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE `deletion_tombstones` ADD COLUMN `ownerFirebaseUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `deletion_tombstones` SET `ownerFirebaseUid` = `firebaseUid` WHERE `ownerFirebaseUid` = ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deletion_tombstones_firebaseUid` ON `deletion_tombstones` (`firebaseUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deletion_tombstones_ownerFirebaseUid` ON `deletion_tombstones` (`ownerFirebaseUid`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bataydar_evidence_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
