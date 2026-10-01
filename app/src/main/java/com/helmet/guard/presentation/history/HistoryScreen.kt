package com.helmet.guard.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helmet.guard.domain.model.AccidentRecord
import com.helmet.guard.presentation.components.DashboardCard
import com.helmet.guard.presentation.home.LogItemRow
import com.helmet.guard.presentation.theme.StatusAlert
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.presentation.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val records by viewModel.accidentRecords.collectAsState()
    val logs by viewModel.deviceLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "历史记录与日志", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { viewModel.clearAllHistory() },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAlert),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(text = "清空全部", fontSize = 11.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = StatusSafe
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("事故研判与报警记录 (${records.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("设备状态与连接日志 (${logs.size})") }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 0) {
                    if (records.isEmpty()) {
                        item {
                            EmptyHistoryBox("暂无历史事故报警记录")
                        }
                    } else {
                        items(records) { record ->
                            AccidentRecordCard(record)
                        }
                    }
                } else {
                    if (logs.isEmpty()) {
                        item {
                            EmptyHistoryBox("暂无设备与连接日志")
                        }
                    } else {
                        items(logs) { log ->
                            LogItemRow(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccidentRecordCard(record: AccidentRecord) {
    val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestampMs))

    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "事故事件 #${record.eventId}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (record.isMock) {
                        Text(
                            text = " [MOCK仿真]",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusWarning
                        )
                    }
                }
                Text(
                    text = record.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (record.status == "SENT") StatusSafe else StatusAlert,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "触发时间: $timeStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "冲击峰值: ${String.format("%.2f", record.impactPeakG)}g",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "旋转峰值: ${String.format("%.1f", record.gyroPeakDps)}°/s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "静止确认: ${record.stillDurationSec}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = if (record.latitude != 0.0) {
                    "坐标: ${record.latitude}, ${record.longitude} (精度约 ${record.locationAccuracy.toInt()}m${if (record.isHistoricalLocation) "，历史降级" else ""})"
                } else {
                    "坐标: 未获取有效定位"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "分发结果: ${record.dispatchResult}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyHistoryBox(msg: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
