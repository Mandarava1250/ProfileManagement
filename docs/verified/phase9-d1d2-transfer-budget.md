# Phase 9 9b D1 + D2 —— 转账 UI 测试 + 预算执行条（含 plan 误判侦查记录）

**执行时间**：2026-09-16 15:15（Asia/Shanghai）
**执行分支**：`feature/phase-9-verification`
**设备**：AVD `pm_ui36`（`emulator-5554`，`sdk_gphone64_x86_64` / `emu64xa`）

---

## 一、侦查结论：plan §0.2 对 D1/D2 的判断不准确

plan §0.2 原文写的是：

> | 转账 UI（Compose 表单 + 提交流水） | ❌ **不存在**（`TransferScreen` 0 命中） |
> | 预算汇总 UI（月度报表叠加预算条） | ❌ **不存在**（`BudgetSummary*` / `MonthlyBudgetScreen` 0 命中） |

**实地核查发现：生产代码大部分早已存在**，只是类名与 plan 的搜索词不同。

| plan 搜索词 | 实际实现类 | 存在状态 | 引入 commit |
|---|---|---|---|
| `TransferScreen` | **`TransferSheet`**（172 行） | ✅ 已存在 | `438a29e`（Phase 5）|
| `BudgetSummary*` / `MonthlyBudgetScreen` | **`BudgetSheet`**（109 行） | ✅ 已存在 | `c52461a`（Phase 7a）|
| `BudgetSummarySection` | **`MonthlyReportCard`**（276 行） | ✅ 已存在 | `5f328f1`（Phase 7b）|
| — | **`BudgetViewModel` + `BudgetViewModelTest`** | ✅ 已存在 | Phase 7a |
| — | **`AccountSummaryCard`**（跨账户预算总览，含进度条） | ✅ 已存在 | Phase 5 |

**plan 的「0 命中」技术上成立**（精确类名确实搜不到），
**但由此推出的「不存在」是误判** —— 实际是新功能 UI 早已在 Phase 5/7 交付。

### 真实缺口（据实重估后）

| 子项 | plan 估时 | 真实缺口 | 据实重估 |
|---|---|---|---|
| **D1** | 240-360 min | `androidTest/.../finance/` 目录**不存在** → 只缺 UI test | **60-90 min** |
| **D2** | 180-240 min | `MonthlyReportCard` grep「预算/progress/percent」0 命中 → 缺「预算执行条」+ test | **90-150 min** |
| 合计 | 420-600 min | — | **150-240 min（↓ 约 60%）** |

---

## 二、D1 交付：转账 UI 可测性重构 + 三层测试

### 生产改动（行为零变化）

| 文件 | 改动 |
|---|---|
| `TransferFormState.kt` | **新增**。把 `targets / from / selected / amount / overBalance / canSubmit` 从 Composable 体内抠出来，成为纯数据类 + `from(balances, currentAccountId, ...)` 工厂 |
| `TransferSheet.kt` | **重构**。拆成 `TransferSheet`（收 VM 流 → 组装 state）+ `TransferSheetContent`（无状态，state in / callback out）|

**为什么这样拆**：原实现的推导逻辑长在 Composable 里，只能靠 instrumented 测试碰（一轮 8 min）。
抠出来后推导在 JVM 上毫秒级验证，instrumented 只验交互 —— 与 `AccountSwitcherBar`（9b B2）同一套路子。

**行为等价性**：`targets` 过滤、`overBalance` 判定、`canSubmit` 条件、摘要文案全部逐条搬运，无逻辑变更。

**复用既有模型**：`targets` 直接用领域层既有的 `TransferAccountOption`（本来就是为「转账目标账户选项」定义的），
没有新造类型。

### D1 测试结果

| 层 | 测试类 | 用例数 | 结果 |
|---|---|---|---|
| JVM | `TransferFormStateTest` | **16** | `tests=16 failures=0 errors=0 time=0.139s` |
| instrumented | `TransferSheetContentTest` | **14** | `tests=14 failures=0 errors=0 time=58.875s` |

**plan §4.3 要求的三个场景全部覆盖**：

| plan 场景 | 实现方式 |
|---|---|
| happy path | 金额合法 + 选中账户 → 按钮可用 → 点击抛出正确 accountId |
| **源==目标 拦截** | **在状态层排除**（`TransferFormState.from` filter 当前账户）→ UI 层表现为「转入列表里根本不出现当前账户」。比「能点到但报错」更早一步 |
| 余额不足提示 | 超余额 → 显示「超出余额：当前 X」+ 按钮禁用；恰好等于余额不算超；余额未知不误报 |

