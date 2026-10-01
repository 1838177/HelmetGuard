package com.helmet.guard

import android.content.Context
import com.helmet.guard.core.ble.CompanionPairing
import com.helmet.guard.core.ble.HelmetBleClient
import com.helmet.guard.core.compatibility.DeviceReliabilityCenter
import com.helmet.guard.core.detector.AdvancedAccidentDetector
import com.helmet.guard.core.location.NativeLocationProvider
import com.helmet.guard.core.sms.SmsPipeline
import com.helmet.guard.data.db.GuardDatabase
import com.helmet.guard.data.prefs.GuardPreferences
import com.helmet.guard.data.recording.TelemetryRecorder
import com.helmet.guard.domain.DetectorSnapshot
import com.helmet.guard.domain.TelemetrySample
import com.helmet.guard.service.IncidentCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RuntimeStore {
    private val _telemetry = MutableStateFlow<TelemetrySample?>(null)
    val telemetry: StateFlow<TelemetrySample?> = _telemetry.asStateFlow()
    private val _detector = MutableStateFlow(DetectorSnapshot())
    val detector: StateFlow<DetectorSnapshot> = _detector.asStateFlow()

    fun update(sample: TelemetrySample, detector: DetectorSnapshot) {
        _telemetry.value = sample
        _detector.value = detector
    }

    fun updateDetector(detector: DetectorSnapshot) { _detector.value = detector }
}

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    val database = GuardDatabase.create(appContext)
    val preferences = GuardPreferences(appContext)
    val ble = HelmetBleClient(appContext)
    val companionPairing = CompanionPairing(appContext)
    val detector = AdvancedAccidentDetector()
    val location = NativeLocationProvider(appContext)
    val sms = SmsPipeline(appContext, database)
    val recorder = TelemetryRecorder(appContext, database.telemetry())
    val reliability = DeviceReliabilityCenter(appContext)
    val runtime = RuntimeStore()
    val incidents = IncidentCoordinator(appContext, database, preferences, location, sms)
}
