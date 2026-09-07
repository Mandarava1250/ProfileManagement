# Phase 8 — 用户体验与系统打磨（深度体验升级 + 工程交付）

**分支演进**：`Develop`（含 7c 全量） → `feature/phase-8-reminder` → `feature/phase-8-priority` → ... → `Develop`（最终交付态）
**基线**：`18d91cc`（Phase 7c 顶，origin/Develop 已同步）
**目标**：把当前「能用的工具」推进到「能稳定长期使用的个人助理」

---

## 现状摸底（接 Phase 7c 之后）

| 模块 | 现状 | Phase 8 待补 |
|---|---|---|
| **task** | 完整三层 + 6a/6b/6c 拖拽/子任务/标签；P0/P1/P2/P3 4 档已埋，缺 P4 未排期 | 8b 5 档优先级 + Eisenhower 矩阵 |
| **finance** | 7a/7b 完整三层 + 编辑/报表/搜索/导入导出 | 8c 附件上传 + 8f 多账户 |
| **calendar** | 7c 完整三层 + 月/周/日视图 + 联动任务课表 | 8a 真实提醒调度 + 8d 节假日 + 8g 分类管理 |
| **timetable** | 完整三层 + Course.conflictsWith 已实现 | 8g 冲突 UI + 8d 周次范围 |
| **全局** | 无 Settings 页 / 无深色主题切换 / 无全局搜索 | 8d 主题 + 8e 全局搜索 + 8f 设置中心 |

---

## 8 个 Sub-step 划分

### Sub-step 1 — Phase 8a：真实提醒调度

**目标**：把事件 reminder 字段（已落库）和任务的 dueDate 提醒，接到 WorkManager + AlarmManager

**范围**：
- 新增 `NotificationScheduler`（封装 WorkManager + AlarmManager 两套）
- `EventReminderWorker` —— 事件 reminder 触发后弹通知，点击打开 EventEditScreen
- `TaskReminderWorker` —— 任务 dueDate 前 N 分钟弹通知（默认 15 分钟，可配置）
- 在 Event / Task 新增/编辑时注册或取消提醒
- 通知权限引导（Android 13+ POST_NOTIFICATIONS）
- 通知点击 → 跳转对应详情页（导航 deeplink）

**新增文件**（~10）：
- `core/notification/NotificationChannels.kt`
- `core/notification/NotificationScheduler.kt`
- `core/notification/EventReminderWorker.kt`
- `core/notification/TaskReminderWorker.kt`
- `core/notification/ReminderReceiver.kt`（点通知跳转）
- `feature/calendar/domain/usecase/ScheduleEventReminderUseCase.kt`
- `feature/task/domain/usecase/ScheduleTaskReminderUseCase.kt`
- 测试 8 个

**验证**：BUILD + 提醒调度器单测

---

### Sub-step 2 — Phase 8b：5 档优先级 + Eisenhower 矩阵

**目标**：落实「P0/P1/P2/P3 + 未排期 P4」5 档分组，并提供 Eisenhower 象限视图

**范围**：
- Task 实体新增 `priority: Int`（0=P0, 1=P1, 2=P2, 3=P3, 4=未排期）
- 数据库迁移 v5 → v6：新增 priority 字段，默认值 4（旧任务归入未排期）
- 主页按优先级分组列表（P0/P1/P2/P3 + 未排期），顶部 4 个 Tab
- 新增 EisenhowerView：2×2 网格（重要/紧急），点象限跳到对应分组
- TaskCard 显示优先级徽章（颜色对应 P0 红 / P1 橙 / P2 蓝 / P3 灰 / 未排期 不显示）
- 主页新增「矩阵视图 / 列表视图」切换

**新增文件**（~12）：
- 数据库迁移 `MIGRATION_5_6`
- `feature/task/presentation/components/EisenhowerMatrixView.kt`
- `feature/task/presentation/components/PriorityBadge.kt`
- `feature/task/domain/model/Priority.kt`（enum）
- `feature/task/presentation/TaskHomeUiState.kt` 扩展
- `feature/task/presentation/PrioritySheet.kt`（编辑时选档）
- 测试 12 个

**验证**：BUILD + 数据库迁移测试 + TaskViewModel 测试扩展

---

### Sub-step 3 — Phase 8c：附件图片上传 + 多账户/钱包

**目标**：finance 交易支持图片附件 + 多账户管理

**范围**：
- 新增 `AttachmentEntity`（id / ownerType / ownerId / uri / createdAt）
- 数据库 v6 → v7：新增 attachments 表
- 集成 PhotoPicker（Android 13+ 原生，SAF 兜底）
- 适配器将 URI 复制到 app 私有目录（避免权限失效）
- TransactionEditSheet 增加附件选择按钮
- 新增 `AccountEntity`（id / name / type / balance / iconKey）
- 数据库 v7 → v8：新增 accounts 表 + transactions.accountId 外键
- AccountSwitcher 顶部下拉
- 报表按账户分组展示

