package com.helmet.guard

import com.helmet.guard.core.dispatcher.EmergencyMessage
import com.helmet.guard.core.location.LocationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyMessageFormatTest {

    @Test
    fun testAlertMessageFormatWithAccurateLocation() {
        val location = LocationResult.Success(
            latitude = 31.2304,
            longitude = 121.4737,
            accuracyMeters = 15.0f,
            timestampMs = 1774087800000L,
            isHistoricalFallback = false
        )

        val message = EmergencyMessage.buildAlertBody(
            deviceName = "智能头盔 Pro",
            batteryPercent = 85,
            locationResult = location,
            timestampMs = 1774087800000L
        )

        assertTrue(message.contains("[智能头盔事故报警]"))
        assertTrue(message.contains("31.2304"))
        assertTrue(message.contains("121.4737"))
        assertTrue(message.contains("精度约 15 米"))
        assertTrue(message.contains("85%"))

        // 验证禁止包含任何 Emoji 字符 (Unicode Emoji 区间)
        val emojiRegex = Regex("[\\p{So}\\p{Cn}]")
        assertFalse("报警文本严格禁止包含 Emoji 表情", emojiRegex.containsMatchIn(message))
    }

    @Test
    fun testAlertMessageFormatWithHistoricalFallbackLocation() {
        val location = LocationResult.Success(
            latitude = 31.2000,
            longitude = 121.4000,
            accuracyMeters = 40.0f,
            timestampMs = 1774087500000L,
            isHistoricalFallback = true
        )

        val message = EmergencyMessage.buildAlertBody(
            deviceName = "智能头盔 Pro",
            batteryPercent = 60,
            locationResult = location
        )

        assertTrue("降级历史定位必须在消息中显著注明", message.contains("当前为历史降级定位"))
    }

    @Test
    fun testAlertMessageFormatWithLocationFailure() {
        val failureLocation = LocationResult.Failure("卫星信号搜寻超时")
        val message = EmergencyMessage.buildAlertBody(
            deviceName = "智能头盔 Pro",
            batteryPercent = 50,
            locationResult = failureLocation
        )

        assertTrue("定位失败时必须提示直接电话联系", message.contains("请直接拨打骑手电话联系"))
    }
}
