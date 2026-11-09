# Phase 10 真人目测清单（Manual Verification Checklist）

> **使用说明**：
> - 这是 Phase 10 留下的待真人手动验证清单（6 项），对应 DEV_LOG.md 顶部速览段列出的「Phase 10：真人目测」条目
> - 每节含「场景 / 前置条件 / 步骤 / 预期 / 截图位置 / 结果记录」六段；伙伴跑完填写最后一段（✅/❌ + 备注）
> - 截图存在 `docs/manual-verification/screenshots/<phase>-<item>.png`；本目录已 gitignore 截图二进制（仅本 .md 入库）
> - **本清单状态图例**：✅ = 已通过；❌ = 未通过（请描述现象）；🟨 = 部分通过
>
> **生成信息**：
> - 生成时间：2026-09-17（Phase 10 · 10c VL-1）
> - 生成者：昔涟（Cyrene）自动生成
> - 对应计划：`.cyrene/docs/plan-20260916-175218.md` §4.4
> - 测试基线：合并到 Develop 后，所有 Phase 8/9 已验证功能在真机 / 真实数据下的视觉与交互验收
> - 2026-11-xx 追加：VL-10x（自动备份写文件真机路径）—— instrumented 侧 SAF happy path
>   **至今从未真跑通过**（AVD 上拿不到已授权的 SAF tree URI，该用例已降级为 skipped），
>   真实写文件路径由本节承接

---

## 📋 清单总览

| # | 编号 | 标题 | 优先级 | 来源 |
|---|---|---|---|---|
| 1 | VL-8a | 通知 deeplink 真机点击 | 🟨 高（功能核心）| Phase 8a + Phase 9 9c C1（已自动验证 4/4 UI tree 命中）|
| 2 | VL-8b | 任务矩阵视图双布局真机截图 | 🟨 高（视觉核心）| Phase 8b'' + Phase 9 9c C2（已自动验证 Compose UI 渲染）|
| 3 | VL-9b-B2 | AccountSwitcher 文字行视觉与切换交互 | 🟡 中（UX 核心）| Phase 9 9b B2（已 JVM 23 + instrumented 8/8，缺真人目测视觉）；**Phase 26 起组件已从 Chip 改为文字行** |
| 4 | VL-9c-sw600 | 平板屏宽 sw600dp+ 布局 | 🟡 中（平板覆盖）| Phase 9 9c（AVD 仅跑默认屏宽，未单独验证 sw600+）|
| 5 | VL-9b-D1D2 | 转账 Sheet + 预算执行条视觉 | 🟡 中（记账核心）| Phase 9 9b D1 + D2（已 JVM + instrumented 覆盖逻辑，缺视觉验收）|
| 6 | VL-10x | 自动备份写文件真机路径 | 🟨 高（数据安全）| Phase 10 SAF-1（instrumented happy path 在 AVD 上无法满足前置条件，已降级为 skipped）|

---

## 1. VL-8a — 通知 deeplink 真机点击

**场景**：用户在系统通知栏点击任务/事件/课程通知，确认跳转到对应详情页（不是简单的「点开 App」）。

**前置条件**：
- 真机（**非 AVD**，AVD 上通知通道可能在冷启动被禁用）
- 应用已登录至少 1 个账户
- 系统设置：通知权限已授权
- 已创建至少 1 个任务 / 1 个事件 / 1 个课程（带 reminder）

**步骤**：
1. 启动应用，进入任务页 → 创建任务「测试 deeplink」+ reminder 时间 = 当前 + 2 min
2. 后台退出应用
3. 等通知触发，从系统通知栏下滑、找到「测试 deeplink」通知
4. **点击通知**（不是滑动清除、不是「全部清除」）
5. 观察：是否进入任务详情页（应是 `tasks/detail/{id}`）
6. 后台退出，重复步骤 1-5，事件类（calendar）通知 + 课程类（timetable）通知各做一遍

**预期**：
- 三类通知（task / event / course）点击后都进入对应详情页
- 详情页正确显示该任务的标题 / 时间 / 备注
- 不闪退 / 不进入 MainActivity 后跳 Home / 不停留在通知点击前的页面

