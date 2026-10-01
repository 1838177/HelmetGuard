package com.helmet.guard.presentation.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helmet.guard.core.ble.SensorDataFrame
import com.helmet.guard.domain.repository.IHelmetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SensorViewModel(private val repository: IHelmetRepository) : ViewModel() {

    val sensorData: StateFlow<SensorDataFrame> = repository.sensorFlow

    // 控制开关
    private val _isRealtimeRefresh = MutableStateFlow(true)
    val isRealtimeRefresh: StateFlow<Boolean> = _isRealtimeRefresh.asStateFlow()

    private val _isShowCharts = MutableStateFlow(true)
    val isShowCharts: StateFlow<Boolean> = _isShowCharts.asStateFlow()

    // 历史折线图采样环形缓冲 (最近 30 个点)
    private val _accHistory = MutableStateFlow<List<Float>>(emptyList())
    val accHistory: StateFlow<List<Float>> = _accHistory.asStateFlow()

    private val _gyroHistory = MutableStateFlow<List<Float>>(emptyList())
    val gyroHistory: StateFlow<List<Float>> = _gyroHistory.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sensorFlow.collect { sample ->
                if (_isRealtimeRefresh.value) {
                    val currentAcc = _accHistory.value.toMutableList()
                    currentAcc.add(sample.totalAcc)
                    if (currentAcc.size > 30) currentAcc.removeAt(0)
                    _accHistory.value = currentAcc

                    val currentGyro = _gyroHistory.value.toMutableList()
                    currentGyro.add(sample.totalGyro)
                    if (currentGyro.size > 30) currentGyro.removeAt(0)
                    _gyroHistory.value = currentGyro
                }
            }
        }
        viewModelScope.launch {
            repository.mockScenario.collect { scenario ->
                if (scenario == com.helmet.guard.data.mock.MockScenario.STANDBY_PAUSED) {
                    _accHistory.value = List(30) { 1.0f }
                    _gyroHistory.value = List(30) { 0.0f }
                }
            }
        }
    }

    fun toggleRealtimeRefresh(enabled: Boolean) {
        _isRealtimeRefresh.value = enabled
    }

    fun toggleCharts(enabled: Boolean) {
        _isShowCharts.value = enabled
    }

    fun triggerCalibration() {
        repository.calibrateImu()
    }
}
