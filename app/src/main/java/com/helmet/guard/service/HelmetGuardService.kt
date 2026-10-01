package com.helmet.guard.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.helmet.guard.appGraph
import com.helmet.guard.core.ble.HelmetButton
import com.helmet.guard.core.detector.MockScenario
import com.helmet.guard.core.detector.MockTelemetryEngine
import com.helmet.guard.domain.DetectionFeatures
import com.helmet.guard.domain.IncidentCandidate
import com.helmet.guard.domain.IncidentSource
import com.helmet.guard.domain.TelemetrySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HelmetGuardService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val graph by lazy { appGraph }
    private var simulationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        enterForeground(includeLocation = false)
        scope.launch {
            graph.ble.telemetry.collect { processSample(it, false) }
        }
        scope.launch {
            graph.ble.hardwareIncidents.collect { event ->
                val now = System.currentTimeMillis()
                graph.ble.acknowledge(event.eventId)
                graph.incidents.trigger(
                    IncidentCandidate(
                        now, now, IncidentSource.HELMET_HARDWARE, 0.94f,
                        DetectionFeatures(
                            peakAccelerationG = event.peakAccelerationG,
                            peakAngularSpeedDps = event.peakAngularSpeedDps,
                            postImpactGyroRms = 0f
                        )
                    ),
                    event.requestedCountdownSeconds.takeIf { it in 5..30 }
                )
                graph.detector.markCountdownActive()
            }
        }
        scope.launch {
            graph.ble.buttonEvents.collect { button ->
                when (button) {
                    HelmetButton.LONG -> graph.incidents.cancel()
                    HelmetButton.DOUBLE -> graph.incidents.sendNow()
                    else -> Unit
                }
            }
        }
        scope.launch {
            combine(graph.ble.state, graph.recorder.state) { connection, recording -> connection to recording.isRecording }
                .collect { (connection, recording) ->
                    val manager = getSystemService(android.app.NotificationManager::class.java)
                    manager?.notify(GuardNotifications.MONITORING_ID, GuardNotifications.monitoring(this@HelmetGuardService, connection, recording))
                }
        }
        scope.launch {
            while (isActive) {
                graph.preferences.heartbeat()
                delay(60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> scope.launch {
                graph.preferences.setMonitoringEnabled(true)
                graph.preferences.boundAddress.first()?.let { graph.ble.reconnect(it) }
            }
            ACTION_STOP -> scope.launch {
                graph.preferences.setMonitoringEnabled(false)
                graph.recorder.stop()
                graph.ble.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SAFE -> graph.incidents.cancel(intent.getLongExtra(EXTRA_EVENT_ID, -1))
            ACTION_SEND_NOW -> graph.incidents.sendNow(intent.getLongExtra(EXTRA_EVENT_ID, -1))
            ACTION_MANUAL_SOS -> graph.incidents.triggerManual(intent.getBooleanExtra(EXTRA_SIMULATION, false))
            ACTION_LOCATION_MODE -> enterForeground(includeLocation = true)
            ACTION_RUN_SIMULATION -> runSimulation(intent.getStringExtra(EXTRA_SCENARIO))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        simulationJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun processSample(sample: TelemetrySample, simulation: Boolean) {
        val result = graph.detector.process(sample, simulation)
        graph.runtime.update(sample, result.snapshot)
        graph.recorder.record(sample, result.snapshot)
        result.incident?.let {
            graph.detector.markCountdownActive()
            graph.incidents.trigger(it)
        }
    }

    private fun runSimulation(rawScenario: String?) {
        val scenario = runCatching { MockScenario.valueOf(rawScenario.orEmpty()) }.getOrDefault(MockScenario.NORMAL_RIDE)
        simulationJob?.cancel()
        simulationJob = scope.launch {
            graph.detector.reset()
            MockTelemetryEngine().play(scenario).collect { processSample(it, true) }
        }
    }

    private fun enterForeground(includeLocation: Boolean) {
        val notification = GuardNotifications.monitoring(this, graph.ble.state.value, graph.recorder.state.value.isRecording)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (includeLocation && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            runCatching { startForeground(GuardNotifications.MONITORING_ID, notification, type) }
                .onFailure { startForeground(GuardNotifications.MONITORING_ID, notification) }
        } else startForeground(GuardNotifications.MONITORING_ID, notification)
    }

    companion object {
        const val ACTION_START = "com.helmet.guard.action.START"
        const val ACTION_STOP = "com.helmet.guard.action.STOP"
        const val ACTION_SAFE = "com.helmet.guard.action.SAFE"
        const val ACTION_SEND_NOW = "com.helmet.guard.action.SEND_NOW"
        const val ACTION_MANUAL_SOS = "com.helmet.guard.action.MANUAL_SOS"
        const val ACTION_LOCATION_MODE = "com.helmet.guard.action.LOCATION_MODE"
        const val ACTION_RUN_SIMULATION = "com.helmet.guard.action.RUN_SIMULATION"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_SIMULATION = "simulation"
        const val EXTRA_SCENARIO = "scenario"
    }
}
