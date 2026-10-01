package com.helmet.guard.presentation.sensor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helmet.guard.presentation.components.AttitudeHorizonIndicator
import com.helmet.guard.presentation.components.CollapsibleSection
import com.helmet.guard.presentation.components.DashboardCard
import com.helmet.guard.presentation.components.IndustrialToggleSwitch
import com.helmet.guard.presentation.components.MetricTile
import com.helmet.guard.presentation.components.RealtimeLineChart
import com.helmet.guard.presentation.components.StatusDot
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.presentation.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(
    viewModel: SensorViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val data by viewModel.sensorData.collectAsState()
    val isRealtime by viewModel.isRealtimeRefresh.collectAsState()
    val isShowCharts by viewModel.isShowCharts.collectAsState()
    val accHistory by viewModel.accHistory.collectAsState()
    val gyroHistory by viewModel.gyroHistory.collectAsState()

    var isAxisDetailsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                title = {
                    Text(
                        text = "六轴传感器遥测",
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
            // 1. 顶部状态摘要与零偏校准
            item {
                DashboardCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            StatusDot(color = if (data.isImuNormal) StatusSafe else StatusWarning, sizeDp = 8)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (data.isImuNormal) "IMU 传感器运行正常" else "IMU 传感器状态异常",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "采样率: 20Hz · " + if (data.isCalibrated) "已校准" else "未校准",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.triggerCalibration() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(text = "零偏校准", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 2. 第一层：核心指标置顶 (双并排大卡片，快速阅读)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardCard(modifier = Modifier.weight(1f)) {
                        Column {
                            Text(
                                text = "合加速度",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format("%.2f", data.totalAcc),
                                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 28.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (data.totalAcc > 2.5f) StatusWarning else StatusSafe
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "g",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }

                    DashboardCard(modifier = Modifier.weight(1f)) {
                        Column {
                            Text(
                                text = "合角速度",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format("%.1f", data.totalGyro),
                                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 28.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "°/s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. 第二层：骑行姿态指示 (人工地平仪 + 姿态角)
            item {
                DashboardCard {
                    Column {
                        Text(
                            text = "骑行姿态指示 (人工地平仪)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AttitudeHorizonIndicator(
                                roll = data.roll,
                                pitch = data.pitch
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricTile(label = "横滚角 (Roll)", value = String.format("%.1f", data.roll), unit = "°")
                                MetricTile(label = "俯仰角 (Pitch)", value = String.format("%.1f", data.pitch), unit = "°")
                                MetricTile(label = "相对偏航角 (Yaw相对)", value = String.format("%.1f", data.yaw), unit = "°")
                            }
                        }
                    }
                }
            }

            // 4. 第三层：详细三轴数据 (默认折叠，按需展开，降低视觉噪音)
            item {
                DashboardCard {
                    CollapsibleSection(
                        title = "详细轴数据 (X / Y / Z 定点)",
                        isExpanded = isAxisDetailsExpanded,
                        onToggle = { isAxisDetailsExpanded = !isAxisDetailsExpanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column {
                                Text(
                                    text = "三轴加速度",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricTile(label = "X 轴", value = String.format("%.2f", data.ax), unit = "g")
                                    MetricTile(label = "Y 轴", value = String.format("%.2f", data.ay), unit = "g")
                                    MetricTile(label = "Z 轴", value = String.format("%.2f", data.az), unit = "g")
                                }
                            }

                            Column {
                                Text(
                                    text = "三轴角速度",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricTile(label = "X 轴(Roll率)", value = String.format("%.1f", data.gx), unit = "°/s")
                                    MetricTile(label = "Y 轴(Pitch率)", value = String.format("%.1f", data.gy), unit = "°/s")
                                    MetricTile(label = "Z 轴(Yaw率)", value = String.format("%.1f", data.gz), unit = "°/s")
                                }
                            }
                        }
                    }
                }
            }

            // 5. 第四层：实时波形图与显示控制
            item {
                DashboardCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "实时波形渲染",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "在 Canvas 画布上流式绘制动态折线",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IndustrialToggleSwitch(
                                checked = isShowCharts,
                                onCheckedChange = { viewModel.toggleCharts(it) }
                            )
                        }

                        if (isShowCharts) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "合加速度实时波形 (g)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RealtimeLineChart(
                                dataPoints = accHistory,
                                lineColor = StatusSafe,
                                minVal = 0f,
                                maxVal = 4f
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "合角速度实时波形 (°/s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RealtimeLineChart(
                                dataPoints = gyroHistory,
                                lineColor = StatusWarning,
                                minVal = 0f,
                                maxVal = 300f
                            )
                        }
                    }
                }
            }
        }
    }
}
