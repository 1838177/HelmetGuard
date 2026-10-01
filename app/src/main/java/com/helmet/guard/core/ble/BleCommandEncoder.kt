package com.helmet.guard.core.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 控制指令组装器
 * 负责将手机端指令序列化为符合协议的二进制帧，附带序号与 CRC8 校验码
 */
object BleCommandEncoder {

    private var sequenceCounter: Int = 0

    @Synchronized
    private fun getNextSeq(): Byte {
        sequenceCounter = (sequenceCounter + 1) and 0xFF
        return sequenceCounter.toByte()
    }

    /**
     * 基础帧封包
     */
    private fun buildFrame(msgType: Byte, payload: ByteArray): ByteArray {
        val seq = getNextSeq()
        val len = payload.size.toByte()
        val totalSize = 2 + 1 + 1 + 1 + 1 + payload.size + 1
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(BleConstants.FRAME_HEADER_BYTE1)
        buffer.put(BleConstants.FRAME_HEADER_BYTE2)
        buffer.put(BleConstants.PROTOCOL_VERSION)
        buffer.put(msgType)
        buffer.put(seq)
        buffer.put(len)
        buffer.put(payload)

        val dataBeforeCrc = buffer.array()
        val crc = BleProtocolParser.computeCrc8(dataBeforeCrc, 0, totalSize - 1)
        buffer.put(crc)

        return buffer.array()
    }

    /**
     * 构建事故事件 ACK 确认帧 (告知头盔手机已收到并处理该事件)
     */
    fun buildAckFrame(eventId: Long, statusCode: Byte = 0x00): ByteArray {
        val payload = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .put(BleConstants.CMD_ACK_EVENT)
            .putInt((eventId and 0xFFFFFFFFL).toInt())
            .put(statusCode)
            .array()
        return buildFrame(BleConstants.MSG_TYPE_COMMAND_ACK, payload)
    }

    /**
     * 构建取消报警命令帧
     * @param cancelSource 0x01: 手机长按取消, 0x02: 头盔按键取消
     */
    fun buildCancelAlarmFrame(eventId: Long, cancelSource: Byte = 0x01): ByteArray {
        val payload = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .put(BleConstants.CMD_CANCEL_ALARM)
            .putInt((eventId and 0xFFFFFFFFL).toInt())
            .put(cancelSource)
            .array()
        return buildFrame(BleConstants.MSG_TYPE_COMMAND_ACK, payload)
    }

    /**
     * 构建查找头盔命令 (触发蜂鸣器鸣响)
     */
    fun buildFindHelmetFrame(durationSec: Int = 5): ByteArray {
        val payload = byteArrayOf(
            BleConstants.CMD_FIND_HELMET,
            durationSec.coerceIn(1, 30).toByte()
        )
        return buildFrame(BleConstants.MSG_TYPE_COMMAND_ACK, payload)
    }

    /**
     * 构建零偏校准命令帧
     */
    fun buildCalibrateImuFrame(): ByteArray {
        val payload = byteArrayOf(BleConstants.CMD_CALIBRATE_IMU)
        return buildFrame(BleConstants.MSG_TYPE_COMMAND_ACK, payload)
    }

    /**
     * 构建时间同步帧
     */
    fun buildSyncTimeFrame(epochSeconds: Long = System.currentTimeMillis() / 1000): ByteArray {
        val payload = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
            .put(BleConstants.CMD_SYNC_TIME)
            .putInt((epochSeconds and 0xFFFFFFFFL).toInt())
            .array()
        return buildFrame(BleConstants.MSG_TYPE_COMMAND_ACK, payload)
    }
}
