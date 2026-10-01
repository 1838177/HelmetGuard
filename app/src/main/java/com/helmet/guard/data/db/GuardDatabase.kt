package com.helmet.guard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EmergencyContactEntity::class,
        TelemetrySessionEntity::class,
        TelemetrySampleEntity::class,
        AccidentEntity::class,
        SmsPartEntity::class,
        SystemLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class GuardDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao
    abstract fun telemetry(): TelemetryDao
    abstract fun accidents(): AccidentDao
    abstract fun smsParts(): SmsPartDao
    abstract fun logs(): LogDao

    companion object {
        @Volatile private var instance: GuardDatabase? = null

        fun create(context: Context): GuardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                GuardDatabase::class.java,
                "helmet_guard_v2.db"
            ).build().also { instance = it }
        }
    }
}
