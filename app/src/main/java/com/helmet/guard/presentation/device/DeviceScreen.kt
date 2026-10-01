package com.helmet.guard.presentation.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helmet.guard.domain.model.ConnectionStatus
import com.helmet.guard.domain.model.HelmetDevice
import com.helmet.guard.presentation.components.CollapsibleSection
import com.helmet.guard.presentation.components.DashboardCard
import com.helmet.guard.presentation.components.HelmetDangerButton
import com.helmet.guard.presentation.components.HelmetPrimaryButton
import com.helmet.guard.presentation.components.HelmetSecondaryButton
import com.helmet.guard.presentation.components.MetricTile
import com.helmet.guard.presentation.components.StatusDot
import com.helmet.guard.presentation.theme.StatusAlert
import com.helmet.guard.presentation.theme.StatusOffline
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.presentation.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel,
    onNavigateToSelfCheck: () -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val discoveredList by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var showForgetConfirmDialog by remember { mutableStateOf(false) }
    var isAdvancedInfoExpanded by remember { mutableStateOf(false) }

    val isConnected = connectionStatus == ConnectionStatus.CONNECTED
    val isBound = connectionStatus != ConnectionStatus.UNBOUND

    val statusColor = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> StatusSafe
        ConnectionStatus.CONNECTING -> StatusWarning
        ConnectionStatus.UNSTABLE -> StatusWarning
        ConnectionStatus.DISCONNECTED -> StatusAlert
        ConnectionStatus.UNBOUND -> StatusOffline
    }

    val statusText = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> "已连接"
        ConnectionStatus.CONNECTING -> "正在连接..."
        ConnectionStatus.UNSTABLE -> "连接不稳定"
        ConnectionStatus.DISCONNECTED -> "未连接"
        ConnectionStatus.UNBOUND -> "未绑定"
    }

    if (showForgetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showForgetConfirmDialog = false },
            title = {
                Text(
                    text = "解除绑定与忘记设备",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatusAlert
                )
            },
            text = {
                Text(
                    text = "确认清除当前绑定的头盔设备信息？解绑后若需使用需重新搜索并配对。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForgetConfirmDialog = false
                        viewModel.forgetDevice()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAlert),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "确认解除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgetConfirmDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                title = {
                    Text(
                        text = "设备与连接管理",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 当前主设备状态卡片 (单一明确主状态源，杜绝未绑定与已连接冲突)
            item {
                DashboardCard(
                    borderColor = if (isConnected) StatusSafe.copy(alpha = 0.5f) else null
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                StatusDot(
                                    color = statusColor,
                                    isPulsing = connectionStatus == ConnectionStatus.CONNECTING,
                                    sizeDp = 10
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    val deviceDisplayName = when {
                                        connectionStatus == ConnectionStatus.UNBOUND -> "尚未绑定头盔"
                                        deviceInfo.name.isNotBlank() && deviceInfo.name != "未绑定" -> deviceInfo.name
                                        else -> "智能头盔"
                                    }
                                    Text(
                                        text = deviceDisplayName,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isConnected) "GATT 通信畅通 · 硬件在线" else if (isBound) "设备处于离线状态" else "请在下方扫描列表查找头盔配对",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isConnected) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricTile(label = "电池电量", value = "${deviceInfo.batteryPercent ?: "--"}", unit = if (deviceInfo.batteryPercent != null) "%" else "")
                                MetricTile(label = "信号强度", value = "${deviceInfo.rssi ?: "--"}", unit = if (deviceInfo.rssi != null) "dBm" else "")
                                MetricTile(label = "固件版本", value = deviceInfo.firmwareVersion ?: "--", unit = "")
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HelmetSecondaryButton(
                                    text = "断开连接",
                                    onClick = { viewModel.disconnect() },
                                    modifier = Modifier.weight(1f)
                                )

                                HelmetSecondaryButton(
                                    text = "设备自检",
                                    onClick = onNavigateToSelfCheck,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HelmetDangerButton(
                                text = "解除绑定 (忘记设备)",
                                onClick = { showForgetConfirmDialog = true },
                                isOutlined = true
                            )
                        } else if (isBound) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HelmetPrimaryButton(
                                    text = "重新连接",
                                    onClick = { viewModel.reconnect() },
                                    modifier = Modifier.weight(1f)
                                )

                                HelmetDangerButton(
                                    text = "解除绑定",
                                    onClick = { showForgetConfirmDialog = true },
                                    modifier = Modifier.weight(1f),
                                    isOutlined = true
                                )
                            }
                        }
                    }
                }
            }

            // 2. 高级参数折叠容器
            if (isBound) {
                item {
                    DashboardCard {
                        CollapsibleSection(
                            title = "硬件高级参数 (MAC / 协议规范)",
                            isExpanded = isAdvancedInfoExpanded,
                            onToggle = { isAdvancedInfoExpanded = !isAdvancedInfoExpanded }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "MAC 地址", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = deviceInfo.address.ifBlank { "AA:BB:CC:DD:EE:FF" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "BLE 通信协议", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = deviceInfo.protocolVersion?.ifBlank { "v1.0 (26B定点)" } ?: "--", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "GATT 服务通道", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "0000FFF0-0000-1000", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // 3. 附近头盔设备搜索
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "附近可用头盔",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedButton(
                        onClick = {
                            if (isScanning) viewModel.stopScan() else viewModel.startScan()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = StatusSafe,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "停止扫描", fontSize = 12.sp)
                        } else {
                            Text(text = "搜索设备", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 设备搜索结果列表
            if (discoveredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isScanning) "正在扫描附近 BLE 设备广播..." else "暂未发现设备，请确保头盔已开机并处于广播配对状态",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(discoveredList) { dev ->
                    DiscoveredDeviceRow(
                        device = dev,
                        onConnect = { viewModel.connectDevice(dev) }
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoveredDeviceRow(
    device: HelmetDevice,
    onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable { onConnect() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${device.address}  |  RSSI: ${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onConnect,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatusSafe)
        ) {
            Text(text = "连接", fontSize = 12.sp, color = Color(0xFF0D0F11), fontWeight = FontWeight.SemiBold)
        }
    }
}
