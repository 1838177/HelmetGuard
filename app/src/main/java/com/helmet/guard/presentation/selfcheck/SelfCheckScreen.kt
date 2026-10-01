package com.helmet.guard.presentation.selfcheck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helmet.guard.presentation.components.DashboardCard
import com.helmet.guard.presentation.components.StatusDot
import com.helmet.guard.presentation.theme.StatusAlert
import com.helmet.guard.presentation.theme.StatusOffline
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.presentation.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfCheckScreen(
    viewModel: SelfCheckViewModel,
    onNavigateBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val isChecking by viewModel.isChecking.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "设备与功能自检", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(16.dp)
        ) {
            Button(
                onClick = { viewModel.startFullCheck() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(6.dp),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(text = "开始 10 项系统完整自检", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items) { item ->
                    CheckItemCard(item)
                }
            }
        }
    }
}

@Composable
fun CheckItemCard(item: CheckItem) {
    val (statusText, statusColor) = when (item.status) {
        CheckStatus.WAITING -> Pair("待检测", StatusOffline)
        CheckStatus.CHECKING -> Pair("检测中", StatusWarning)
        CheckStatus.PASS -> Pair("正常", StatusSafe)
        CheckStatus.WARNING -> Pair("警告", StatusWarning)
        CheckStatus.FAIL -> Pair("失败", StatusAlert)
    }

    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = statusColor, isPulsing = item.status == CheckStatus.CHECKING)
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(
                        text = "${item.id}. ${item.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.suggestion.isNotBlank()) {
                Text(
                    text = "建议: ${item.suggestion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarning
                )
            }
        }
    }
}
