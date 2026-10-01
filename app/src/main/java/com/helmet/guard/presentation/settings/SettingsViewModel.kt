package com.helmet.guard.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helmet.guard.data.preferences.UserPreferencesRepository
import com.helmet.guard.domain.repository.IHelmetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: IHelmetRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    val isDetectionEnabled: StateFlow<Boolean> = preferences.isDetectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val countdownDuration: StateFlow<Int> = preferences.countdownDurationSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    val isMockMode: StateFlow<Boolean> = repository.isMockMode
    val mockScenario: StateFlow<com.helmet.guard.data.mock.MockScenario> = repository.mockScenario

    val emergencyContacts: StateFlow<List<com.helmet.guard.domain.model.EmergencyContact>> = repository.getEmergencyContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<Int> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    fun toggleDetection(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDetectionEnabled(enabled)
        }
    }

    fun setCountdown(seconds: Int) {
        viewModelScope.launch {
            preferences.setCountdownDuration(seconds)
        }
    }

    fun toggleMockMode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setMockMode(enabled)
        }
    }

    fun toggleContactEnabled(contact: com.helmet.guard.domain.model.EmergencyContact) {
        viewModelScope.launch {
            repository.updateEmergencyContactEnabled(contact.id, !contact.isEnabled)
        }
    }

    fun setTheme(mode: Int) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    // Mock 仿真注入
    fun mockEmergencyBrake() = repository.injectMockEmergencyBrake()
    fun mockCrashAndFall() = repository.injectMockCrashAndFall()
    fun mockLowBattery() = repository.injectMockLowBattery()
    fun mockBleDisconnect() = repository.injectMockBleDisconnect()
    fun mockRestoreNormal() = repository.injectMockRestoreNormal()
    fun stopMockTelemetry() = repository.stopMockTelemetry()
}
