# Phase 9 9c C2 —— 矩阵视图屏宽矩阵自动化验证（复跑 + 补缺口）

**执行时间**：2026-09-16 15:05（Asia/Shanghai）
**执行分支**：`feature/phase-9-verification`
**设备**：AVD `pm_ui36`（`emulator-5554`，`sdk_gphone64_x86_64` / `emu64xa`）

## 这一轮做了两件事

### 1. 补上 Phase 8 遗留的自动化缺口（核心交付）

Phase 8b'' 的 DEV_LOG 里诚实登记过一条未覆盖项：

> **矩阵视图（`TaskViewMode.MATRIX`）自动化切换未跑通** ——
> 当时靠 `adb input tap` 点坐标，按钮位置随屏宽浮动容易点偏；
> 安全做法需要 `uiautomator2`（需 pip 安装，本机无）。

**这个缺口在 Compose UI test 里有正解**：`onNodeWithText()` 走**语义树**而非像素坐标，
只要文本是「矩阵」就点得到，与屏宽无关。本轮用它把该缺口真正补上。

新增 2 个 instrumented 测试类：

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `MatrixViewToggleTest` | 5 | 「列表 / 矩阵」语义点击 → 对应回调触发；来回切换；选中态下仍可点；两档 + 「视图」标签渲染 |
| `TaskMatrixViewLayoutTest` | 11 | 2×2 / 纵向 5 区两种布局分支；未排期区显隐；空格子占位；折叠展开；断点 599/600 边界 |
| **合计** | **16** | **AVD 真跑 `tests=16 failures=0 errors=0 skipped=0 time=75.283s`，BUILD SUCCESSFUL in 2m 5s** |

### 2. 双屏宽 UI tree dump 归档（延续 T7 口径）

用 `wm size` 切屏宽 + `uiautomator dump` 拿两份真实 UI 树：

| # | 布局 | rotation | 证据文件 |
|---|---|---|---|
| 1 | Portrait（原生 1080x2400） | `0` | `c2-portrait.xml`（12300 B） |
| 2 | Landscape（override 1280x800 + `user_rotation=1`） | `1` | `c2-landscape.xml`（12296 B） |

两棵树顶层 `package` 均为 `com.example.personalassistant.debug`，渲染不崩，
底部 nav 4 个 tab（课表 / 任务 / 记账 / 日历）在两种宽度下均在位。

一键复跑脚本：`scripts/c2_screenwidth_dump.bat`
dump 摘要脚本：`scripts/c2_summarize_dump.ps1`

## 关键技术点（可复用的做法）

### 用 `LocalConfiguration` 伪造屏宽，替代改设备分辨率

`TaskMatrixView` 的分支靠 `LocalConfiguration.current.screenWidthDp` 判定
（断点 `TaskMatrix.MATRIX_BREAKPOINT_DP` = 600）。`LocalConfiguration`
是可 provide 的 CompositionLocal，所以测试里直接：

```kotlin
val fake = Configuration(base).apply { screenWidthDp = 800 }
CompositionLocalProvider(LocalConfiguration provides fake) { TaskMatrixView(...) }
```

好处：**不需要 `wm size`，不需要第二台 AVD，断点两侧可精确断言**（599 / 600 各一条）。

## 踩坑记录（4 条，都是真实踩到的）

1. **纵向布局是 `LazyColumn`，屏外节点不在语义树里**
   直接 `assertIsDisplayed()` 会报 `The component is not displayed!` ——
   不是布局坏了，是节点未被惰性组合。正解：
   `onNode(hasScrollAction()).performScrollToNode(hasText("消除"))`。

2. **折叠逻辑只存在于 2×2 布局**
   「未排期超 3 条 → 显示『查看全部 N 条』」在 `UnscheduledSection` 里，
   **只有 2×2 布局用它**；纵向布局直接把任务铺进 LazyColumn，**没有折叠**。
   最初把这个断言放在窄屏下 → 必然失败。**是我自己的测试前提写错了位置，不是生产缺陷**，
   已挪到宽屏用例并保留此记录。

3. **伪造宽屏时不能用 `assertIsDisplayed` 断言到底**
   伪造 `screenWidthDp = 800` 时真实窗口仍是手机宽度，内容可能落到物理视口外。
   所以宽屏用例一律 `assertExists()`（结构正确性），窄屏（真实宽度）才用 `assertIsDisplayed()`。
   **真机宽屏人眼目测仍留 Phase 10。**

4. **`onNodeWithText` 要求唯一匹配**
   全空矩阵同屏有多个「无任务 ♪」（每空格子一个），
   直接断言报 `Expected at most 1 node but found 3`。正解：`onAllNodesWithText(...).onFirst()`。

## 环境事实（诚实记录）

- **AVD 本轮冷启动过一次**：上一轮 17+ 小时的 `pm_ui36` 在 C2 首次运行时挂掉
  （日志：`device 'emulator-5554' not found`），冷启动 `-no-snapshot-load` 约 70 s 恢复。
- **重装 APK 后需重新授权通知权限**（同 C1 踩坑），脚本已内置
  `pm grant POST_NOTIFICATIONS`。
- **横屏 dump 首次失败**：旋转后立刻 dump 会报
  `ERROR: null root node returned by UiTestAutomationBridge` ——
  旋转后需等 ~15 s 再 dump（脚本里 `sleep 9` 不够，重试时加到 ~15 s 成功）。

## 验收状态（9c C2）

- ✅ Phase 8 遗留的「矩阵 toggle 自动化切换」缺口：**已补**（`MatrixViewToggleTest` 5 例）
- ✅ 矩阵双布局断点：**已锁**（`TaskMatrixViewLayoutTest` 11 例，599/600 边界各一条）
- ✅ 双屏宽 UI tree dump：**已归档**（portrait + landscape）
- 🟨 真机 / 真平板（`sw600dp` 以上）人眼目测：**未做**（Phase 10，需用户在场）
- 🟨 实际视觉效果（间距 / 截断 / 配色）：**未目测**（同上）