package com.helmet.guard.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helmet.guard.data.mock.MockScenario
import com.helmet.guard.domain.model.AccidentLifecycleState
import com.helmet.guard.domain.model.ConnectionStatus
import com.helmet.guard.domain.model.MonitoringStatus
import com.helmet.guard.service.SosAccessibilityService
import com.helmet.guard.domain.model.DeviceLog
import com.helmet.guard.presentation.components.DashboardCard
import com.helmet.guard.presentation.components.HelmetDangerButton
import com.helmet.guard.presentation.components.HelmetMockBanner
import com.helmet.guard.presentation.components.HelmetSecondaryButton
import com.helmet.guard.presentation.components.IndustrialToggleSwitch
import com.helmet.guard.presentation.components.StatusDot
import com.helmet.guard.presentation.theme.StatusAlert
import com.helmet.guard.presentation.theme.StatusOffline
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.presentation.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSensor: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDevice: () -> Unit,
    onToggleDetection: (Boolean) -> Unit = { viewModel.toggleDetection(it) }
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val accidentState by viewModel.accidentState.collectAsState()
    val isDetectionEnabled by viewModel.isDetectionEnabled.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()
    val isMockMode by viewModel.isMockMode.collectAsState()
    val monitoringStatus by viewModel.monitoringStatus.collectAsState()
    val mockScenario by viewModel.mockScenario.collectAsState()

    var showSosConfirmDialog by remember { mutableStateOf(false) }

    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

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
        ConnectionStatus.UNSTABLE -> "信号不稳定"
        ConnectionStatus.DISCONNECTED -> "未连接"
        ConnectionStatus.UNBOUND -> "未绑定头盔"
    }

    if (showSosConfirmDialog) {
        val context = LocalContext.current
        val isAccessibilityEnabled = remember(showSosConfirmDialog) {
            SosAccessibilityService.isEnabled(context)
        }

        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            title = {
                Text(
                    text = "确认触发紧急求救 SOS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatusAlert
                )
            },
            text = {
                Column {
                    Text(
                        text = "系统将立刻定位并向所有预设紧急联系人发出报警求救短信。是否确认发送？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!isAccessibilityEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "提示: 尚未开启无障碍免拦截辅助，若系统弹出发短信拦截确认框请点击发送；可在「设置」中开启辅助实现全自动秒级放行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSosConfirmDialog = false
                        viewModel.triggerManualSos()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAlert),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "确认发送", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSosConfirmDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部品牌标题
        item {
            Column(modifier = Modifier.padding(bottom = 2.dp)) {
                Text(
                    text = "Helmet Guard",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "智能安全头盔终端",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 演示模式常驻横幅
        if (isMockMode) {
            item {
                val scenarioLabel = when (mockScenario) {
                    MockScenario.NORMAL_RIDING -> "正常骑行"
                    MockScenario.EMERGENCY_BRAKE -> "模拟急刹"
                    MockScenario.SPEED_BUMP -> "减速带颠簸"
                    MockScenario.CRASH_AND_FALL -> "模拟摔倒事故"
                    MockScenario.LOW_BATTERY -> "模拟低电量"
                    MockScenario.DISCONNECT -> "模拟蓝牙断开"
                    MockScenario.STANDBY_PAUSED -> "待机静止"
                }
                val detailLabel = if (accidentState == AccidentLifecycleState.COUNTDOWN) {
                    "等待倒计时"
                } else {
                    "仿真数据运行中"
                }
                HelmetMockBanner(
                    title = "演示模式 · 不会发送真实短信",
                    statusDetail = "$scenarioLabel · $detailLabel"
                )
            }
        }

        // 1. 头盔连接摘要 (紧凑单行，点击进入设备页)
        item {
            DashboardCard(
                modifier = Modifier.clickable { onNavigateToDevice() }
            ) {
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
                            val cardTitle = when {
                                connectionStatus == ConnectionStatus.UNBOUND -> "尚未绑定头盔"
                                isConnected -> if (deviceInfo.name.isNotBlank() && deviceInfo.name != "未绑定") deviceInfo.name else "智能头盔"
                                else -> if (deviceInfo.name.isNotBlank() && deviceInfo.name != "未绑定") deviceInfo.name else "智能头盔"
                            }
                            Text(
                                text = cardTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val battStr = deviceInfo.batteryPercent?.let { "电量 $it%" } ?: "电量未知"
                            val rssiStr = deviceInfo.rssi?.let { "信号良好 ($it dBm)" } ?: "信号未知"
                            Text(
                                text = if (isConnected) "$battStr · $rssiStr" else "点击进入设备管理连接",
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
            }
        }

        // 2. 当前核心安全状态卡片 (首屏视觉重点，最小高140dp，状态变化平滑无跳跃)
        item {
            val isAlertActive = accidentState == AccidentLifecycleState.COUNTDOWN ||
                    accidentState == AccidentLifecycleState.LOCATING ||
                    accidentState == AccidentLifecycleState.SENDING ||
                    accidentState == AccidentLifecycleState.SENT ||
                    accidentState == AccidentLifecycleState.SEND_FAILED ||
                    accidentState == AccidentLifecycleState.SUSPECTED

            val safetyBorderColor = when {
                isAlertActive -> when (accidentState) {
                    AccidentLifecycleState.SUSPECTED -> StatusWarning
                    AccidentLifecycleState.COUNTDOWN,
                    AccidentLifecycleState.LOCATING,
                    AccidentLifecycleState.SENDING -> StatusAlert
                    AccidentLifecycleState.SENT -> StatusSafe
                    AccidentLifecycleState.SEND_FAILED -> StatusAlert
                    else -> MaterialTheme.colorScheme.outline
                }
                monitoringStatus == MonitoringStatus.UNBOUND -> MaterialTheme.colorScheme.outline
                monitoringStatus == MonitoringStatus.DISCONNECTED -> StatusAlert
                monitoringStatus == MonitoringStatus.DATA_STALE -> StatusWarning
                monitoringStatus == MonitoringStatus.SIMULATION -> StatusWarning.copy(alpha = 0.6f)
                else -> StatusSafe.copy(alpha = 0.6f)
            }

            val safetyBgColor = when {
                isAlertActive -> when (accidentState) {
                    AccidentLifecycleState.SUSPECTED -> StatusWarning.copy(alpha = 0.08f)
                    AccidentLifecycleState.COUNTDOWN,
                    AccidentLifecycleState.LOCATING,
                    AccidentLifecycleState.SENDING -> StatusAlert.copy(alpha = 0.12f)
                    AccidentLifecycleState.SENT -> StatusSafe.copy(alpha = 0.10f)
                    AccidentLifecycleState.SEND_FAILED -> StatusAlert.copy(alpha = 0.10f)
                    else -> MaterialTheme.colorScheme.surface
                }
                monitoringStatus == MonitoringStatus.DISCONNECTED -> StatusAlert.copy(alpha = 0.08f)
                monitoringStatus == MonitoringStatus.DATA_STALE -> StatusWarning.copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surface
            }

            val safetyTitle = when {
                isAlertActive -> when (accidentState) {
                    AccidentLifecycleState.SUSPECTED -> "检测到异常运动"
                    AccidentLifecycleState.COUNTDOWN -> "即将发送求救报警"
                    AccidentLifecycleState.LOCATING -> "正在捕获精准定位"
                    AccidentLifecycleState.SENDING -> "正在提交求救信息"
                    AccidentLifecycleState.SENT -> "报警短信已向运营商提交"
                    AccidentLifecycleState.SEND_FAILED -> "报警发送失败"
                    else -> "系统就绪待命"
                }
                monitoringStatus == MonitoringStatus.UNBOUND -> "尚未开始监测"
                monitoringStatus == MonitoringStatus.DISCONNECTED -> "头盔连接已断开"
                monitoringStatus == MonitoringStatus.DATA_STALE -> "遥测延迟中断"
                monitoringStatus == MonitoringStatus.SIMULATION -> "演示仿真中"
                else -> "正在监测 · 暂未检测到异常"
            }

            val safetyDesc = when {
                isAlertActive -> when (accidentState) {
                    AccidentLifecycleState.SUSPECTED -> "检测到瞬时冲击，正在进行撞击后静止确认..."
                    AccidentLifecycleState.COUNTDOWN -> "防误触倒计时中，长按屏幕底部可随时取消"
                    AccidentLifecycleState.LOCATING -> "获取卫星及基站高精度经纬度坐标"
                    AccidentLifecycleState.SENDING -> "通过运营商短信通道向紧急联系人推送警报"
                    AccidentLifecycleState.SENT -> "已将准确经纬度与事故详情推达紧急联系人"
                    AccidentLifecycleState.SEND_FAILED -> "短信通道受阻，请确保手机有信号并开启权限"
                    else -> "连接头盔后将启动全自动防护"
                }
                monitoringStatus == MonitoringStatus.UNBOUND -> "尚未绑定头盔设备，实时碰撞防护未启动"
                monitoringStatus == MonitoringStatus.DISCONNECTED -> "蓝牙通信已中断，当前无法提供实时跌倒与撞击防护"
                monitoringStatus == MonitoringStatus.DATA_STALE -> "超过 3 秒未接收到有效传感器帧，正在尝试恢复..."
                monitoringStatus == MonitoringStatus.SIMULATION -> "当前处于本地虚拟传感器数据仿真测试模式"
                else -> "六轴姿态与加速度处于正常基线范围"
            }

            DashboardCard(
                borderColor = safetyBorderColor,
                backgroundColor = safetyBgColor
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "安全监控状态",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AnimatedContent(
                        targetState = safetyTitle,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "safety_title"
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 26.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    AnimatedContent(
                        targetState = safetyDesc,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "safety_desc"
                    ) { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (accidentState == AccidentLifecycleState.SENT || accidentState == AccidentLifecycleState.SEND_FAILED) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.resolveAlertAndResumeMonitoring() },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (accidentState == AccidentLifecycleState.SENT) StatusSafe else StatusAlert
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "确认已安全 · 恢复监测",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. 事故自动报警开关 (横向紧凑设置行)
        item {
            DashboardCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "事故自动报警",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isDetectionEnabled) "实时监测撞击、翻滚与持续静止" else "系统已暂停自动求救研判",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IndustrialToggleSwitch(
                        checked = isDetectionEnabled,
                        onCheckedChange = onToggleDetection
                    )
                }
            }
        }

        // 4. 两个核心快捷操作 (并排分级：普通操作边框，高危操作红色并防误触)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HelmetSecondaryButton(
                    text = "查看传感器",
                    onClick = onNavigateToSensor,
                    modifier = Modifier.weight(1f)
                )

                HelmetDangerButton(
                    text = "主动发送 SOS",
                    onClick = { showSosConfirmDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5. 最近状态日志 (精简展示 2 条，点击“查看全部”直达历史页面)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最近状态记录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "查看全部",
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusSafe,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onNavigateToHistory() }
                )
            }
        }

        val displayLogs = recentLogs.take(2)
        if (displayLogs.isEmpty()) {
            item(key = "empty_recent_logs") {
                Text(
                    text = "暂无状态记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        } else {
            items(displayLogs, key = { it.id }) { log ->
                LogItemRow(log)
            }
        }
    }
}

@Composable
fun LogItemRow(log: DeviceLog) {
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestampMs))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = log.detail,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = log.result,
                style = MaterialTheme.typography.labelSmall,
                color = if (log.result == "成功") StatusSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
