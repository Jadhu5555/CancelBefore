package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.DetectedNotificationDao
import com.example.data.dao.RecordDao
import com.example.data.entity.DetectedNotificationEntity
import com.example.data.entity.RecordEntity

@Database(
    entities = [RecordEntity::class, DetectedNotificationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CancelBeforeDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun detectedNotificationDao(): DetectedNotificationDao

    companion object {
        const val DATABASE_NAME = "cancelbefore_database"
        const val CURRENT_VERSION = 2

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safely alter detected_notifications table to add Phase 2 columns
                db.execSQL("ALTER TABLE detected_notifications ADD COLUMN normalizedMerchant TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE detected_notifications ADD COLUMN eventCategory TEXT NOT NULL DEFAULT 'NEW_MANDATE_CREATED'")
                db.execSQL("ALTER TABLE detected_notifications ADD COLUMN eventFingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE detected_notifications ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE detected_notifications ADD COLUMN matchedRecordId INTEGER DEFAULT NULL")

                // Backfill existing rows so fingerprints and normalized names are populated safely
                db.execSQL("UPDATE detected_notifications SET normalizedMerchant = extractedMerchant WHERE normalizedMerchant = ''")
                db.execSQL("UPDATE detected_notifications SET eventFingerprint = notificationHash || '_' || id WHERE eventFingerprint = ''")

                // Create the unique fingerprint index safely
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_detected_notifications_eventFingerprint ON detected_notifications (eventFingerprint)")
            }
        }

        @Volatile
        private var INSTANCE: CancelBeforeDatabase? = null

        fun getDatabase(context: Context): CancelBeforeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CancelBeforeDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
