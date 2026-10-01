package com.helmet.guard.core.dispatcher

import com.helmet.guard.core.location.LocationResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 紧急求救文本生成器
 * 遵循专业、克制原则，严禁使用 Emoji 或夸张辞藻
 */
object EmergencyMessage {

    fun buildAlertBody(
        deviceName: String,
        batteryPercent: Int,
        locationResult: LocationResult,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
        val locationText = when (locationResult) {
            is LocationResult.Success -> locationResult.formatSummary()
            is LocationResult.Failure -> "位置状态: 当前未能获取有效定位信息 (${locationResult.reason})，请直接拨打骑手电话联系"
        }

        return """
[智能头盔事故报警]
系统检测到头盔佩戴者可能发生强烈碰撞或摔倒事故。
时间: $timeStr
设备: $deviceName
头盔电量: $batteryPercent%
$locationText

请尽快联系骑手核实安全情况。
(注: 此短信由安全系统自动分发，提交成功不代表联系人已查阅)
        """.trimIndent()
    }
}
