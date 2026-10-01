package com.helmet.guard.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.helmet.guard.HelmetGuardApp
import com.helmet.guard.MainActivity
import com.helmet.guard.R
import com.helmet.guard.domain.model.AccidentLifecycleState
import com.helmet.guard.domain.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 前台保活服务
 * 保持蓝牙后台监听、传感器数据流接收与事故告警状态机驻留
 */
class HelmetGuardService : Service() {

    companion object {
        const val TAG = "HelmetGuardService"
        const val CHANNEL_SERVICE_ID = "helmet_guard_service_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            try {
                val intent = Intent(context, HelmetGuardService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "启动前台服务失败: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, HelmetGuardService::class.java)
                context.stopService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "停止前台服务失败: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_SERVICE_ID,
                    getString(R.string.notification_channel_service),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notification_channel_service_desc)
                    setShowBadge(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            } catch (e: Throwable) {
                Log.w(TAG, "创建通知渠道失败: ${e.message}")
            }
        }
    }

    private fun startAsForeground() {
        try {
            val notification = buildNotification("Helmet Guard", "正在监测头盔状态")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasBt = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

                var type = 0
                if (hasBt) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                if (hasLocation) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

                if (type != 0) {
                    startForeground(NOTIFICATION_ID, notification, type)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startForeground 异常拦截: ${e.message}", e)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildNotification(title, content))
        } catch (e: Throwable) {
            Log.w(TAG, "更新通知失败: ${e.message}")
        }
    }

    private fun observeState() {
        val app = application as? HelmetGuardApp ?: return
        val repo = app.repository

        serviceScope.launch {
            repo.connectionStatus.collect { status ->
                val statusText = when (status) {
                    ConnectionStatus.CONNECTED -> "头盔已连接 - 监测正常"
                    ConnectionStatus.CONNECTING -> "正在重新连接头盔..."
                    ConnectionStatus.DISCONNECTED -> "头盔已断开连接"
                    ConnectionStatus.UNSTABLE -> "头盔连接不稳定"
                    ConnectionStatus.UNBOUND -> "未绑定头盔"
                }
                updateNotification("Helmet Guard", statusText)
            }
        }

        serviceScope.launch {
            repo.accidentState.collect { state ->
                if (state == AccidentLifecycleState.COUNTDOWN) {
                    updateNotification("警告: 疑似发生事故", "正在进行事故倒计时确认，请在手机或头盔取消")
                } else if (state == AccidentLifecycleState.SENT) {
                    updateNotification("紧急提醒", "事故报警短信已成功提交")
                }
            }
        }
    }
}
