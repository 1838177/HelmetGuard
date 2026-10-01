package com.helmet.guard.core.dispatcher

import kotlinx.coroutines.delay

/**
 * 仿真模拟环境下的紧急短信分发器
 * 绝对严禁调用任何底层 SmsManager 或系统 Intent，杜绝误发真实短信扣费或打扰紧急联系人
 */
class FakeEmergencyDispatcher : EmergencyDispatcher {

    override suspend fun dispatch(phone: String, messageBody: String): DispatchResult {
        // 模拟网络与分发耗时
        delay(800)
        return DispatchResult.Success("[仿真测试] 模拟求救短信已虚拟分发")
    }
}
