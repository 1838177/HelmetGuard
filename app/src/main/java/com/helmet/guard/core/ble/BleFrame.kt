package com.helmet.guard.core.ble

/**
 * 二进制协议帧及业务有效载荷实体定义
 */
data class RawBleFrame(
    val protocolVersion: Byte,
    val msgType: Byte,
    val sequence: Int,
    val length: Int,
    val payload: ByteArray,
    val crc: Byte
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawBleFrame
        return sequence == other.sequence &&
                msgType == other.msgType &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = sequence
        result = 31 * result + msgType
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * 传感器解析数据帧 (六轴 IMU + 姿态 + 电量)
 */
data class SensorDataFrame(
    val timestampMs: Long,
    val ax: Float, // g
    val ay: Float, // g
    val az: Float, // g
    val totalAcc: Float, // g
    val gx: Float, // °/s
    val gy: Float, // °/s
    val gz: Float, // °/s
    val totalGyro: Float, // °/s
    val roll: Float, // °
    val pitch: Float, // °
    val yaw: Float, // ° (短时间相对航向)
    val batteryPercent: Int, // 0..100
    val isImuNormal: Boolean,
    val isCalibrated: Boolean
) {
    companion object {
        val STATIC_BASELINE = SensorDataFrame(
            timestampMs = 0L,
            ax = 0.0f,
            ay = 0.0f,
            az = 1.0f,
            totalAcc = 1.0f,
            gx = 0.0f,
            gy = 0.0f,
            gz = 0.0f,
            totalGyro = 0.0f,
            roll = 0.0f,
            pitch = 0.0f,
            yaw = 0.0f,
            batteryPercent = 100,
            isImuNormal = true,
            isCalibrated = true
        )
    }
}

/**
 * 头盔硬件上报的事故锁定事件帧
 */
data class AccidentEventFrame(
    val deviceId: Long,
    val eventId: Long,
    val impactPeakG: Float,
    val gyroPeakDps: Float,
    val stillDurationSec: Float,
    val hardwareCountdownSec: Int,
    val needsPhoneAck: Boolean,
    val isBuzzerActive: Boolean
)

/**
 * 物理按键事件帧
 */
enum class HelmetButtonAction {
    SINGLE_CLICK,
    DOUBLE_CLICK,
    LONG_PRESS,
    UNKNOWN
}

data class ButtonEventFrame(
    val action: HelmetButtonAction,
    val rawCode: Byte
)
