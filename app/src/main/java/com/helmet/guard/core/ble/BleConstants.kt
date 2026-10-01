package com.helmet.guard.core.ble

import java.util.UUID

/**
 * BLE UUID 常量与自定义二进制通信协议参数定义
 * 集中配置，便于与具体硬件模块（如 nRF52840, ESP32 等）对齐
 */
object BleConstants {
    // 广播过滤名称前缀
    const val DEVICE_NAME_PREFIX = "HelmetGuard"

    // 智能头盔主服务 UUID (128位标准 UUID，硬件侧需配置一致)
    val SERVICE_HELMET: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")

    // 特征值：传感器实时数据 (Notify)
    val CHAR_SENSOR_DATA: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")

    // 特征值：事故与按键事件通知 (Indicate / Notify)
    val CHAR_EVENT_NOTIFY: UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")

    // 特征值：双向控制与ACK命令 (Write / Write Without Response)
    val CHAR_CONTROL_COMMAND: UUID = UUID.fromString("0000FFF3-0000-1000-8000-00805F9B34FB")

    // 协议帧头魔数 (2 字节)
    const val FRAME_HEADER_BYTE1: Byte = 0xAA.toByte()
    const val FRAME_HEADER_BYTE2: Byte = 0x55.toByte()

    // 协议版本
    const val PROTOCOL_VERSION: Byte = 0x01.toByte()

    // 消息类型
    const val MSG_TYPE_SENSOR: Byte = 0x01.toByte()      // 传感器遥测数据 (24 字节载荷)
    const val MSG_TYPE_ACCIDENT: Byte = 0x02.toByte()    // 事故锁定事件 (16 字节载荷)
    const val MSG_TYPE_BUTTON: Byte = 0x03.toByte()      // 物理按键事件 (1 字节载荷)
    const val MSG_TYPE_COMMAND_ACK: Byte = 0x04.toByte() // 控制命令与ACK确认
    const val MSG_TYPE_HEARTBEAT: Byte = 0x05.toByte()   // 心跳状态帧

    // 命令字子类型 (MsgType = 0x04)
    const val CMD_ACK_EVENT: Byte = 0x01.toByte()        // 手机响应事故事件 ACK
    const val CMD_CANCEL_ALARM: Byte = 0x02.toByte()     // 取消报警命令
    const val CMD_FIND_HELMET: Byte = 0x03.toByte()      // 查找头盔 (蜂鸣器)
    const val CMD_CALIBRATE_IMU: Byte = 0x04.toByte()    // 零偏校准
    const val CMD_SYNC_TIME: Byte = 0x05.toByte()        // 时间同步

    // 推荐 MTU
    const val DESIRED_MTU = 64
}
