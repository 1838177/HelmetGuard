package com.helmet.guard.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.helmet.guard.data.database.entity.AccidentRecordEntity
import com.helmet.guard.data.database.entity.DeviceLogEntity
import com.helmet.guard.data.database.entity.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccidentRecordDao {
    @Query("SELECT * FROM accident_records ORDER BY timestampMs DESC")
    fun getAllRecords(): Flow<List<AccidentRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AccidentRecordEntity): Long

    @Query("DELETE FROM accident_records")
    suspend fun clearAll()
}

@Dao
interface DeviceLogDao {
    @Query("SELECT * FROM device_logs ORDER BY timestampMs DESC")
    fun getAllLogs(): Flow<List<DeviceLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DeviceLogEntity): Long

    @Query("DELETE FROM device_logs")
    suspend fun clearAll()
}

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, id ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(contact: EmergencyContactEntity): Long

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE emergency_contacts SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateEnabledStatus(id: Long, isEnabled: Boolean)

    @Query("UPDATE emergency_contacts SET isPrimary = 0 WHERE id != :exceptId")
    suspend fun clearOtherPrimary(exceptId: Long = 0L)

    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun getCount(): Int
}
