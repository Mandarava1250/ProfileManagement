# 项目开发日志（DEV_LOG）

> 维护者：昔涟（自动更新）
> 触发规则：每次执行完一个计划（plan）后追加一段
> 作用：记录已完成的步骤、对应的计划章节、关键变更文件、验证结果、踩坑笔记 —— 方便后续记忆与查漏补缺

---

## 📜 命名差异说明（plan 编号 vs 实际编号）

| 描述 | `phase-8-plan.md` 中 | 实际 git commit 中 | 备注 |
|---|---|---|---|
| 分类/重复事件/冲突/农历 | **Sub-step 7（Phase 8g）** | **Sub-step 9** | 实际编号比 plan 多 2，原因是中间还有「Settings 卡片 + HolidayStrategy（plan Sub-step 8）」和「主题模块（plan Sub-step 6）」被插队。**Sub-step 9 = 真正的 8g 内容**，从 commit `535c73dd` 开始。 |
| 主题模块 | Sub-step 6（Phase 8f） | **Sub-step 6** ✅ | 对得上，commit `82361321` |
| 备份恢复 | Sub-step 4（Phase 8d） | **Sub-step 4** ✅ | 对得上，commit `3bd863d7` |
| 全局搜索 | Sub-step 5（Phase 8e） | **Sub-step 3** | 编号前移，对应 commit `44fb4130` |

> **看日志时以 git commit 编号为准**；需要查 plan 内容时按上表反查。

---

## ✅ 已完成列表（按时间倒序，最近的在上）

### Step 5 — Phase 8 Sub-step 9 Step 5：月视图农历角标

**Commit**：`47ccb23e809aa49f1639561b02974b31cd78961c`（`feat(calendar): 月视图农历角标 (Phase 8 Sub-step 9 Step 5)`）
**日期**：2026-09-09
**分支**：`feature/phase-8-reminder`
**对应 Plan**：`phase-8-plan.md` Sub-step 7（Phase 8g）末尾"农历显示"段落

**完成项**：
- 新增 `core/lunar/LunarDate.kt` —— 纯数据类，封装农历字段
- 新增 `core/lunar/LunarCalendar.kt` interface —— `toLunarDate` / `shortLabel` / `displayLabel`
- 新增 `core/lunar/DefaultLunarCalendar.kt` —— 基于 `cn.6tail:lunar:1.7.5`（**走 Maven Central**，不走 JitPack）
- 新增 `feature/calendar/di/LunarModule.kt` —— Hilt `@Binds` 绑定
- 修改 `CalendarViewModel.kt` —— 注入 `LunarCalendar`，`buildUiState` 时生成 42 格角标
- 修改 `CalendarUiState.kt` —— 新增 `monthLunarLabels: Map<Long, LunarCellLabel>`
- 修改 `MonthDayCell.kt` —— 右下角加 9sp 农历 Text（节日 tertiary / 普通 onSurfaceVariant 0.6）
- 修改 `MonthCalendarGrid.kt` / `CalendarScreen.kt` —— 接收并分发 `lunarLabels`
- 新增测试 `LunarCalendarTest`（9 个）

**依赖变更**：
- `gradle/libs.versions.toml` —— 新增 `lunar = "1.7.5"` + `cn.6tail:lunar:1.7.5`
- `app/build.gradle.kts` —— 引入 `lunar` 依赖 + `mavenCentral()`
- `.gitignore` —— 新增 `/nul`（Windows phantom 文件，git checkout 偶发生成）

**踩坑笔记**：
1. `lunar.isLeap()` 不存在 → 改用 `monthInChinese.startsWith("闰")` 推断闰月
2. `monthInChinese` 不带「月」字 → domain 层统一补全成「正月/冬月/腊月」
3. 节日名不一致 —— 「春节」无「节」字，「中秋节/国庆节/元旦节」有「节」字，UI 原样展示不做硬编码
4. Windows `nul` phantom 文件 —— 是 Windows 设备名（不是普通文件），`del nul` 删不掉，git 提交会带进来

**验证**：
- ✅ `assembleDebug` BUILD SUCCESSFUL（APK 出包成功）
- ✅ `testDebugUnitTest` 全绿
- ✅ 新增 9 个 `LunarCalendarTest` 用例（春节/中秋/国庆/元旦/春节次日/普通日期/月首/timestamp 边界/闰月推断）

**当时给伙伴的汇报**：
> 月视图右下角就会出现「初一」「春节」「国庆节」这些小字啦～

---

### Step 4.2 — Phase 8 Sub-step 9 Step 4.2：event conflict detection UI

**Commit**：`098aae64e7ca7d819bedd1e87767cb9f61b171a4`
**日期**：2026-09-08
**分支**：`feature/phase-8-reminder`
**对应 Plan**：`phase-8-plan.md` Sub-step 7（Phase 8g）"DayDetailSheet 显示冲突标记"

**完成项**：
- 事件冲突 UI 在 `DayDetailSheet` 中显示冲突红条
- 事件 vs 课程的冲突检测 UseCase（基于 `WeekCalculator` + `EventTimeMapper`）

---

### Step 4.1 — Phase 8 Sub-step 9 Step 4.1：conflict detection domain

**Commit**：`a16f388622180843c917f959a1a693dbc71d873e`
**日期**：2026-09-08
**对应 Plan**：同 Step 4.2

**完成项**：
- `WeekCalculator`、`EventTimeMapper`、`Live/Saved ConflictUseCase`
- Domain 层就绪 → UI 层才接得上

---

### Step 3 — Phase 8 Sub-step 9 Step 3：RRULE 重复事件

**Commit**：`36b40dacda0bde0544eeaa565735f164cc6c28a1`
**日期**：2026-09-08
**对应 Plan**：`phase-8-plan.md` Sub-step 7（Phase 8g）"重复事件规则"