**截图位置**：`docs/manual-verification/screenshots/VL-8a-task-detail.png`、`VL-8a-event-detail.png`、`VL-8a-course-detail.png`

**结果记录**（伙伴填）：
- 任务通知：✅ / ❌（描述）___ 备注：___
- 事件通知：✅ / ❌（描述）___ 备注：___
- 课程通知：✅ / ❌（描述）___ 备注：___

---

## 2. VL-8b — 任务矩阵视图双布局真机截图

**场景**：任务页切到矩阵视图，验证 600dp 以下纵向 5 区 / 600dp 以上 2×2 网格 + 未排期独立区的视觉。

**前置条件**：
- 真机（或 AVD 通过 `adb shell wm size 1080x1920` / `wm size 2400x1600` 模拟）
- 应用已创建至少 6 个任务：2 高优先级 / 2 中优先级 / 2 低优先级，至少 3 个有截止日期、至少 2 个排进了具体时段（dueTime）、至少 1 个无截止日期
- 系统时间设置为当前时间

**步骤**：
1. 启动应用，进入任务页
2. 找到视图切换控件（顶部 toggle「列表 / 矩阵」），切换到矩阵视图
3. **小屏**（屏宽 < 600dp，例如竖屏手机）：截图
4. 滚动矩阵视图到底部：截图
5. **大屏**（屏宽 ≥ 600dp，例如平板或横屏手机）：截图
6. 点开「未排期」独立区（折叠 → 展开），截图
7. 点回列表视图，截图对比

**预期**：
- 小屏：纵向 5 区（高 / 中 / 低 + 未排期独立），每个区有标题 + 任务卡片
- 大屏：2×2 网格（高 + 中 / 低 + 未排期独立），左右分布
- 未排期区在两屏宽下都是独立（不混在优先级区里）
- 任务卡片有完整信息（标题、dueTime、标签）
- toggle 切换不闪退 / 不丢滚动位置

**截图位置**：
- `docs/manual-verification/screenshots/VL-8b-small-portrait-top.png`
- `docs/manual-verification/screenshots/VL-8b-small-portrait-bottom.png`
- `docs/manual-verification/screenshots/VL-8b-large-landscape.png`
- `docs/manual-verification/screenshots/VL-8b-unscheduled-expanded.png`
- `docs/manual-verification/screenshots/VL-8b-list-view-compare.png`

**结果记录**（伙伴填）：
- 小屏 5 区布局：✅ / ❌ 备注：___
- 大屏 2×2 布局：✅ / ❌ 备注：___
- 未排期独立区：✅ / ❌ 备注：___
- toggle 切换流畅：✅ / ❌ 备注：___

---

## 3. VL-9b-B2 — AccountSwitcher 文字行视觉与切换交互

> ⚠️ **口径订正（Phase 26 起）**：本组件**已从 `FilterChip` 改成文字行**（选中态只画一根 3dp 竖条，
> 不再铺 `primaryContainer` 色块）。下面凡是写「Chip」的地方，一律按**文字行**验收；
> 「Chip 间距 / Chip 背景色 / Chip elevation」这些说法已过期。

**场景**：记账页（FinanceScreen）顶部 AccountSwitcher 文字行横条，验证行间距、长账户名截断、点击切换流畅性。

**前置条件**：
- 真机
- 应用已创建至少 3 个账户（建议账户名长度差异大：1 字 / 4 字 / 8 字 / 12 字，例如「现」、「支付宝」、「招行信用卡 1234」、「建设银行储蓄账户」）
- 至少 1 个账户有 ≥ 5 条交易记录

**步骤**：
1. 启动应用，进入记账页
2. 顶部 AccountSwitcher Bar 截图（默认账户应在视觉上区分「选中」状态）
3. 点击非选中账户的文字行 → 观察：当前账户高亮切换 + 下方交易列表刷新
4. 长账户名的文字行是否截断（省略号 / fade）
5. 账户数 ≥ 5 时横条是否可水平滑动（应能滑到尽头看到全部）
6. 单账户 / 加载中状态下整个 Bar 是否隐藏
7. 切换失败时是否有 Snackbar 提示（手动构造：把数据库文件锁住再切换，看 Snackbar）

