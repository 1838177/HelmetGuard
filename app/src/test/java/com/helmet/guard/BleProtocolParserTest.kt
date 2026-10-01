package com.helmet.guard

import com.helmet.guard.core.ble.BleCommandEncoder
import com.helmet.guard.core.ble.BleConstants
import com.helmet.guard.core.ble.BleProtocolParser
import com.helmet.guard.core.ble.HelmetButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BleProtocolParserTest {

    @Test
    fun testCrc8Calculation() {
        val testData = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x01, 0x01, 0x00, 0x00)
        val crc1 = BleProtocolParser.computeCrc8(testData)
        val crc2 = BleProtocolParser.computeCrc8(testData)
        assertEquals("CRC8 计算应保证确定性", crc1, crc2)
    }

    @Test
    fun testRawFrameParsingAndCrcValidation() {
        // 构建一个控制帧
        val frameBytes = BleCommandEncoder.buildFindHelmetFrame(5)
        val raw = BleProtocolParser.parseRawFrame(frameBytes)

        assertNotNull("合法帧应成功解出", raw)
        assertEquals(BleConstants.MSG_TYPE_COMMAND_ACK, raw?.msgType)
        assertEquals(2, raw?.length)

        // 篡改一个字节，验证 CRC 校验失败
        val corruptBytes = frameBytes.clone()
        corruptBytes[6] = (corruptBytes[6] + 1).toByte()
        val corruptRaw = BleProtocolParser.parseRawFrame(corruptBytes)
        assertNull("CRC 损坏帧必须返回 null 被丢弃", corruptRaw)
    }

    @Test
    fun testSensorPayloadParsing() {
        // 模拟 24 字节传感器 payload
        val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(123456) // timestamp
        buffer.putShort(1200.toShort()) // ax = 1.2g
        buffer.putShort((-500).toShort()) // ay = -0.5g
        buffer.putShort(980.toShort()) // az = 0.98g
        buffer.putShort(150.toShort()) // gx = 15.0 °/s
        buffer.putShort(20.toShort())  // gy = 2.0 °/s
        buffer.putShort((-45).toShort()) // gz = -4.5 °/s
        buffer.putShort(350.toShort()) // roll = 35.0°
        buffer.putShort((-120).toShort()) // pitch = -12.0°
        buffer.putShort(1800.toShort()) // yaw = 180.0°
        buffer.put(88.toByte()) // battery = 88%
        buffer.put(0x03.toByte()) // status: Bit0(IMU正常) | Bit1(已校准)

        val parsed = BleProtocolParser.parseSensorPayload(buffer.array())
        assertNotNull(parsed)
        assertEquals(123456L, parsed?.timestampMs)
        assertEquals(1.2f, parsed?.ax ?: 0f, 0.001f)
        assertEquals(-0.5f, parsed?.ay ?: 0f, 0.001f)
        assertEquals(0.98f, parsed?.az ?: 0f, 0.001f)
        assertEquals(15.0f, parsed?.gx ?: 0f, 0.01f)
        assertEquals(35.0f, parsed?.roll ?: 0f, 0.01f)
        assertEquals(-12.0f, parsed?.pitch ?: 0f, 0.01f)
        assertEquals(88, parsed?.batteryPercent)
        assertTrue(parsed?.isImuNormal == true)
        assertTrue(parsed?.isCalibrated == true)
    }

    @Test
    fun testButtonPayloadParsing() {
        val singleClick = BleProtocolParser.parseButtonPayload(byteArrayOf(0x01))
        assertEquals(HelmetButtonAction.SINGLE_CLICK, singleClick?.action)

        val longPress = BleProtocolParser.parseButtonPayload(byteArrayOf(0x03))
        assertEquals(HelmetButtonAction.LONG_PRESS, longPress?.action)
    }
}
