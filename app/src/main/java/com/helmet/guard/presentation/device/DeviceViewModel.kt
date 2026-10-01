package com.helmet.guard.presentation.device

import androidx.lifecycle.ViewModel
import com.helmet.guard.domain.model.ConnectionStatus
import com.helmet.guard.domain.model.HelmetDevice
import com.helmet.guard.domain.repository.IHelmetRepository
import kotlinx.coroutines.flow.StateFlow

class DeviceViewModel(private val repository: IHelmetRepository) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val deviceInfo: StateFlow<HelmetDevice> = repository.deviceInfo
    val discoveredDevices: StateFlow<List<HelmetDevice>> = repository.discoveredDevices
    val isScanning: StateFlow<Boolean> = repository.isScanning

    fun startScan() {
        repository.startScan()
    }

    fun stopScan() {
        repository.stopScan()
    }

    fun connectDevice(device: HelmetDevice) {
        repository.connect(device)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun reconnect() {
        repository.reconnect()
    }

    fun forgetDevice() {
        repository.forgetDevice()
    }
}
