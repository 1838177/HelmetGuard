package com.helmet.guard.presentation.theme

import androidx.compose.ui.graphics.Color

// 仪表盘调色板 (符合工业安全规范与极简设计语言，零廉价渐变与荧光)
val DarkBackground = Color(0xFF0D0F11)
val DarkSurface = Color(0xFF15191D)
val DarkSurfaceVariant = Color(0xFF1C2227)
val DarkBorder = Color(0xFF303840)

val LightBackground = Color(0xFFF4F5F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8EBF0)
val LightBorder = Color(0xFFD6DAE1)

// 语义状态指示色
val StatusSafe = Color(0xFF00D26A)      // 安全绿色 (深色模式: 正常/已连接/通过)
val StatusSafeLight = Color(0xFF08783E) // 浅色模式安全绿 (对比度 5.2:1，符合 WCAG AA 防眩)
val StatusWarning = Color(0xFFFFA62B)   // 琥珀橙色 (不稳定/警告/测试)
val StatusAlert = Color(0xFFE5484D)     // 警示红 (事故/高危/断开)
val StatusOffline = Color(0xFF6F7A84)   // 工业中性灰 (未绑定/待命)

// 文本层次
val TextPrimaryDark = Color(0xFFF4F7F8)
val TextSecondaryDark = Color(0xFFA5AFB8)
val TextTertiaryDark = Color(0xFF6F7A84)

val TextPrimaryLight = Color(0xFF1A1D21)
val TextSecondaryLight = Color(0xFF5A636E)
val TextTertiaryLight = Color(0xFF8F98A3)
