package com.helmet.guard.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helmet.guard.domain.model.AccidentLifecycleState
import com.helmet.guard.domain.model.ConnectionStatus
import com.helmet.guard.domain.model.DeviceLog
import com.helmet.guard.domain.model.HelmetDevice
import com.helmet.guard.domain.repository.IHelmetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: IHelmetRepository) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val deviceInfo: StateFlow<HelmetDevice> = repository.deviceInfo
    val accidentState: StateFlow<AccidentLifecycleState> = repository.accidentState
    val isDetectionEnabled: StateFlow<Boolean> = repository.isAccidentDetectionEnabled
    val countdownSecondsLeft: StateFlow<Int> = repository.countdownSecondsLeft
    val isMockMode: StateFlow<Boolean> = repository.isMockMode
    val monitoringStatus: StateFlow<com.helmet.guard.domain.model.MonitoringStatus> = repository.monitoringStatus
    val mockScenario: StateFlow<com.helmet.guard.data.mock.MockScenario> = repository.mockScenario
    val mockScenarioTimestamp: StateFlow<Long> = repository.mockScenarioTimestamp

    val recentLogs: StateFlow<List<DeviceLog>> = repository.getDeviceLogs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleDetection(enabled: Boolean) {
        repository.setAccidentDetectionEnabled(enabled)
    }

    fun triggerTestAlarm() {
        repository.triggerManualSos()
    }

    fun triggerManualSos() {
        repository.triggerManualSos()
    }

    fun resolveAlertAndResumeMonitoring() {
        repository.resolveAlertAndResumeMonitoring()
    }

    fun findHelmet() {
        repository.findHelmet(5)
    }

    fun cancelAlarm() {
        repository.cancelAlarm(isFromHelmet = false)
    }
}
