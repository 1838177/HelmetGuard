package com.helmet.guard.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.helmet.guard.core.ble.AccidentEventFrame
import com.helmet.guard.core.ble.BleConstants
import com.helmet.guard.core.ble.BleProtocolParser
import com.helmet.guard.core.ble.ButtonEventFrame
import com.helmet.guard.core.ble.SensorDataFrame
import com.helmet.guard.domain.model.ConnectionStatus
import com.helmet.guard.domain.model.HelmetDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 工业级 Android 原生 BLE 驱动管理器
 * 负责扫描、自动过滤、MTU协商、GATT连接、服务发现、特征订阅与断线指数退避重连
 */
class RealBleManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetDeviceAddress: String? = null
    private var isUserExplicitDisconnect = false

    // 重连参数
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    // 状态与数据流
    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<HelmetDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<HelmetDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedDevice = MutableStateFlow<HelmetDevice?>(null)
    val connectedDevice: StateFlow<HelmetDevice?> = _connectedDevice.asStateFlow()

    private val _sensorDataFlow = MutableSharedFlow<SensorDataFrame>(extraBufferCapacity = 64)
    val sensorDataFlow: SharedFlow<SensorDataFrame> = _sensorDataFlow.asSharedFlow()

    private val _accidentEventFlow = MutableSharedFlow<AccidentEventFrame>(extraBufferCapacity = 16)
    val accidentEventFlow: SharedFlow<AccidentEventFrame> = _accidentEventFlow.asSharedFlow()

    private val _buttonEventFlow = MutableSharedFlow<ButtonEventFrame>(extraBufferCapacity = 16)
    val buttonEventFlow: SharedFlow<ButtonEventFrame> = _buttonEventFlow.asSharedFlow()

    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // GATT 串行操作队列 (防止并发写冲突与丢包)
    private val gattCommandQueue = ArrayDeque<() -> Unit>()
    private var isGattBusy = false

    @Synchronized
    private fun enqueueGattOp(operation: () -> Unit) {
        gattCommandQueue.add(operation)
        processNextGattOp()
    }

    @Synchronized
    private fun processNextGattOp() {
        if (isGattBusy || gattCommandQueue.isEmpty()) return
        val next = gattCommandQueue.removeFirst()
        isGattBusy = true
        try {
            next.invoke()
        } catch (e: Exception) {
            isGattBusy = false
            processNextGattOp()
        }
    }

    @Synchronized
    private fun completeGattOp() {
        isGattBusy = false
        processNextGattOp()
    }

    @Synchronized
    private fun clearGattQueue() {
        gattCommandQueue.clear()
        isGattBusy = false
    }

    // 扫描回调
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val name = device.name ?: "未知头盔"
            val address = device.address ?: return
            val rssi = result.rssi

            val currentList = _discoveredDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.address == address }
            val item = HelmetDevice(name = name, address = address, rssi = rssi)
            if (existingIndex >= 0) {
                currentList[existingIndex] = item
            } else {
                currentList.add(item)
            }
            _discoveredDevices.value = currentList
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _isScanning.value = false
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf(
            ScanFilter.Builder()
                .build() // 扫描附近所有 BLE 设备，便于演示与调试
        )

        try {
            scanner.startScan(filters, settings, scanCallback)
            // 15 秒后自动停止扫描
            scope.launch {
                delay(15000L)
                stopScan()
            }
        } catch (e: Exception) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_isScanning.value) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        stopScan()
        targetDeviceAddress = address
        isUserExplicitDisconnect = false
        _connectionState.value = ConnectionStatus.CONNECTING

        val device = bluetoothAdapter.getRemoteDevice(address)
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isUserExplicitDisconnect = true
        reconnectJob?.cancel()
        clearGattQueue()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionStatus.DISCONNECTED
        _connectedDevice.value = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    reconnectAttempt = 0
                    _connectionState.value = ConnectionStatus.CONNECTED
                    _connectedDevice.value = HelmetDevice(
                        name = gatt?.device?.name ?: "智能头盔",
                        address = gatt?.device?.address ?: "",
                        lastConnectedTimeMs = System.currentTimeMillis()
                    )
                    // 请求扩大 MTU 至 64 字节
                    gatt?.requestMtu(BleConstants.DESIRED_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    clearGattQueue()
                    _connectionState.value = ConnectionStatus.DISCONNECTED
                    gatt?.close()
                    bluetoothGatt = null
                    if (!isUserExplicitDisconnect) {
                        scheduleReconnect()
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            // MTU 改变完成后发现服务
            gatt?.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(BleConstants.SERVICE_HELMET)
                if (service != null) {
                    // 订阅传感器特征值
                    val sensorChar = service.getCharacteristic(BleConstants.CHAR_SENSOR_DATA)
                    if (sensorChar != null) {
                        enableNotification(gatt, sensorChar)
                    }
                    // 订阅事故事件特征值
                    val eventChar = service.getCharacteristic(BleConstants.CHAR_EVENT_NOTIFY)
                    if (eventChar != null) {
                        enableNotification(gatt, eventChar)
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            completeGattOp()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            completeGattOp()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val bytes = characteristic?.value ?: return
            handleIncomingBytes(bytes)
        }

        // Android 13+ 新增回调
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingBytes(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        enqueueGattOp {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == 0
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                if (!success) {
                    completeGattOp()
                }
            } else {
                completeGattOp()
            }
        }
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        val frame = BleProtocolParser.parseRawFrame(bytes) ?: return
        when (frame.msgType) {
            BleConstants.MSG_TYPE_SENSOR -> {
                BleProtocolParser.parseSensorPayload(frame.payload)?.let { sensorData ->
                    _sensorDataFlow.tryEmit(sensorData)
                }
            }
            BleConstants.MSG_TYPE_ACCIDENT -> {
                BleProtocolParser.parseAccidentPayload(frame.payload)?.let { accidentData ->
                    _accidentEventFlow.tryEmit(accidentData)
                }
            }
            BleConstants.MSG_TYPE_BUTTON -> {
                BleProtocolParser.parseButtonPayload(frame.payload)?.let { buttonData ->
                    _buttonEventFlow.tryEmit(buttonData)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCommand(commandBytes: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(BleConstants.SERVICE_HELMET) ?: return false
        val char = service.getCharacteristic(BleConstants.CHAR_CONTROL_COMMAND) ?: return false

        enqueueGattOp {
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, commandBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == 0
            } else {
                @Suppress("DEPRECATION")
                char.value = commandBytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
            if (!success) {
                completeGattOp()
            }
        }
        return true
    }

    private fun scheduleReconnect() {
        val address = targetDeviceAddress ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt++
            // 指数退避: 2s, 4s, 8s, 最大 30s
            val delayMs = (2000L * (1 shl (reconnectAttempt - 1).coerceAtMost(4))).coerceAtMost(30000L)
            _connectionState.value = ConnectionStatus.UNSTABLE
            delay(delayMs)
            if (!isUserExplicitDisconnect) {
                connect(address)
            }
        }
    }
}