**预期**：
- 选中账户有明确视觉差异（**3dp 竖条 + 账户名加粗**；未选中账户留同样的竖条空位，账户名不左右跳）
- 长账户名截断合理（不撑爆 Bar、不换行）
- 切换响应 < 500ms（不卡顿、不闪退）
- 单账户状态 Bar 完全隐藏（不留空白条）
- 切换失败有红色 Snackbar 提示，且 5s 自动消失
- （无障碍）TalkBack 聚焦到某个账户时，能念出「已选中 / 未选中」——这依赖 `selectable` 语义，
  视觉上看不出来，但必须成立

**截图位置**：
- `docs/manual-verification/screenshots/VL-9b-B2-bar-default.png`
- `docs/manual-verification/screenshots/VL-9b-B2-bar-after-switch.png`
- `docs/manual-verification/screenshots/VL-9b-B2-long-name-truncate.png`
- `docs/manual-verification/screenshots/VL-9b-B2-single-account-hidden.png`
- `docs/manual-verification/screenshots/VL-9b-B2-snackbar-error.png`

**结果记录**（伙伴填）：
- 文字行选中状态视觉：✅ / ❌ 备注：___
- 切换流畅：✅ / ❌ 备注：___
- 长账户名截断：✅ / ❌ 备注：___
- 单账户隐藏：✅ / ❌ 备注：___
- 错误 Snackbar：✅ / ❌ 备注：___
- TalkBack 选中语义：✅ / ❌ 备注：___

---

## 4. VL-9c-sw600 — 平板屏宽 sw600dp+ 布局

**场景**：模拟 sw600dp+ 屏宽（平板或大屏横屏手机），验证 4 个页面（任务页 / 记账页 / 日历页 / 课表页）的平板布局不崩。

**前置条件**：
- 真机平板 OR AVD 模拟 sw600dp+
- AVD 命令：`avdmanager create avd -n pm_sw600 -k "system-images;android-36;google_apis;x86_64" -d pixel_tablet` + 启动后 `adb shell wm size 1600x2560`（或类似 sw600+ 分辨率）
- 应用已登录 + 至少有 1 个账户 + 至少 1 条任务 / 1 条交易 / 1 个事件 / 1 门课程

**步骤**：
1. 启动 AVD / 真机平板
2. 应用启动 → 截图首页（4 个 Tab 应水平排布或可滑动切换）
3. 进入任务页 → 截图（任务列表应使用更大列宽 / 网格布局）
4. 进入记账页 → 截图（AccountSwitcher + 交易列表 + FAB 位置合理）
5. 进入日历页 → 截图（月视图应占据更多列宽）
6. 进入课表页 → 截图（周视图应能并排显示更多天）
7. 横竖屏切换各做一遍（部分组件可能有 orientation-specific 布局）

**预期**：
- 4 个 Tab 切换不闪退
- 任务页 / 记账页 / 日历页 / 课表页都不崩、不出现「撑爆」或「挤成一团」现象
- 关键文本（按钮、菜单项、卡片标题）字号合适（不应过小）
- AccountSwitcher Chip 横条在 sw600+ 是否从横排转竖排或折叠（看实际效果）

**截图位置**：
- `docs/manual-verification/screenshots/VL-9c-sw600-home.png`
- `docs/manual-verification/screenshots/VL-9c-sw600-tasks.png`
- `docs/manual-verification/screenshots/VL-9c-sw600-finance.png`
- `docs/manual-verification/screenshots/VL-9c-sw600-calendar.png`
- `docs/manual-verification/screenshots/VL-9c-sw600-timetable.png`
- `docs/manual-verification/screenshots/VL-9c-sw600-landscape.png`

**结果记录**（伙伴填）：
- 任务页 sw600+：✅ / ❌ 备注：___
- 记账页 sw600+：✅ / ❌ 备注：___
- 日历页 sw600+：✅ / ❌ 备注：___
- 课表页 sw600+：✅ / ❌ 备注：___
- 4 Tab 切换流畅：✅ / ❌ 备注：___

---

## 5. VL-9b-D1D2 — 转账 Sheet + 预算执行条视觉

