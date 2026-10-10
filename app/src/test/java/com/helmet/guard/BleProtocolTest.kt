package com.helmet.guard

import com.helmet.guard.core.ble.Crc8
import com.helmet.guard.core.ble.FrameStreamDecoder
import com.helmet.guard.core.ble.HelmetFrameCodec
import com.helmet.guard.core.ble.HelmetProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BleProtocolTest {
    @Test fun `crc vector stays firmware compatible`() {
        val input = byteArrayOf(0xAA.toByte(), 0x55, 0x01, 0x03, 0x08, 0x01, 0x02)
        assertEquals(0x0F.toByte(), Crc8.compute(input))
    }

    @Test fun `fragmented and coalesced notifications are reassembled`() {
        val first = HelmetFrameCodec.encode(HelmetProtocol.TYPE_BUTTON, 7, byteArrayOf(3))
        val second = HelmetFrameCodec.encode(HelmetProtocol.TYPE_HEARTBEAT, 8, byteArrayOf(1, 2, 3))
        val decoder = FrameStreamDecoder()
        assertTrue(decoder.append(first.copyOfRange(0, 4)).isEmpty())
        val frames = decoder.append(first.copyOfRange(4, first.size) + second)
        assertEquals(2, frames.size)
        assertEquals(7, frames[0].sequence)
        assertEquals(8, frames[1].sequence)
    }

    @Test fun `corrupt crc is rejected and stream recovers`() {
        val bad = HelmetFrameCodec.encode(HelmetProtocol.TYPE_BUTTON, 1, byteArrayOf(1)).also { it[it.lastIndex] = 0 }
        val good = HelmetFrameCodec.encode(HelmetProtocol.TYPE_BUTTON, 2, byteArrayOf(2))
        val frames = FrameStreamDecoder().append(bad + good)
        assertEquals(1, frames.size)
        assertEquals(2, frames.single().sequence)
    }

    @Test fun `telemetry payload retains engineering units`() {
        val payload = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1234)
            .putShort(100).putShort((-200).toShort()).putShort(980)
            .putShort(125).putShort((-225).toShort()).putShort(50)
            .putShort(120).putShort((-35).toShort()).putShort(900)
            .put(87).put(3).array()
        val frame = FrameStreamDecoder().parse(HelmetFrameCodec.encode(HelmetProtocol.TYPE_TELEMETRY, 9, payload))
        val value = frame?.let { HelmetFrameCodec.telemetry(it, 10, 20) }
        assertNotNull(value)
        assertEquals(0.1f, value!!.ax, 0.0001f)
        assertEquals(-22.5f, value.gy, 0.0001f)
        assertEquals(87, value.batteryPercent)
        assertTrue(value.imuHealthy)
        assertTrue(value.calibrated)
        assertTrue(value.batteryValid) // Legacy non-zero battery remains compatible without bit 2.
    }

    @Test fun `zero battery is unknown unless firmware marks it valid`() {
        fun decode(flags: Byte) = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1)
            .putShort(0).putShort(0).putShort(1000)
            .putShort(0).putShort(0).putShort(0)
            .putShort(0).putShort(0).putShort(0)
            .put(0).put(flags).array()
            .let { HelmetFrameCodec.encode(HelmetProtocol.TYPE_TELEMETRY, 1, it) }
            .let { FrameStreamDecoder().parse(it)!! }
            .let { HelmetFrameCodec.telemetry(it, 1, 1)!! }

        assertEquals(false, decode(0x03).batteryValid)
        assertEquals(true, decode(0x07).batteryValid)
    }
}
