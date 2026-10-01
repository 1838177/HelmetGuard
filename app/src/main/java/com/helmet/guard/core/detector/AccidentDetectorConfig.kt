package com.helmet.guard.core.detector

/**
 * 事故检测算法配置与初始测试基准参数
 * 注意：以下均为实验室测试与工程样机初始基准值，实际落地需配合专业碰撞台车与实测标定。
 */
data class AccidentDetectorConfig(
    // 冲击判定阈值：合加速度峰值 (单位: g, 初始测试值: 3.0g)
    val impactThresholdG: Float = 3.0f,

    // 冲击观察窗 (毫秒, 初始测试值: 200ms)
    val impactWindowMs: Long = 200L,

    // 旋转角速度突变阈值 (单位: °/s, 初始测试值: 200°/s)
    val gyroThresholdDps: Float = 200.0f,

    // 姿态倾斜失衡判定角 (单位: 度, 初始测试值: 45°)
    val tiltThresholdDeg: Float = 45.0f,

    // 碰撞后静止确认持续时长 (毫秒, 初始测试值: 5000ms)
    val stillnessConfirmDurationMs: Long = 5000L,

    // 静止判断加速度方差阈值 (单位: g^2, 初始测试值: 0.15)
    val stillnessVarianceThreshold: Float = 0.15f,

    // 默认防误触倒计时 (秒, 可在设置中修改为 5/10/15/30)
    val countdownDurationSec: Int = 15
)
