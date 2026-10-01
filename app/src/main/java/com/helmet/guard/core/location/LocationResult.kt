package com.helmet.guard.core.location

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 定位结果实体，区分高精度实时定位、历史降级定位与不可用状态
 */
sealed class LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val timestampMs: Long,
        val isHistoricalFallback: Boolean
    ) : LocationResult() {
        fun formatSummary(): String {
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
            val mapUrl = "https://uri.amap.com/marker?position=$longitude,$latitude&name=事故现场&coordinate=wgs84"
            return if (isHistoricalFallback) {
                "注意: 当前为历史降级定位\n时间: $timeStr, 精度约 ${accuracyMeters.toInt()} 米\n坐标: $latitude, $longitude\n高德地图: $mapUrl"
            } else {
                "实时定位时间: $timeStr, 精度约 ${accuracyMeters.toInt()} 米\n坐标: $latitude, $longitude\n高德地图: $mapUrl"
            }
        }
    }

    data class Failure(val reason: String) : LocationResult()
}
