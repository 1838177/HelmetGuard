package com.helmet.guard.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.helmet.guard.core.location.LocationOutcome
import com.helmet.guard.core.location.NativeLocationProvider
import com.helmet.guard.core.sms.SmsPipeline
import com.helmet.guard.data.db.AccidentEntity
import com.helmet.guard.data.db.GuardDatabase
import com.helmet.guard.data.prefs.GuardPreferences
import com.helmet.guard.domain.DetectionFeatures
import com.helmet.guard.domain.EmergencyContact
import com.helmet.guard.domain.EmergencyStage
import com.helmet.guard.domain.EmergencyUiState
import com.helmet.guard.domain.IncidentCandidate
import com.helmet.guard.domain.IncidentSource
import com.helmet.guard.domain.LocationFix
import com.helmet.guard.domain.SmsPartState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class IncidentCoordinator(
    private val context: Context,
    private val database: GuardDatabase,
    private val preferences: GuardPreferences,
    private val locationProvider: NativeLocationProvider,
    private val smsPipeline: SmsPipeline
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val _state = MutableStateFlow(EmergencyUiState())
    val state: StateFlow<EmergencyUiState> = _state.asStateFlow()
    private var activeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var sendImmediately = false

    fun trigger(candidate: IncidentCandidate, countdownSeconds: Int? = null) {
        scope.launch {
            mutex.withLock {
                if (_state.value.stage in ACTIVE_STAGES) return@withLock
                val countdown = (countdownSeconds ?: preferences.countdownSeconds.first()).coerceIn(5, 30)
                val deadline = System.currentTimeMillis() + countdown * 1_000L
                val initialStage = if (candidate.isSimulation) EmergencyStage.COUNTDOWN else EmergencyStage.COUNTDOWN
                database.accidents().upsert(candidate.toEntity(initialStage, deadline))
                _state.value = EmergencyUiState(
                    stage = initialStage,
                    eventId = candidate.eventId,
                    secondsLeft = countdown,
                    confidence = candidate.confidence,
                    detail = if (candidate.isSimulation) "模拟演练：不会发送真实短信" else "请确认是否安全；倒计时结束后将自动求救",
                    canCancel = true,
                    isSimulation = candidate.isSimulation
                )
                notifyState()
                acquireWakeLock()
                activeJob = scope.launch { runCountdown(candidate, deadline) }
            }
        }
    }

    fun triggerManual(isSimulation: Boolean = false) {
        val now = System.currentTimeMillis()
        trigger(
            IncidentCandidate(now, now, IncidentSource.MANUAL, 1f, DetectionFeatures(peakAccelerationG = 0f), isSimulation),
            countdownSeconds = 5
        )
    }

    fun cancel(eventId: Long? = null) {
        scope.launch {
            mutex.withLock {
                val current = _state.value
                if (current.stage !in ACTIVE_STAGES || (eventId != null && eventId >= 0 && current.eventId != eventId)) return@withLock
                activeJob?.cancel()
                current.eventId?.let { database.accidents().updateStage(it, EmergencyStage.CANCELLED.name, "用户确认安全并取消") }
                _state.value = current.copy(stage = EmergencyStage.CANCELLED, secondsLeft = 0, detail = "已确认安全，本次不会发送求救短信", canCancel = false)
                notifyState()
                releaseWakeLock()
            }
        }
    }

    fun sendNow(eventId: Long? = null) {
        scope.launch {
            mutex.withLock {
                val current = _state.value
                if (current.stage != EmergencyStage.COUNTDOWN || (eventId != null && eventId >= 0 && current.eventId != eventId)) return@withLock
                sendImmediately = true
            }
        }
    }

    fun restorePending() {
        scope.launch {
            val pending = database.accidents().pending() ?: return@launch
            mutex.withLock {
                if (_state.value.stage in ACTIVE_STAGES) return@withLock
                val candidate = pending.toCandidate()
                val restoredRemaining = (pending.countdownDeadlineMs - System.currentTimeMillis()).coerceIn(0L, 30_000L)
                _state.value = EmergencyUiState(
                    EmergencyStage.valueOf(pending.stage), pending.eventId,
                    (restoredRemaining / 1_000L).toInt(),
                    pending.confidence, "恢复上次未完成的事故处理", pending.stage == EmergencyStage.COUNTDOWN.name,
                    pending.isSimulation
                )
                notifyState()
                acquireWakeLock()
                activeJob = scope.launch {
                    if (pending.stage == EmergencyStage.COUNTDOWN.name) {
                        runCountdown(candidate, System.currentTimeMillis() + restoredRemaining)
                    } else dispatch(candidate)
                }
            }
        }
    }

    private suspend fun runCountdown(candidate: IncidentCandidate, wallDeadlineMs: Long) {
        sendImmediately = false
        val remainingAtStart = (wallDeadlineMs - System.currentTimeMillis()).coerceAtLeast(0)
        val elapsedDeadline = android.os.SystemClock.elapsedRealtime() + remainingAtStart
        while (true) {
            val leftMs = elapsedDeadline - android.os.SystemClock.elapsedRealtime()
            if (leftMs <= 0 || sendImmediately) break
            val seconds = ((leftMs + 999) / 1_000).toInt()
            _state.value = _state.value.copy(secondsLeft = seconds)
            notifyState()
            delay(250)
        }
        if (candidate.isSimulation) {
            database.accidents().updateStage(candidate.eventId, EmergencyStage.SIMULATED.name, "模拟演练完成，未发送短信")
            _state.value = _state.value.copy(stage = EmergencyStage.SIMULATED, secondsLeft = 0, detail = "演练完成：定位与短信均未真实执行", canCancel = false)
            notifyState()
            releaseWakeLock()
            return
        }
        dispatch(candidate)
    }

    private suspend fun dispatch(candidate: IncidentCandidate) {
        val eventId = candidate.eventId
        database.accidents().updateStage(eventId, EmergencyStage.LOCATING.name, "正在获取事故位置")
        _state.value = _state.value.copy(stage = EmergencyStage.LOCATING, secondsLeft = 0, detail = "正在通过系统定位服务获取位置", canCancel = false)
        notifyState()
        promoteServiceForLocation()
        delay(150)
        val locationOutcome = locationProvider.locate(10_000)
        val fix: LocationFix? = (locationOutcome as? LocationOutcome.Success)?.fix
        val locationDetail = when (locationOutcome) {
            is LocationOutcome.Success -> if (locationOutcome.fix.historical) "使用最近一次有效位置" else "已获取事故位置"
            LocationOutcome.PermissionMissing -> "无定位权限"
            LocationOutcome.ProviderDisabled -> "系统定位已关闭"
            is LocationOutcome.Unavailable -> locationOutcome.reason
        }

        database.accidents().complete(
            eventId, EmergencyStage.SENDING.name, locationDetail,
            fix?.latitude, fix?.longitude, fix?.accuracyMeters, fix?.timeMs, fix?.provider
        )
        _state.value = _state.value.copy(stage = EmergencyStage.SENDING, detail = "$locationDetail，正在发送求救短信")
        notifyState()

        val contacts = database.contacts().active().map {
            EmergencyContact(it.id, it.name, it.phone, it.relationship, it.priority, it.enabled)
        }
        val report = smsPipeline.sendEmergency(
            eventId = eventId,
            contacts = contacts,
            requestedSubscriptionId = preferences.smsSubscriptionId.first(),
            riderName = preferences.riderName.first(),
            riderPhone = preferences.riderPhone.first(),
            location = fix,
            confidence = candidate.confidence,
            detectedAtMs = candidate.detectedAtMs,
            isSimulation = false
        )
        if (!report.launched) {
            val detail = report.errors.joinToString("；").ifBlank { "短信发送未启动" }
            finish(eventId, EmergencyStage.FAILED, "$detail。请立即手动拨打紧急电话。", fix)
            return
        }

        _state.value = _state.value.copy(detail = "已排队 ${report.queuedContacts} 位联系人、${report.queuedParts} 个短信分段，等待运营商回执")
        notifyState()
        withTimeoutOrNull(20_000) {
            database.smsParts().observeForEvent(eventId).first { rows ->
                val submitted = rows.filter { it.kind == "SENT" }
                submitted.isNotEmpty() && submitted.none { it.state == SmsPartState.QUEUED.name }
            }
        }
        database.smsParts().markQueuedTimedOut(eventId, "20 秒内未收到系统回执")
        val results = database.smsParts().forEvent(eventId).filter { it.kind == "SENT" }
        val accepted = results.count { it.state == SmsPartState.SUBMITTED.name }
        val total = results.size
        val finalStage = when {
            total == 0 || accepted == 0 -> EmergencyStage.FAILED
            accepted == total -> EmergencyStage.SENT
            else -> EmergencyStage.PARTIAL_SUCCESS
        }
        val detail = when (finalStage) {
            EmergencyStage.SENT -> "全部 $total 个短信分段已由运营商接受；送达状态将在历史记录中继续更新"
            EmergencyStage.PARTIAL_SUCCESS -> "$accepted/$total 个短信分段已提交，部分发送失败；请尝试电话求助"
            else -> "未收到任何短信提交成功回执，请立即手动拨打紧急电话"
        }
        finish(eventId, finalStage, detail, fix)
    }

    private suspend fun finish(eventId: Long, stage: EmergencyStage, detail: String, fix: LocationFix?) {
        database.accidents().complete(eventId, stage.name, detail, fix?.latitude, fix?.longitude, fix?.accuracyMeters, fix?.timeMs, fix?.provider)
        _state.value = _state.value.copy(stage = stage, detail = detail, canCancel = false)
        notifyState()
        releaseWakeLock()
    }

    private fun notifyState() {
        notifications?.notify(GuardNotifications.EMERGENCY_ID, GuardNotifications.emergency(context, _state.value))
    }

    private fun promoteServiceForLocation() {
        val intent = Intent(context, HelmetGuardService::class.java).setAction(HelmetGuardService.ACTION_LOCATION_MODE)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        releaseWakeLock()
        val manager = context.getSystemService(PowerManager::class.java) ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HelmetGuard:incident").apply {
            setReferenceCounted(false)
            acquire(45_000)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    private fun IncidentCandidate.toEntity(stage: EmergencyStage, deadline: Long) = AccidentEntity(
        eventId, detectedAtMs, System.currentTimeMillis(), source.name, stage.name, confidence,
        features.peakAccelerationG, features.peakAngularSpeedDps, features.orientationChangeDeg,
        features.freeFallDurationMs, deadline,
        null, null, null, null, null,
        if (isSimulation) "模拟事故" else "等待用户确认", isSimulation
    )

    private fun AccidentEntity.toCandidate() = IncidentCandidate(
        eventId, detectedAtMs, runCatching { IncidentSource.valueOf(source) }.getOrDefault(IncidentSource.PHONE_ALGORITHM),
        confidence,
        DetectionFeatures(peakAccelerationG, peakAngularSpeedDps, orientationChangeDeg, freeFallDurationMs),
        isSimulation
    )

    companion object {
        private val ACTIVE_STAGES = setOf(EmergencyStage.COUNTDOWN, EmergencyStage.LOCATING, EmergencyStage.SENDING)
    }
}
