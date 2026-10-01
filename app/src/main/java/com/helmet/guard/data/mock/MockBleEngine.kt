package com.helmet.guard.data.mock

import com.helmet.guard.core.ble.AccidentEventFrame
import com.helmet.guard.core.ble.ButtonEventFrame
import com.helmet.guard.core.ble.HelmetButtonAction
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 全功能智能头盔硬件仿真引擎 (Mock Engine)
 * 在完全无硬件环境下提供逼真的六轴 IMU 数据流、摔倒冲击碰撞注入、断线及低电量仿真
 */
class MockBleEngine {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var simulationJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionStatus.CONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow(
        HelmetDevice(
            name = "HelmetGuard-Mock[仿真]",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -55,
            batteryPercent = 92,
            firmwareVersion = "v1.0.0-MOCK",
            protocolVersion = "v1.0",
            lastConnectedTimeMs = System.currentTimeMillis()
        )
    )
    val connectedDevice: StateFlow<HelmetDevice?> = _connectedDevice.asStateFlow()

    private val _sensorDataFlow = MutableSharedFlow<SensorDataFrame>(extraBufferCapacity = 64)
    val sensorDataFlow: SharedFlow<SensorDataFrame> = _sensorDataFlow.asSharedFlow()

    private val _accidentEventFlow = MutableSharedFlow<AccidentEventFrame>(extraBufferCapacity = 16)
    val accidentEventFlow: SharedFlow<AccidentEventFrame> = _accidentEventFlow.asSharedFlow()

    private val _buttonEventFlow = MutableSharedFlow<ButtonEventFrame>(extraBufferCapacity = 16)
    val buttonEventFlow: SharedFlow<ButtonEventFrame> = _buttonEventFlow.asSharedFlow()

    private val _scenarioState = MutableStateFlow(MockScenario.NORMAL_RIDING)
    val scenarioState: StateFlow<MockScenario> = _scenarioState.asSharedFlow().let { _scenarioState.asStateFlow() }

    private val _scenarioTimestamp = MutableStateFlow(System.currentTimeMillis())
    val scenarioTimestamp: StateFlow<Long> = _scenarioTimestamp.asStateFlow()

    private var currentScenario = MockScenario.NORMAL_RIDING
    private var scenarioStep = 0
    private var batteryLevel = 92
    private var isSimulating = false
    private var mockEventSeq = 1000L

    fun start() {
        if (isSimulating) return
        isSimulating = true
        _connectionState.value = ConnectionStatus.CONNECTED

        simulationJob = scope.launch {
            var tick = 0L
            while (isActive) {
                tick++
                generateFrame(tick)
                delay(50L) // 20Hz 工业采样节流输出
            }
        }
    }

    fun stop() {
        isSimulating = false
        simulationJob?.cancel()
        simulationJob = null
        _connectionState.value = ConnectionStatus.DISCONNECTED
    }

    fun stopScenarioAndStandby() {
        this.currentScenario = MockScenario.STANDBY_PAUSED
        this.scenarioStep = 0
        _scenarioState.value = MockScenario.STANDBY_PAUSED
        _scenarioTimestamp.value = System.currentTimeMillis()
        val frame = SensorDataFrame.STATIC_BASELINE.copy(
            timestampMs = System.currentTimeMillis(),
            batteryPercent = batteryLevel
        )
        _sensorDataFlow.tryEmit(frame)
    }

    fun applyScenario(scenario: MockScenario) {
        if (scenario == MockScenario.STANDBY_PAUSED) {
            stopScenarioAndStandby()
            return
        }
        this.currentScenario = scenario
        this.scenarioStep = 0
        _scenarioState.value = scenario
        _scenarioTimestamp.value = System.currentTimeMillis()
        if (scenario == MockScenario.DISCONNECT) {
            _connectionState.value = ConnectionStatus.DISCONNECTED
        } else {
            if (_connectionState.value != ConnectionStatus.CONNECTED) {
                _connectionState.value = ConnectionStatus.CONNECTED
            }
        }
        if (scenario == MockScenario.LOW_BATTERY) {
            batteryLevel = 9
            _connectedDevice.value = _connectedDevice.value.copy(batteryPercent = batteryLevel)
        } else if (scenario == MockScenario.NORMAL_RIDING) {
            // Restoring the normal scenario must restore the simulated device state too.
            batteryLevel = 92
            _connectedDevice.value = _connectedDevice.value.copy(
                batteryPercent = batteryLevel,
                rssi = -55
            )
        }
    }