**场景**：在记账页验证转账 Sheet（TransferSheet）的交互 + 月度报表卡（MonthlyReportCard）顶部的预算执行条（BudgetProgressBar）视觉。

**前置条件**：
- 真机
- 应用至少 2 个账户、其中至少 1 个账户有预算设置（¥1000 餐饮）+ 该账户当月已有 ≥ 3 条餐饮类支出（总额 ~ ¥400-¥800）
- 转账测试：源账户和目标账户都至少 ¥1000 余额

**步骤**：
1. 启动应用 → 记账页 → 截图当前账户的「本月餐饮预算执行条」（应为 ~40-80% 进度 + 颜色区分）
2. 触发转账 Sheet：点击 FAB → 选「转账」 → 截图空表单
3. 填源账户 / 目标账户 / 金额 → 截图填好状态
4. 故意填：源 == 目标 → 应弹错误提示（状态层就拦截，不发起）
5. 故意填：金额 > 源账户余额 → 应弹错误提示（不发起）
6. 填合法数据：源 ¥1000 → 目标 ¥500 → 金额 ¥200 → 确认 → 截图交易后状态（双方余额变化 + 交易列表新增一条）
7. 进入月度报表 → 截图预算执行条 + 月度汇总数字

**预期**：
- 预算执行条：进度条颜色清晰（绿 / 黄 / 红 区分）/ 圆角 / 数字文字正确（已花 ¥500 / 1000 ¥）
- 转账 Sheet：表单字段清晰 / 错误提示不破坏布局 / 余额实时更新
- 源 == 目标、余额不足都被状态层拦截，不进入交易提交阶段
- 转账成功后双方余额 ±¥200 正确（不丢精度）
- 转账在月度报表中显示为「转账 / 转出」分类，不混入餐饮

**截图位置**：
- `docs/manual-verification/screenshots/VL-9b-D1D2-budget-progress.png`
- `docs/manual-verification/screenshots/VL-9b-D1D2-transfer-empty.png`
- `docs/manual-verification/screenshots/VL-9b-D1D2-transfer-filled.png`
- `docs/manual-verification/screenshots/VL-9b-D1D2-transfer-error-same.png`
- `docs/manual-verification/screenshots/VL-9b-D1D2-transfer-error-balance.png`
- `docs/manual-verification/screenshots/VL-9b-D1D2-transfer-success.png`
- `docs/manual-verification/screenshots/VL-9b-D1D2-monthly-report.png`

**结果记录**（伙伴填）：
- 预算执行条视觉：✅ / ❌ 备注：___
- 转账 Sheet 空 / 填好状态：✅ / ❌ 备注：___
- 源 == 目标拦截：✅ / ❌ 备注：___
- 余额不足拦截：✅ / ❌ 备注：___
- 转账成功双方余额 ±¥200：✅ / ❌ 备注：___
- 月度报表分类正确：✅ / ❌ 备注：___

---

## 6. VL-10x — 自动备份写文件真机路径

**场景**：打开「自动备份」开关 → 用系统 picker 选一个目录 → 触发一次立即备份 →
确认目标目录里真的出现 `cyrene-backup-*.zip`、大小 > 0，且这份备份能被恢复。

> ⚠️ **为什么这一节必须人工做**：SAF tree URI 的访问授权**只能由系统 picker**
> （`ACTION_OPEN_DOCUMENT_TREE`）授予，调用方无法自行合成一个「已授权」的 tree URI ——
> 所以 instrumented 侧根本造不出这个前置条件
> （`DailyAutoBackupWorkerTest.safHappyPathWritesZipAndUpdatesTimestamp` 已降级为 **skipped**）。
> **SAF happy path 至今从未真跑通过**，本节是它唯一的验收口。

**前置条件**：
- 真机（**AVD 上不行**：没有系统 picker 交互，DownloadsProvider 的授权路径也和真机不同）
- 应用已有数据：至少 1 个账户 / 1 条交易 / 1 个任务 / 1 门课程（备份内容非空才看得出 zip 大小 > 0）
- 目标目录选一个**空目录**（例如先新建 `CyreneBackupTest/`），方便确认里面只有备份文件