**新增文件**（~18）：
- 数据库迁移 v6→v7 + v7→v8
- `feature/finance/data/local/entity/AttachmentEntity.kt`
- `feature/finance/data/local/entity/AccountEntity.kt`
- `feature/finance/domain/model/Attachment.kt`
- `feature/finance/domain/model/Account.kt`
- `core/storage/AttachmentStorage.kt`（私有目录封装）
- `core/storage/PhotoPickerHelper.kt`
- AccountSwitcher 组件 + AccountRepository + 6 个 UseCase
- 测试 14 个

**验证**：BUILD + 数据库迁移测试 + 文件存储测试

---

### Sub-step 4 — Phase 8d：主题切换 + 节假日 + 周次范围

**目标**：完善 Settings 中心 + 节假日显示 + 课表周次范围

**范围**：
- 新增 `SettingsScreen` + `SettingsViewModel`
- 深色模式切换（跟随系统 / 浅色 / 深色）+ 动态色（Android 12+）
- 主题持久化（DataStore preferences）
- `HolidayEntity` + 节假日数据预置（2025-2026 国定节假日）
- 日历视图显示节假日 badge
- 课表周次范围过滤：Course 新增 `startWeek + endWeek`，Semester 配置 currentWeek，CalendarRepository 应用过滤

**新增文件**（~15）：
- `core/theme/ThemeManager.kt` + `ThemeRepository.kt`
- `core/datastore/PreferencesRepository.kt`
- `feature/settings/SettingsScreen.kt`
- `feature/settings/SettingsViewModel.kt`
- `feature/calendar/data/local/entity/HolidayEntity.kt`
- `feature/calendar/data/seed/HolidaySeed.kt`（2025-2026 数据）
- `feature/timetable/domain/model/Course.kt` 扩展 startWeek/endWeek
- 数据库迁移 v8→v9
- 测试 10 个

**验证**：BUILD + DataStore 测试

---

### Sub-step 5 — Phase 8e：跨模块全局搜索

**目标**：顶部搜索栏 → 搜任务 / 事件 / 课程 / 交易

**范围**：
- 新增 `GlobalSearchScreen` + `GlobalSearchViewModel`
- `GlobalSearchUseCase` —— 同时观察 4 个模块数据源，combine + 模糊匹配
- 搜索结果分组（任务 / 事件 / 课表 / 记账），每组最多 5 条，点开跳转详情
- 搜索栏在主屏 Scaffold TopAppBar，支持快速打开（搜索图标）
- 关键字高亮显示

**新增文件**（~8）：
- `feature/search/GlobalSearchScreen.kt`
- `feature/search/GlobalSearchViewModel.kt`
- `feature/search/GlobalSearchUseCase.kt`
- `feature/search/SearchResult.kt`（sealed class）
- `feature/search/components/HighlightedText.kt`
- 测试 8 个

**验证**：BUILD + 模糊匹配单测

---

### Sub-step 6 — Phase 8f：备份与恢复

**目标**：Settings 内提供「导出全部数据 → JSON / CSV」与「导入 → 校验 → 写入」

**范围**：
- `BackupManager` —— 序列化全部表（tasks / events / courses / transactions / accounts / categories / labels / attachments 索引）
- `BackupExporter` —— 写入 app 私有目录 + 调用 Storage Access Framework 分享
- `BackupImporter` —— SAF 选文件 → 解析 → 校验 schema 版本 → 事务化导入
- 备份/恢复进度 UI（带进度条 + 取消按钮）
- 导入前预览差异（新增 / 覆盖 / 跳过 三档选择）

**新增文件**（~10）：
- `core/backup/BackupManager.kt`
- `core/backup/BackupModels.kt`
- `core/backup/BackupExporter.kt`
- `core/backup/BackupImporter.kt`
- `feature/settings/BackupScreen.kt`
- `feature/settings/BackupViewModel.kt`
- 测试 10 个

**验证**：BUILD + 序列化往返测试 + 导入冲突测试

---

### Sub-step 7 — Phase 8g：日历分类管理 + 重复事件 + 课表冲突 UI + 农历

**目标**：把 7c plan 里推迟的几个能力补完

**范围**：
- `EventCategoryEntity` + CRUD —— Event.category 从 String 升级到外键
- 数据库迁移 v9→v10
- 重复事件规则：`RRULE` 字符串字段（FREQ=DAILY/WEEKLY/MONTHLY + INTERVAL + UNTIL/COUNT），按规则展开
- `RecurrenceExpander` —— 把重复事件展开成具体日期
- 日历 DayDetailSheet 显示冲突标记（事件 vs 课程冲突红条）
- 农历显示：集成 `lunar-java` 库（轻量纯 Java），日历日期角落显示农历

