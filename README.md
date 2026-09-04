# 多功能个人助手 App（Personal Assistant）

> 纯离线 Android 个人助手 App，集 **课表 / 任务 / 记账 / 日历** 四大模块于一体
> 基于 Jetpack Compose + Room + Hilt + MVVM + Clean Architecture

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android)
![Language](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-34-blue)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

---

## ✨ 已实现（Phase 1 — 基础架构）

- ✅ Gradle 工程配置（Kotlin DSL + Version Catalog）
- ✅ AndroidManifest（纯离线，无 INTERNET 权限）
- ✅ Application + Hilt + WorkManager 集成
- ✅ 单 Activity + Navigation Compose + 4 个底部 Tab
- ✅ Material 3 主题系统（樱花粉 + 浅色/深色 + 动态色）
- ✅ Room 数据库（9 张表全部定义完毕）
- ✅ DataStore 偏好设置
- ✅ 通用工具（DateUtils / CurrencyFormatter / NotificationHelper）
- ✅ 通知渠道（4 类：课程/任务/事件/记账）
- ✅ App Icon（Adaptive Icon 占位）

## 🚧 待实现（Phase 2+ — 业务层）

| 阶段 | 内容 |
|------|------|
| Phase 2 | 课表模块：周视图网格、课程 CRUD、冲突检测、多学期 |
| Phase 3 | 任务模块：日期分组、优先级、子任务、重复任务 |
| Phase 4 | 记账模块：零钱池（含跨天刷新）、分类、月度报表、图表 |
| Phase 5 | 日历模块：月/周/日视图、与任务和课表联动 |
| Phase 6 | 设置：主题切换、备份导出、提醒、清除数据 |
| Phase 7 | 测试优化、UI 打磨、正式图标设计 |

---

## 🚀 快速开始

### 前置环境

- **Android Studio** Hedgehog (2023.1) 或更新
- **JDK 17**（已自带，AGP 8.3 需要）
- **Android SDK** 34 + Build-Tools 34.0.0
- （可选）**Gradle 8.5** 用于生成 wrapper

### 第一次打开

