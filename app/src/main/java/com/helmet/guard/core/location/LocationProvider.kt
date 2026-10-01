package com.helmet.guard.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 具有超时降级与精度校验的定位提供者
 */
class LocationProvider(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getAccurateLocation(timeoutMs: Long = 10000L): LocationResult {
        return try {
            val tokenSource = CancellationTokenSource()
            // 优先获取实时高精度 GPS 位置
            val freshLocation: Location? = withTimeoutOrNull(timeoutMs) {
                awaitTask(
                    fusedClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        tokenSource.token
                    )
                )
            }

            if (freshLocation != null) {
                LocationResult.Success(
                    latitude = freshLocation.latitude,
                    longitude = freshLocation.longitude,
                    accuracyMeters = freshLocation.accuracy,
                    timestampMs = freshLocation.time,
                    isHistoricalFallback = false
                )
            } else {
                // 超时，尝试降级读取最后已知位置
                val lastKnown: Location? = awaitTask(fusedClient.lastLocation)
                if (lastKnown != null) {
                    LocationResult.Success(
                        latitude = lastKnown.latitude,
                        longitude = lastKnown.longitude,
                        accuracyMeters = lastKnown.accuracy,
                        timestampMs = lastKnown.time,
                        isHistoricalFallback = true
                    )
                } else {
                    LocationResult.Failure("获取定位超时且无历史定位缓存")
                }
            }
        } catch (e: SecurityException) {
            LocationResult.Failure("未授予定位权限: ${e.message}")
        } catch (e: Exception) {
            LocationResult.Failure("定位异常: ${e.localizedMessage ?: "未知错误"}")
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T? = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { result ->
            cont.resume(result)
        }.addOnFailureListener { exception ->
            cont.resumeWithException(exception)
        }.addOnCanceledListener {
            cont.cancel()
        }
    }
}
