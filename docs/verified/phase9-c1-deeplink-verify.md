# Phase 9 9c C1 —— 通知 deeplink 4 路由自动化验证（复跑证据）

**执行时间**：2026-09-16 14:20（Asia/Shanghai）
**执行分支**：`feature/phase-9-verification`
**设备**：AVD `emulator-5554`（`sdk_gphone64_x86_64` / `emu64xa`，`pm_ui36`）
**APK**：`app/build/outputs/apk/debug/app-debug.apk`（`:app:assembleDebug` BUILD SUCCESSFUL in 14s，与当前 HEAD 一致）

## 与 Phase 8 T6/8a 的关系

本证据是 Phase 8 `f746ca6`（`docs/notif-deeplink-evidence/`）的**同口径复跑**：
同 4 条路由、同 `am start` intent 形态、同 `uiautomator dump` 取树方式。
Phase 9 复跑的意义：确认 9a/9b 的生产改动（`PersonalAssistantApp.onCreate` 接线、
`FinanceScreen` 接入 `AccountSwitcherBar`、备份模块接口拆分）**未破坏 deeplink 路由链路**。

## 触发方式（同 Phase 8 口径）

deeplink **不走 URL scheme**，用 explicit Intent + extras：

```
adb shell am force-stop com.example.personalassistant.debug
adb shell am start -n com.example.personalassistant.debug/com.example.personalassistant.MainActivity \
    --es nav_route '<route>' \
    --es deeplink_source '<source>' \
    --activity-clear-top
adb shell uiautomator dump /sdcard/<name>.xml
adb pull /sdcard/<name>.xml <out>
```

一键复跑脚本：`scripts/c1_deeplink_verify.bat`
取树文本抽取脚本：`scripts/c1_extract_text.ps1`

## 4 个场景实测结果（本轮真跑）

| # | 场景 | nav_route | deeplink_source | 落到页面 | UI 树实测文本 | 结果 |
|---|---|---|---|---|---|---|
| 1 | 任务详情 | `tasks/detail/1` | `task_reminder` | 任务详情页 | 「任务详情」 | ✅ |
| 2 | 任务编辑 | `tasks/edit/1` | `task_reminder` | 任务编辑页 | 「标题」「编辑任务」「保存」 | ✅ |
| 3 | 日程编辑 | `calendar/edit/1` | `event_reminder` | 事件占位/详情页 | 「事件不存在（id=1，可能已被删除）」「事件详情」 | ✅ |
| 4 | 课程编辑 | `timetable/edit/1` | `course_reminder` | 课程编辑页 | 「课程名称\*」「教师」「地点」「新增课程」 | ✅ |

**4/4 命中目标页面**，每棵树的 `package` 均为 `com.example.personalassistant.debug`（未串到系统弹窗）。

**证据文件**（同目录）：
- `c1-ui-task1.xml`（6372 B）
- `c1-ui-task-edit.xml`（10069 B）
- `c1-ui-event-edit.xml`（6783 B）
- `c1-ui-course-edit.xml`（8967 B）

## 复跑过程中的环境事实（诚实记录）

1. **AVD 是活的**（本轮开始时 `emulator.exe` + `qemu-system-x86_64.exe` 均在，未冷启动 —— 与 Phase 8 关闭时的 AVD 是同一个）。
2. **`adb` 不在 PATH** —— 需用绝对路径 `D:\Software\Code\Android\Sdk\platform-tools\adb.exe`（SDK 路径取自 `local.properties` 的 `sdk.dir`）。
3. **首次启动撞通知权限弹窗** —— 新装 APK 首启时系统弹 `POST_NOTIFICATIONS` 授权对话框，UI 树会抓成 `com.google.android.permissioncontroller`。已用
   `adb shell pm grant ... POST_NOTIFICATIONS` + `appops set ... POST_NOTIFICATION allow` 预先授权后重跑。
   **规则**：后续 AVD 自动化前置加一步权限预授权。
4. **`timeout` 命令在重定向/管道环境下报 `Input redirection is not supported`** —— 脚本内延时改用 `ping -n N 127.0.0.1 >nul`。
5. **cmd 下带引号的可执行路径 + 管道会解析失败** —— 改用 `cd /d <sdk platform-tools> && adb.exe ...` 形态。

## 数据状态说明（不虚标）

本轮 4 条路由的 id 均为 1，**AVD 上处于无数据/占位状态**，所以：
- 场景 1 只渲染出标题（「任务详情」），未展开完整详情字段
- 场景 2 / 3 渲染的是「不存在」占位分支

这**不影响路由验证结论**（路由正确路由到了目标 Composable，而非仍停在首页），
但与 Phase 8 原证据同属「路由层验证」。**带真实数据的完整渲染**属真人目测范畴，留 Phase 10。

## 未覆盖维度（延续 Phase 8 登记，未变）

- 真实通知触发链（`BootReceiver` + `WorkManager` + `NotificationManager` → 用户点通知）→ 未覆盖，
  deeplink 的 intent 路径本身已由本证据覆盖。
- `onNewIntent`（应用在后台时的热启动重路由）→ 本轮仍走 `--activity-clear-top` 等价路径。