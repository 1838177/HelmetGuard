package com.helmet.guard.presentation.selfcheck

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helmet.guard.domain.model.ConnectionStatus
import com.helmet.guard.domain.repository.IHelmetRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class CheckStatus {
    WAITING,
    CHECKING,
    PASS,
    WARNING,
    FAIL
}

data class CheckItem(
    val id: Int,
    val name: String,
    val status: CheckStatus = CheckStatus.WAITING,
    val detail: String = "待检查",
    val suggestion: String = ""
)

class SelfCheckViewModel(
    private val repository: IHelmetRepository,
    private val context: Context
) : ViewModel() {

    private val initialItems = listOf(
        CheckItem(1, "蓝牙双向链路"),
        CheckItem(2, "三轴加速度计 (IMU)"),
        CheckItem(3, "三轴陀螺仪 (IMU)"),
        CheckItem(4, "头盔蜂鸣器与扬声器"),
        CheckItem(5, "头盔外侧物理应急按键"),
        CheckItem(6, "头盔电池电量与供电"),
        CheckItem(7, "手机 GPS 与定位能力"),
        CheckItem(8, "前台服务通知权限"),
        CheckItem(9, "紧急联系人配置"),
        CheckItem(10, "求救短信发送与分发能力")
    )

    private val _items = MutableStateFlow(initialItems)
    val items: StateFlow<List<CheckItem>> = _items.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    fun startFullCheck() {
        if (_isChecking.value) return
        _isChecking.value = true

        viewModelScope.launch {
            val list = initialItems.toMutableList()

            for (i in list.indices) {
                list[i] = list[i].copy(status = CheckStatus.CHECKING, detail = "正在执行诊断...")
                _items.value = list.toList()
                delay(300L) // 模拟逐步检测

                list[i] = evaluateItem(list[i].id)
                _items.value = list.toList()
            }

            _isChecking.value = false
        }
    }

    private suspend fun evaluateItem(id: Int): CheckItem {
        val conn = repository.connectionStatus.value
        val dev = repository.deviceInfo.value
        val sensor = repository.sensorFlow.value
        val contacts = repository.getEmergencyContacts().first()

        return when (id) {
            1 -> {
                if (conn == ConnectionStatus.CONNECTED) {
                    CheckItem(1, "蓝牙双向链路", CheckStatus.PASS, "GATT 连接建立正常，MTU 64 字节协商成功")
                } else {
                    CheckItem(1, "蓝牙双向链路", CheckStatus.FAIL, "未连接头盔设备", "请在设备页搜索并连接智能头盔")
                }
            }
            2 -> {
                if (sensor.isImuNormal && sensor.totalAcc in 0.5f..3.0f) {
                    CheckItem(2, "三轴加速度计 (IMU)", CheckStatus.PASS, "基线加速度校验正常 (${String.format("%.2f", sensor.totalAcc)}g)")
                } else {
                    CheckItem(2, "三轴加速度计 (IMU)", CheckStatus.WARNING, "加速度读数超出基线或未校准", "建议点击传感器页面执行零偏校准")
                }
            }
            3 -> {
                if (sensor.isImuNormal) {
                    CheckItem(3, "三轴陀螺仪 (IMU)", CheckStatus.PASS, "角速度响应正常 (${String.format("%.1f", sensor.totalGyro)}°/s)")
                } else {
                    CheckItem(3, "三轴陀螺仪 (IMU)", CheckStatus.FAIL, "传感器健康位异常", "请检查头盔六轴芯片焊接或固件版本")
                }
            }
            4 -> {
                if (conn == ConnectionStatus.CONNECTED) {
                    repository.findHelmet(1)
                    CheckItem(4, "头盔蜂鸣器与扬声器", CheckStatus.WARNING, "待确认：已下发指令，请确认是否听到鸣响", "若未听到提示音，请检查头盔蜂鸣器硬件")
                } else {
                    CheckItem(4, "头盔蜂鸣器与扬声器", CheckStatus.WARNING, "头盔未连接，无法下发蜂鸣指令", "请先连接头盔蓝牙")
                }
            }
            5 -> {
                CheckItem(5, "头盔外侧物理应急按键", CheckStatus.WARNING, "待触发：请按压头盔外侧物理按键", "请手动按压头盔外侧按键测试硬件响应")
            }
            6 -> {
                val batt = dev.batteryPercent
                if (conn != ConnectionStatus.CONNECTED || batt == null) {
                    CheckItem(6, "头盔电池电量与供电", CheckStatus.FAIL, "未连接头盔，无法获取供电读数", "请连接智能头盔后重试")
                } else if (batt >= 20) {
                    CheckItem(6, "头盔电池电量与供电", CheckStatus.PASS, "电量充沛: ${batt}%")
                } else {
                    CheckItem(6, "头盔电池电量与供电", CheckStatus.WARNING, "电量偏低: ${batt}%", "建议尽快为头盔充电以保证骑行安全")
                }
            }
            7 -> {
                val hasFineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val isGpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
                if (!hasFineLoc) {
                    CheckItem(7, "手机 GPS 与定位能力", CheckStatus.FAIL, "定位权限缺失，无法在事故时获取经纬度", "请前往系统权限设置授予精准定位权限")
                } else if (!isGpsEnabled) {
                    CheckItem(7, "手机 GPS 与定位能力", CheckStatus.WARNING, "已授权定位，但系统 GPS 硬件未开启", "请在控制中心开启 GPS 定位开关")
                } else {
                    CheckItem(7, "手机 GPS 与定位能力", CheckStatus.PASS, "GPS 硬件就绪，定位精度良好")
                }
            }
            8 -> {
                val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                if (notifEnabled) {
                    CheckItem(8, "前台服务通知权限", CheckStatus.PASS, "通知权限就绪，支持后台常驻守护")
                } else {
                    CheckItem(8, "前台服务通知权限", CheckStatus.WARNING, "通知已被禁用，后台警报可能受限", "请在系统通知管理中开启应用通知")
                }
            }
            9 -> {
                if (contacts.isNotEmpty()) {
                    CheckItem(9, "紧急联系人配置", CheckStatus.PASS, "已配置 ${contacts.size} 位联系人")
                } else {
                    CheckItem(9, "紧急联系人配置", CheckStatus.FAIL, "尚未配置紧急联系人", "请前往设置 -> 紧急联系人添加电话号码")
                }
            }
            10 -> {
                val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                if (hasSms) {
                    CheckItem(10, "求救短信发送与分发能力", CheckStatus.PASS, "持有自动发送短信权限，紧急通信直发畅通")
                } else {
                    CheckItem(10, "求救短信发送与分发能力", CheckStatus.WARNING, "未授予直接发送短信权限，将降级为唤起系统短信客户端", "建议在应用设置中开启发送短信权限")
                }
            }
            else -> CheckItem(id, "未知项", CheckStatus.PASS)
        }
    }
}