1. 用 Android Studio 打开本目录
2. 等待 Gradle Sync 完成（首次会下载约 500 MB 依赖）
3. 若提示 "Gradle wrapper not found"，按下面 [生成 wrapper](#-生成-gradle-wrapper) 操作
4. 连接 Android 8.0+ 设备或启动模拟器
5. 点击 ▶ Run

### 生成 Gradle Wrapper

若项目根目录缺少 `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`，执行：

```bash
# 需先在系统装 Gradle 8.5+
gradle wrapper --gradle-version 8.5
```

或者用 Android Studio 打开项目时会自动创建。

---

## 📂 项目结构

```
PersonalAssistant/
├── settings.gradle.kts            # 模块声明 + 仓库
├── build.gradle.kts               # 顶层插件版本
├── gradle.properties              # JVM/AndroidX/Kotlin 配置
├── gradle/
│   ├── libs.versions.toml         # 版本目录（依赖集中管理）
│   └── wrapper/                   # Gradle wrapper
│
└── app/                           # 唯一应用模块
    ├── build.gradle.kts           # app 构建脚本（依赖、签名）
    ├── proguard-rules.pro         # Release 混淆规则
    └── src/main/
        ├── AndroidManifest.xml    # 无 INTERNET 权限
        ├── java/com/example/personalassistant/
        │   ├── MainActivity.kt           # 单 Activity 入口
        │   ├── PersonalAssistantApp.kt   # Application + Hilt 根
        │   ├── navigation/               # 路由 + BottomBar
        │   ├── core/
        │   │   ├── database/             # AppDatabase + Converters
        │   │   ├── di/                   # CoreModule（DB + DataStore + Dispatcher）
        │   │   ├── ui/theme/             # Color/Type/Shape/Theme
        │   │   └── utils/                # DateUtils/CurrencyFormatter/NotificationHelper
        │   ├── feature/                  # 4 大功能模块
        │   │   ├── timetable/            # 课表
        │   │   │   ├── data/local/       # CourseEntity + CourseDao + SemesterEntity + SemesterDao
        │   │   │   └── TimetableScreen.kt
        │   │   ├── task/                 # 任务
        │   │   │   ├── data/local/       # TaskEntity + SubTaskEntity + TaskDao
        │   │   │   └── TaskScreen.kt
        │   │   ├── calendar/             # 日历
        │   │   │   ├── data/local/       # EventEntity + EventDao
        │   │   │   └── CalendarScreen.kt
        │   │   ├── finance/              # 记账
        │   │   │   ├── data/local/       # Transaction + Category + MonthlyBudget + DailyPool + FinanceDao
        │   │   │   └── FinanceScreen.kt
        │   │   └── settings/             # 设置
        │   │       ├── SettingsScreen.kt
        │   │       ├── SettingsViewModel.kt
        │   │       └── SettingsRepository.kt
        │   └── service/reminder/         # BootReceiver (开机重设 WorkManager)
        │
        └── res/                          # 资源
            ├── drawable/ic_launcher_foreground.xml
            ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
            ├── values/{strings,colors,themes}.xml
            └── xml/{backup_rules,data_extraction_rules}.xml
```

---

## 🗃 数据库 Schema（9 张表）

| 表 | 说明 | 关键字段 |
|----|------|---------|
| `courses` | 课程 | dayOfWeek, startTime(分钟数), endTime, semester, weeks |
| `semesters` | 学期 | name(unique), startDate, endDate, isCurrent |
| `tasks` | 任务 | title, dueDate, priority, isCompleted, repeatType |
| `sub_tasks` | 子任务 | parentTaskId→tasks, order |
| `events` | 日历事件 | date, startTime, isAllDay, reminder |
| `transactions` | 记账流水 | amount, type(0=支/1=收), categoryId, date |
| `categories` | 记账分类 | name, type, iconName, isDefault |
| `monthly_budgets` | 月预算 | yearMonth(unique), totalAmount, carryOverEnabled |
| `daily_pools` | 每日零钱池 | date(unique), remainingAmount, carryOver |

### 零钱池专项约束（说明书 6.7）

⚠️ `transactions` 与 `daily_pools` 的写入必须在同一 Room 事务中执行，否则会出现：
- 记账了但零钱池没扣 → 数据不一致
- 并发记账时丢失更新 → 余额错乱

参考 [FinanceDao.recordExpense / recordIncome / deleteTransactionAndRollbackPool](app/src/main/java/com/example/personalassistant/feature/finance/data/local/FinanceDao.kt)

---

## 🎨 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 1.9.22 |
| UI | Jetpack Compose (BOM) | 2024.02.02 |
| 主题 | Material 3 | 1.2.x |
| 架构 | MVVM + Clean Architecture | — |
| 导航 | Navigation Compose | 2.7.7 |
| 数据库 | Room + KSP | 2.6.1 |
| DI | Hilt | 2.50 |
| 偏好 | DataStore Preferences | 1.0.0 |
| 后台 | WorkManager + AlarmManager | 2.9.0 |
| 图表 | MPAndroidChart | v3.1.0 |
| 序列化 | kotlinx-serialization | 1.6.2 |
| 构建 | AGP | 8.3.0 |
| Gradle | Gradle | 8.5 |
| JDK | JDK | 17 |

---

## 🔒 离线特性

- ❌ 不申请 `INTERNET` / `ACCESS_NETWORK_STATE` 权限
- ✅ 课程/任务/事件提醒通知（POST_NOTIFICATIONS, Android 13+ 需运行时申请）
- ✅ 开机自启重设 WorkManager（RECEIVE_BOOT_COMPLETED）
- ✅ 精确闹钟（SCHEDULE_EXACT_ALARM）
- ✅ 数据备份通过 Storage Access Framework 导出到本地（不申请存储权限）

---

## 📝 开发规范

- **包结构**：按 feature 模块分包，每个模块内部 data/domain/presentation 三层
- **命名**：Entity 后缀统一（CourseEntity），DAO 接口（CourseDao），Repository 实现（XxxRepositoryImpl）
- **协程**：所有 IO 操作挂起函数，DB 自动在 IO 线程执行
- **Flow**：DB 查询返回 Flow，UI 用 `collectAsStateWithLifecycle()`
- **时间**：所有时间字段用 `Long` 时间戳（毫秒），课表时间用当天分钟数（0-1439）
- **事务**：涉及多表写入的 UseCase 用 `@Transaction` 包裹

---

## 📄 License

MIT
