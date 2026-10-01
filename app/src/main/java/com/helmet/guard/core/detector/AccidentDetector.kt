package com.helmet.guard.core.detector

import com.helmet.guard.core.ble.SensorDataFrame
import kotlin.math.abs
import kotlin.math.max

/**
 * 六轴 IMU 复合事故研判算法
 * 阶段流水线：冲击检测 -> 旋转/姿态倾斜 -> 静止确认 -> 事故判定
 */
class AccidentDetector(
    private var config: AccidentDetectorConfig = AccidentDetectorConfig()
) {
    enum class DetectionStage {
        IDLE,           // 正常骑行/静止
        IMPACT_DETECTED,// 捕获到合加速度冲击脉冲
        POSTURE_TILTED, // 确认伴随剧烈翻滚或异常倾角
        STILLNESS_CHECK // 正在进行撞击后静止确认 (持续 5s)
    }

    private var currentStage = DetectionStage.IDLE
    private var impactTimestamp: Long = 0L
    private var maxImpactRecordedG: Float = 0f
    private var maxGyroRecordedDps: Float = 0f

    // 静止窗口缓冲
    private val stillnessAccBuffer = mutableListOf<Float>()
    private var stillnessStartTimestamp: Long = 0L

    fun updateConfig(newConfig: AccidentDetectorConfig) {
        this.config = newConfig
    }

    /**
     * 流式推入传感器帧
     * @return 若确认发生事故返回 Pair(peakG, gyroDps)，否则返回 null
     */
    @Synchronized
    fun processSample(sample: SensorDataFrame): Pair<Float, Float>? {
        val now = sample.timestampMs

        when (currentStage) {
            DetectionStage.IDLE -> {
                if (sample.totalAcc >= config.impactThresholdG) {
                    currentStage = DetectionStage.IMPACT_DETECTED
                    impactTimestamp = now
                    maxImpactRecordedG = sample.totalAcc
                    maxGyroRecordedDps = sample.totalGyro
                }
            }

            DetectionStage.IMPACT_DETECTED -> {
                maxImpactRecordedG = max(maxImpactRecordedG, sample.totalAcc)
                maxGyroRecordedDps = max(maxGyroRecordedDps, sample.totalGyro)

                // 冲击观察窗判断
                if (now - impactTimestamp <= config.impactWindowMs) {
                    val hasHighRotation = sample.totalGyro >= config.gyroThresholdDps
                    val hasPostureTilt = abs(sample.roll) >= config.tiltThresholdDeg ||
                            abs(sample.pitch) >= config.tiltThresholdDeg

                    if (hasHighRotation || hasPostureTilt) {
                        currentStage = DetectionStage.POSTURE_TILTED
                    }
                } else {
                    // 超出冲击窗未伴随翻转/倾角（如急刹车或正常颠簸），重置
                    reset()
                }
            }

            DetectionStage.POSTURE_TILTED -> {
                // 进入静止窗口确认
                currentStage = DetectionStage.STILLNESS_CHECK
                stillnessStartTimestamp = now
                stillnessAccBuffer.clear()
                stillnessAccBuffer.add(sample.totalAcc)
            }

            DetectionStage.STILLNESS_CHECK -> {
                stillnessAccBuffer.add(sample.totalAcc)
                if (stillnessAccBuffer.size > 200) {
                    stillnessAccBuffer.removeAt(0)
                }

                val elapsed = now - stillnessStartTimestamp
                if (elapsed >= config.stillnessConfirmDurationMs) {
                    // 计算加速度方差
                    val variance = calculateVariance(stillnessAccBuffer)
                    if (variance <= config.stillnessVarianceThreshold) {
                        // 确认事故！
                        val result = Pair(maxImpactRecordedG, maxGyroRecordedDps)
                        reset()
                        return result
                    } else {
                        // 骑手明显活动，排除误报
                        reset()
                    }
                }
            }
        }
        return null
    }

    fun reset() {
        currentStage = DetectionStage.IDLE
        impactTimestamp = 0L
        maxImpactRecordedG = 0f
        maxGyroRecordedDps = 0f
        stillnessAccBuffer.clear()
        stillnessStartTimestamp = 0L
    }

    val stage: DetectionStage get() = currentStage

    private fun calculateVariance(list: List<Float>): Float {
        if (list.isEmpty()) return 0f
        val mean = list.average().toFloat()
        var sumSquares = 0f
        for (item in list) {
            val diff = item - mean
            sumSquares += diff * diff
        }
        return sumSquares / list.size
    }
}
