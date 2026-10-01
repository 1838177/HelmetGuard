package com.helmet.guard.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Home : Screen("home", "首页", Icons.Default.Home)
    object Sensor : Screen("sensor", "传感器", Icons.Default.Sensors)
    object Device : Screen("device", "设备", Icons.Default.Bluetooth)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)

    // 二级页面
    object EmergencyContacts : Screen("emergency_contacts", "紧急联系人")
    object History : Screen("history", "历史记录")
    object SelfCheck : Screen("self_check", "设备自检")
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Sensor,
    Screen.Device,
    Screen.Settings
)
