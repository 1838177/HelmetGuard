package com.helmet.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机广播接收器
 * 手机重启后自动恢复头盔监控服务
 */
class BootCompletedReceiver : BroadcastReceiver {
    constructor() : super()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            HelmetGuardService.startService(context)
        }
    }
}
