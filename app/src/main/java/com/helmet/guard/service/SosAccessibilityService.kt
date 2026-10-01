package com.helmet.guard.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.helmet.guard.HelmetGuardApp
import com.helmet.guard.domain.model.LogType

/**
 * SOS 紧急短信免拦截无障碍辅助服务
 * 借鉴开源辅助工具机制，在 Android 厂商系统弹出短信防扣费/高危拦截弹窗（例如“‘Helmet Guard’将发短信 [取消 (10s)] [发送]”）时，
 * 毫秒级自动检索并点击「发送」或「允许」，确保事故发生时无需佩戴者手动触屏即可顺利发出求救信号。
 */
class SosAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SosAccessibility"
        private var lastActionTime = 0L
        private const val ACTION_COOLDOWN_MS = 1500L
        private const val ACTIVE_WINDOW_MS = 10_000L

        @Volatile
        private var lastDispatchTriggerTimeMs = 0L

        /**
         * 当紧急求救短信开始分发时通知辅助服务，开启 10 秒免拦截窗口
         */
        fun notifyDispatchTriggered() {
            lastDispatchTriggerTimeMs = System.currentTimeMillis()
        }

        /**
         * 检查当前应用无障碍服务是否已在系统设置中启用
         */
        fun isEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, SosAccessibilityService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SosAccessibilityService 已连接并处于监听状态")
        recordAuditLog("无障碍服务已启动", "SOS 免拦截辅助服务已就绪，守护系统紧急通信通道", "正常运行")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        // 绝不拦截或处理本应用自身界面（彻底防止无障碍服务点击自身界面的主动 SOS 按钮导致弹窗死循环）
        val eventPkg = event.packageName?.toString() ?: ""
        if (eventPkg == packageName || eventPkg == "com.helmet.guard") {
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val rootPkg = rootNode.packageName?.toString() ?: ""
        if (rootPkg == packageName || rootPkg == "com.helmet.guard") {
            return
        }

        try {
            inspectAndAutoConfirm(rootNode)
        } catch (e: Exception) {
            Log.e(TAG, "解析窗口节点异常: ${e.message}", e)
        }
    }

    private fun inspectAndAutoConfirm(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < ACTION_COOLDOWN_MS) {
            return
        }
        // 门禁：仅在求救短信分发后的 10 秒时间窗口内生效，防止平时无端监听或误操作
        if (now - lastDispatchTriggerTimeMs > ACTIVE_WINDOW_MS) {
            return
        }

        // 1. 收集当前活动窗口中的所有文本
        val windowTexts = mutableListOf<String>()
        collectAllTexts(rootNode, windowTexts)
        val fullText = windowTexts.joinToString(" ")

        // 2. 研判是否为短信拦截/二次确认窗口
        // 涵盖典型场景：
        // - “‘Helmet Guard’将发短信” / “将发短信” / “发短信”
        // - 包含 “短信” 或 “SMS”，同时带有资费、扣费、发送、尝试发送等提示
        val hasSmsIndicator = fullText.contains("发短信") ||
                fullText.contains("将发短信") ||
                fullText.contains("短信", ignoreCase = true) ||
                fullText.contains("SMS", ignoreCase = true)

        val hasAppContext = fullText.contains("Helmet Guard", ignoreCase = true) ||
                fullText.contains("HelmetGuard", ignoreCase = true) ||
                fullText.contains("智能头盔")

        val hasRiskIndicator = fullText.contains("资费") ||
                fullText.contains("费用") ||
                fullText.contains("扣费") ||
                fullText.contains("可能产生") ||
                fullText.contains("可能会产生") ||
                fullText.contains("取消 (") ||
                fullText.contains("取消(")

        // 命中条件：(有短信指示 且 (应用名匹配 或 风险防扣费词汇))
        val isTargetDialog = hasSmsIndicator && (hasAppContext || hasRiskIndicator || fullText.contains("将发短信"))

        if (!isTargetDialog) {
            return
        }

        // 3. 寻找正向操作按钮（「发送」、「允许」、「始终允许」、「确定」）并模拟点击
        val clicked = findAndClickPositiveButton(rootNode)
        if (clicked) {
            lastActionTime = now
            Log.i(TAG, "已自动放行系统短信弹窗")
            recordAuditLog(
                "自动放行短信拦截",
                "已确认系统短信安全拦截弹窗，保障紧急通信畅通",
                "放行成功"
            )
        }
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, result: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            collectAllTexts(child, result)
            child?.recycle()
        }
    }

    private fun findAndClickPositiveButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        // 严格排除取消/拒绝操作及 SOS 主动按钮
        val isNegative = text.contains("取消") ||
                text.contains("拒绝") ||
                text.contains("禁止") ||
                text.contains("不再提示") ||
                text.contains("SOS", ignoreCase = true) ||
                text.contains("Cancel", ignoreCase = true) ||
                text.contains("Deny", ignoreCase = true) ||
                text.contains("Reject", ignoreCase = true) ||
                viewId.contains("cancel") ||
                viewId.contains("deny")

        if (!isNegative) {
            val isPositive = text == "发送" ||
                    text.startsWith("发送") ||
                    text == "允许" ||
                    text.startsWith("允许") ||
                    text == "始终允许" ||
                    text == "确定" ||
                    text.startsWith("确定") ||
                    text == "确认" ||
                    text.startsWith("确认") ||
                    text.equals("Allow", ignoreCase = true) ||
                    text.equals("Send", ignoreCase = true) ||
                    viewId.endsWith(":id/button1") ||
                    viewId.contains("btn_positive") ||
                    viewId.contains("accept") ||
                    viewId.contains("confirm")

            if (isPositive) {
                if (performNodeClick(node)) {
                    Log.d(TAG, "成功点击确认按钮: text='$text', viewId='$viewId'")
                    return true
                }
            }
        }

        // 递归子节点检索
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findAndClickPositiveButton(child)) {
                child?.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    private fun performNodeClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun recordAuditLog(title: String, detail: String, result: String) {
        try {
            val app = application as? HelmetGuardApp ?: return
            app.repository.recordLog(LogType.SYSTEM, title, detail, result)
        } catch (e: Exception) {
            Log.w(TAG, "写入审计日志失败: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SosAccessibilityService onInterrupt")
    }
}
