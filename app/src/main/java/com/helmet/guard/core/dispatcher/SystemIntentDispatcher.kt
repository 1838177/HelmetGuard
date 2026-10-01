package com.helmet.guard.core.dispatcher

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 方案 B (合规兜底方案)：打开系统短信编辑发送界面，由用户或前台确认发送
 */
class SystemIntentDispatcher(private val context: Context) : EmergencyDispatcher {

    override suspend fun dispatch(phone: String, messageBody: String): DispatchResult {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phone")
                putExtra("sms_body", messageBody)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            DispatchResult.Success("已唤起系统短信客户端")
        } catch (e: Exception) {
            DispatchResult.Failure("唤起系统短信界面失败: ${e.localizedMessage}")
        }
    }
}
