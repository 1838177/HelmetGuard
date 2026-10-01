# HelmetGuard BLE 协议 v1（2.0 App 兼容）

## GATT

| 项目 | UUID | 属性 |
|---|---|---|
| 主服务 | `0000FFF0-0000-1000-8000-00805F9B34FB` | Primary Service |
| 遥测 | `0000FFF1-0000-1000-8000-00805F9B34FB` | Notify |
| 事故/按键 | `0000FFF2-0000-1000-8000-00805F9B34FB` | Indicate 或 Notify |
| 手机命令 | `0000FFF3-0000-1000-8000-00805F9B34FB` | Write |

广播名称以 `HelmetGuard` 开头，并建议在广告包中包含 FFF0 服务 UUID。建议 MTU 64；失败时协议仍可通过通知分片组帧。

## 帧格式

均为小端序：

```text
AA 55 | Version(1) | Type(1) | Sequence(1) | PayloadLength(1) | Payload(N) | CRC8(1)
```

CRC 为从帧头到 Payload 最后一字节的 CRC-8-CCITT，初值 `0x00`，多项式 `0x07`。序号按 0–255 循环递增。

## 消息

### `0x01` 遥测（24 bytes）

| 偏移 | 类型 | 含义 | 缩放 |
|---|---:|---|---:|
| 0 | uint32 | 启动后毫秒 | 1 ms |
| 4/6/8 | int16×3 | AX/AY/AZ | 1/1000 g |
| 10/12/14 | int16×3 | GX/GY/GZ | 1/10 °/s |
| 16/18/20 | int16×3 | Roll/Pitch/Yaw | 1/10 ° |
| 22 | uint8 | 电量 | 0–100% |
| 23 | uint8 | 状态位 | bit0 IMU 正常；bit1 已校准 |

推荐 20–50 Hz。频率低于 20 Hz 会降低冲击持续时间和自由落体特征的精度。

### `0x02` 硬件事故（16 bytes）

`deviceId:uint32, eventId:uint32, impactPeak:int16(/100g), gyroPeak:int16(°/s), stillDuration:uint16(/10s), countdown:uint8, flags:uint8`。flags bit0 表示需要手机 ACK，bit1 可表示蜂鸣器正在响。

### `0x03` 按键（1 byte）

`1=单击, 2=双击, 3=长按`。当前 App 将双击用于立即求救、长按用于取消正在处理的警报。

### `0x04` 手机命令

- ACK：`01 + eventId:uint32 + status:uint8`
- 取消：`02 + eventId:uint32 + source:uint8`
- 查找：`03 + duration:uint8`
- IMU 校准：`04`
- 对时：`05 + epochSeconds:uint32`

## 固件可靠性要求

1. FFF1/FFF2 的 CCCD 写入成功后才开始推流。
2. 事故帧持续重发，直到收到相同 `eventId` ACK 或硬件超时；重发不产生新的事件号。
3. 手机断开时硬件蜂鸣器和物理按键仍独立工作。
4. IMU I²C 异常应清除 status bit0，不得继续发送看似正常的固定值。
5. 使用 ESP32 看门狗；事件号与关键故障建议写入 NVS，但不要按帧写 Flash。
6. 一条协议帧可跨多个 GATT notification，也允许一个 notification 包含多条帧；App 均能重组。
