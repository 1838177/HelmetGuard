package com.helmet.guard.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.helmet.guard.domain.model.LogType

@Entity(tableName = "accident_records")
data class AccidentRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val eventId: Long,
    val timestampMs: Long,
    val impactPeakG: Float,
    val gyroPeakDps: Float,
    val stillDurationSec: Float,
    val latitude: Double,
    val longitude: Double,
    val locationAccuracy: Float,
    val isHistoricalLocation: Boolean,
    val status: String,
    val dispatchResult: String,
    val isMock: Boolean
)

@Entity(tableName = "device_logs")
data class DeviceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestampMs: Long,
    val type: LogType,
    val title: String,
    val detail: String,
    val result: String
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val phone: String,
    val isPrimary: Boolean,
    val relationship: String,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true
)
