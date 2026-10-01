package com.helmet.guard.core.extension

import kotlinx.coroutines.flow.Flow

/**
 * HUD 抬头显示投屏扩展接口 (预留)
 */
interface IHudDisplayBridge {
    fun sendSpeedDisplay(speedKmh: Float)
    fun sendNavigationTurn(iconResId: Int, distanceMeters: Int, roadName: String)
    fun sendAlertWarning(warningText: String)
}

/**
 * 头盔内置行车记录仪/摄像头扩展接口 (预留)
 */
interface IHelmetCameraBridge {
    fun startRecording()
    fun stopRecording()
    fun captureSnapshot(): ByteArray?
    fun getVideoLiveStreamUrl(): String?
    fun lockEventVideo(eventId: Long, durationBeforeSec: Int = 30, durationAfterSec: Int = 30)
}

/**
 * AR 骑行导航扩展接口 (预留)
 */
interface IArNavigationBridge {
    fun updateRiderHeading(yawDegrees: Float)
    fun projectArArrow(bearingToTarget: Float, distanceMeters: Float)
}

/**
 * 外卖/快递物流订单系统联动扩展接口 (预留)
 */
data class DeliveryOrderInfo(
    val orderId: String,
    val merchantName: String,
    val destinationAddress: String,
    val remainingDeliveryMinutes: Int
)

interface IDeliveryOrderBridge {
    fun observeCurrentOrder(): Flow<DeliveryOrderInfo?>
    fun notifyAccidentToPlatform(orderId: String, reason: String)
}
