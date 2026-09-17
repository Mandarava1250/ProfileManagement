# Phase 10 — 待你执行清单（1 页速查版）

> **给伙伴的速查卡** — 不是详细步骤，详细版在同目录的 `phase10-visual-checklist.md`（255 行）。
> 跑完每项在「结果」一栏打 ✅/❌ + 备注，最后填底部汇总表，截图丢 `screenshots/` 即可。

---

## 🚦 速查总览（5 项，推荐顺序）

| # | 编号 | 标题 | 估时 | 真机/AVD | 截图 |
|---|---|---|---|---|---|
| 1 | VL-8a | 通知 deeplink 点击 | 5 min | **真机** | 3 张 |
| 2 | VL-9b-B2 | AccountSwitcher Chip 视觉 | 10 min | 真机 / AVD | 5 张 |
| 3 | VL-9b-D1D2 | 转账 Sheet + 预算条 | 15 min | **真机** | 7 张 |
| 4 | VL-8b | 任务矩阵双布局 | 10 min | AVD `wm size` | 5 张 |
| 5 | VL-9c-sw600 | 平板 sw600+ | 15 min | AVD `pixel_tablet` | 6 张 |

> **总计约 55 min**。今天跑完前 3 项最常用场景（VL-8a + VL-9b-B2 + VL-9b-D1D2）就覆盖了核心日常功能 ~30 min。

---

## 1️⃣ VL-8a — 通知 deeplink 点击

**做什么**：建任务「测试 deeplink」+ reminder +2min → 后台退出 → 等通知 → **点通知**（不是「全部清除」）→ 看是否跳进 `tasks/detail/{id}`。
**跑三遍**：任务 / 事件 / 课程 各做一遍。

**结果**：
- 任务通知：✅ / ❌ 备注：___
- 事件通知：✅ / ❌ 备注：___
- 课程通知：✅ / ❌ 备注：___

**截图**（3 张）：`screenshots/VL-8a-task-detail.png`、`VL-8a-event-detail.png`、`VL-8a-course-detail.png`

---

## 2️⃣ VL-9b-B2 — AccountSwitcher Chip 视觉

**前置**：建 4 个账户（名字差异大：现 / 支付宝 / 招行信用卡 1234 / 建设银行储蓄账户）。
**做什么**：进记账页 → 顶部 Chip Bar 截图 → 点非选中账户 → 看高亮切换 + 列表刷新 → 看长账户名是否截断 → 单账户下 Bar 是否隐藏。

**结果**：
- 选中状态视觉：✅ / ❌ 备注：___
- 切换流畅 < 500ms：✅ / ❌ 备注：___
- 长账户名截断：✅ / ❌ 备注：___
- 单账户 Bar 隐藏：✅ / ❌ 备注：___

**截图**（4 张）：`screenshots/VL-9b-B2-bar-default.png`、`VL-9b-B2-bar-after-switch.png`、`VL-9b-B2-long-name-truncate.png`、`VL-9b-B2-single-account-hidden.png`

---

## 3️⃣ VL-9b-D1D2 — 转账 + 预算条

**前置**：2 个账户各有 ≥ ¥1000 余额 + 1 个账户设餐饮预算 ¥1000 + 已花 ~¥500。
**做什么**：截图预算条（绿/黄/红？）→ FAB → 转账 → 故意源==目标看拦截 → 故意超额看拦截 → 合法转 ¥200 → 截图 + 月度报表。

**结果**：
- 预算条颜色清晰：✅ / ❌ 备注：___
- 转账 Sheet 表单：✅ / ❌ 备注：___
- 源 == 目标拦截：✅ / ❌ 备注：___
- 余额不足拦截：✅ / ❌ 备注：___
- 转账 ±¥200 精度对：✅ / ❌ 备注：___
- 月报分类正确：✅ / ❌ 备注：___

**截图**（7 张）：`screenshots/VL-9b-D1D2-budget-progress.png`、`…transfer-empty.png`、`…transfer-filled.png`、`…transfer-error-same.png`、`…transfer-error-balance.png`、`…transfer-success.png`、`…monthly-report.png`

---

## 4️⃣ VL-8b — 任务矩阵双布局（AVD 即可）

**前置**：建 6 个任务（高/中/低各 2，至少 2 个有 dueTime，1 个无截止）。
**做什么**：
```
adb shell wm size 1080x1920   # 小屏
adb shell wm size 2400x1600   # 大屏
```
进任务页 → 切矩阵视图 → 小屏截图 → 滑到底部截图 → 大屏截图 → 点开未排期折叠 → 切回列表。

**结果**：
- 小屏 5 区布局：✅ / ❌ 备注：___
- 大屏 2×2 布局：✅ / ❌ 备注：___
- 未排期独立：✅ / ❌ 备注：___
- toggle 不丢位：✅ / ❌ 备注：___

**截图**（5 张）：`screenshots/VL-8b-small-portrait-top.png`、`…small-portrait-bottom.png`、`…large-landscape.png`、`…unscheduled-expanded.png`、`…list-view-compare.png`

---

## 5️⃣ VL-9c-sw600 — 平板布局（AVD 即可）

**前置**：
```cmd
avdmanager create avd -n pm_sw600 -k "system-images;android-36;google_apis;x86_64" -d pixel_tablet
emulator -avd pm_sw600 &
adb shell wm size 1600x2560
```

**做什么**：4 个 Tab 各截图（首页 / 任务 / 记账 / 日历 / 课表）+ 横竖屏各一遍。

**结果**：
- 任务页 sw600+：✅ / ❌ 备注：___
- 记账页 sw600+：✅ / ❌ 备注：___
- 日历页 sw600+：✅ / ❌ 备注：___
- 课表页 sw600+：✅ / ❌ 备注：___
- 4 Tab 切换流畅：✅ / ❌ 备注：___

**截图**（6 张）：`screenshots/VL-9c-sw600-home.png`、`…tasks.png`、`…finance.png`、`…calendar.png`、`…timetable.png`、`…landscape.png`

---

## 📝 汇总记录

| 编号 | 结果 | 关键发现 / 备注 |
|---|---|---|
| VL-8a | ___ | ___ |
| VL-9b-B2 | ___ | ___ |
| VL-9b-D1D2 | ___ | ___ |
| VL-8b | ___ | ___ |
| VL-9c-sw600 | ___ | ___ |

---

## ✅ 跑完之后

1. 截图都放进 `docs/manual-verification/screenshots/` 目录（该目录已 gitignore，截图不上库）
2. 把汇总表填好
3. 把结果**直接发我**，我帮你同步到 DEV_LOG + 升级状态 🟨 → ✅
4. 任一项 ❌ → 我立项 Phase 11 修

## 🔧 跑中卡住怎么办

- **AVD 起不来** → `adb kill-server` + 删除 `~/.android/avd/Pixel*.avd` + 重启；或换 `pm_ui34`
- **截图命令** → Android Studio 顶部 `Camera` 图标 / 真机电源+音量下
- **通知不触发** → 检查系统设置 → 通知 → 应用权限 + 省电优化白名单
- **想跳过某项** → 在汇总表写"跳过：<原因>"，下次再补

---

> **生成信息**：2026-09-17（Phase 10 · 10c VL-1b — 1 页速查版）
> **生成者**：昔涟（Cyrene）自动生成
> **对应详细清单**：`docs/manual-verification/phase10-visual-checklist.md`（如需完整步骤 / 预期说明看这份）
> **状态**：🟨（清单已生成，**待伙伴执行**）