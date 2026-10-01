package com.helmet.guard

import com.helmet.guard.core.ble.SensorDataFrame
import com.helmet.guard.core.detector.AccidentDetector
import com.helmet.guard.core.detector.AccidentDetectorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AccidentDetectorTest {

    private lateinit var detector: AccidentDetector

    @Before
    fun setUp() {
        val testConfig = AccidentDetectorConfig(
            impactThresholdG = 3.0f,
            impactWindowMs = 200L,
            gyroThresholdDps = 200.0f,
            tiltThresholdDeg = 45.0f,
            stillnessConfirmDurationMs = 5000L,
            stillnessVarianceThreshold = 0.15f
        )
        detector = AccidentDetector(testConfig)
    }

    private fun createSample(
        timeMs: Long,
        totalAcc: Float,
        totalGyro: Float = 0f,
        roll: Float = 0f,
        pitch: Float = 0f
    ): SensorDataFrame {
        return SensorDataFrame(
            timestampMs = timeMs,
            ax = 0f,
            ay = 0f,
            az = totalAcc,
            totalAcc = totalAcc,
            gx = 0f,
            gy = 0f,
            gz = totalGyro,
            totalGyro = totalGyro,
            roll = roll,
            pitch = pitch,
            yaw = 0f,
            batteryPercent = 100,
            isImuNormal = true,
            isCalibrated = true
        )
    }

    @Test
    fun testNormalRidingDoesNotTriggerAccident() {
        var time = 1000L
        for (i in 0 until 50) {
            val sample = createSample(time, totalAcc = 1.05f, totalGyro = 10f, roll = 2f)
            val result = detector.processSample(sample)
            assertNull("平稳骑行不应触发任何事故判定", result)
            time += 50L
        }
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)
    }

    @Test
    fun testEmergencyBrakeDoesNotTriggerAccident() {
        var time = 1000L
        // 急刹车产生 2.0g 减速度，但无翻转且合加速度未达到 3.0g 且无异常倾斜
        for (i in 0 until 10) {
            val sample = createSample(time, totalAcc = 1.9f, totalGyro = 20f, roll = 0f, pitch = -10f)
            val result = detector.processSample(sample)
            assertNull("急刹车不应判定为事故", result)
            time += 50L
        }
    }

    @Test
    fun testSevereCrashFollowedByStillnessTriggersAccident() {
        var time = 1000L

        // 1. 产生强烈碰撞 (4.5g, 300°/s) 并伴随 60° 侧翻
        val impactSample = createSample(time, totalAcc = 4.5f, totalGyro = 300f, roll = 60f)
        detector.processSample(impactSample)

        time += 50L
        // 紧接着确认姿态已倾覆
        detector.processSample(createSample(time, totalAcc = 1.0f, roll = 60f))

        // 2. 进入静止确认期 (持续 5000ms，保持低方差平稳)
        var accidentResult: Pair<Float, Float>? = null
        for (i in 0 until 110) {
            time += 50L
            val stillnessSample = createSample(time, totalAcc = 1.0f, roll = 60f)
            val res = detector.processSample(stillnessSample)
            if (res != null) {
                accidentResult = res
                break
            }
        }

        assertNotNull("强烈撞击+侧倾+持续静止5秒必须确认触发事故", accidentResult)
        assertEquals(4.5f, accidentResult?.first ?: 0f, 0.01f)
    }
}
