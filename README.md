# Helmet Guard (智能头盔安全防护系统)

一款专为智能头盔设计的 Android 原生物联网安全伴侣应用。基于现代 Android 架构体系开发，通过 BLE 低功耗蓝牙与头盔端传感器保持高频通信，结合六轴 IMU 算法复合研判骑行状态，在发生严重碰撞或摔倒事故时自动启动防误触倒计时，并行预取高精度 GPS 坐标并零间隔向全部预设紧急联系人直发求救短信。

---

## 核心特性

- **BLE 实时传感器遥测**：采用 20Hz 高频六轴 IMU 遥测数据流解析（三轴加速度、三轴角速度、姿态角、电量），集成单调时钟数据新鲜度看门狗与 GATT 串行操作队列，杜绝特征写入冲突与虚假在线。
- **复合事故研判状态机**：支持冲量碰撞研判与跌倒后静止检测双阶段复合判定，有效过滤颠簸与日常晃动。
- **防误触与零延迟直发分发**：支持 15 秒（可调）防误触倒计时；倒计时触发瞬间后台异步预取高精度定位，倒计时自然归零瞬间 0 毫秒并发直发求救短信，支持多联系人独立分发与回执校验。
- **联系人管理与启闭指示器**：二级管理界面提供紧急联系人增删改查、首选优先级设定，采用工业纯色方框实体绿块指示器，直观掌控求救目标。
- **工业冷光仪表盘美学**：天然去雕饰设计风格，默认工业深冷色调高对比仪表盘，兼顾日间强光浅色防眩模式；内嵌实时人工地平仪与动态姿态波形。
- **真实自检与硬隔离仿真**：内置 10 项软硬件运行环境与权限真实自检矩阵；支持多场景事故模拟仿真，仿真环境严格物理隔离，绝不外发真实求救短信。

---

## 技术架构与依赖栈

| 模块 / 维度 | 选型与技术栈 | 说明 |
| :--- | :--- | :--- |
| **开发语言** | Kotlin 1.9+ | 协程 Coroutines、StateFlow / SharedFlow 响应式状态管理 |
| **界面框架** | Jetpack Compose + Material 3 | 全声明式 UI，深色/浅色自适应主题，零 XML 布局 |
| **架构模式** | MVVM + Clean Architecture | 严格分层：Domain（领域契约）、Data（仓库与驱动）、Presentation（视图） |
| **蓝牙通信** | Android Bluetooth Low Energy API | GATT 队列调度、CCCD 描述符管理、重连容灾机制 |
| **本地存储** | Room Database v2 + DataStore Preferences | 支持平滑数据迁移（Migration 1->2）、参数持久化与字段默认值防崩溃 |
| **定位与分发** | Fused Location Provider + SmsManager | 高精度定位预取、双卡与多短信分段 PendingIntent 隔离 |

---

## 工程目录结构

```text
app/src/main/java/com/helmet/guard/
├── core/
│   ├── ble/              # BLE 蓝牙连接管理、扫描与 GATT 操作队列
│   ├── detector/         # 六轴 IMU 碰撞与跌倒复合事故研判算法
│   ├── dispatcher/       # 短信分发器（SmsManager直发与仿真隔离）
│   └── location/         # FusedLocationProvider 高精度定位与预取
├── data/
│   ├── database/         # Room 数据库定义、Entity 与 DAO
│   ├── mock/             # 仿真引擎与测试场景数据流注入
│   ├── preferences/      # DataStore 用户配置与安全参数持久化
│   └── repository/       # 数据仓库实现与业务调度协同
├── domain/
│   ├── model/            # 领域核心实体模型与状态机定义
│   └── repository/       # 仓库契约接口定义
├── presentation/
│   ├── common/           # 路由导航与全局事件
│   ├── components/       # 工业仪表盘组件、人工地平仪、波形画布
│   ├── countdown/        # 全屏防误触倒计时报警遮罩
│   ├── device/           # 蓝牙设备扫描与配对连接页面
│   ├── emergency/        # 紧急联系人管理二级页面
│   ├── history/          # 历史事故与报警记录页面
│   ├── home/             # 首页仪表盘与系统监控中心
│   ├── selfcheck/        # 10 项系统与传感器自检矩阵页面
│   ├── settings/         # 系统偏好设置与安全参数配置页面
│   └── theme/            # 工业深冷色系与浅色防眩主题配置
└── service/              # 前台保活服务与辅助服务
```

---

## 快速构建与运行

### 1. 环境要求
- **Android Studio**：Ladybug (2024.2.1) / Koala / Hedgehog 或更新版本
- **JDK**：OpenJDK 17 / Microsoft OpenJDK 17
- **Android SDK**：
  - `compileSdk`: 35
  - `targetSdk`: 35
  - `minSdk`: 26 (Android 8.0+)
- **构建工具**：Gradle 8.10.2 (包含完整 Wrapper)

### 2. 本地编译指令

克隆工程到本地后，在项目根目录运行以下终端命令：

```bash
# 运行全部单元测试
./gradlew testDebugUnitTest

# 编译生成 Debug 测试安装包
./gradlew assembleDebug

# 编译生成 Release 正式发布包
./gradlew assembleRelease
```

编译输出目录位于 `app/build/outputs/apk/`。

---

## 开源许可与声明

本项目仅供学习、研发及技术原型评估使用。生产环境部署请结合具体硬件板卡传感参数与运营商规范进行实地测试与安全评估。