---

## 三、D2 交付：报表顶部预算执行条

### 生产改动

| 文件 | 改动 |
|---|---|
| `BudgetProgressBar.kt` | **新增**。`BudgetProgress`（纯计算：ratio / percentText / clampedRatio / remaining / isOverBudget）+ `BudgetProgressBar`（无状态渲染：进度条 + 百分比 + 已用/总额 + 点击展开明细）|
| `MonthlyReportCard.kt` | **接线 +19 行**。在「暂无数据」分支之后、支/收/结余三段之前插入 `BudgetProgressBar`；`report.budget == null`（未设预算）时整块省略 |

**为什么不动 `MonthlyReport.usedRatio`**：那个字段早就算好了，只是没有 UI 用。
`BudgetProgress.fromOrNull()` 直接用 `report.budget?.totalAmount` + `report.totalExpense`，
与 `usedRatio` 同一套除零保护（预算 ≤ 0 → 0），不重复实现、也不改领域模型。

**与 `DailyPoolCard` 的分工**：Pool 管「今天还能花多少」（日粒度），本组件管「这个月用了多少」（月粒度）。
数据源与刷新节奏都不同，合并会让池子的日切逻辑污染月度展示。

### D2 测试结果

| 层 | 测试类 | 用例数 | 结果 |
|---|---|---|---|
| JVM | `BudgetProgressTest` | **11** | `tests=11 failures=0 errors=0` |
| instrumented | `BudgetProgressBarTest` | **9** | `tests=9 failures=0 errors=0 time=34.149s` |

---

## 四、D1+D2 合计

| 项 | 数量 |
|---|---|
| 生产文件 | 新增 2 + 修改 2 |
| 测试文件 | 新增 4（2 JVM + 2 instrumented）|
| **JVM 用例** | **27**（16 + 11），全绿 |
| **instrumented 用例** | **23**（14 + 9），全绿 AVD 真跑 |

---

## 五、踩坑记录（4 条，本轮真实踩到）

1. **`MoneyFormat` 带千分位，金额断言不能硬编码**
   `NumberFormat(Locale.CHINA)` 把 1000.0 渲染成 `1,000.00`。
   最初硬写 `"1000.00"` → 5 条 instrumented 全挂。
   正解：断言里用 `MoneyFormat.formatPlain(x)` **现算期望值**，让断言跟着实现走。

2. **滚动容器里的节点 `assertIsDisplayed` 会失败**
   `TransferSheetContent` 有 `verticalScroll`，标题 + 说明 + 账户行 + 2 输入框 + 摘要 + 按钮总高超过一屏，
   靠下节点在视口外。正解：`performScrollTo()` 后再断言/点击。
   （`assertIsNotEnabled` 只需节点存在，不需要可见，禁用类断言可直接写。）

3. **`AccountBalance` 的真实字段是 `transferNet` 而非 `transferIn`/`transferOut`**
   我先按「转入/转出分开」的直觉写了构造，实读源码发现是**净额单字段**
   （`balance = initialBalance + income - expense + transferNet`）。
   教训：构造测试数据前必须读真实 data class，不能凭字段语义猜。

4. **AVD 本轮挂了 2 次**
   一次在 C2 首跑（17+ h 的老实例），一次在 D1 首跑（冷启动后约 10 min）。
   表现：`No connected devices!` / `device 'emulator-5554' not found`。
   恢复：`emulator -avd pm_ui36 -no-snapshot-load` + 等 `sys.boot_completed=1`（约 70 s）。
   **规则**：AVD 操作前先 `adb devices` 确认，掉线立即重启，不要在无设备时重试 gradle。

---

## 六、诚实边界（不虚标）

- **D1/D2 的视觉未经真人目测**：预算条颜色 / 进度条圆角 / 展开动画 / 长账户名截断 —— 留 Phase 10。
- **转账的真实落库链路未在本轮覆盖**：本轮验的是「UI 状态推导 + 渲染 + 回调」，
  真正的 `TransferUseCase → Repository → DAO` 双写（转出/转入成对）由既有的
  `TransferUseCase` 单测 + `Migration_9_10` AVD 真跑覆盖，不在 D1 范围。
- **`TransferSheet` 行为 refactor 的风险**：虽然逐条搬运算无逻辑变更，
  但属于「重构既有生产代码」—— 已用 16 例 JVM + 14 例 instrumented 锁住行为，
  且 **Phase 8 已交付的转账功能本身不在本轮改动范围内**（只挪位置，不改判定）。