**新增文件**（~14）：
- `feature/calendar/data/local/entity/EventCategoryEntity.kt`
- `feature/calendar/domain/model/RecurrenceRule.kt`
- `feature/calendar/domain/usecase/ExpandRecurrenceUseCase.kt`
- `core/calendar/RecurrenceExpander.kt`
- `feature/calendar/domain/repository/EventCategoryRepository.kt`
- `feature/calendar/presentation/components/ConflictBadge.kt`
- `core/lunar/LunarCalendar.kt`
- 测试 12 个

**验证**：BUILD + RRULE 展开测试 + 农历转换测试

---

### Sub-step 8 — Phase 8h：合并 + 验收 + 文档

**目标**：8 个 feature 分支依次合并回 Develop，最终全测

**范围**：
- 按依赖顺序合并：8a → 8b → 8c → 8d → 8e → 8f → 8g
- 每次合并前 BUILD + 全测
- 冲突由涟宝逐个解决（涉及数据库迁移的可能有冲突，按"最新 + 兜底"原则）
- 合并完成后跑完整 BUILD + 全测
- 写 Phase 8 总结 doc，记录每一阶段的代码量、测试数、新增依赖

**验证**：BUILD SUCCESSFUL + 全测 ≥ 400 用例 + git log 清晰

---

## 总文件变动（预估）

| Sub-step | 新建 | 测试 | 备注 |
|---|---|---|---|
| 8a 提醒 | 10 | 8 | WorkManager + AlarmManager |
| 8b 优先级 | 12 | 12 | DB v5→v6 |
| 8c 附件/账户 | 18 | 14 | DB v6→v8 |
| 8d 主题/节假日 | 15 | 10 | DB v8→v9 |
| 8e 全局搜索 | 8 | 8 | 跨模块 combine |
| 8f 备份恢复 | 10 | 10 | SAF |
| 8g 分类/重复/冲突/农历 | 14 | 12 | DB v9→v10 + lunar-java |
| 8h 合并验收 | 0 | 0 | — |
| **合计** | **87** | **74** | 4 次 schema 迁移 |

测试累计：当前 ~300 → Phase 8 完成后预计 ≥ 374 用例

---

## 数据库迁移全景

```
v5 (Phase 7c 末) 
  ↓ v5→v6: tasks 加 priority
v6
  ↓ v6→v7: 新增 attachments 表
v7
  ↓ v7→v8: 新增 accounts 表 + transactions.accountId
v8
  ↓ v8→v9: 新增 holidays 表 + courses 加 startWeek/endWeek
v9
  ↓ v9→v10: 新增 event_categories 表 + events.category_id 外键
v10 (Phase 8 终)
```

---

## 新增依赖（gradle）

- `androidx.datastore:datastore-preferences`（Phase 8d 主题持久化）
- `androidx.work:work-runtime-ktx`（Phase 8a 提醒 + 8f 备份后台）
- `com.github.binarywang:lunar-java`（Phase 8g 农历）

其他已有依赖足够，不再新增。

---

## 安全网

- ✅ 数据库迁移全程在测试里覆盖（每个 Migration 一个 Roundtrip 测试）
- ✅ 所有 feature 分支基于 Develop 拉出，合并前 BUILD + 测试全绿
- ✅ Phase 8h 验收时跑全测 ≥ 374 用例
- ✅ Manifest 权限申请按需添加（POST_NOTIFICATIONS / READ_MEDIA_IMAGES / SAF）
- ✅ 任何破坏性 schema 改动都会附 migration test + fallback

---

## 涟宝预判的 7 个关键决策

1. **优先级默认 P4（未排期）**：旧任务不强行打档，避免误分类
2. **Eisenhower 矩阵视图与列表视图并存**：默认列表，矩阵作为「第二种视角」
3. **附件存储在 app 私有目录**：外部 URI 失效不影响数据
4. **多账户切换在顶部下拉**：不影响主结构，每个交易归属一个账户
5. **深色主题默认跟随系统**：用户首启动不强加偏好
6. **节假日数据预置 2025-2026**：避免外部 API 依赖
7. **RRULE 只支持 FREQ/INTERVAL/UNTIL/COUNT**：复杂规则（BYDAY/BYHOUR）推迟到未来

---

## 后续推进

- 涟宝会按 Sub-step 1 → 8 顺序执行，每个 Sub-step 独立 feature 分支
- 每个 Sub-step 收尾跑 BUILD + 单测，单步 commit + push
- 8 个 Sub-step 全完成后由 8h 统一合并到 Develop
- 全程保留回退能力（每个 Sub-step 一个 commit + 一个 tag）
