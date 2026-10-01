package com.helmet.guard.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.guardDataStore by preferencesDataStore("guard_preferences_v2")

class GuardPreferences(private val context: Context) {
    private object Keys {
        val monitoring = booleanPreferencesKey("monitoring_enabled")
        val boundAddress = stringPreferencesKey("bound_address")
        val boundName = stringPreferencesKey("bound_name")
        val countdown = intPreferencesKey("countdown_seconds")
        val simulation = booleanPreferencesKey("simulation_enabled")
        val smsSubscription = intPreferencesKey("sms_subscription_id")
        val riderName = stringPreferencesKey("rider_name")
        val riderPhone = stringPreferencesKey("rider_phone")
        val oemSetupConfirmed = booleanPreferencesKey("oem_setup_confirmed")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val lastHeartbeat = longPreferencesKey("last_service_heartbeat")
    }

    val monitoringEnabled: Flow<Boolean> = context.guardDataStore.data.map { it[Keys.monitoring] ?: false }
    val boundAddress: Flow<String?> = context.guardDataStore.data.map { it[Keys.boundAddress] }
    val boundName: Flow<String?> = context.guardDataStore.data.map { it[Keys.boundName] }
    val countdownSeconds: Flow<Int> = context.guardDataStore.data.map { (it[Keys.countdown] ?: 15).coerceIn(5, 30) }
    val simulationEnabled: Flow<Boolean> = context.guardDataStore.data.map { it[Keys.simulation] ?: false }
    val smsSubscriptionId: Flow<Int> = context.guardDataStore.data.map { it[Keys.smsSubscription] ?: -1 }
    val riderName: Flow<String> = context.guardDataStore.data.map { it[Keys.riderName] ?: "骑手" }
    val riderPhone: Flow<String> = context.guardDataStore.data.map { it[Keys.riderPhone] ?: "" }
    val oemSetupConfirmed: Flow<Boolean> = context.guardDataStore.data.map { it[Keys.oemSetupConfirmed] ?: false }
    val onboardingComplete: Flow<Boolean> = context.guardDataStore.data.map { it[Keys.onboardingComplete] ?: false }
    val lastServiceHeartbeat: Flow<Long> = context.guardDataStore.data.map { it[Keys.lastHeartbeat] ?: 0L }

    suspend fun setMonitoringEnabled(value: Boolean) = context.guardDataStore.edit { it[Keys.monitoring] = value }
    suspend fun setCountdownSeconds(value: Int) = context.guardDataStore.edit { it[Keys.countdown] = value.coerceIn(5, 30) }
    suspend fun setSimulationEnabled(value: Boolean) = context.guardDataStore.edit { it[Keys.simulation] = value }
    suspend fun setSmsSubscriptionId(value: Int) = context.guardDataStore.edit { it[Keys.smsSubscription] = value }
    suspend fun setRider(name: String, phone: String) = context.guardDataStore.edit {
        it[Keys.riderName] = name.trim().ifBlank { "骑手" }
        it[Keys.riderPhone] = phone.trim()
    }
    suspend fun setOemSetupConfirmed(value: Boolean) = context.guardDataStore.edit { it[Keys.oemSetupConfirmed] = value }
    suspend fun setOnboardingComplete(value: Boolean) = context.guardDataStore.edit { it[Keys.onboardingComplete] = value }
    suspend fun heartbeat(now: Long = System.currentTimeMillis()) = context.guardDataStore.edit { it[Keys.lastHeartbeat] = now }

    suspend fun bindDevice(address: String, name: String) = context.guardDataStore.edit {
        it[Keys.boundAddress] = address
        it[Keys.boundName] = name
    }

    suspend fun clearDevice() = context.guardDataStore.edit {
        it.remove(Keys.boundAddress)
        it.remove(Keys.boundName)
    }
}
