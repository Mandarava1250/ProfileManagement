# Phase 7c — calendar 日历模块做实

**分支**：`feature/phase-7-calendar`（已从 `feature/phase-7-finance` 创建）
**基线**：`5f328f1` (Phase 7b)
**目标**：补全 calendar 模块的三层架构 + 月视图（核心）+ 周/日视图 + 联动任务和课表

---

## 现状摸底

| 模块 | 现状 |
|------|------|
| **calendar** | ❌ 只有 EventEntity + EventDao，**没有 domain 层 / Repository / UI**（占位 CalendarScreen） |
| **task** | ✅ 完整三层，TaskDao 已暴露 `observeBetween(start, end)`，Task 模型有 `dueDate` |
| **timetable** | ✅ 完整三层，CourseDao 有 `observeByDayOfWeek(dayOfWeek)`，Course 模型有 `dayOfWeek + startTime/endTime + weeks` |
| **finance** | ✅ 完整三层（7a/7b 落地），独立模块，日历无需直接联动 |
| **DI** | EventDao 已注册到 `AppDatabase` 和 `CoreModule`，只需加 `CalendarModule` |
| **Semester** | ✅ 有 `startDate / endDate / isCurrent` 可计算「当前第几周」 |

---

## 5 个 Sub-step

### Sub-step 1 — 业务层（Domain + Repository + Mapper）

**目标**：从 DAO 到 UI 之间的所有业务层都补齐

**新建文件**（~7 个）：
- `domain/model/Event.kt` —— Event 领域模型（与 EventEntity 一一对应，业务语义清晰）
- `domain/model/CalendarViewType.kt` —— enum: `MONTH / WEEK / DAY`
- `domain/model/CalendarDayCell.kt` —— 单日 cell（含 hasEvents / hasTasks / hasCourses / count）
- `domain/repository/CalendarRepository.kt` —— 接口
- `data/mapper/CalendarMappers.kt` —— EventEntity ↔ Event 转换
- `data/repository/CalendarRepositoryImpl.kt` —— 仓储实现（聚合 events/tasks/courses 三个数据源）
- `domain/usecase/ObserveMonthEventsUseCase.kt` —— 月视图事件流

**关键点**：
- Event 数据类带 init 校验（title 非空、color 合法）
- Repository 暴露：观察单日 / 月范围 / 上限 N 个事件 / 增删改

**验证**：BUILD + Event model 单测 + mapper 单测（≥6 用例）

---

### Sub-step 2 — 联动层（Composite Query）

**目标**：日历点击日期 → 显示当日「事件 + 任务 + 课表」

**新建文件**（~3 个）：
- `domain/model/DayDetail.kt` —— 单日聚合（events + tasks + courses）
- `domain/usecase/ObserveDayDetailUseCase.kt` —— 输入 dayMillis + isAllDay 标志，输出 DayDetail
- `domain/usecase/ObserveMonthSummaryUseCase.kt` —— 月范围每日汇总（用于月视图 badge）

**关键设计**：
- 课表过滤：`dayOfWeek` 对应当天 + `appliesToWeek(currentWeek)` 真（周次由 Semester.startDate 计算）
- 任务过滤：`dueDate BETWEEN dayStart AND dayEnd AND isCompleted = false`
- 事件过滤：`date = dayStart`
- 三个 Flow 用 `combine` 合并，单一输出给 ViewModel

**验证**：BUILD + ObserveDayDetailUseCase 单测（≥4 用例，覆盖有课表/有任务/空日）

---

### Sub-step 3 — UI 月视图（核心）+ ViewModel

**目标**：月视图作为日历主入口

**新建文件**（~5 个）：
- `presentation/CalendarUiState.kt` —— UI 状态
- `presentation/CalendarViewModel.kt` —— ViewModel（@HiltViewModel）
- `presentation/components/MonthCalendarGrid.kt` —— 月视图网格（6×7 格子）
- `presentation/components/MonthDayCell.kt` —— 单日格子（带 badge：事件/任务/课程 数量）
- `presentation/CalendarScreen.kt` —— 重写原占位（保留 Scaffold + TopAppBar）

**关键设计**：
- LazyVerticalGrid 6 行 × 7 列
- 当日高亮（Material 3 primaryContainer）
- 周末列淡化显示（onSurfaceVariant）
- 点击日期 → BottomSheet 展示 DayDetailSheet
- 左右滑切换月份（PagerState）