**步骤**：
1. 启动应用 → 设置页 → 找到「备份与恢复 / 自动备份」
2. 打开**自动备份开关** → 应用应拉起系统目录选择器（`ACTION_OPEN_DOCUMENT_TREE`）
3. 在系统 picker 里选一个目录（例如「下载 / CyreneBackupTest」新建目录），确认授权
4. 回到应用，确认界面显示的是刚选的目录名
5. 触发**立即备份**（手动跑一次备份）
6. 用系统文件管理器打开刚才选的目标目录（或 `adb pull` 下来看）
7. 确认目录里出现 `cyrene-backup-<时间戳>.zip`
8. 确认该文件**大小 > 0**（不是 0 字节的空壳）
9. 回到应用「备份与恢复」，用这份 zip 执行一次**恢复**，确认导入成功、数据没丢
10. 关闭自动备份开关，再观察目标目录：不应再产生新的备份文件

**预期**：
- picker 授权后目录名显示正确；**App 重启后仍是同一个目录**（`backupFolderUri` 持久化成功）
- 「立即备份」结束后目标目录里出现 `cyrene-backup-*.zip`，**大小 > 0**
- 恢复这份 zip 成功：任务 / 交易 / 课程等数据条数与备份前一致，不崩溃、不重复导入
- 关闭开关后不再产生新的备份文件

**截图位置**：
- `docs/manual-verification/screenshots/VL-10x-picker.png`
- `docs/manual-verification/screenshots/VL-10x-folder-selected.png`
- `docs/manual-verification/screenshots/VL-10x-zip-in-folder.png`
- `docs/manual-verification/screenshots/VL-10x-restore-result.png`

**结果记录**（伙伴填）：
- 开关能拉起系统 picker：✅ / ❌ 备注：___
- 选完目录后目录名显示正确（重启后仍是同一个）：✅ / ❌ 备注：___
- 目标目录出现 `cyrene-backup-*.zip`：✅ / ❌ 备注：___
- zip 大小 > 0：✅ / ❌ 备注：___
- 恢复成功、数据条数一致：✅ / ❌ 备注：___
- 关掉开关后不再产生新文件：✅ / ❌ 备注：___

---

## 📝 汇总记录

跑完上述 5 项后，请把每节的「结果记录」汇总到下表（伙伴填）：

| 编号 | 结果（✅/❌） | 关键发现 / 阻塞 / 备注 |
|---|---|---|
| VL-8a | ___ | ___ |
| VL-8b | ___ | ___ |
| VL-9b-B2 | ___ | ___ |
| VL-9c-sw600 | ___ | ___ |
| VL-9b-D1D2 | ___ | ___ |
| VL-10x | ___ | ___（SAF 写文件真机路径 —— instrumented 侧已 skip，本项是唯一验收口）|

**全部跑完后**：
1. 把截图放到 `docs/manual-verification/screenshots/` 目录
2. 在汇总表填好结果
3. 在 DEV_LOG.md 「Phase 10 10c 完成」段（新建）记：
   - 提交日期
   - 6 项结果摘要
   - 发现的问题（如果 ❌ 项）
4. 通过 6 项的 sub-step → 升级状态 🟨 → ✅（合并到 Develop 后）

**回退 / 修复**：
- 任一项 ❌ → 在 GitHub Issues / DEV_LOG 留 issue，关联到对应 Phase
- 若发现新功能缺口 → 立项 Phase 11

---

> **状态**：🟨（清单已生成，**待真人执行**）
> **下一入口**：用户（伙伴）按节跑完后填结果 → DEV_LOG 更新 → Phase 10 10d 收口
>
> **⚠️ 口径备注（不要标 ✅）**：
> - VL-10x 跑通之前，「自动备份写文件到用户指定目录」这条链路**没有任何自动化验收**：
>   instrumented 侧的 SAF happy path 在 AVD 上满足不了前置条件，已降级为 **skipped**。
> - 本清单状态为 🟨；在 VL-10x 真正跑过（真机 + 系统 picker + zip 大小 > 0 + 恢复成功）之前，
>   **不要把自动备份写成 ✅ 已验证**。