package com.helmet.guard.data.repository

import android.content.Context
import com.helmet.guard.core.ble.BleCommandEncoder
import com.helmet.guard.core.ble.HelmetButtonAction
import com.helmet.guard.core.ble.SensorDataFrame
import com.helmet.guard.core.detector.AccidentDetector
import com.helmet.guard.core.dispatcher.DispatchResult
import com.helmet.guard.core.dispatcher.EmergencyMessage
import com.helmet.guard.core.dispatcher.SmsManagerDispatcher
import com.helmet.guard.core.location.LocationProvider
import com.helmet.guard.core.location.LocationResult
import com.helmet.guard.data.ble.RealBleManager
import com.helmet.guard.data.database.AppDatabase
import com.helmet.guard.data.database.entity.AccidentRecordEntity
import com.helmet.guard.data.database.entity.DeviceLogEntity
import com.helmet.guard.data.database.entity.EmergencyContactEntity
import com.helmet.guard.data.mock.MockBleEngine
import com.helmet.guard.data.mock.MockScenario
import com.helmet.guard.data.preferences.UserPreferencesRepository
import com.helmet.guard.domain.model.AccidentLifecycleState
import com.helmet.guard.domain.model.AccidentRecord
import com.helmet.guard.domain.model.ConnectionStatus
import com.helmet.guard.domain.model.DeviceLog
import com.helmet.guard.domain.model.EmergencyContact
import com.helmet.guard.domain.model.HelmetDevice
import com.helmet.guard.domain.model.LogType
import com.helmet.guard.domain.model.MonitoringStatus
import com.helmet.guard.domain.model.OperationMode
import com.helmet.guard.domain.repository.IHelmetRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HelmetRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: UserPreferencesRepository
) : IHelmetRepository {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val realBleManager = RealBleManager(context)
    private val mockBleEngine = MockBleEngine()
    private val detector = AccidentDetector()
    private val locationProvider = LocationProvider(context)
    private val smsDispatcher = SmsManagerDispatcher(context)
    private val fakeDispatcher = com.helmet.guard.core.dispatcher.FakeEmergencyDispatcher()

    private val _isMockMode = MutableStateFlow(false)
    override val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()
    private val _operationMode = MutableStateFlow(OperationMode.LIVE)
    override val operationMode: StateFlow<OperationMode> = _operationMode.asStateFlow()
    override val mockScenario: StateFlow<MockScenario> = mockBleEngine.scenarioState
    override val mockScenarioTimestamp: StateFlow<Long> = mockBleEngine.scenarioTimestamp

    private val _isAccidentDetectionEnabled = MutableStateFlow(true)
    override val isAccidentDetectionEnabled: StateFlow<Boolean> = _isAccidentDetectionEnabled.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.UNBOUND)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _monitoringStatus = MutableStateFlow(MonitoringStatus.UNBOUND)
    override val monitoringStatus: StateFlow<MonitoringStatus> = _monitoringStatus.asStateFlow()

    @Volatile
    private var lastPacketElapsedMs: Long = 0L
    private val countdownMutex = Mutex()

    private val _deviceInfo = MutableStateFlow(HelmetDevice(name = "智能头盔", address = ""))
    override val deviceInfo: StateFlow<HelmetDevice> = _deviceInfo.asStateFlow()

    private val _sensorFlow = MutableStateFlow(
        SensorDataFrame(0, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 100, true, true)
    )
    override val sensorFlow: StateFlow<SensorDataFrame> = _sensorFlow.asStateFlow()

    private val _accidentState = MutableStateFlow(AccidentLifecycleState.SAFE)
    override val accidentState: StateFlow<AccidentLifecycleState> = _accidentState.asStateFlow()

    private val _countdownSecondsLeft = MutableStateFlow(15)
    override val countdownSecondsLeft: StateFlow<Int> = _countdownSecondsLeft.asStateFlow()

    override val discoveredDevices: StateFlow<List<HelmetDevice>> = realBleManager.discoveredDevices
    override val isScanning: StateFlow<Boolean> = realBleManager.isScanning

    private var countdownJob: Job? = null
    private var currentEventId: Long = 0L
    private var currentImpactPeakG: Float = 0f
    private var currentGyroPeakDps: Float = 0f

    init {
        // 读取配置
        scope.launch {
            preferences.isDetectionEnabled.collect { _isAccidentDetectionEnabled.value = it }
        }
        scope.launch {
            preferences.isMockMode.collect { setMockMode(it) }
        }

        // 监听真实蓝牙数据
        scope.launch {
            realBleManager.connectionState.collect { status ->
                if (!_isMockMode.value) {
                    _connectionStatus.value = status
                    if (status == ConnectionStatus.CONNECTED) {
                        recordLog(LogType.BLE_CONNECT, "蓝牙已连接", "成功建立 GATT 连接", "成功")
                    } else if (status == ConnectionStatus.DISCONNECTED) {
                        recordLog(LogType.BLE_DISCONNECT, "蓝牙断开", "连接已断开", "正常断开/信号丢失")
                    }
                }
            }
        }
        scope.launch {
            realBleManager.connectedDevice.collect { device ->
                if (!_isMockMode.value && device != null) {
                    _deviceInfo.value = device
                }
            }
        }
        scope.launch {
            realBleManager.sensorDataFlow.collect { data ->
                if (!_isMockMode.value) {
                    onSensorDataReceived(data)
                }
            }
        }
        scope.launch {
            realBleManager.accidentEventFlow.collect { event ->
                if (!_isMockMode.value) {
                    onHardwareAccidentReported(
                        eventId = event.eventId,
                        peakG = event.impactPeakG,
                        gyroDps = event.gyroPeakDps
                    )
                }
            }
        }
        scope.launch {
            realBleManager.buttonEventFlow.collect { buttonEvent ->
                if (buttonEvent.action == HelmetButtonAction.LONG_PRESS) {
                    // 头盔物理长按取消报警
                    cancelAlarm(isFromHelmet = true)
                }
            }
        }

        // 监听 Mock 引擎数据
        scope.launch {
            mockBleEngine.connectionState.collect { status ->
                if (_isMockMode.value) {
                    _connectionStatus.value = status
                }
            }
        }
        scope.launch {
            mockBleEngine.connectedDevice.collect { device ->
                if (_isMockMode.value && device != null) {
                    _deviceInfo.value = device
                }
            }
        }
        scope.launch {
            mockBleEngine.sensorDataFlow.collect { data ->
                if (_isMockMode.value) {
                    onSensorDataReceived(data)
                }
            }
        }
        scope.launch {
            mockBleEngine.accidentEventFlow.collect { event ->
                if (_isMockMode.value) {
                    onHardwareAccidentReported(
                        eventId = event.eventId,
                        peakG = event.impactPeakG,
                        gyroDps = event.gyroPeakDps
                    )
                }
            }
        }
        scope.launch {
            mockBleEngine.buttonEventFlow.collect { buttonEvent ->
                if (buttonEvent.action == HelmetButtonAction.LONG_PRESS) {
                    cancelAlarm(isFromHelmet = true)
                }
            }
        }
        scope.launch {
            _connectionStatus.collect { updateMonitoringStatus() }
        }
        scope.launch {
            _operationMode.collect { updateMonitoringStatus() }
        }
        // 单调时钟数据新鲜度看门狗 (超时 > 3000ms 标为 DATA_STALE)
        scope.launch {
            while (isActive) {
                delay(1000L)
                if (_connectionStatus.value == ConnectionStatus.CONNECTED && !_isMockMode.value) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (lastPacketElapsedMs > 0 && (now - lastPacketElapsedMs > 3000L)) {
                        if (_monitoringStatus.value != MonitoringStatus.DATA_STALE) {
                            _monitoringStatus.value = MonitoringStatus.DATA_STALE
                        }
                    }
                }
            }
        }
    }

    private fun updateMonitoringStatus() {
        _monitoringStatus.value = when {
            _isMockMode.value || _operationMode.value == OperationMode.SIMULATION -> MonitoringStatus.SIMULATION
            _connectionStatus.value == ConnectionStatus.UNBOUND -> MonitoringStatus.UNBOUND
            _connectionStatus.value == ConnectionStatus.DISCONNECTED -> MonitoringStatus.DISCONNECTED
            _connectionStatus.value == ConnectionStatus.CONNECTING -> MonitoringStatus.INITIALIZING
            _connectionStatus.value == ConnectionStatus.CONNECTED -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (lastPacketElapsedMs > 0 && (now - lastPacketElapsedMs > 3000L)) {
                    MonitoringStatus.DATA_STALE
                } else {
                    MonitoringStatus.ACTIVE_MONITORING
                }
            }
            else -> MonitoringStatus.ACTIVE_MONITORING
        }
    }

    private fun onSensorDataReceived(data: SensorDataFrame) {
        lastPacketElapsedMs = android.os.SystemClock.elapsedRealtime()
        _sensorFlow.value = data
        _deviceInfo.value = _deviceInfo.value.copy(batteryPercent = data.batteryPercent)
        updateMonitoringStatus()

        // 仅在未处于报警锁定、开启事故检测且非待机静止状态时进行本地算法研判
        val isStandby = _isMockMode.value && mockBleEngine.scenarioState.value == MockScenario.STANDBY_PAUSED
        if (_isAccidentDetectionEnabled.value && _accidentState.value == AccidentLifecycleState.SAFE && !isStandby) {
            val detected = detector.processSample(data)
            if (detected != null) {
                // 本地检测到强烈碰撞并确认静止
                val eventId = System.currentTimeMillis() % 1000000L
                triggerCountdown(eventId, detected.first, detected.second)
            }
        }
    }

    private fun onHardwareAccidentReported(eventId: Long, peakG: Float, gyroDps: Float) {
        if (!_isAccidentDetectionEnabled.value) return
        triggerCountdown(eventId, peakG, gyroDps)
    }

    private fun triggerCountdown(eventId: Long, peakG: Float, gyroDps: Float) {
        if (eventId != 0L && eventId == currentEventId) return
        if (_accidentState.value == AccidentLifecycleState.COUNTDOWN ||
            _accidentState.value == AccidentLifecycleState.EVENT_LOCKED ||
            _accidentState.value == AccidentLifecycleState.LOCATING ||
            _accidentState.value == AccidentLifecycleState.SENDING ||
            _accidentState.value == AccidentLifecycleState.SENT ||
            _accidentState.value == AccidentLifecycleState.SEND_FAILED
        ) {
            return
        }

        currentEventId = eventId
        currentImpactPeakG = peakG
        currentGyroPeakDps = gyroDps

        _accidentState.value = AccidentLifecycleState.COUNTDOWN

        countdownJob?.cancel()
        countdownJob = scope.launch {
            val duration = preferences.countdownDurationSec.first()
            var remaining = duration
            _countdownSecondsLeft.value = remaining

            // 时序并行预取：进入 15s 倒计时瞬间后台异步启动定位预取，倒计时 15s 自然结束瞬间定位已就绪，耗时 0ms
            val locationTimeout = (duration * 1000L).coerceAtLeast(15000L)
            val prefetchDeferred: Deferred<LocationResult> = async(Dispatchers.IO) {
                locationProvider.getAccurateLocation(timeoutMs = locationTimeout)
            }

            while (remaining > 0 && _accidentState.value == AccidentLifecycleState.COUNTDOWN) {
                delay(1000L)
                remaining--
                _countdownSecondsLeft.value = remaining
            }

            if (_accidentState.value == AccidentLifecycleState.COUNTDOWN) {
                countdownMutex.withLock {
                    if (_accidentState.value == AccidentLifecycleState.COUNTDOWN) {
                        // 倒计时结束，锁定事故，进入分发管线
                        val locationResult = if (prefetchDeferred.isCompleted) {
                            prefetchDeferred.await()
                        } else {
                            _accidentState.value = AccidentLifecycleState.LOCATING
                            prefetchDeferred.await()
                        }
                        onCountdownFinished(locationResult)
                    }
                }
            }
        }
    }

    private suspend fun onCountdownFinished(locationResult: LocationResult) {
        _accidentState.value = AccidentLifecycleState.EVENT_LOCKED

        // 向硬件回传 ACK 确认帧
        val ackBytes = BleCommandEncoder.buildAckFrame(currentEventId, 0x00)
        if (!_isMockMode.value) {
            realBleManager.writeCommand(ackBytes)
        }

        // 进入发送报警状态
        _accidentState.value = AccidentLifecycleState.SENDING
        dispatchEmergencyAlert(locationResult)
    }

    private suspend fun dispatchEmergencyAlert(locationResult: LocationResult) {
        // 分发管线彻底移除 .take(3) 硬编码，改为全量过滤 activeContacts = allContacts.filter { it.isEnabled && it.phone.isNotBlank() }
        val allContacts = database.emergencyContactDao().getAllContacts().first()
        val activeContacts = allContacts.filter { it.isEnabled && it.phone.isNotBlank() }

        val deviceName = _deviceInfo.value.name
        val battery = _deviceInfo.value.batteryPercent ?: 0
        val messageBody = EmergencyMessage.buildAlertBody(deviceName, battery, locationResult)

        var dispatchStatusText: String
        val isMock = _isMockMode.value || _operationMode.value == OperationMode.SIMULATION

        if (activeContacts.isEmpty()) {
            dispatchStatusText = "未配置或未启动紧急联系人，报警消息无法分发"
            _accidentState.value = AccidentLifecycleState.SEND_FAILED
        } else {
            val activeDispatcher: com.helmet.guard.core.dispatcher.EmergencyDispatcher = if (isMock) {
                fakeDispatcher
            } else {
                smsDispatcher
            }

            if (!isMock) {
                com.helmet.guard.service.SosAccessibilityService.notifyDispatchTriggered()
            }

            // 15s 结束后零间隔并发向所有启动的联系人发送短信
            val dispatchResults = coroutineScope {
                activeContacts.map { contact ->
                    async {
                        val result = activeDispatcher.dispatch(contact.phone, messageBody)
                        contact to result
                    }
                }.awaitAll()
            }

            val results = mutableListOf<String>()
            var anySuccess = false
            for ((contact, result) in dispatchResults) {
                when (result) {
                    is DispatchResult.Success -> {
                        results.add("${contact.name}(${contact.phone}): 成功 - ${result.info}")
                        anySuccess = true
                    }
                    is DispatchResult.FallbackRequired -> {
                        results.add("${contact.name}(${contact.phone}): 降级 - ${result.reason}")
                    }
                    is DispatchResult.Failure -> {
                        results.add("${contact.name}(${contact.phone}): 失败 - ${result.error}")
                    }
                }
            }
            dispatchStatusText = results.joinToString("; ")
            _accidentState.value = if (anySuccess) AccidentLifecycleState.SENT else AccidentLifecycleState.SEND_FAILED
        }

        // 保存事故记录到 Room
        val (lat, lng, accuracy, isHistorical) = when (locationResult) {
            is LocationResult.Success -> Tuple4(locationResult.latitude, locationResult.longitude, locationResult.accuracyMeters, locationResult.isHistoricalFallback)
            is LocationResult.Failure -> Tuple4(0.0, 0.0, 0f, false)
        }

        val record = AccidentRecordEntity(
            eventId = currentEventId,
            timestampMs = System.currentTimeMillis(),
            impactPeakG = currentImpactPeakG,
            gyroPeakDps = currentGyroPeakDps,
            stillDurationSec = 5.0f,
            latitude = lat,
            longitude = lng,
            locationAccuracy = accuracy,
            isHistoricalLocation = isHistorical,
            status = _accidentState.value.name,
            dispatchResult = dispatchStatusText,
            isMock = isMock
        )
        database.accidentRecordDao().insertRecord(record)

        recordLog(
            LogType.ALARM_TRIGGER,
            "事故报警触发",
            "事件ID: $currentEventId, 冲击: ${currentImpactPeakG}g, 结果: $dispatchStatusText",
            if (_accidentState.value == AccidentLifecycleState.SENT) "成功" else "失败"
        )
    }

    override fun cancelAlarm(isFromHelmet: Boolean) {
        // 短信提交进入 SENDING/SENT 后增加防撤回守卫，避免将已提交状态改写为 CANCELLED
        if (_accidentState.value == AccidentLifecycleState.SENDING || _accidentState.value == AccidentLifecycleState.SENT) {
            recordLog(
                LogType.SYSTEM,
                "取消请求已拒绝",
                "报警短信已提交运营商或已发送完成，无法撤回 (当前状态: ${_accidentState.value})",
                "已拒绝"
            )
            return
        }

        countdownJob?.cancel()
        val previousState = _accidentState.value
        _accidentState.value = AccidentLifecycleState.CANCELLED

        detector.reset()

        val cancelSource = if (isFromHelmet) "头盔按键长按" else "手机屏幕长按"
        val cancelBytes = BleCommandEncoder.buildCancelAlarmFrame(
            eventId = currentEventId,
            cancelSource = if (isFromHelmet) 0x02 else 0x01
        )

        if (!_isMockMode.value) {
            realBleManager.writeCommand(cancelBytes)
        }

        recordLog(
            LogType.ALARM_CANCEL,
            "报警已取消",
            "取消方式: $cancelSource, 对应事件: $currentEventId, 前序状态: $previousState",
            "正常取消"
        )

        // 3秒后恢复 SAFE 状态
        scope.launch {
            delay(3000L)
            if (_accidentState.value == AccidentLifecycleState.CANCELLED) {
                _accidentState.value = AccidentLifecycleState.SAFE
            }
        }
    }

    override fun triggerManualSos() {
        val eventId = System.currentTimeMillis() % 1000000L
        triggerCountdown(eventId, peakG = 0f, gyroDps = 0f)
    }

    override fun resolveAlertAndResumeMonitoring() {
        currentEventId = 0L
        currentImpactPeakG = 0f
        currentGyroPeakDps = 0f
        detector.reset()
        _accidentState.value = AccidentLifecycleState.SAFE
        recordLog(LogType.ALARM_CANCEL, "人工解除报警", "用户确认安全，全自动防护已恢复", "已恢复")
    }

    override fun stopMockTelemetry() {
        mockBleEngine.stopScenarioAndStandby()
        detector.reset()
        _sensorFlow.value = SensorDataFrame.STATIC_BASELINE.copy(timestampMs = System.currentTimeMillis())
        recordLog(LogType.SYSTEM, "结束仿真测试", "遥测数据已复位待机，算法研判已清空", "待机中")
    }

    override fun startScan() {
        if (!_isMockMode.value) {
            realBleManager.startScan()
        }
    }

    override fun stopScan() {
        if (!_isMockMode.value) {
            realBleManager.stopScan()
        }
    }

    override fun connect(device: HelmetDevice) {
        scope.launch {
            preferences.saveBoundDevice(device.address, device.name)
        }
        if (!_isMockMode.value) {
            realBleManager.connect(device.address)
        }
    }

    override fun disconnect() {
        if (!_isMockMode.value) {
            realBleManager.disconnect()
        } else {
            mockBleEngine.applyScenario(MockScenario.DISCONNECT)
        }
    }

    override fun reconnect() {
        scope.launch {
            val addr = preferences.lastDeviceAddress.first()
            if (!addr.isNullOrBlank()) {
                realBleManager.connect(addr)
            }
        }
    }

    override fun forgetDevice() {
        disconnect()
        scope.launch {
            preferences.clearBoundDevice()
        }
        _deviceInfo.value = HelmetDevice(name = "未绑定", address = "")
        _connectionStatus.value = ConnectionStatus.UNBOUND
    }

    override fun setAccidentDetectionEnabled(enabled: Boolean) {
        scope.launch {
            preferences.setDetectionEnabled(enabled)
        }
    }

    override fun setCountdownDuration(seconds: Int) {
        scope.launch {
            preferences.setCountdownDuration(seconds)
        }
    }

    override fun findHelmet(durationSec: Int) {
        val bytes = BleCommandEncoder.buildFindHelmetFrame(durationSec)
        if (!_isMockMode.value) {
            realBleManager.writeCommand(bytes)
        }
        recordLog(LogType.BLE_CONNECT, "查找头盔", "发送蜂鸣指令 (持续 ${durationSec}s)", "已发送")
    }

    override fun calibrateImu() {
        val bytes = BleCommandEncoder.buildCalibrateImuFrame()
        if (!_isMockMode.value) {
            realBleManager.writeCommand(bytes)
        }
        recordLog(LogType.CALIBRATION, "传感器标定", "发送零偏校准指令", "已发送")
    }

    override fun setMockMode(enabled: Boolean) {
        _isMockMode.value = enabled
        _operationMode.value = if (enabled) OperationMode.SIMULATION else OperationMode.LIVE
        if (enabled) {
            realBleManager.disconnect()
            mockBleEngine.start()
            _connectionStatus.value = mockBleEngine.connectionState.value
            _deviceInfo.value = mockBleEngine.connectedDevice.value ?: HelmetDevice(name = "HelmetGuard-Mock[仿真]", address = "AA:BB:CC:DD:EE:FF")
        } else {
            mockBleEngine.stop()
            detector.reset()
            _sensorFlow.value = SensorDataFrame.STATIC_BASELINE.copy(timestampMs = System.currentTimeMillis())
            scope.launch {
                val boundAddr = preferences.lastDeviceAddress.first()
                val boundName = preferences.lastDeviceName.first()
                if (boundAddr.isNullOrBlank()) {
                    _connectionStatus.value = ConnectionStatus.UNBOUND
                    _deviceInfo.value = HelmetDevice(name = "智能头盔", address = "")
                } else {
                    val realDev = realBleManager.connectedDevice.value
                    if (realDev != null && realBleManager.connectionState.value == ConnectionStatus.CONNECTED) {
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        _deviceInfo.value = realDev
                    } else {
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                        _deviceInfo.value = HelmetDevice(name = boundName ?: "智能头盔", address = boundAddr)
                    }
                }
            }
        }
    }

    override fun injectMockEmergencyBrake() {
        mockBleEngine.applyScenario(MockScenario.EMERGENCY_BRAKE)
    }

    override fun injectMockCrashAndFall() {
        mockBleEngine.applyScenario(MockScenario.CRASH_AND_FALL)
    }

    override fun injectMockLowBattery() {
        mockBleEngine.applyScenario(MockScenario.LOW_BATTERY)
    }

    override fun injectMockBleDisconnect() {
        mockBleEngine.applyScenario(MockScenario.DISCONNECT)
    }

    override fun injectMockRestoreNormal() {
        countdownJob?.cancel()
        currentEventId = 0L
        _accidentState.value = AccidentLifecycleState.SAFE
        mockBleEngine.applyScenario(MockScenario.NORMAL_RIDING)
    }

    override fun getAccidentRecords(): Flow<List<AccidentRecord>> {
        return database.accidentRecordDao().getAllRecords().map { entities ->
            entities.map {
                AccidentRecord(
                    id = it.id,
                    eventId = it.eventId,
                    timestampMs = it.timestampMs,
                    impactPeakG = it.impactPeakG,
                    gyroPeakDps = it.gyroPeakDps,
                    stillDurationSec = it.stillDurationSec,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    locationAccuracy = it.locationAccuracy,
                    isHistoricalLocation = it.isHistoricalLocation,
                    status = it.status,
                    dispatchResult = it.dispatchResult,
                    isMock = it.isMock
                )
            }
        }
    }

    private val deviceLogsState = database.deviceLogDao().getAllLogs().map { entities ->
        entities.map {
            DeviceLog(
                id = it.id,
                timestampMs = it.timestampMs,
                type = it.type,
                title = it.title,
                detail = it.detail,
                result = it.result
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun getDeviceLogs(): Flow<List<DeviceLog>> = deviceLogsState

    override fun getEmergencyContacts(): Flow<List<EmergencyContact>> {
        return database.emergencyContactDao().getAllContacts().map { entities ->
            entities.map {
                EmergencyContact(
                    id = it.id,
                    name = it.name,
                    phone = it.phone,
                    isPrimary = it.isPrimary,
                    relationship = it.relationship,
                    isEnabled = it.isEnabled
                )
            }
        }
    }

    override suspend fun saveEmergencyContact(contact: EmergencyContact) {
        val insertedId = database.emergencyContactDao().insertOrUpdate(
            EmergencyContactEntity(
                id = contact.id,
                name = contact.name,
                phone = contact.phone,
                isPrimary = contact.isPrimary,
                relationship = contact.relationship,
                isEnabled = contact.isEnabled
            )
        )
        if (contact.isPrimary) {
            val targetId = if (contact.id != 0L) contact.id else insertedId
            database.emergencyContactDao().clearOtherPrimary(targetId)
        }
    }

    override suspend fun updateEmergencyContactEnabled(id: Long, isEnabled: Boolean) {
        database.emergencyContactDao().updateEnabledStatus(id, isEnabled)
    }

    override suspend fun deleteEmergencyContact(id: Long) {
        database.emergencyContactDao().deleteById(id)
    }

    override suspend fun clearHistory() {
        database.accidentRecordDao().clearAll()
        database.deviceLogDao().clearAll()
    }

    override fun recordLog(type: LogType, title: String, detail: String, result: String) {
        scope.launch {
            database.deviceLogDao().insertLog(
                DeviceLogEntity(
                    timestampMs = System.currentTimeMillis(),
                    type = type,
                    title = title,
                    detail = detail,
                    result = result
                )
            )
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
