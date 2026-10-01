package com.helmet.guard.domain.model

/**
 * 系统运行模式
 */
enum class OperationMode {
    LIVE,               // 真实头盔在线监测
    SIMULATION,         // 本地仿真模拟（严禁调用底层真实通信组件）
    EXPLICIT_SMS_TEST   // 用户在设置页显式触发的单次短信通道联调
}