**完成项**：
- `RecurrenceRule`（FREQ=DAILY/WEEKLY/MONTHLY + INTERVAL + UNTIL/COUNT）
- `RecurrenceExpander` —— 按规则展开为具体日期
- 单元测试（多个）

---

### Step 2 — Phase 8 Sub-step 9 Step 2：event categories UI

**Commit**：`baa8707751802b1e0b2382d14ce1ec4f41ca1a76`
**日期**：2026-09-08
**对应 Plan**：`phase-8-plan.md` Sub-step 7（Phase 8g）"EventCategoryEntity + CRUD"

**完成项**：
- `CategorySelectorRow`（事件表单里选分类的横向 chips）
- `CategoryManagementDialog`（分类管理对话框：增/删/改/排序）

---

### Step 1 — Phase 8 Sub-step 9 Step 1：event categories domain/data

**Commit**：`535c73dd8c9ca38d7dfade502c3f6f9f3e798859`
**日期**：2026-09-08
**对应 Plan**：`phase-8-plan.md` Sub-step 7（Phase 8g）`EventCategoryEntity` + 数据库迁移 v9→v10

**完成项**：
- `EventCategoryEntity`（id / name / color / iconKey / orderIndex）
- `EventCategoryRepository`（domain 接口 + data 实现）
- 数据库迁移 `MIGRATION_9_10`：events 表 `category` (String) → `category_id` (Long 外键)
- 迁移 roundtrip 测试
- CRUD UseCase 集

---

### Sub-step 8 — Settings 卡片 + Task 表单 HolidayStrategy

**Commit**：`07086dd4963dc3a2cddeaf592c362bd82bd1b136`
**日期**：2026-09-08
**对应 Plan**：`phase-8-plan.md` Sub-step 8（Phase 8h）合并段（注：实际编号就是 Sub-step 8）

**完成项**：
- Settings 主屏布局（卡片式）
- Task 表单新增 `HolidayStrategy` 选项（"提前 / 当天 / 跳过"）

---

### Sub-step 7 — holiday awareness + smart reminder

**Commit**：`32fc6b518b60a28d0dcf970e5b72fbc65bb4e283`
**日期**：2026-09-08
**对应 Plan**：`phase-8-plan.md` Sub-step 4（Phase 8d）"节假日显示"

**完成项**：
- `HolidayEntity` 落库 + 2025-2026 国定节假日预置
- Task 表单 awareness —— 新建任务时感知其 dueDate 是否节假日
- 智能提醒（提前/当天策略）

---

### Sub-step 6 — 主题模块

**Commit**：`82361321c3166ae052d677d48f81ed9924f1d9c4`
**日期**：2026-09-08
**对应 Plan**：`phase-8-plan.md` Sub-step 6（Phase 8f）"主题切换"

**完成项**：
- `ThemeMode` enum（SYSTEM / LIGHT / DARK）
- `ThemeRepository` / `ThemeUseCase` / `ThemeViewModel`
- dynamicColor（Android 12+）
- 测试

---

### Sub-step 4 — 备份与恢复

**Commit**：`3bd863d74faa8349b417c09b21bd13d389a205e6`
**日期**：2026-09-07
**对应 Plan**：`phase-8-plan.md` Sub-step 4（Phase 8d）末尾"备份"—— 实际 plan 命名为「Sub-step 4」对应 "Phase 8d" 内容

**完成项**：
- `BackupManager` —— 全表序列化
- `BackupExporter`（SAF 分享）
- `BackupImporter`（SAF 选文件 → 校验 → 事务化导入）

---

### Sub-step 3 — 全局搜索

**Commit**：`44fb4130a6ca4bb309d23093b63a663a293f318e`
**日期**：2026-09-07
**对应 Plan**：`phase-8-plan.md` Sub-step 5（Phase 8e）"全局搜索"

**完成项**：
- `GlobalSearchScreen` / `GlobalSearchViewModel` / `GlobalSearchUseCase`
- 4 模块 combine + 模糊匹配
- 搜索结果分组 + 关键字高亮

---

## 🔧 配置杂项

### `.gitattributes` + autocrlf

**Commit**：`0ab0b6b1a44d0b5ad27084f510b667d8296b2908`
**目的**：避免 Windows ↔ Linux 提交时 CRLF/LF 噪声污染 diff。

---

## 📊 累计进度（截至 2026-09-09 00:55）

- **测试总数**：约 519 用例（基线 510 + 9 个新增 LunarCalendarTest）
- **数据库版本**：v10（已含 event_categories 表 + events.category_id 外键）
- **Phase 8 剩余 Sub-step**：8a 真实提醒调度（未做）、8b 5 档优先级 + Eisenhower（未做）、8c 附件上传 + 多账户（未做）、8d 主题/节假日/周次范围中的「周次范围」部分（未做）、8e 全局搜索（✅ 已做）、8f 备份恢复（✅ 已做）、8g Sub-step 9 全部 ✅ 已做
- **当前分支**：`feature/phase-8-reminder`（sub-step 9 全部完成，工作树干净）

> 注：plan 中 8 个 sub-step 跟实际 git 编号不是一一对应——以 commit 标题里的 "sub-step X" 为准。

---

## 📌 下次继续时的入口

**当前最新 commit**：`47ccb23`（农历显示）
**待办候选**：
1. Phase 8a 真实提醒调度（plan 中第一个未做 sub-step，依赖 WorkManager）
2. Phase 8b 5 档优先级 + Eisenhower 矩阵（DB v5→v6）
3. Phase 8c 附件上传 + 多账户（DB v6→v8）
4. 8h 合并：把 feature/phase-8-reminder 合并回 Develop