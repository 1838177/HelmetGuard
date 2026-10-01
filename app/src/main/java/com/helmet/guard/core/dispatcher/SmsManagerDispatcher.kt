package com.helmet.guard.core.dispatcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager

/**
 * 样机与正式版短信直发组件：
 * 支持号码强力清洗、SmsManager 双重空指针兜底、SIM 卡状态校验及多段短信 PendingIntent 独立绑定。
 * 需声明并持有 SEND_SMS 权限。
 */
class SmsManagerDispatcher(private val context: Context) : EmergencyDispatcher {

    companion object {
        // 号码清洗正则：匹配 ASCII 及全角空格、横杠、减号、破折号、括号
        private val PHONE_CLEAN_REGEX = Regex("[\\s\\-\\(\\)\uFF08\uFF09\u2014\u2013\uFF0D\u3000]")

        /**
         * 强力清洗电话号码：支持去除中英文空格、连字符及中英文括号
         */
        fun cleanPhoneNumber(phone: String): String {
            return phone.replace(PHONE_CLEAN_REGEX, "").trim()
        }
    }

    override suspend fun dispatch(phone: String, messageBody: String): DispatchResult {
        // 1. 号码正则强力清洗（去空格、横杠、括号）
        val cleanPhone = cleanPhoneNumber(phone)
        if (cleanPhone.isBlank()) {
            return DispatchResult.Failure("紧急联系人电话号码为空")
        }

        return try {
            // 2. 双重空指针与 SIM 卡有效性防呆兜底
            val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            if (smsManager == null) {
                return DispatchResult.Failure("系统短信服务不可用 (SmsManager 为空)")
            }

            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null && telephonyManager.simState == TelephonyManager.SIM_STATE_ABSENT) {
                return DispatchResult.Failure("未检测到有效 SIM 卡，无法发送报警短信")
            }

            // 3. 多段短信 PendingIntent 绑定 requestCode = index（防 Android RequestCode 缓存覆写碰撞）
            val parts = smsManager.divideMessage(messageBody) ?: arrayListOf(messageBody)
            val sentIntents = ArrayList<PendingIntent>()

            for (index in parts.indices) {
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val intent = Intent("com.helmet.guard.SMS_SENT").apply {
                    setPackage(context.packageName)
                    data = Uri.parse("sms:$cleanPhone#$index")
                    putExtra("part_index", index)
                    putExtra("phone", cleanPhone)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    index, // requestCode = index 防缓存覆写碰撞
                    intent,
                    flags
                )
                sentIntents.add(pendingIntent)
            }

            smsManager.sendMultipartTextMessage(cleanPhone, null, parts, sentIntents, null)

            DispatchResult.Success("已向运营商提交报警短信发送请求 (共 ${parts.size} 条)")
        } catch (e: SecurityException) {
            DispatchResult.Failure("缺少自动发送短信权限，请先在安全设置中开启事故报警")
        } catch (e: Exception) {
            DispatchResult.Failure("短信提交失败: ${e.localizedMessage ?: "未知错误"}")
        }
    }
}
