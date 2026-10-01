package com.helmet.guard.data.mock

enum class MockScenario {
    NORMAL_RIDING,      // 正常骑行 (轻微路面颠簸，重力合加速度 ~1.0g)
    EMERGENCY_BRAKE,    // 急刹车 (纵向加速度跃升至 1.8g，小倾角，无翻滚)
    SPEED_BUMP,         // 减速带颠簸 (瞬间 1.6g 冲击，100ms 内恢复)
    CRASH_AND_FALL,     // 强烈碰撞并侧倾摔倒 (合加速度 4.8g，翻转 65°，摔地静止 5s)
    LOW_BATTERY,        // 低电量警报 (电量骤降至 9%)
    DISCONNECT,         // 蓝牙意外断开
    STANDBY_PAUSED      // 待机静止与遥测暂停 (测试结束或复位待机，绝对零噪声，算法不驱动)
}
