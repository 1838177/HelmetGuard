package com.helmet.guard.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helmet.guard.presentation.theme.StatusAlert
import com.helmet.guard.presentation.theme.StatusSafe
import com.helmet.guard.presentation.theme.StatusWarning
import kotlin.math.max
import kotlin.math.sin

/**
 * 工业状态指示圆点，支持平滑但短暂的微幅呼吸
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false,
    sizeDp: Int = 10
) {
    if (isPulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
    } else {
        Box(
            modifier = modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * 简雅仪表盘卡片，严控圆角 (12dp) 与工业边框
 */
@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    backgroundColor: Color? = null,
    content: @Composable () -> Unit
) {
    val finalBorderColor = borderColor ?: MaterialTheme.colorScheme.outline
    val finalBgColor = backgroundColor ?: MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(finalBgColor)
            .border(1.dp, finalBorderColor, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * 主要操作按钮：绿色实心，48dp触摸高度，10dp圆角
 */
@Composable
fun HelmetPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StatusSafe,
            contentColor = Color(0xFF0D0F11),
            disabledContainerColor = StatusSafe.copy(alpha = 0.3f),
            disabledContentColor = Color(0xFF0D0F11).copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 普通操作按钮：高对比度边框按钮，48dp触摸高度，10dp圆角
 */
@Composable
fun HelmetSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled).copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 危险操作按钮：警示红，48dp触摸高度，10dp圆角
 */
@Composable
fun HelmetDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isOutlined: Boolean = false
) {
    if (isOutlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = StatusAlert
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = enabled).copy(
                brush = androidx.compose.ui.graphics.SolidColor(StatusAlert)
            )
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StatusAlert,
                contentColor = Color.White
            )
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 演示模式常驻横幅 (醒目工业黄，明确声明零真实短信调用)
 */
@Composable
fun HelmetMockBanner(
    scenarioName: String? = null,
    title: String = "演示模式 · 不会发送真实短信",
    statusDetail: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StatusWarning.copy(alpha = 0.12f))
            .border(1.dp, StatusWarning.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = StatusWarning, sizeDp = 8)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = StatusWarning,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val detail = statusDetail ?: scenarioName?.let { "场景: $it" } ?: "仿真运行中"
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = StatusWarning.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * 可平滑折叠展开的组件容器
 */
@Composable
fun CollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
        ) {
            Box(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

/**
 * 高对比度数值展示块
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = valueColor
            )
            if (unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * 高性能实时折线图 (Canvas 绘制最近采样点，自适应动态扩展量程防截平)
 */
@Composable
fun RealtimeLineChart(
    dataPoints: List<Float>,
    lineColor: Color = StatusSafe,
    minVal: Float = 0f,
    maxVal: Float = 5f,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(100.dp)
) {
    val dynamicMax = max(maxVal, (dataPoints.maxOrNull() ?: 1f) * 1.15f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val range = (dynamicMax - minVal).coerceAtLeast(0.1f)

        // 背景基准参考线
        drawLine(
            color = Color.Gray.copy(alpha = 0.2f),
            start = Offset(0f, height * 0.5f),
            end = Offset(width, height * 0.5f),
            strokeWidth = 1.dp.toPx()
        )

        if (dataPoints.size < 2) return@Canvas

        val path = Path()
        val stepX = width / (dataPoints.size - 1).coerceAtLeast(1)

        dataPoints.forEachIndexed { index, value ->
            val normalizedY = 1.0f - ((value - minVal) / range).coerceIn(0f, 1f)
            val x = index * stepX
            val y = normalizedY * height

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * 骑行姿态指示器 (模拟航空地平仪，144dp 完整渲染天空、地面与地平线)
 */
@Composable
fun AttitudeHorizonIndicator(
    roll: Float, // °
    pitch: Float, // °
    modifier: Modifier = Modifier.size(144.dp)
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val skyColor = Color(0xFF18324D)        // 天空
    val groundColor = Color(0xFF49352B)     // 地面
    val horizonLineColor = Color(0xFFD8E4EA) // 地平分割线

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)

        val circlePath = Path().apply {
            addOval(Rect(center = center, radius = radius))
        }

        clipPath(circlePath) {
            // 完整渲染天空底色
            drawRect(
                color = skyColor,
                topLeft = Offset(center.x - radius * 2, center.y - radius * 2),
                size = Size(radius * 4, radius * 4)
            )

            // 旋转角度为横滚角
            rotate(degrees = -roll, pivot = center) {
                // 俯仰偏移
                val pitchOffset = (pitch / 90f).coerceIn(-1f, 1f) * (radius * 0.8f)

                // 绘制地平线下半部分 (地面)
                drawRect(
                    color = groundColor,
                    topLeft = Offset(center.x - radius * 2, center.y + pitchOffset),
                    size = Size(radius * 4, radius * 3)
                )

                // 绘制地平分割线 (宽 2dp)
                drawLine(
                    color = horizonLineColor,
                    start = Offset(center.x - radius * 2, center.y + pitchOffset),
                    end = Offset(center.x + radius * 2, center.y + pitchOffset),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // 绘制外圈刻度边框
        drawCircle(
            color = borderColor,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // 中心固定白十字准星 (宽 2dp)
        val crossLength = 12.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(center.x - crossLength, center.y),
            end = Offset(center.x + crossLength, center.y),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = Color.White,
            start = Offset(center.x, center.y - crossLength),
            end = Offset(center.x, center.y + crossLength),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/**
 * 仪表盘高对比度滑动开关：
 * 开启时背景为绿色，右侧为白色小圆形按钮；
 * 关闭时背景为深灰，左侧为白色小圆形按钮。
 * 高对比度工业风，稳定无缩放抖动与闪烁。
 */
@Composable
fun IndustrialToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) StatusSafe else Color(0xFF33383F),
        animationSpec = tween(durationMillis = 200),
        label = "switch_track"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) StatusSafe else Color(0xFF555B64),
        animationSpec = tween(durationMillis = 200),
        label = "switch_border"
    )
    val bias by animateFloatAsState(
        targetValue = if (checked) 1f else -1f,
        animationSpec = tween(durationMillis = 200),
        label = "switch_bias"
    )

    Box(
        modifier = modifier
            .width(50.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = BiasAlignment(horizontalBias = bias, verticalBias = 0f)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
