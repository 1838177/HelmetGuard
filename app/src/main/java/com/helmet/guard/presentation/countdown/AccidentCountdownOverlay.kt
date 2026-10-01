package com.helmet.guard.presentation.countdown

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helmet.guard.domain.model.AccidentLifecycleState
import com.helmet.guard.presentation.theme.StatusAlert
import com.helmet.guard.presentation.theme.StatusWarning

/**
 * 全屏防误触事故倒计时报警遮罩
 * 严格遵循极简与高对比度规范，固定按键物理位置，支持长按安全解除
 */
@Composable
fun AccidentCountdownOverlay(
    state: AccidentLifecycleState,
    secondsLeft: Int,
    batteryPercent: Int,
    isMockMode: Boolean = false,
    onCancelAlarm: () -> Unit
) {
    val isVisible = state == AccidentLifecycleState.COUNTDOWN ||
            state == AccidentLifecycleState.LOCATING ||
            state == AccidentLifecycleState.SENDING

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        var isLongPressing by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF140505)) // 极简暗红黑背景
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 顶部状态提示与常驻演示横幅
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isMockMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusWarning.copy(alpha = 0.15f))
                                .border(1.dp, StatusWarning, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "演示模式 · 不会发送真实短信",
                                style = MaterialTheme.typography.labelMedium,
                                color = StatusWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Text(
                        text = "检测到疑似事故",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                        color = StatusAlert,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "头盔电量: $batteryPercent%  |  定位服务: 待命中",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFAAAAAA)
                    )
                }

                // 中间巨大倒计时数字
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$secondsLeft",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = Color.White
                    )
                    Text(
                        text = when (state) {
                            AccidentLifecycleState.LOCATING -> "倒计时已结束，正在获取精准 GPS..."
                            AccidentLifecycleState.SENDING -> "正在向紧急联系人分发求救信息..."
                            else -> "秒后将向预设联系人发送求救信息"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFDDDDDD)
                    )
                }

                // 底部固定操作按钮 (进入 SENDING 状态后锁定防撤回)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isSubmitting = state == AccidentLifecycleState.SENDING
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSubmitting) Color(0xFF1E1E1E)
                                else if (isLongPressing) Color(0xFF333333)
                                else Color(0xFF222222)
                            )
                            .then(
                                if (!isSubmitting) {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isLongPressing = true
                                                val released = tryAwaitRelease()
                                                isLongPressing = false
                                            },
                                            onLongPress = {
                                                onCancelAlarm()
                                            }
                                        )
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSubmitting) "报警短信已提交运营商，不可撤回"
                                else if (isLongPressing) "松开将继续倒计时，请按住不放..."
                                else "长按此处或头盔按键取消报警",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSubmitting) Color(0xFF888888) else if (isLongPressing) Color.White else Color(0xFFCCCCCC)
                        )
                    }
                }
            }
        }
    }
}