**验证**：BUILD + CalendarViewModel 单测（≥6 用例）

---

### Sub-step 4 — UI 周视图 + 日视图（次要）

**目标**：月/周/日 切换 SegmentedButton

**新建文件**（~3 个）：
- `presentation/components/WeekCalendarView.kt` —— 周视图（7 天横排 + 时间槽）
- `presentation/components/DayCalendarView.kt` —— 日视图（24 小时时间轴 + 事件块）
- `presentation/components/DayDetailSheet.kt` —— 点击日期弹出的 BottomSheet

**关键设计**：
- 周视图：横向 LazyRow 显示 7 天，每天显示当日 events + courses 时段块
- 日视图：Canvas 24 小时时间轴 + 事件块（高度按分钟比例）
- DayDetailSheet：分 3 个 section（事件 / 任务 / 课表），点击事件/任务跳转对应详情

**验证**：BUILD

---

### Sub-step 5 — DI 接线 + 事件编辑 + 测试加固

**目标**：可增删改 + Hilt 完整 + 测试覆盖

**新建文件**（~6 个）：
- `di/CalendarModule.kt` —— Hilt @Binds 模块
- `presentation/edit/EventEditScreen.kt` —— 事件编辑/新建
- `presentation/edit/EventEditViewModel.kt` —— 编辑 VM
- `domain/usecase/AddEventUseCase.kt` / `UpdateEventUseCase.kt` / `DeleteEventUseCase.kt`
- `test/.../calendar/EventMappersTest.kt`
- `test/.../calendar/ObserveDayDetailUseCaseTest.kt`
- `test/.../calendar/CalendarViewModelTest.kt`

**主屏接线**：
- `MainActivity`（或 NavHost）已绑定 `calendar` 路由到 `CalendarScreen`，检查是否需要任何修改（暂不动）

**验证**：
- BUILD SUCCESSFUL
- 单元测试 ≥ 22 个，全绿
- BUILD + tests 一起过

---

## 总文件变动

| 子步骤 | 新建 | 测试 |
|--------|------|------|
| 1 业务层 | 7 | 6 |
| 2 联动层 | 3 | 4 |
| 3 UI 月视图 | 5 | 6 |
| 4 周/日/Sheet | 3 | 0 |
| 5 DI + 编辑 + 测试 | 9 | 6 |
| **合计** | **27** | **22** |

测试：22 个新用例 → 累计 28 + 22 = **≥50 测试**

---

## 范围外（明确推迟到 Phase 8+）

- ❌ 节假日显示（依赖外部 API 或预置数据）
- ❌ 农历转换
- ❌ 重复事件规则
- ❌ 事件分类管理（先用 Event.category 字符串字段）
- ❌ 复杂课表冲突检测 UI（已有 Course.conflictsWith，但日历不展示冲突）
- ❌ 与 finance 联动（记账由 finance 独立 Tab 处理）
- ❌ 真实提醒调度（事件 reminder 字段已落库，调度留给 Phase 8）

---

## 安全网

- ✅ 零 schema 改动（events 表 Phase 1 已定型）
- ✅ task / timetable / finance 模块全部保留
- ✅ 现有 28 测试必须仍通过
- ✅ 全程在 `feature/phase-7-calendar` 分支
- ✅ 单步可独立 revert（每个 Sub-step 一个 commit）

---

## 7 个关键决策（涟宝预判）

1. **月视图布局**：6×7 LazyVerticalGrid（42 格），顶部对齐周一（国内习惯）
2. **课表过滤逻辑**：Course 永远按 dayOfWeek 显示，周次规则在 Semester 范围内计算（无学期时全部显示）
3. **任务联动**：只展示「未完成 + dueDate 落在当天 0-24h」的任务
4. **事件排序**：当日内按 isAllDay DESC → startTime ASC
5. **月切换动画**：HorizontalPager（涟宝感觉比手写 offset 计算简洁）
6. **日历日期格式**：内部统一用 Long（startOfDayMillis），UI 层转 LocalDate
7. **DayDetailSheet**：事件点击 → 事件编辑；任务点击 → 任务详情；课程点击 → 课程编辑（统一收口到对应模块的 nav route）

---

## 后续计划

- 涟宝会按 Sub-step 顺序执行（1→5），单步完成立刻跑 BUILD + 单测
- 全部完成后一次 commit + push 到 GitHub
- 推送到远端后给伙伴看 7c 总成绩单

---

**审批后涟宝就开始推 ♪**