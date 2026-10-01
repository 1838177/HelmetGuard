package com.helmet.guard.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val KEY_DETECTION_ENABLED = booleanPreferencesKey("key_detection_enabled")
    private val KEY_COUNTDOWN_SEC = intPreferencesKey("key_countdown_sec")
    private val KEY_MOCK_MODE = booleanPreferencesKey("key_mock_mode")
    private val KEY_LAST_DEVICE_ADDR = stringPreferencesKey("key_last_device_addr")
    private val KEY_LAST_DEVICE_NAME = stringPreferencesKey("key_last_device_name")
    private val KEY_AUTO_RECONNECT = booleanPreferencesKey("key_auto_reconnect")
    private val KEY_THEME_MODE = intPreferencesKey("key_theme_mode") // 0: System, 1: Light, 2: Dark

    val isDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DETECTION_ENABLED] ?: true }
    val countdownDurationSec: Flow<Int> = context.dataStore.data.map { it[KEY_COUNTDOWN_SEC] ?: 15 }
    val isMockMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_MOCK_MODE] ?: false }
    val lastDeviceAddress: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_DEVICE_ADDR] }
    val lastDeviceName: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_DEVICE_NAME] }
    val isAutoReconnect: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_RECONNECT] ?: true }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 2 } // 默认 Dark 仪表盘

    suspend fun setDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DETECTION_ENABLED] = enabled }
    }

    suspend fun setCountdownDuration(seconds: Int) {
        context.dataStore.edit { it[KEY_COUNTDOWN_SEC] = seconds }
    }

    suspend fun setMockMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MOCK_MODE] = enabled }
    }

    suspend fun saveBoundDevice(address: String, name: String) {
        context.dataStore.edit {
            it[KEY_LAST_DEVICE_ADDR] = address
            it[KEY_LAST_DEVICE_NAME] = name
        }
    }

    suspend fun clearBoundDevice() {
        context.dataStore.edit {
            it.remove(KEY_LAST_DEVICE_ADDR)
            it.remove(KEY_LAST_DEVICE_NAME)
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }
}
