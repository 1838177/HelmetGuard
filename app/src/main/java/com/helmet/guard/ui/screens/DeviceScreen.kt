package com.helmet.guard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helmet.guard.AppGraph
import com.helmet.guard.domain.ConnectionPhase
import com.helmet.guard.ui.components.InfoCard
import com.helmet.guard.ui.components.SectionTitle
import com.helmet.guard.ui.components.StatusPill
import com.helmet.guard.ui.theme.GuardBlue
import com.helmet.guard.ui.theme.GuardGreen
import com.helmet.guard.ui.theme.GuardOrange
import kotlinx.coroutines.launch

@Composable
fun DeviceScreen(graph: AppGraph, requestPermissions: () -> Unit, startMonitoring: () -> Unit, associateCompanion: () -> Unit) {
    val connection by graph.ble.state.collectAsState()
    val devices by graph.ble.devices.collectAsState()
    val telemetry by graph.runtime.telemetry.collectAsState()
    val loss by graph.ble.packetLoss.collectAsState()
    val scope = rememberCoroutineScope()
    val busy = connection.phase in setOf(ConnectionPhase.CONNECTING, ConnectionPhase.NEGOTIATING, ConnectionPhase.DISCOVERING, ConnectionPhase.SUBSCRIBING)

    LazyColumn(
        Modifier.statusBarsPadding().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            SectionTitle("我的头盔", "ESP32-S3 · MPU6050") {
                StatusPill(if (connection.isReady) "在线" else connection.phase.name, if (connection.isReady) GuardGreen else GuardOrange)
            }
        }
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bluetooth, null, tint = GuardBlue, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(connection.device?.name ?: "尚未绑定头盔", style = MaterialTheme.typography.titleMedium)
                        Text(connection.device?.address ?: "扫描附近的 HelmetGuard 设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    telemetry?.let { StatusPill("${it.batteryPercent}%", GuardGreen) }
                }
                Text(connection.message, style = MaterialTheme.typography.bodyMedium)
                if (connection.isReady) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { graph.ble.findHelmet() }, modifier = Modifier.weight(1f)) { Text("响铃查找") }
                        OutlinedButton(onClick = { graph.ble.calibrate() }, modifier = Modifier.weight(1f)) { Text("校准 IMU") }
                    }
                    OutlinedButton(onClick = { graph.ble.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("断开连接") }
                } else {
                    Button(
                        onClick = {
                            requestPermissions()
                            startMonitoring()
                            graph.ble.startScan()
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy
                    ) {
                        Icon(if (connection.phase == ConnectionPhase.SCANNING) Icons.Outlined.Radar else Icons.Outlined.Refresh, null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (connection.phase == ConnectionPhase.SCANNING) "正在扫描…" else "扫描附近头盔")
                    }
                    OutlinedButton(onClick = associateCompanion, modifier = Modifier.fillMaxWidth()) {
                        Text("系统配套设备关联（增强后台连接）")
                    }
                }
            }
        }
        if (devices.isNotEmpty() && !connection.isReady) {
            item { SectionTitle("发现的设备", "点击设备完成绑定并连接") }
            items(devices, key = { it.address }) { device ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch { graph.preferences.bindDevice(device.address, device.name) }
                        graph.ble.connect(device.address)
                    }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bluetooth, null, tint = GuardBlue)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text(device.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${device.rssi ?: 0} dBm", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { SectionTitle("设备自检", "READY 才代表服务、特征和通知均已就绪") }
        item {
            InfoCard {
                SelfCheckRow("蓝牙链路", connection.isReady, if (connection.isReady) "通知订阅完成" else connection.message, Icons.Outlined.Bluetooth)
                SelfCheckRow("IMU 状态", telemetry?.imuHealthy == true, if (telemetry?.imuHealthy == true) "MPU6050 正常" else "等待健康帧", Icons.Outlined.GraphicEq)
                SelfCheckRow("零偏校准", telemetry?.calibrated == true, if (telemetry?.calibrated == true) "校准有效" else "需要校准", Icons.Outlined.Tune)
                SelfCheckRow("实时数据", connection.lastPacketElapsedMs > 0, "累计疑似丢帧 $loss", Icons.Outlined.NotificationsActive)
                SelfCheckRow("定位能力", graph.location.hasForegroundPermission() && graph.location.isLocationEnabled(), "事故发生时使用系统原生定位", Icons.Outlined.MyLocation)
                SelfCheckRow("头盔电量", (telemetry?.batteryPercent ?: 0) >= 20, telemetry?.let { "当前 ${it.batteryPercent}%" } ?: "等待数据", Icons.Outlined.Battery5Bar)
            }
        }
        item {
            Text(
                "提示：蓝牙显示“已连接”不等于可用。HelmetGuard 只有完成 MTU 协商、服务发现和两个通知特征订阅后才进入 READY。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SelfCheckRow(title: String, passed: Boolean, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (passed) GuardGreen else GuardOrange)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (passed) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (passed) GuardGreen else GuardOrange)
    }
}
