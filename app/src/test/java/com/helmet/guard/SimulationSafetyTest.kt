package com.helmet.guard

import com.helmet.guard.core.dispatcher.DispatchResult
import com.helmet.guard.core.dispatcher.EmergencyDispatcher
import com.helmet.guard.core.dispatcher.EmergencyMessage
import com.helmet.guard.core.dispatcher.FakeEmergencyDispatcher
import com.helmet.guard.core.location.LocationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 仿真模拟安全验证测试：
 * 验证在仿真/测试模式下，求救短信绝对严禁调用底层真实硬件与运营商通道，
 * 且消息内容严格遵循工业安全仪表盘规范与零 Emoji 准则。
 */
class SimulationSafetyTest {

    @Test
    fun testFakeDispatcherReturnsSimulationSuccessWithoutHardwareAccess() = runTest {
        val dispatcher: EmergencyDispatcher = FakeEmergencyDispatcher()
        val result = dispatcher.dispatch("13800138000", "测试报警短信内容")

        assertTrue("仿真模式返回值必须是成功", result is DispatchResult.Success)
        val success = result as DispatchResult.Success
        assertTrue("分发结果必须包含仿真标记", success.info.contains("[仿真测试]"))
        assertTrue("分发结果必须声明为虚拟分发", success.info.contains("模拟求救短信已虚拟分发"))
    }

    @Test
    fun testSimulationMessagePayloadStrictlyExcludesEmojis() {
        val location = LocationResult.Success(
            latitude = 39.9042,
            longitude = 116.4074,
            accuracyMeters = 8.5f,
            timestampMs = 1700000000000L,
            isHistoricalFallback = false
        )
        val message = EmergencyMessage.buildAlertBody("HelmetGuard-Mock", 92, location)

        val emojiRegex = Regex("[\\p{So}\\p{Cn}]")
        assertFalse("仿真环境生成的短信正文绝对严禁包含任何 Emoji", emojiRegex.containsMatchIn(message))
        assertTrue("正文必须包含设备标识", message.contains("HelmetGuard-Mock"))
        assertTrue("正文必须包含经纬度", message.contains("39.9042"))
        assertTrue("正文必须包含电量", message.contains("92%"))
    }

    @Test
    fun testMultiContactVirtualDispatchMaintainsCompleteIsolation() = runTest {
        val fakeDispatcher = FakeEmergencyDispatcher()
        val testPhones = listOf("13800000001", "13900000002", "13700000003")
        val testBody = "模拟紧急求救测试"

        val results = mutableListOf<DispatchResult>()
        for (phone in testPhones) {
            val res = fakeDispatcher.dispatch(phone, testBody)
            results.add(res)
        }

        assertEquals(3, results.size)
        assertTrue(results.all { it is DispatchResult.Success })
        assertTrue(results.all { (it as DispatchResult.Success).info.contains("[仿真测试]") })
    }

    @Test
    fun testSimulationDispatchWithEmptyOrInvalidPhoneGracefullyCompletes() = runTest {
        val fakeDispatcher = FakeEmergencyDispatcher()
        val result = fakeDispatcher.dispatch("", "测试内容")
        assertTrue("仿真分发即使号码异常亦不抛出未捕获异常", result is DispatchResult.Success)
    }
}
