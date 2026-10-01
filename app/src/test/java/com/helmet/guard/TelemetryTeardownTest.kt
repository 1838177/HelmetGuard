package com.helmet.guard

import com.helmet.guard.core.ble.SensorDataFrame
import com.helmet.guard.core.detector.AccidentDetector
import com.helmet.guard.core.detector.AccidentDetectorConfig
import com.helmet.guard.data.mock.MockBleEngine
import com.helmet.guard.data.mock.MockScenario
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 遥测清零与算法停发测试：
 * 验证“结束测试后加速度依然检测”缺陷修复闭环，
 * 确认仿真结束或手动待机后，遥测数据彻底复位至 STATIC_BASELINE，算法研判完全停发。
 */
class TelemetryTeardownTest {

    private lateinit var detector: AccidentDetector
    private lateinit var mockEngine: MockBleEngine

    @Before
    fun setUp() {
        detector = AccidentDetector(AccidentDetectorConfig())
        mockEngine = MockBleEngine()
    }

    @Test
    fun testStaticBaselineGuaranteesZeroDynamicNoise() {
        val baseline = SensorDataFrame.STATIC_BASELINE

        assertEquals("静态基线合加速度必须为标准重力 1.0g", 1.0f, baseline.totalAcc, 0.001f)
        assertEquals("静态基线三轴加速度 X 应为 0", 0.0f, baseline.ax, 0.001f)
        assertEquals("静态基线三轴加速度 Y 应为 0", 0.0f, baseline.ay, 0.001f)
        assertEquals("静态基线三轴加速度 Z 应为 1.0g", 1.0f, baseline.az, 0.001f)
        assertEquals("静态基线合角速度必须为 0°/s", 0.0f, baseline.totalGyro, 0.001f)
        assertEquals("静态基线横滚角应为 0°", 0.0f, baseline.roll, 0.001f)
        assertEquals("静态基线俯仰角应为 0°", 0.0f, baseline.pitch, 0.001f)
        assertTrue("静态基线 IMU 状态应为正常", baseline.isImuNormal)
    }

    @Test
    fun testStopScenarioAndStandbyPutsEngineInPausedState() {
        // 先应用摔倒场景
        mockEngine.applyScenario(MockScenario.CRASH_AND_FALL)
        assertEquals(MockScenario.CRASH_AND_FALL, mockEngine.scenarioState.value)

        // 执行停止与复位待机
        mockEngine.stopScenarioAndStandby()

        // 验证场景状态已切实变更为 STANDBY_PAUSED
        assertEquals("场景状态必须切换为 STANDBY_PAUSED", MockScenario.STANDBY_PAUSED, mockEngine.scenarioState.value)

        // 验证基线数据合加速度为1.0g，角速度为0
        val baseline = SensorDataFrame.STATIC_BASELINE
        assertEquals(1.0f, baseline.totalAcc, 0.001f)
        assertEquals(0.0f, baseline.totalGyro, 0.001f)
    }

    @Test
    fun testStaticBaselineStreamNeverTriggersAlgorithm() {
        detector.reset()
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)

        // 模拟连续接收 100 帧待机基线遥测
        var time = System.currentTimeMillis()
        for (i in 0 until 100) {
            time += 50
            val frame = SensorDataFrame.STATIC_BASELINE.copy(timestampMs = time)
            val result = detector.processSample(frame)

            assertNull("待机基线数据流绝不触发任何研判告警", result)
            assertEquals("算法阶段必须持续锁定在 IDLE", AccidentDetector.DetectionStage.IDLE, detector.stage)
        }
    }

    @Test
    fun testTeardownFromActiveCrashImmediatelyHaltsDetection() {
        var t = 1000L

        // 模拟撞击进入冲击检测阶段
        val impactFrame = SensorDataFrame(
            timestampMs = t,
            ax = 3.5f,
            ay = 2.0f,
            az = 2.5f,
            totalAcc = 4.7f,
            gx = 100f,
            gy = 100f,
            gz = 100f,
            totalGyro = 173f,
            roll = 10f,
            pitch = 10f,
            yaw = 0f,
            batteryPercent = 90,
            isImuNormal = true,
            isCalibrated = true
        )
        detector.processSample(impactFrame)
        assertEquals(AccidentDetector.DetectionStage.IMPACT_DETECTED, detector.stage)

        // 用户主动点击“结束测试并复位待机”：执行全链路拆除
        mockEngine.stopScenarioAndStandby()
        detector.reset()

        // 验证算法阶段完全复位
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)
        assertEquals(MockScenario.STANDBY_PAUSED, mockEngine.scenarioState.value)

        // 后续数据流转入待机基线，绝无残余状态触发
        t += 50
        val teardownResult = detector.processSample(SensorDataFrame.STATIC_BASELINE.copy(timestampMs = t))
        assertNull("拆除复位后遥测研判彻底停发", teardownResult)
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)
    }
}
