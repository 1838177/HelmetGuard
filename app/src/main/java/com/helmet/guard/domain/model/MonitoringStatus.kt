package com.helmet.guard.domain.model

/**
 * 监测就绪与有效性状态
 * 解耦物理连接、遥测活性与事故生命周期状态，杜绝断开/未绑定时的假“当前安全”
 */
enum class MonitoringStatus {
    INITIALIZING,       // 正在初始化
    UNBOUND,            // 未绑定头盔 (灰: 尚未开始监测)
    DISCONNECTED,       // 蓝牙已断开 (红: 监测已中断)
    DATA_STALE,         // 遥测延迟或中断 (黄: 超过3秒未更新)
    ACTIVE_MONITORING,  // 正常监测中 (绿: 正在防护)
    SIMULATION          // 演示仿真中 (橙: 模拟数据)
}
