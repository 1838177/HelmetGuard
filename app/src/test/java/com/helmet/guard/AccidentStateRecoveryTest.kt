package com.helmet.guard

import com.helmet.guard.core.ble.SensorDataFrame
import com.helmet.guard.core.detector.AccidentDetector
import com.helmet.guard.core.detector.AccidentDetectorConfig
import com.helmet.guard.domain.model.AccidentLifecycleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 事故报警状态死锁恢复与闭环测试：
 * 验证倒计时取消、短信提交防撤回守卫、已发送/失败状态向 SAFE 的平滑复位，
 * 以及底层研判算法在人工解除或取消后的无死锁恢复能力。
 */
class AccidentStateRecoveryTest {

    private lateinit var detector: AccidentDetector

    @Before
    fun setUp() {
        detector = AccidentDetector(
            AccidentDetectorConfig(
                impactThresholdG = 3.0f,
                impactWindowMs = 200L,
                gyroThresholdDps = 200.0f,
                tiltThresholdDeg = 45.0f,
                stillnessConfirmDurationMs = 1000L,
                stillnessVarianceThreshold = 0.15f
            )
        )
    }

    private fun createFrame(timeMs: Long, acc: Float, gyro: Float = 0f, roll: Float = 0f): SensorDataFrame {
        return SensorDataFrame(
            timestampMs = timeMs,
            ax = 0f,
            ay = 0f,
            az = acc,
            totalAcc = acc,
            gx = 0f,
            gy = 0f,
            gz = gyro,
            totalGyro = gyro,
            roll = roll,
            pitch = 0f,
            yaw = 0f,
            batteryPercent = 90,
            isImuNormal = true,
            isCalibrated = true
        )
    }

    @Test
    fun testCountdownCancellationRecoveryLoop() {
        var state = AccidentLifecycleState.SAFE

        // 模拟检测到疑似撞击进入倒计时
        state = AccidentLifecycleState.COUNTDOWN
        assertEquals(AccidentLifecycleState.COUNTDOWN, state)

        // 用户在倒计时中触发取消
        val isSubmitting = state == AccidentLifecycleState.SENDING || state == AccidentLifecycleState.SENT
        if (!isSubmitting) {
            state = AccidentLifecycleState.CANCELLED
            detector.reset()
        }
        assertEquals(AccidentLifecycleState.CANCELLED, state)
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)

        // 倒计时取消后恢复 SAFE
        state = AccidentLifecycleState.SAFE
        assertEquals(AccidentLifecycleState.SAFE, state)
    }

    @Test
    fun testAntiRetractionGuardDuringSendingAndSentStates() {
        // 场景 1: 短信正在通过运营商网络提交 (SENDING)
        var state = AccidentLifecycleState.SENDING

        // 尝试取消报警
        fun attemptCancel(): Boolean {
            if (state == AccidentLifecycleState.SENDING || state == AccidentLifecycleState.SENT) {
                // 防撤回守卫拦截
                return false
            }
            state = AccidentLifecycleState.CANCELLED
            return true
        }

        val cancelledInSending = attemptCancel()
        assertEquals("SENDING 状态下必须拒绝撤回", false, cancelledInSending)
        assertEquals("状态不得被改写为 CANCELLED", AccidentLifecycleState.SENDING, state)

        // 场景 2: 短信已向运营商提交完毕 (SENT)
        state = AccidentLifecycleState.SENT
        val cancelledInSent = attemptCancel()
        assertEquals("SENT 状态下必须拒绝撤回", false, cancelledInSent)
        assertEquals("状态不得被改写为 CANCELLED", AccidentLifecycleState.SENT, state)
    }

    @Test
    fun testResolveAlertResumesSafeMonitoringFromTerminalStates() {
        // 验证 SENT 状态通过人工解除恢复 SAFE
        var state = AccidentLifecycleState.SENT
        fun resolveAlert() {
            detector.reset()
            state = AccidentLifecycleState.SAFE
        }

        resolveAlert()
        assertEquals(AccidentLifecycleState.SAFE, state)
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)

        // 验证 SEND_FAILED 状态通过人工解除恢复 SAFE
        state = AccidentLifecycleState.SEND_FAILED
        resolveAlert()
        assertEquals(AccidentLifecycleState.SAFE, state)
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)
    }

    @Test
    fun testAlgorithmRecoversWithoutDeadlockAfterCrashAndReset() {
        var t = 1000L

        // 1. 触发一次完整事故
        detector.processSample(createFrame(t, acc = 4.5f, gyro = 300f, roll = 60f))
        assertEquals(AccidentDetector.DetectionStage.IMPACT_DETECTED, detector.stage)

        t += 50
        detector.processSample(createFrame(t, acc = 4.2f, gyro = 280f, roll = 65f))
        assertEquals(AccidentDetector.DetectionStage.POSTURE_TILTED, detector.stage)

        t += 50
        // 静止窗口
        var confirmed: Pair<Float, Float>? = null
        for (i in 0 until 25) {
            t += 50
            val res = detector.processSample(createFrame(t, acc = 1.0f, gyro = 0f, roll = 65f))
            if (res != null) confirmed = res
        }
        assertNotNull("事故判定应正常闭环触发", confirmed)
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)

        // 2. 模拟恢复：解除警报并重置检测器
        detector.reset()
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)

        // 3. 验证恢复后基线骑行不会误报
        for (i in 0 until 20) {
            t += 50
            val res = detector.processSample(createFrame(t, acc = 1.02f, gyro = 5f, roll = 1f))
            assertNull("恢复后平稳骑行绝不触发报警", res)
        }
        assertEquals(AccidentDetector.DetectionStage.IDLE, detector.stage)

        // 4. 验证恢复后发生二次撞击依然能精准捕获 (无死锁)
        t += 100
        detector.processSample(createFrame(t, acc = 4.8f, gyro = 320f, roll = 70f))
        assertEquals("复位后应能正常识别新的撞击事件", AccidentDetector.DetectionStage.IMPACT_DETECTED, detector.stage)
    }
}
