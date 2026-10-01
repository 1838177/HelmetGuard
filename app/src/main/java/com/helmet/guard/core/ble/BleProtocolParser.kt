package com.helmet.guard.core.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * 协议二进制帧解析器
 * 负责字节流反序列化、校验和计算与数据还原 (小端序 Little-Endian)
 */
object BleProtocolParser {

    /**
     * 计算 CRC-8-CCITT
     * 多项式: x^8 + x^2 + x + 1 (0x07), 初始值: 0x00
     */
    fun computeCrc8(data: ByteArray, offset: Int = 0, length: Int = data.size): Byte {
        var crc = 0x00
        for (i in offset until (offset + length)) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc.toByte()
    }

    /**
     * 拆解原始帧
     * 格式: [0xAA 0x55] [Ver:1B] [MsgType:1B] [Seq:1B] [Len:1B] [Payload:Len] [CRC:1B]
     */
    fun parseRawFrame(bytes: ByteArray): RawBleFrame? {
        if (bytes.size < 7) return null
        if (bytes[0] != BleConstants.FRAME_HEADER_BYTE1 || bytes[1] != BleConstants.FRAME_HEADER_BYTE2) {
            return null
        }

        val ver = bytes[2]
        val msgType = bytes[3]
        val seq = bytes[4].toInt() and 0xFF
        val payloadLen = bytes[5].toInt() and 0xFF

        val expectedTotalSize = 6 + payloadLen + 1
        if (bytes.size < expectedTotalSize) return null

        // 校验 CRC8
        val expectedCrc = bytes[6 + payloadLen]
        val calculatedCrc = computeCrc8(bytes, 0, 6 + payloadLen)
        if (expectedCrc != calculatedCrc) {
            return null // CRC 校验失败
        }

        val payload = ByteArray(payloadLen)
        System.arraycopy(bytes, 6, payload, 0, payloadLen)

        return RawBleFrame(
            protocolVersion = ver,
            msgType = msgType,
            sequence = seq,
            length = payloadLen,
            payload = payload,
            crc = expectedCrc
        )
    }

    /**
     * 解析传感器遥测数据 (MsgType = 0x01, Payload = 24 字节)
     */
    fun parseSensorPayload(payload: ByteArray): SensorDataFrame? {
        if (payload.size < 24) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
        val axRaw = buffer.short.toInt()
        val ayRaw = buffer.short.toInt()
        val azRaw = buffer.short.toInt()

        val gxRaw = buffer.short.toInt()
        val gyRaw = buffer.short.toInt()
        val gzRaw = buffer.short.toInt()

        val rollRaw = buffer.short.toInt()
        val pitchRaw = buffer.short.toInt()
        val yawRaw = buffer.short.toInt()

        val battery = buffer.get().toInt() and 0xFF
        val status = buffer.get().toInt() and 0xFF

        val ax = axRaw / 1000f
        val ay = ayRaw / 1000f
        val az = azRaw / 1000f
        val totalAcc = sqrt(ax * ax + ay * ay + az * az)

        val gx = gxRaw / 10f
        val gy = gyRaw / 10f
        val gz = gzRaw / 10f
        val totalGyro = sqrt(gx * gx + gy * gy + gz * gz)

        val roll = rollRaw / 10f
        val pitch = pitchRaw / 10f
        val yaw = yawRaw / 10f

        val isImuNormal = (status and 0x01) != 0
        val isCalibrated = (status and 0x02) != 0

        return SensorDataFrame(
            timestampMs = timestamp,
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
            batteryPercent = battery.coerceIn(0, 100),
            isImuNormal = isImuNormal,
            isCalibrated = isCalibrated
        )
    }

    /**
     * 解析事故事件数据 (MsgType = 0x02, Payload = 16 字节)
     */
    fun parseAccidentPayload(payload: ByteArray): AccidentEventFrame? {
        if (payload.size < 16) return null
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val deviceId = buffer.int.toLong() and 0xFFFFFFFFL
        val eventId = buffer.int.toLong() and 0xFFFFFFFFL
        val impactPeakRaw = buffer.short.toInt()
        val gyroPeakRaw = buffer.short.toInt()
        val stillDurationRaw = buffer.short.toInt() and 0xFFFF
        val countdown = buffer.get().toInt() and 0xFF
        val flag = buffer.get().toInt() and 0xFF

        val impactPeakG = impactPeakRaw / 100f
        val gyroPeakDps = gyroPeakRaw.toFloat()
        val stillDurationSec = stillDurationRaw / 10f

        return AccidentEventFrame(
            deviceId = deviceId,
            eventId = eventId,
            impactPeakG = impactPeakG,
            gyroPeakDps = gyroPeakDps,
            stillDurationSec = stillDurationSec,
            hardwareCountdownSec = countdown,
            needsPhoneAck = (flag and 0x01) != 0,
            isBuzzerActive = (flag and 0x02) != 0
        )
    }

    /**
     * 解析按键事件 (MsgType = 0x03, Payload = 1 字节)
     */
    fun parseButtonPayload(payload: ByteArray): ButtonEventFrame? {
        if (payload.isEmpty()) return null
        val code = payload[0]
        val action = when (code.toInt()) {
            0x01 -> HelmetButtonAction.SINGLE_CLICK
            0x02 -> HelmetButtonAction.DOUBLE_CLICK
            0x03 -> HelmetButtonAction.LONG_PRESS
            else -> HelmetButtonAction.UNKNOWN
        }
        return ButtonEventFrame(action = action, rawCode = code)
    }
}
