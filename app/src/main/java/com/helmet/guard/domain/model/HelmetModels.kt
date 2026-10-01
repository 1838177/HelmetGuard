package com.helmet.guard.domain.model

enum class ConnectionStatus {
    UNBOUND,        // 尚未绑定
    DISCONNECTED,   // 未连接 (红)
    CONNECTING,     // 正在连接 (黄)
    CONNECTED,      // 已连接 (绿)
    UNSTABLE        // 连接不稳定 / 信号极弱 (黄)
}

enum class AccidentLifecycleState {
    SAFE,               // 正常安全
    SUSPECTED,          // 疑似异常运动
    COUNTDOWN,          // 倒计时中 (等待用户确认)
    EVENT_LOCKED,       // 倒计时结束，事故事件锁定
    WAITING_FOR_PHONE,  // 头盔离线暂存，等待手机恢复连接
    LOCATING,           // 获取定位信息中
    SENDING,            // 提交紧急短信中
    SENT,               // 报警已向运营商提交
    SEND_FAILED,        // 报警发送失败
    RETRYING,           // 重试发送中
    CANCELLED           // 报警已由用户取消
}

data class HelmetDevice(
    val name: String,
    val address: String,
    val rssi: Int? = null,
    val batteryPercent: Int? = null,
    val firmwareVersion: String? = null,
    val protocolVersion: String? = null,
    val lastConnectedTimeMs: Long = 0L,
    val isAutoReconnectEnabled: Boolean = true
)

data class EmergencyContact(
    val id: Long = 0L,
    val name: String,
    val phone: String,
    val isPrimary: Boolean = true,
    val relationship: String = "亲友",
    val isEnabled: Boolean = true
)

data class AccidentRecord(
    val id: Long = 0L,
    val eventId: Long,
    val timestampMs: Long,
    val impactPeakG: Float,
    val gyroPeakDps: Float,
    val stillDurationSec: Float,
    val latitude: Double,
    val longitude: Double,
    val locationAccuracy: Float,
    val isHistoricalLocation: Boolean,
    val status: String,
    val dispatchResult: String,
    val isMock: Boolean = false
)

enum class LogType {
    BLE_CONNECT,
    BLE_DISCONNECT,
    LOW_BATTERY,
    SENSOR_ANOMALY,
    ALARM_TRIGGER,
    ALARM_CANCEL,
    ALARM_SENT_SUCCESS,
    ALARM_SENT_FAIL,
    CALIBRATION,
    SYSTEM
}

data class DeviceLog(
    val id: Long = 0L,
    val timestampMs: Long,
    val type: LogType,
    val title: String,
    val detail: String,
    val result: String
)
