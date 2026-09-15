# 通知 Deeplink 真机验证证据（P7 · 2026-09-15 10:50）

> **状态**：进行中。已发现**重要的负面证据** —— JVM 单测通过不代表真机 deeplink 跳转通过。

## 1. 验证环境

- **AVD**：`emulator-5554`（pm_ui36，Android 16，SDK 36）
- **AVD 状态**：System UI 间歇性 ANR，但 App 进程稳定（pid 4512 在跑）
- **App 包**：`com.example.personalassistant.debug`（`build.gradle` 启用 `.debug` suffix）
- **App 版本**：当前 `Develop @ 4abfaad`（8c.1 真跑修复后）
- **验证方式**：绕过真实通知触发，直接用 ADB `am start` 模拟通知点击携带的 Intent extras（`nav_route` + `deeplink_source`）

## 2. 验证命令

**冷启动（启动 app 同时带 deeplink extras）**：

```bash
adb shell am start -a android.intent.action.VIEW \
  -n com.example.personalassistant.debug/com.example.personalassistant.MainActivity \
  --es nav_route "task/1" \
  --es deeplink_source "task_reminder"
```

**热启动（app 已在运行时带 extras 触发 onNewIntent）**：

```bash
adb shell am start -n com.example.personalassistant.debug/com.example.personalassistant.MainActivity \
  --es nav_route "task/1" \
  --es deeplink_source "task_reminder" \
  -f 0x10000000
```

## 3. 实际结果

### 3.1 冷启动

| 项 | 结果 |
|---|---|
| App 启动 | ✅ MainActivity 成为 topResumedActivity |
| App 包 | ✅ `com.example.personalassistant.debug` |
| 实际落点 | ❌ **课表 Tab**（"还没有学期"空态页），**不是** task/1 详情页 |
| 预期落点 | task/{id} 详情页 |

### 3.2 热启动

| 项 | 结果 |
|---|---|
| Intent 触发 | ✅ `Starting: Intent { ... (has extras) }` |
| 实际落点 | 待 dump UI 验证（与冷启动一致推测） |
| 预期落点 | task/{id} 详情页 |

## 4. 负面发现分析

**JVM 单测通过 ≠ 真机跳转通过**。

- ✅ `DeepLinksTest` 21 / 0 失败（解析逻辑 100% 覆盖）
- ❌ AVD 上 `am start --es nav_route "task/1"` 没让 UI 跳转到详情页**

可能原因（待排查）：

1. **App 启动时 Compose Navigation 还没初始化好**，pendingNavRoute 被 LaunchedEffect 错过
2. **MainActivity 的 onCreate 读 extras 时机不对**（extras 写入 pendingNavRoute 但 Compose tree 已经 Composable 了第一次 composition）
3. **AppNavHost 的白名单校验没把 "task/{id}" 列入**
4. **extras 类型转换错误**（plan v2 §2.2 提到的"参数完整性"维度未真机验证）
5. **App 收到 Intent 后用了错误的处理路径**（MainActivity vs AppNavHost 时序）

## 5. 现有证据文件

| 文件 | 内容 |
|---|---|
| `ui_deeplink.xml` | 冷启动后 UI dump（12299 字节）—— 显示 App 在课表 Tab，非任务详情 |
| `ui_task.xml` | 热启动后 UI dump（待确认） |
| `ui.xml`（之前） | 显示 App 桌面 launcher 状态（非 app） |

## 6. 下一步

### 6.1 真机调试建议

- 读 `MainActivity.kt` 看 onCreate 怎么处理 `getIntent()` extras
- 读 `AppNavHost.kt` 看 `LaunchedEffect(pendingNavRoute)` 触发时机
- 读 `Screen.kt` 看 task/xxx 是否在 nav route 白名单里
- 加 `Log.d("DeepLink", ...)` 跟踪 pendingNavRoute 的生命周期

### 6.2 真机验证补全

需要在 AVD 上分别验证 3 类提醒（task / event / course）× 冷/热启动 = 6 种场景：

| # | 场景 | 期望落点 | 当前状态 |
|---|---|---|---|
| 1 | task · 冷启动 | task/{taskId} 详情 | ❌ 落到课表 |
| 2 | task · 热启动 | task/{taskId} 详情 | ❓ 未验证 |
| 3 | event · 冷启动 | event/{eventId} 详情 | ⬜ 未验证 |
| 4 | event · 热启动 | event/{eventId} 详情 | ⬜ 未验证 |
| 5 | course · 冷启动 | course/{courseId} 详情 | ⬜ 未验证 |
| 6 | course · 热启动 | course/{courseId} 详情 | ⬜ 未验证 |

### 6.3 真实通知触发验证

目前只验证了 Intent extras 的传递，**没有验证真实通知的 PendingIntent**。
需要：
1. 在 app 内创建 task + 设置提醒
2. 触发通知触发
3. 点击通知（不是直接 am start）
4. 截图通知 + 详情页

## 7. DEV_LOG 状态

- **P7 行**：保持 🟨
- **本轮发现**：deeplink 在真实 AVD 上**没正确跳转**，与 JVM 单测预期不一致 → 需要修代码 + 重跑真机
- **下一轮**：先修 deeplink bug，再继续 3 类 × 2 启动 = 6 场景验证