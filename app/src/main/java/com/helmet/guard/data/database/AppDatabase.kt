package com.helmet.guard.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.helmet.guard.data.database.dao.AccidentRecordDao
import com.helmet.guard.data.database.dao.DeviceLogDao
import com.helmet.guard.data.database.dao.EmergencyContactDao
import com.helmet.guard.data.database.entity.AccidentRecordEntity
import com.helmet.guard.data.database.entity.DeviceLogEntity
import com.helmet.guard.data.database.entity.EmergencyContactEntity

@Database(
    entities = [
        AccidentRecordEntity::class,
        DeviceLogEntity::class,
        EmergencyContactEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accidentRecordDao(): AccidentRecordDao
    abstract fun deviceLogDao(): DeviceLogDao
    abstract fun emergencyContactDao(): EmergencyContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE emergency_contacts ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "helmet_guard.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
