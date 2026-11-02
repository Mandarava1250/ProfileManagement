# 多功能个人助手 App（Personal Assistant）

> 纯离线 Android 个人助手 App，集 **课表 / 任务 / 记账 / 日历** 四大模块于一体，
> 外加 **多账户 + 账户头像 + 跨账户转账**、**全局搜索**、**备份恢复** 与 **主题系统**
> 基于 Jetpack Compose + Room + Hilt + MVVM + Clean Architecture

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android)
![Language](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-34-blue)
![Room](https://img.shields.io/badge/Room%20schema-v14-2ea44f)
![Tests](https://img.shields.io/badge/tests-865%20JVM%20%2B%206%20instrumented-brightgreen)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

---

## 📌 当前状态速览

| 项 | 当前状态 |
|---|---|
| 项目类型 | Android 课表 / 日历 / 任务 / 记账 应用（纯离线） |
| 包名 / 命名空间 | `com.example.personalassistant` |
| 版本 | `versionCode 1` / `versionName 0.1.0`（首个 release：`v0.1.0`） |
| 开发分支 | `Develop`（`feature/phase-16-checklist-rebuild` 进行中；`main` 为 release 分支） |
| 累计 commit | 176 |
| Room 数据库 | **v15**（15 张表，schema 快照 `app/schemas/…/1.json ~ 15.json` 全量保留） |
| 性能优化 | 五批（索引补全 / 备份批量化 / 重组收敛 / 调度器归位 / WAL + baseline profile）—— 详见 [`DEV_LOG.md`](DEV_LOG.md) 末尾段 |
| 测试 | **921 JVM 单测** 全绿（99 个测试类，0 失败 / 0 错误）+ **6 instrumented**（AVD `pm_api34` 真跑 0 失败）<br>↑ JVM 数字是 Phase 15 收尾实测（`app/build/test-results/testDebugUnitTest` 下全量 XML 求和）；instrumented 数字仍是上一轮实测 |
| 台账 | 见 [`DEV_LOG.md`](DEV_LOG.md) —— 全部 Phase 的完成项 / 验证结果 / 踩坑笔记 |

> ⚠️ 阶段 Phase 5（跨账户转账 + 账户余额 + 预算汇总）当前是 **🟨 进行中**：代码已 push、JVM 全绿，
> 但 `Migration_9_10` 的 instrumented 真跑与人工 UI 验收未做，因此台账未打 ✅。

---

## ✨ 已完成功能（按模块）

### 基础设施
- Gradle 工程（Kotlin DSL + Version Catalog + KSP），wrapper 已随仓库提供
- AndroidManifest：**纯离线，无 `INTERNET` 权限**
- 单 Activity + Navigation Compose + 底部 4 个 Tab + 第 5 位「更多」面板（我 / 设置）
- Material 3 主题系统（樱花粉 + 浅色/深色/跟随系统 + Android 12+ 动态色）
- Room 数据库 + DataStore 偏好设置 + WorkManager 后台调度
- 通知渠道 4 类（课程 / 任务 / 事件 / 记账）+ Android 13 通知权限引导 UI
- 通用工具：`DateUtils` / `CurrencyFormatter` / `NotificationHelper`

### 课表（timetable）
- `CourseEntity` / `SemesterEntity` + CRUD、周次展开（按学期起止 + 周次循环）
- 周视图课程网格、课程编辑页、事件 vs 课程冲突检测

### 任务（task）
- 任务 / 子任务 / 标签（`TagEntity` + 关联表）三层 CRUD
- 优先级 5 档 + 四象限分组（Eisenhower），6 Tab 切换 + 长按自定义档名（DataStore 持久化）
- 筛选 / 排序 / 拖拽手动排序（`tasks.manualOrder`）/ 重复任务（repeatType + repeatDays）
- 提醒调度：WorkManager 预排 + AlarmManager 精确触发，节假日感知策略（提前 / 当天 / 跳过）
- **清单能力**（Phase 15）：分类 / 截止时间 / 优先级 / 金额 / 标签 / 分组筛选排序 / 子任务嵌套（最深 3 层），独立 `ChecklistRepository` + `ChecklistViewModel`
- **清单模型合并 + 抽屉式清单页**（Phase 16–17）：两套模型合一（`tasks` 承载清单项，`task_lists` 承载归属地）；底栏「任务」改名「清单」，去掉双子 Tab 改**单页 + 左侧抽屉** —— 抽屉装**智能清单**（收集箱 / 今天 / 明天 / 最近 7 天 / 全部 / 已完成）与**用户清单**（自建归属地，带未完成数徽标，可新建 / 重命名 / 删除 / 归档）

### 记账（finance）
- 流水 CRUD + 分类管理（含自定义图标）+ 月度预算 + 每日零钱池（跨天刷新）
- 月度报表（柱状图 + 分类饼图，MPAndroidChart）、搜索与多维筛选、CSV 导入导出
- **多账户**：记账域 4 表按 `accountId` 隔离，账户切换入口在设置页
- **附件上传**：PhotoPicker + Coil 缩略图，落盘 `filesDir/attachments`（长边压至 1080 / JPEG 85）
- **账户头像**：独立 `filesDir/avatars` 目录（长边 512），换图 / 清除 / 删账户三处级联清理
- **跨账户转账**：两行配对（`transferPairId`）+ 派生余额（`期初 + 收 − 支 + 转入 − 转出`）+ 账户总览卡

### 日历（calendar）
- 月 / 周 / 日视图 + `DayDetailSheet` + 事件编辑闭环
- 事件分类（`event_categories`）、RRULE 重复事件、事件智能提醒、冲突红条提示
- 月视图农历角标（`cn.6tail:lunar`）+ 节假日库（2025-2026 预置）

### 全局与设置
- 全局搜索：4 模块 combine + 模糊匹配 + 分组展示 + 关键字高亮
- 备份恢复：全表序列化 → SAF 导出 zip/json → 校验 → 事务化导入；WorkManager 周期备份
- 设置页卡片式布局：主题切换、账户管理（新建 / 切换 / 重命名 / 删除 + 期初余额 + 头像）、备份与恢复入口、通知权限引导
- **危险操作：清除全部数据**（阶段 Phase 6）— 红色 Card 入口 + 「建议先备份」弹窗 + 长按 3 秒确认 + 完成反馈弹窗，覆盖 Room 14 张表 / DataStore 偏好 / 附件 / 头像四个数据源，调度层 cancel 全部走在数据清之前

---

## 🧭 Phase 编号说明（两套，别混）

| 体系 | 含义 | 范围 | 状态 |
|---|---|---|---|
| **模块 Phase 1~8** | 按模块演进：基础架构 → 课表 → 任务 → 提醒 → 记账 → 日历 → 综合扩展 | 1 / 2 / 3 / 4a / 4b / 5 / 5b / 6a / 6b / 6c / 7a / 7b / 7c / 8（sub-step 1~9 + 8b'） | 全部落地 |
| **阶段 Phase 1~5** | Phase 8 末尾的补完计划（通知权限 / 附件 / 多账户 / 头像 / 转账） | 1 ✅ / 2 ✅ / 3 ✅ / 4 ✅ / 5 🟨 | 进行到 5 |

完整时间线与逐 commit 记录见 [`DEV_LOG.md`](DEV_LOG.md)；计划文档见 [`docs/plans/`](docs/plans)。

---

## 🚧 待办与已知缺口

| 优先级 | 内容 | 出处 |
|---|---|---|
| ⛔ | `Migration_9_10` instrumented 真跑（开机 AVD → 加 9→10 用例 → `connectedDebugAndroidTest`），跑通后阶段 Phase 5 才转 ✅ | 阶段 Phase 5 |
| 🟨 | 人工 UI 验收：「设期初 → 转账 → 两账本各多一行 → 删转账 → 总览卡数字」；头像圆角观感 | 阶段 Phase 4 / 5 |
| 🔜 | 附件旧文件回收：`AttachmentStore.delete()` / `deleteAll()` 至今**零生产调用**，旧附件从未清理 | 模块 Phase 7b / 阶段 Phase 2 |
| 🔜 | 备份 / CSV 导入对转账配对的往返验证（`transferImbalanced` 目前只提示、不阻止） | 阶段 Phase 5 |
| 🔜 | **设置页与全局搜索在 UI 上没有任何入口**：`settings` / `globalsearch` 两条路由已在 `AppNavHost` 注册，但全仓 11 处 `navigate()` 调用（feature 层 5 处：课表 ×3 / 任务 ×2；`AppNavHost` 内 6 处：BottomBar ×1 + 搜索结果跳转 ×4 + 详情页编辑 ×1）**无一处指向这两页**。`Screen.Settings` 仅以 `BottomTab.SETTINGS` 常量形式存在，注释里说的「底部溢出菜单」并未实现。本轮 `Walk` 走查只能在 4 个 Tab 间循环，这两页**无法人工验收** | 本轮真机走查实测 |
| 🔜 | `Screen.EditEvent` / `AddTransaction` / `MonthlyBudget` 三条路由已声明但 `NavHost` 未注册。记账/日历靠各页就地弹层（`AddTransactionSheet` / `BudgetSheet` / `EditEventSheet`）完成闭环，所以**目前不崩**；但 `AppNavHost` 第 118 行会 navigate 到未注册的 `calendar/edit/{id}` —— 一旦全局搜索接入可达，点「事件」命中项将直接抛 `IllegalArgumentException`（潜伏缺陷，非现役崩溃） | 本轮导航核对 |
| 🔜 | 记账页顶部**账户切换快捷入口**（当前只能进设置页换） | 阶段 Phase 3 |
| 🔜 | 图片 EXIF 旋转处理（附件与头像均未做） | 阶段 Phase 2 |
| 🔜 | **设置页「清除数据」入口**：旧 README 列为 Phase 6 计划项，本轮已落地（设置页底部红色 Card + 备份建议 + 长按 3 秒确认 + 完成反馈） | 阶段 Phase 6 ✅ |
| ⏳ | `feature/phase-14-ui-refresh` → `Develop` 回合合并（Phase 14 UI 收口后） | Phase 14 |

---

## 🚀 快速开始

### 前置环境

- **Android Studio** Ladybug (2024.2) 或更新（AGP 8.7.3 要求）
- **JDK 17**
- **Android SDK** 34 + Build-Tools 34.0.0
- Gradle **无需**自行安装：仓库已带 wrapper（Gradle 9.5.0，走腾讯镜像）

### 第一次打开

1. 复制 `local.properties.template` → `local.properties`，填 `sdk.dir`
2. 用 Android Studio 打开本目录，等待 Gradle Sync（首次会下载较多依赖）
3. 连接 Android 8.0+ 设备或启动模拟器，点击 ▶ Run

> ⚠️ `sdk.dir` 路径若含空格或单引号（例如 `JetBrain's`），**别把它手敲进 cmd / 嵌套引号命令**，
> 单引号会截断命令导致静默失败。用 Android Studio，或走下方 `scripts/local.ps1`（已按双引号安全处理）。

### 用 PowerShell 构建并浏览界面

仓库自带 `scripts/local.ps1`，把「构建 → 安装 → 冷启动 → 导出界面控件树 → 逐屏走查」收进一条命令：

```powershell
cd <本目录>
.\scripts\local.ps1            # All：构建 + 安装 + 启动 + 导 UI + 逐屏走查四个 Tab
.\scripts\local.ps1 Build      # 只 assembleDebug
.\scripts\local.ps1 Run        # 只安装并冷启动（跳过构建）
.\scripts\local.ps1 Ui         # 导出当前屏可见文字与控件边界
.\scripts\local.ps1 Walk       # 依次点「课表 / 任务 / 记账 / 日历」，逐屏导文字并确认进程存活
.\scripts\local.ps1 Tap -Label 记账   # 按文字 / content-desc 定位并点击单个控件
.\scripts\local.ps1 Shot       # 截图到 artifacts/screen.png
.\scripts\local.ps1 Log        # 抓本进程告警及以上日志（崩溃时定位根因）
.\scripts\local.ps1 Test       # JVM 单测
.\scripts\local.ps1 Device    # 列设备与 AVD
```

脚本要点：
- 只走本机 Gradle + adb，不需要网络；产物落在 `artifacts/`（已 gitignore）。
- `Walk` 会在切屏后**重试 dump**：Compose 页面切换动画期间 `uiautomator` 会返回 `null root node`，
  此时设备上残留上一屏旧 XML，不重试就会读到脏数据。
- 任一跳崩的屏会被 `Walk` 当场抓出（进程消失即报错 + 自动贴日志）。
- 文件需以 **UTF-8 with BOM** 保存，否则 Windows PowerShell 5.1 按 GBK 解码，
  中文字符串会破坏引号配对导致解析失败。

### 模拟器选型建议（先看这一节再开 AVD）

**结论：当前 SDK 里只有 `aosp_atd` 这一个镜像，用它跑任何 App 都是黑屏——**
**这不是 App 代码的锅，是镜像本身的合成子系统硬伤。** 要看真实 UI 必须用 `google_apis` 镜像或真机。

本机实测过两台 AVD（均同镜像 `system-images\android-34\aosp_atd\x86_64`）：

| AVD | `hw.gpu.enabled` | 分辨率 | 启动模式 | `screencap` 像素 | `Displayed` | `Total frames` | 能否看见界面 |
|---|---|---|---|---|---|---|---|
| `pm_api34` | `no` | 320×640 | 无窗口 | 全黑 `(0,0,0,255)` × 2.5M | ✅ | 0 | ❌ |
| `pm_ui34` | `yes / host` | 1080×2340 | **独立窗口** | **全黑 `(0,0,0,255)` × 2.5M** | ✅ +6.7s | **0** | ❌ |

`pm_ui34` 已把 GPU 显式打开、分辨率拉到 Pixel 5、独立窗口（`Created extended window`），
连 `-gpu angle_indirect` 都试过（被新版 emulator 直接拒绝回退 `auto`）——
**所有路径都跑出 2,527,200 个像素全是 `(0,0,0,255)`**。

证据归档在 `artifacts/`：

- `probe_system_pm_ui34.png` —— 开机后 launcher 截图
- `probe_app_pm_ui34.png` —— App 启动后截图（`Displayed` +6.7s，进程在跑）

`dumpsys gfxinfo` 同时给出更深的信号：

```
Total frames rendered: 0
Pipeline=Skia (OpenGL)
View hierarchy: 6 views, 10.71 kB of render nodes
PROFILEDATA: IssueDrawCommandsStart=0  SwapBuffers=0  FrameCompleted=0
              DequeueBufferDuration=360288004566220878  ← 卡了约 100 小时
```

**翻译**：App 视图树（6 个 View / 10.71 kB RenderNode）建出来了，SurfaceFlinger 也给分配了层，
但**缓冲区分配本身卡死**，GPU 合成路径根本没拿到可写的 surface → 全黑。

**想看 UI 怎么办**（二选一）：

1. **加装 `google_apis` 镜像**（推荐）：

   ```bash
   # SDK 在 D:\Software\Code\JetBrain's\Android\Sdk
   # 路径里的单引号会让 cmd 截断命令，PowerShell 下安全
   & "D:\Software\Code\JetBrain's\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" `
     "system-images;android-34;google_apis;x86_64"
   ```

   装完后在 AS → Device Manager → Create Device → 选 **Pixel 5** → 系统镜像选 **API 34 Google APIs**。
   **务必勾选 Graphics = Hardware - GLES 2.0**，勾 Software 等于又退化。

2. **用真机**：USB 连接 + 开发者模式 + `adb devices` 看见设备即可。

`aosp_atd` 跑得通的是 JVM 单测与崩溃检测（ComposeView 真的建出来了，View 节点真的写进了控件树，
进程没有崩）。**只适合自动化，不适合人工验收 UI。**

> AVD 属本机环境，**不在版本库里**。

### 首启白屏：App 代码层 vs 镜像层（两件事，别混）

「白屏」有**两层独立含义**，排查时务必先分清是哪一层：

| 层 | 现象 | 证据 | 修复状态 |
|---|---|---|---|
| **App 代码层** | 权限弹窗抢在首帧之前拉起 `MainActivity` 进 paused，`Displayed` 永不出现 | `NotificationPermissionGate` 原 commit（`launcher.launch` 直接放在 `LaunchedEffect` 体里） | ✅ **已修复**（awaitFirstDraw + withTimeoutOrNull，commit 见 `git log`） |
| **镜像层（aosp_atd）** | 任何 App 都是全屏黑屏，与代码无关 | `screencap` 100% `(0,0,0,255)`、`DequeueBuffer` 卡死、`Total frames=0` | ❌ **必须换 `google_apis` 镜像或真机** |

#### 代码层修复细节（已生效）

`NotificationPermissionGate` 原先在 `LaunchedEffect` 里**直接** `launcher.launch(POST_NOTIFICATIONS)`。
系统权限 Activity 会抢在首帧提交之前把 `MainActivity` 切走，WindowManager 永远收不到 `Displayed`，
屏幕只剩 `Surface` 的纯白底 —— 直到用户点掉权限框。

防线是 `awaitFirstDraw(view)`：注册 `ViewTreeObserver.OnDrawListener`，等首帧（或 2s 超时兜底）之后
再发起权限请求。**这里的首帧顺序是硬约束，改动该 gate 时不要把 `launcher.launch` 挪回 `awaitFirstDraw` 之前。**
同一处还把权限结果等待包成 `withTimeoutOrNull`，回调丢失时保持「未询问」，下次启动重试。

在 **`google_apis` 镜像或真机**上验证修复后行为：

```powershell
.\scripts\local.ps1 Install
adb logcat -d | Select-String 'Displayed com.example|action.REQUEST_PERMISSIONS'
# 期望：MainActivity 的 Displayed 行存在，且时间戳早于 REQUEST_PERMISSIONS
```

#### 镜像层补救（必须做，否则永远黑屏）

详见上一节「模拟器选型建议」的 google_apis 安装步骤。**这是当前 AS 里跑不出来界面的真正原因，**
**跟 awaitFirstDraw 修复无关。** 装了 google_apis 之后等代码层修复就立刻生效，
不需要再改一行业务代码。

### 常用 Gradle 命令

```powershell
./gradlew :app:assembleDebug                   # 出包（会跑完整 Hilt 依赖图，提交前的最低门槛）
./gradlew :app:testDebugUnitTest              # JVM 单测（本轮修复后再次实测全绿）
./gradlew :app:compileDebugAndroidTestKotlin  # 仪表测试源集编译
./gradlew :app:connectedDebugAndroidTest      # 需 AVD：Room 迁移 / 真实文件读写
```

---

## 📂 项目结构

```
ProfileManagement/
├── DEV_LOG.md                           # 项目开发日志（完成项 / 验证 / 踩坑，权威进度来源）
├── scripts/
│   └── local.ps1                        # 本地构建 / 安装 / 启动 / UI 走查 / 抓日志（PowerShell）
├── docs/
│   ├── specs/                           # 需求说明书
│   └── plans/                           # 分阶段计划（phase-8-plan / calendar-7c-plan）
├── gradle/libs.versions.toml            # 版本目录（依赖集中管理）
├── app/
│   ├── build.gradle.kts                 # KSP room.schemaLocation / testOptions / androidTest assets
│   ├── schemas/                         # Room schema 快照 1.json ~ 10.json（迁移校验用）
│   └── src/
│       ├── main/java/com/example/personalassistant/
│       │   ├── MainActivity.kt                  # 单 Activity 入口
│       │   ├── PersonalAssistantApp.kt          # Application + Hilt 根
│       │   ├── navigation/                      # Screen 路由 + BottomTab + AppNavHost
│       │   ├── core/
│       │   │   ├── database/                    # AppDatabase + Converters + Migration_*
│       │   │   ├── di/                          # CoreModule（DB / DataStore / Dispatcher）
│       │   │   ├── backup/                      # 备份：model / serializer / validator / exporter / importer / scheduler
│       │   │   ├── storage/                     # ImageEncoder（附件与头像共用的编码/删除助手）
│       │   │   ├── holiday/                     # 节假日库 + 感知策略 + 调度
│       │   │   ├── lunar/                       # 农历转换（月视图角标）
│       │   │   ├── notification/                # 通知渠道 + 权限
│       │   │   ├── ui/theme/                    # Color / Type / Shape / Theme
│       │   │   ├── coroutine/                   # Dispatcher 提供
│       │   │   └── utils/                       # DateUtils / CurrencyFormatter / NotificationHelper
│       │   ├── feature/                         # 每个模块内部 data / domain / presentation 三层
│       │   │   ├── timetable/                   # 课表（含编辑、冲突检测）
│       │   │   ├── task/                        # 任务（四象限 / 标签 / 排序 / 重复 / 提醒）
│       │   │   ├── calendar/                    # 日历（月周日视图 / 事件分类 / RRULE / 农历）
│       │   │   ├── finance/                     # 记账（流水 / 分类 / 预算 / 零钱池 / 转账 / 报表 / 导入导出）
│       │   │   ├── profile/                     # 多账户 + 头像（AvatarStorage / SetAccountAvatarUseCase）
│       │   │   ├── globalsearch/                # 全局搜索
│       │   │   └── settings/                    # 设置 + 主题（theme/ 子模块）
│       │   └── service/reminder/                # ReminderScheduler / Receiver / BootReceiver
│       ├── test/                         # JVM 单测（Fake 优先，无 Mockito）
│       ├── androidTest/                  # 仪表测试（Room 迁移 / 真实文件 IO）
│       └── res/                          # 主题、字符串、图标、备份规则
```

---

## 🗃 数据库 Schema（Room v14，15 张表）

| 表 | 说明 | 引入版本 |
|----|------|---------|
| `courses` | 课程（dayOfWeek、startTime/endTime 当天分钟数、weeks） | v1 |
| `semesters` | 学期（name unique、startDate、endDate、isCurrent） | v1 |
| `tasks` | 任务 = 清单项（**v15 起两套模型合并**）：priority、category、repeatType/repeatDays、reminderTime、manualOrder、holidayStrategy + **listId / parentTaskId / amount / startAt / endAt / completedAt / isPinned** | v1 → v6 → **v15** |
| `task_lists` | 清单（nameNormalized、displayName、colorHex、iconKey、sortOrder、isSystem、isArchived）；内置「收集箱」id = 1 | **v15**（Phase 16） |
| `tags` / `task_tag_cross_ref` | 标签与任务↔标签关联（v15 起清单项也走这张表） | v5（Phase 6c） |
| `events` | 日历事件（date、startTime、isAllDay、reminder、categoryId、rrule） | v1 → v8 |
| `event_categories` | 事件分类（name、color、iconKey、orderIndex） | v7（Phase 8 Sub-step 9） |
| `transactions` | 记账流水（amount、type、categoryId、date、attachmentUri、accountId、**transferAccountId / transferPairId**） | v1 → **v10** |
| `categories` | 记账分类（含内置「转账」分类 `type = 2`，不进选择器） | v1（v10 加内置行） |
| `monthly_budgets` | 月预算（yearMonth unique、totalAmount、carryOverEnabled） | v1 |
| `daily_pools` | 每日零钱池（date unique、remainingAmount、carryOver） | v1 |
| `holidays` | 节假日（2025-2026 预置） | v6（Phase 8 Sub-step 7） |
| `user_profiles` | 账户（name、avatarUri、**initialBalance**；当前账户标记走 DataStore，不在表里） | v9 → **v10** |

> **v15 起 `sub_tasks` / `sub_task_tag_cross_ref` 已不存在** —— 清单项并入 `tasks`，
> 层级改用 `tasks.parentTaskId` 自引用表达。

### 版本演进关键节点

| 迁移 | 内容（据 `app/schemas/<n>.json` 快照实测） |
|---|---|
| v3 | `tasks.repeatDays`（按星期重复的具体日） |
| v4 | `tasks.manualOrder`（拖拽手动排序） |
| v5 | 标签体系：`tags` + `task_tag_cross_ref` |
| v6 | `holidays` 表 + `tasks.holidayStrategy`（节假日感知策略） |
| v7 | `event_categories` + `events.categoryId` |
| v8 | `events.rrule`（RRULE 重复事件） |
| v9 | `user_profiles` + `transactions.accountId`（记账域按账户隔离，**单列 FK 升复合 FK 需重建表**） |
| v10 | `transactions.transferAccountId` / `transferPairId` + `user_profiles.initialBalance` + `index_transactions_accountId_type`（纯增量 ADD COLUMN / 索引 / 幂等 INSERT…SELECT） |
| v11 | `student_profile` 补 `gpaTarget` / `resumeText` 的 DEFAULT，`gpaRuleJson` 落库 |
| v12 | `student_profile` **重建表**：`gpaRuleJson` 由 NOT NULL 变 nullable + 带 DEFAULT（`ADD COLUMN` 改不了已有列的 NOT NULL / DEFAULT） |
| v13 | `sub_tasks` 加 `category` / `dueDate` / `priority` / `amount` / `parentSubTaskId` + 新建 `sub_task_tag_cross_ref`（纯增量 ADD COLUMN / 建表 / 索引） |
| v14 | 纯性能索引补全，**不动表结构 / 列 / 数据 / 外键**：`transactions` 加 `transferPairId` 与 `(accountId, categoryId)` 索引；`tasks` 加 `(isCompleted, priority, dueDate)` 索引；`sub_tasks` 加 `category` 索引 |
| v15 | **清单模型合并**（Phase 16）：新建 `task_lists`（内置收集箱 id=1）+ `tasks` 加 7 列（`listId` / `parentTaskId` / `amount` / `startAt` / `endAt` / `completedAt` / `isPinned`）+ **`sub_tasks` 整表并入 `tasks`**（恒定 id 偏移搬迁）、`sub_task_tag_cross_ref` 并入 `task_tag_cross_ref` |

> ⚠️ `DEV_LOG.md` 早期段落里的部分字段名与快照有出入，**精确列名以 `app/schemas/` 为准**：
> 快照显示 `tasks.priority` / `tasks.category` 在 **v1 就已存在**（日志写的是「v2→v3 新增」）；
> 拖拽排序列实名为 `manualOrder`（日志写 `orderIndex`）；`event_categories` 与 `events.categoryId` 落在 **v7**，
> v8 加的是 `events.rrule`（日志写「Sub-step 9 的 MIGRATION_9_10 做 category→category_id」——该迁移在代码里并不存在）。
> 这条差异已记入下方「文档与台账的可信度边界」。

### 两条必须守住的事务铁律

1. **零钱池**（说明书 6.7）：`transactions` 与 `daily_pools` 的写入必须在同一 Room 事务中，
   否则会出现「记账了但零钱池没扣」或并发丢更新。
   参考 `FinanceDao.recordExpense / recordIncome / deleteTransactionAndRollbackPool`
2. **转账配对**：一笔转账是**两行**（转出 + 转入，共享 `transferPairId`），必须 `@Transaction` 同生同灭；
   删除走 `pairId` 一次删两行，且**禁止原地编辑**、禁止走普通 `deleteTransaction`（DAO + Repository 两层都拦）。

> ⚠️ schema 变更流程：改 Entity → `APP_DATABASE_VERSION + 1` → 写 Migration 并注册 → 同步 `BackupFormatVersion.DB_VERSION`。
> 版本号常量是**唯一真相源**（`@Database` 注解与交叉引用测试都读它），别改回字面量。
> 已发布的 Migration 不得修改；`transferAccountId` 等新列名先在代码与 `app/schemas/` 里查证再编号，避免抢号。

---

## 🎨 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| 编译 | KSP | 2.0.21-1.0.28 |
| 构建 | AGP | 8.7.3 |
| Gradle | Gradle（wrapper） | 9.5.0 |
| UI | Jetpack Compose (BOM) | 2024.02.02 |
| 主题 | Material 3 + Material Components | 1.11.0 |
| 架构 | MVVM + Clean Architecture（data / domain / presentation） | — |
| 导航 | Navigation Compose | 2.7.7 |
| 数据库 | Room | 2.6.1 |
| DI | Hilt | 2.51.1 |
| 偏好 | DataStore Preferences | 1.0.0 |
| 后台 | WorkManager（+ AlarmManager 精确闹钟） | 2.9.0 |
| 图表 | MPAndroidChart | v3.1.0 |
| 图片 | Coil Compose | 2.7.0 |
| 农历 | cn.6tail:lunar | 1.7.5 |
| 序列化 | kotlinx-serialization | 1.6.2 |
| 测试 | JUnit4 + kotlinx-coroutines-test + Room-testing + Espresso | — |
| JDK | JDK | 17 |

---

## 🔒 离线特性

- ❌ 不申请 `INTERNET` / `ACCESS_NETWORK_STATE`（Manifest 内确认无网络权限）
- ✅ `POST_NOTIFICATIONS`：课程 / 任务 / 事件提醒（Android 13+ 运行时申请，已有引导 UI）
- ✅ `RECEIVE_BOOT_COMPLETED`：开机重设 WorkManager 调度
- ✅ `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM`：精确提醒
- ✅ 备份走 Storage Access Framework 导出到本地，附件 / 头像存 `filesDir`（不申请存储权限）
- ✅ PhotoPicker（`PickVisualMedia`）选图，立即拷为自有 `file://`，**不需要**持久化 URI 授权

---

## 📝 开发规范

- **包结构**：按 feature 模块分包，模块内部 `data` / `domain` / `presentation` 三层；跨模块复用下沉到 `core/`
- **命名**：Entity 统一后缀（`CourseEntity`）、DAO 接口（`CourseDao`）、Repository 实现（`XxxRepositoryImpl`）、业务编排（`XxxUseCase`）
- **协程**：IO 操作一律挂起函数；DB 自动在 IO 线程执行
- **Flow**：DB 查询返回 `Flow`，UI 用 `collectAsStateWithLifecycle()`；`combine` 有 5 流上限，新读需求另开 `StateFlow` 而不是硬塞
- **ViewModel 的 `init{}` 必须写在它引用的属性之后**：`viewModelScope` 是 `Dispatchers.Main.immediate`，
  构造期 `launch` 的协程体会在主线程内联立即执行；若被引用的 `StateFlow` 属性按声明顺序尚未初始化，
  读到的就是 `null` → NPE，表现为「一进该 Tab 就闪退」（日历屏曾踩过，见 `CalendarViewModel` 注释）
- **时间**：时间字段用 `Long` 毫秒时间戳，课表时间用当天分钟数（0-1439）
- **事务**：涉及多表写入的 UseCase 用 `@Transaction` 包裹
- **测试**：**Fake 优先，不引入 Mockito**；顺序不变量放在「能被少量 fake 覆盖」的 UseCase 切口上；
  仪表测试 `@Test` 方法**必须显式写 `: Unit`**（否则 JUnit4 会静默整类不跑）
- **提交前**：至少 `assembleDebug`（唯一会跑完整 Hilt 依赖图的档位）+ `testDebugUnitTest` 全绿
- **错误可见**：有「合法但被拒绝」分支的操作，必须把拒绝原因显示出来，别用无 `onFailure` 的 `runCatching` 静默吞掉
- **台账**：每完成一次交付走**两步式提交** —— 先功能 commit + push 拿到 hash，再单独提交 `chore(dev_log): … @hash`
  把对应条目转 ✅ 并补「已完成步骤 / 关键变更文件 / 验证结果 / 踩坑笔记」；未 push、未验证不打 ✅

---

## 📚 文档导航

| 文件 | 用途 |
|---|---|
| [`DEV_LOG.md`](DEV_LOG.md) | **权威进度来源**：逐 Phase 的完成项、验证结果、踩坑笔记、下一步入口 |
| [`docs/specs/「昔涟个人助手APP」项目需求说明书.md`](docs/specs/) | 需求与功能约束（含零钱池 6.7 等专项规则） |
| [`docs/plans/`](docs/plans) | 分阶段计划文档（`phase-8-plan.md`、`calendar-7c-plan.md`） |
| [`docs/verified/`](docs/verified) | 真机 / 模拟器验证留档（每份产物对应一次实测） |
| [`docs/manual-verification/`](docs/manual-verification) | 人工目测清单（Phase 10 视觉、Phase 14 视觉回归） |
| `app/schemas/` | Room 各版本 schema 快照，迁移正确性的机械真相 |

---

## 🧭 文档与台账的可信度边界

各文档承担的**真相层级不同**，冲突时按下面的优先级取信：

| 优先级 | 来源 | 可信内容 | 不可信内容 |
|---|---|---|---|
| 1 | 代码 + `app/schemas/` | 表结构、列名、版本号、调用点 | —— |
| 2 | `DEV_LOG.md` 的**状态与验证结论** | 哪些 Phase 完成 / 阻塞、测试真实计数、踩坑 | —— |
| 3 | `DEV_LOG.md` 的**早期段落叙述** | 大致演进顺序 | 具体列名与 schema 版本节点（多处与快照不符，已在上方标注） |
| 4 | `docs/plans/*.md` | 当初想做什么 | 做没做、最后怎么做成的 —— plan 写完即过时 |

- 台账里「`MIGRATION_9_10` 做 events `category` → `category_id`」这类描述，对应迁移在代码中**不存在**；
  该日志段落自己也已把这条列为「版本号记错」的存疑项。
- 台账顶部速览的**活数据**（HEAD / commit 数 / 测试数）会滞后，以 `git log` 与 `testDebugUnitTest` 实测为准。
- 本 README 只描述**已落地**的能力；进行中的阶段 Phase 5 一律标注 🟨，与台账状态保持一致。

---

## 📄 License

MIT
