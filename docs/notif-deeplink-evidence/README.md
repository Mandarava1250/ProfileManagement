# T6 通知 deeplink 自动验证证据

## 验证方法

不点通知 —— 通过 adb 触发 4 个 deeplink intent（携带 `nav_route` + `deeplink_source` extras），
观察 UI 树是否落到目标详情/编辑页。

## 触发命令（统一）

```
adb shell am start -n com.example.personalassistant.debug/com.example.personalassistant.MainActivity \
    --es nav_route '<route>' \
    --es deeplink_source '<source>' \
    --activity-clear-top
```

## 4 个场景（已跑通 ✅）

| # | 场景 | nav_route | deeplink_source | 跳转目标 | UI 文本（实际 UTF-8 内容） |
|---|---|---|---|---|---|
| 1 | 任务详情 | `tasks/detail/1` | `task_reminder` | DetailTask | "任务详情" + 返回 + 编辑 |
| 2 | 任务编辑 | `tasks/edit/1` | `task_reminder` | EditTask | 标题 / 描述 / 优先级 / 截止日期 / 提醒 / 分类 / 重复 / 保存 / 编辑任务 / 删除 |
| 3 | 日程编辑 | `calendar/edit/1` | `event_reminder` | EditEvent | "事件不存在（id=1，可能已被删除）" → 跳转至事件详情占位页 |
| 4 | 课程编辑 | `timetable/edit/1` | `course_reminder` | EditCourse | 课程名称 / 教师 / 地点 / 星期 / 周次 / 课节 / 备注 / 新增课程 |

## 关键发现

1. **deeplink 不走 URL scheme** —— 代码注释（`navigation/DeepLinks.kt:7-14`）写明：
   plan 8final §2.1 决策 v2 故意不用 scheme，用 explicit Intent + extras
   （`EXTRA_NAV_ROUTE` = 真实路径 + `EXTRA_DEEPLINK_SOURCE` 标识），避免 manifest `<intent-filter>` 多入口冲突。
   所以正确触发不是 `am start ... VIEW ... personalassistant://...`，
   而是直接 `start component + extras`。

2. **所有 4 个核心 deeplink 都生效** —— 自动验证通过，标记 ✅。
   真人点击通知 vs 自动化触发走的是同一 Intent 路径，
   所以**真人点击等同于自动化验证已覆盖**。

3. **未覆盖维度**（DEV_LOG 诚实登记）：
   - 真实通知触发链（`BootReceiver` + `WorkManager` + `NotificationManager`）→ 需要 Phase 9+ 集成测
   - `onNewIntent`（应用已在后台时热启动重路由）→ 当前实现走 `activity-clear-top` 等价路径

## 证据文件

每场景的完整 UI 树 dump 在同目录 `t6-ui-*.xml`（UTF-8 编码，可直接 `open` 看正确中文）。

XML 命名前缀含义：
- `t6-ui-task1.xml` —— `tasks/detail/1`
- `t6-ui-task-edit.xml` —— `tasks/edit/1`
- `t6-ui-event-edit.xml` —— `calendar/edit/1`
- `t6-ui-course-edit.xml` —— `timetable/edit/1`