    fun triggerPhysicalButtonLongPress() {
        _buttonEventFlow.tryEmit(
            ButtonEventFrame(
                action = HelmetButtonAction.LONG_PRESS,
                rawCode = 0x03
            )
        )
    }

    private fun generateFrame(tick: Long) {
        val now = System.currentTimeMillis()

        if (currentScenario == MockScenario.STANDBY_PAUSED) {
            val frame = SensorDataFrame.STATIC_BASELINE.copy(
                timestampMs = now,
                batteryPercent = batteryLevel
            )
            _sensorDataFlow.tryEmit(frame)
            return
        }

        var ax = (Random.nextFloat() - 0.5f) * 0.08f
        var ay = (Random.nextFloat() - 0.5f) * 0.08f
        var az = 1.0f + (Random.nextFloat() - 0.5f) * 0.05f

        var gx = (Random.nextFloat() - 0.5f) * 3.0f
        var gy = (Random.nextFloat() - 0.5f) * 3.0f
        var gz = (Random.nextFloat() - 0.5f) * 2.0f

        var roll = sin(tick * 0.05f) * 3.0f // 正常行驶微幅晃动
        var pitch = sin(tick * 0.03f) * 2.0f
        var yaw = (tick * 0.1f) % 360f

        when (currentScenario) {
            MockScenario.NORMAL_RIDING -> {
                // 默认自然骑行
            }

            MockScenario.EMERGENCY_BRAKE -> {
                scenarioStep++
                if (scenarioStep in 1..8) {
                    // 急刹车: 纵向加速度跃升至 1.8g
                    ay = 1.8f + (Random.nextFloat() - 0.5f) * 0.2f
                    pitch = -12.0f
                } else if (scenarioStep > 15) {
                    stopScenarioAndStandby()
                    return
                }
            }

            MockScenario.SPEED_BUMP -> {
                scenarioStep++
                if (scenarioStep in 1..4) {
                    az = 1.6f + (Random.nextFloat() - 0.5f) * 0.2f
                } else if (scenarioStep > 8) {
                    stopScenarioAndStandby()
                    return
                }
            }

            MockScenario.CRASH_AND_FALL -> {
                scenarioStep++
                if (scenarioStep in 1..6) {
                    // 阶段 1: 撞击峰值 4.8g，角速度 350°/s
                    ax = 3.2f
                    ay = 2.5f
                    az = 2.8f
                    gx = 280.0f
                    gy = 310.0f
                    gz = 120.0f
                    roll = 35.0f
                    pitch = 40.0f
                } else if (scenarioStep in 7..20) {
                    // 阶段 2: 侧倾倒地
                    ax = 0.1f
                    ay = 0.9f
                    az = 0.2f
                    roll = 65.0f
                    pitch = 50.0f
                    gx = 1.0f
                    gy = 1.0f
                    gz = 0.5f
                } else if (scenarioStep == 21) {
                    // 头盔本地锁定事件帧发送
                    _accidentEventFlow.tryEmit(
                        AccidentEventFrame(
                            deviceId = 0x8899AAL,
                            eventId = System.currentTimeMillis() % 10000L,
                            impactPeakG = 4.82f,
                            gyroPeakDps = 360.5f,
                            stillDurationSec = 5.0f,
                            hardwareCountdownSec = 10,
                            needsPhoneAck = true,
                            isBuzzerActive = true
                        )
                    )
                } else if (scenarioStep > 100) {
                    // 恢复待机
                    stopScenarioAndStandby()
                    return
                }
            }

            MockScenario.LOW_BATTERY -> {
                batteryLevel = 8
            }

            MockScenario.DISCONNECT -> {
                return
            }

            MockScenario.STANDBY_PAUSED -> {
                return
            }
        }

        val totalAcc = sqrt(ax * ax + ay * ay + az * az)
        val totalGyro = sqrt(gx * gx + gy * gy + gz * gz)

        val frame = SensorDataFrame(
            timestampMs = now,
            ax = ax,
            ay = ay,
            az = az,
            totalAcc = totalAcc,
            gx = gx,
            gy = gy,
            gz = gz,
            totalGyro = totalGyro,
            roll = roll,
            pitch = pitch,
            yaw = yaw,
            batteryPercent = batteryLevel,
            isImuNormal = true,
            isCalibrated = true
        )
        _sensorDataFlow.tryEmit(frame)
    }
}
