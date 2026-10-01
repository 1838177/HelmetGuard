package com.helmet.guard.domain.repository

import com.helmet.guard.core.ble.SensorDataFrame
import com.helmet.guard.domain.model.AccidentLifecycleState
import com.helmet.guard.domain.model.AccidentRecord
import com.helmet.guard.domain.model.ConnectionStatus
import com.helmet.guard.domain.model.DeviceLog
import com.helmet.guard.domain.model.EmergencyContact
import com.helmet.guard.domain.model.HelmetDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IHelmetRepository {
    val connectionStatus: StateFlow<ConnectionStatus>
    val deviceInfo: StateFlow<HelmetDevice>
    val sensorFlow: StateFlow<SensorDataFrame>
    val accidentState: StateFlow<AccidentLifecycleState>
    val countdownSecondsLeft: StateFlow<Int>
    val discoveredDevices: StateFlow<List<HelmetDevice>>
    val isScanning: StateFlow<Boolean>
    val isMockMode: StateFlow<Boolean>
    val operationMode: StateFlow<com.helmet.guard.domain.model.OperationMode>
    val monitoringStatus: StateFlow<com.helmet.guard.domain.model.MonitoringStatus>
    val mockScenario: StateFlow<com.helmet.guard.data.mock.MockScenario>
    val mockScenarioTimestamp: StateFlow<Long>
    val isAccidentDetectionEnabled: StateFlow<Boolean>

    fun startScan()
    fun stopScan()
    fun connect(device: HelmetDevice)
    fun disconnect()
    fun reconnect()
    fun forgetDevice()

    fun setAccidentDetectionEnabled(enabled: Boolean)
    fun setCountdownDuration(seconds: Int)
    fun cancelAlarm(isFromHelmet: Boolean = false)
    fun triggerManualSos()
    fun resolveAlertAndResumeMonitoring()

    fun findHelmet(durationSec: Int = 5)
    fun calibrateImu()
    fun recordLog(type: com.helmet.guard.domain.model.LogType, title: String, detail: String, result: String)

    // Mock 仿真注入与测试控制
    fun setMockMode(enabled: Boolean)
    fun stopMockTelemetry()
    fun injectMockEmergencyBrake()
    fun injectMockCrashAndFall()
    fun injectMockLowBattery()
    fun injectMockBleDisconnect()
    fun injectMockRestoreNormal()

    // 历史与持久化
    fun getAccidentRecords(): Flow<List<AccidentRecord>>
    fun getDeviceLogs(): Flow<List<DeviceLog>>
    fun getEmergencyContacts(): Flow<List<EmergencyContact>>
    suspend fun saveEmergencyContact(contact: EmergencyContact)
    suspend fun updateEmergencyContactEnabled(id: Long, isEnabled: Boolean)
    suspend fun deleteEmergencyContact(id: Long)
    suspend fun clearHistory()
}
