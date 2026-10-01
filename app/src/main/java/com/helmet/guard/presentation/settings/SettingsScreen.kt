package com.helmet.guard.presentation.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.helmet.guard.data.mock.MockScenario
import com.helmet.guard.presentation.components.DashboardCard
import com.helmet.guard.service.SosAccessibilityService
import com.helmet.guard.presentation.components.HelmetMockBanner
import com.helmet.guard.presentation.components.IndustrialToggleSwitch
import com.helmet.guard.presentation.components.StatusDot
import com.helmet.guard.presentation.theme.StatusAlert
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.presentation.theme.StatusWarning

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onToggleDetection: (Boolean) -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSelfCheck: () -> Unit
) {
    val context = LocalContext.current
    val isDetectionEnabled by viewModel.isDetectionEnabled.collectAsState()
    val countdownDuration by viewModel.countdownDuration.collectAsState()
    val isMockMode by viewModel.isMockMode.collectAsState()
    val mockScenario by viewModel.mockScenario.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()

    var lastActionFeedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "系统与偏好设置",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 1. 安全配置分组
        item {
            Text(
                text = "安全防护配置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 事故检测开关
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
                                text = "六轴加速度与角速度复合研判",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IndustrialToggleSwitch(
                            checked = isDetectionEnabled,
                            onCheckedChange = onToggleDetection
                        )
                    }

                    // 倒计时时长选择
                    Column {
                        Text(
                            text = "防误触倒计时时长: ${countdownDuration} 秒",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(5, 10, 15, 30).forEach { sec ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel.setCountdown(sec) }
                                ) {
                                    RadioButton(
                                        selected = countdownDuration == sec,
                                        onClick = { viewModel.setCountdown(sec) },
                                        colors = RadioButtonDefaults.colors(selectedColor = StatusSafe)
                                    )
                                    Text(
                                        text = "${sec}s",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 紧急联系人与短信模板管理入口 (在二级菜单中配置启闭与联系人)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigateToEmergency() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "紧急联系人与短信模板",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "管理求救联系人、选择启用状态及短信内容",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "管理 >",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 2. 界面显示与主题
        item {
            Text(
                text = "显示与主题",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "界面主题模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themeOptions = listOf(
                            Triple(2, "深色仪表盘", "默认"),
                            Triple(1, "浅色防眩", "高对比"),
                            Triple(0, "跟随系统", "自适应")
                        )
                        themeOptions.forEach { (mode, title, subtitle) ->
                            val isSelected = currentThemeMode == mode
                            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setTheme(mode) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 设备联动与诊断
        item {
            Text(
                text = "设备状态与日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigateToSelfCheck() }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "系统与传感器 10 项完整自检", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(text = "诊断蓝牙、IMU、电池、GPS 及短信通道", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(text = "开始自检 >", color = StatusSafe, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigateToHistory() }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "事故历史与设备运行日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                            Text(text = "查阅本地 Room 数据库留存的报警证据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(text = "查看历史 >", color = StatusSafe, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 4. 后台运行保障 (权限与保活精炼看板)
        item {
            Text(
                text = "后台运行保障",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasBtScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else true
            val hasPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
            val hasAccessibility = SosAccessibilityService.isEnabled(context)

            DashboardCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "系统权限状态",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "确保手机锁屏后仍能持续接收头盔数据与自动分发短信",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermissionStatusRow(name = "发送短信权限 (自动求救必需)", isGranted = hasSms)
                        PermissionStatusRow(name = "高精度定位权限 (事故坐标必需)", isGranted = hasLocation)
                        PermissionStatusRow(name = "蓝牙与附近设备 (头盔连接必需)", isGranted = hasBtScan)
                        PermissionStatusRow(name = "前台通知权限 (保活服务必需)", isGranted = hasPostNotifications)
                        PermissionStatusRow(name = "无障碍免拦截辅助 (自动确认10s短信弹窗)", isGranted = hasAccessibility)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "前往权限设置",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "配置电池白名单",
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (hasAccessibility) MaterialTheme.colorScheme.primary else StatusAlert
                        )
                    ) {
                        Text(
                            text = if (hasAccessibility) "无障碍免拦截辅助已开启 (点击管理)" else "前往开启无障碍免拦截辅助 (防10s弹窗阻断)",
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 5. 开发测试：独立 Mock 仿真引擎分组
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "开发测试",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusWarning.copy(alpha = 0.15f))
                        .border(1.dp, StatusWarning.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "MOCK MODE", fontSize = 10.sp, color = StatusWarning, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            DashboardCard(
                borderColor = if (isMockMode) StatusWarning.copy(alpha = 0.5f) else null
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mock 传感器与硬件仿真模式",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "无需物理开发板，生成完整六轴高频数据流",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IndustrialToggleSwitch(
                            checked = isMockMode,
                            onCheckedChange = { viewModel.toggleMockMode(it) }
                        )
                    }

                    if (isMockMode) {
                        val scenarioLabel = when (mockScenario) {
                            MockScenario.NORMAL_RIDING -> "正常骑行 (合加速度 ~1.0g)"
                            MockScenario.EMERGENCY_BRAKE -> "急刹车 (1.8g 纵向减速度)"
                            MockScenario.SPEED_BUMP -> "减速带颠簸 (1.6g 冲击)"
                            MockScenario.CRASH_AND_FALL -> "摔倒事故 (4.8g + 65°侧翻)"
                            MockScenario.LOW_BATTERY -> "低电量预警 (8%)"
                            MockScenario.DISCONNECT -> "蓝牙意外断连"
                            MockScenario.STANDBY_PAUSED -> "待机静止 (遥测暂停，零噪声)"
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusWarning.copy(alpha = 0.1f))
                                .border(1.dp, StatusWarning.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "当前仿真状态: $scenarioLabel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StatusWarning,
                                    fontWeight = FontWeight.Medium
                                )
                                if (lastActionFeedback != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = lastActionFeedback!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StatusWarning.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "场景事件注入 (点击即时生效):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.mockEmergencyBrake()
                                    lastActionFeedback = "已注入：急刹车场景 (1.8g)"
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "模拟急刹", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.mockLowBattery()
                                    lastActionFeedback = "已注入：低电量场景 (8%)"
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "模拟低电量", fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.mockBleDisconnect()
                                    lastActionFeedback = "已注入：蓝牙意外断开"
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "模拟断开", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.mockCrashAndFall()
                                    lastActionFeedback = "已注入：摔倒事故 (进入倒计时)"
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAlert)
                            ) {
                                Text(text = "模拟摔倒事故", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.stopMockTelemetry()
                                lastActionFeedback = "已结束测试并复位待机"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "结束测试并复位待机", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(name: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (isGranted) "已允许" else "需要设置",
            style = MaterialTheme.typography.labelSmall,
            color = if (isGranted) StatusSafe else StatusWarning,
            fontWeight = FontWeight.Bold
        )
    }
}
