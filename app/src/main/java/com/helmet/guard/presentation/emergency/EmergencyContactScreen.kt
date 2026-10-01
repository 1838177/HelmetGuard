package com.helmet.guard.presentation.emergency

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helmet.guard.core.dispatcher.SmsManagerDispatcher
import com.helmet.guard.domain.model.EmergencyContact
import com.helmet.guard.presentation.components.DashboardCard
import com.helmet.guard.presentation.theme.StatusAlert
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.presentation.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactScreen(
    viewModel: EmergencyViewModel,
    onNavigateBack: () -> Unit
) {
    val contactsNullable by viewModel.contacts.collectAsState()
    val isLoaded = contactsNullable != null
    val contacts = contactsNullable ?: emptyList()

    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var relationInput by remember { mutableStateOf("家属") }
    var isPrimaryInput by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "紧急联系人", style = MaterialTheme.typography.titleLarge) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 提示栏 (仅在数据加载完成后研判显示，杜绝初始化空列表引发的闪烁)
            item {
                if (isLoaded) {
                    if (contacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusAlert.copy(alpha = 0.1f))
                                .border(1.dp, StatusAlert, RoundedCornerShape(6.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "请先设置紧急联系人。未设置联系人前，发生事故时系统无法向外界分发求救信息。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusAlert
                            )
                        }
                    } else if (contacts.none { it.isEnabled }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusWarning.copy(alpha = 0.12f))
                                .border(1.dp, StatusWarning, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "警示: 所有紧急联系人均处于未启动状态，事故报警时将无法发送短信！",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusWarning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 新增/编辑联系人输入卡片
            item {
                DashboardCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "添加紧急联系人",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("姓名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it.replace(Regex("[^0-9+\\-()（）—－\\s\u3000]"), "") },
                            label = { Text("手机号码 (用于接收报警短信)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = relationInput,
                            onValueChange = { relationInput = it },
                            label = { Text("关系 (如: 妻子 / 父母 / 朋友 / 队长)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isPrimaryInput,
                                onCheckedChange = { isPrimaryInput = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "设为第一优先求救联系人",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                val cleanedPhone = SmsManagerDispatcher.cleanPhoneNumber(phoneInput)
                                if (nameInput.isNotBlank() && cleanedPhone.isNotBlank()) {
                                    viewModel.saveContact(
                                        name = nameInput.trim(),
                                        phone = cleanedPhone,
                                        isPrimary = isPrimaryInput || contacts.isEmpty(),
                                        relationship = relationInput.trim(),
                                        isEnabled = true
                                    )
                                    nameInput = ""
                                    phoneInput = ""
                                    relationInput = "家属"
                                    isPrimaryInput = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            enabled = nameInput.isNotBlank() && phoneInput.isNotBlank()
                        ) {
                            Text(text = "保存联系人", fontSize = 14.sp)
                        }
                    }
                }
            }

            // 现有联系人列表
            item {
                Text(
                    text = "已配置联系人 (${contacts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(contacts) { contact ->
                DashboardCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleContactEnabled(contact) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 统一方框 + 绿方块启动指示器 (22dp 外框，激活 14dp 纯色绿方块)
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (contact.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(2.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (contact.isEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (contact.isPrimary) {
                                        Text(
                                            text = " [首选]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = if (contact.isEnabled) " [已启动]" else " [未启动]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (contact.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${contact.phone} (${contact.relationship})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.deleteContact(contact.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 短信模板说明
            item {
                DashboardCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "报警短信模板预览",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = """[智能头盔事故报警]
系统检测到头盔佩戴者可能发生强烈碰撞或摔倒事故。
时间: 2026-09-20 15:30:12
设备: 智能头盔
头盔电量: 85%
实时定位时间: 15:30:12, 精度约 12 米
坐标: 31.2304, 121.4737
高德地图: https://uri.amap.com/marker?position=121.4737,31.2304&name=事故现场&coordinate=wgs84

请尽快联系骑手核实安全情况。
(注: 此短信由安全系统自动分发，提交成功不代表联系人已查阅)""",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
