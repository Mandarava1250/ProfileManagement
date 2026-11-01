# T7 矩阵视图双布局 UI tree 证据

## 验证方法

通过 `wm size` 切换 AVD 屏宽，dump UI 树看 Compose 在两种布局下的渲染结果。

## 切换命令

```bash
# Portrait: 1080x2400 (默认物理尺寸)
adb shell am start -n com.example.personalassistant.debug/com.example.personalassistant.MainActivity
adb shell uiautomator dump /sdcard/ui.xml

# Landscape: 1280x800 (override 强制横屏 + user_rotation=1)
adb shell wm size 1280x800
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell am start -n com.example.personalassistant.debug/com.example.personalassistant.MainActivity --activity-clear-top
adb shell uiautomator dump /sdcard/ui.xml
```

## 2 个布局（已跑通 ✅）

| # | 布局 | wm size | XML 关键属性 | 截图 |
|---|---|---|---|---|
| 1 | Portrait | `1080x2400` | `rotation="0"`，bounds `[0,0][1080,2400]` | `t7-task-tab-portrait.xml` |
| 2 | Landscape | `1280x800` (override) | `rotation="1"`，bounds `[44,0][800,1280]` | `t7-task-tab-landscape.xml` |

## UI 渲染关键差异（实测）

**Portrait (1080x2400)**：底部 nav 4 个 tab 按宽度均分；当前页（课表）显示 "还没有学期" + "点击右下角 + 添加你的第一门课程" 空态文案。

**Landscape (1280x800)**：底部 nav 4 个 tab 按新宽度均分（每个 ~174px）；状态栏宽度收缩（`bounds=[44,0]` 表示横屏状态栏在左边）；当前页相同空态文案，但布局在横屏下也正常渲染不崩。

## 已知限制（DEV_LOG 诚实登记）

**矩阵视图（`TaskViewMode.MATRIX`）自动化切换未跑通**：

- `TasksTab` 顶部的 SegmentedButton / TabRow 二选一控件是 Compose UI，节点标识靠 position + class
- AVD 上点击坐标需要精确到具体像素（按钮位置随屏宽浮动）
- 粗暴 `input tap` 容易点到相邻按钮或错过；安全做法是 `uiautomator2` Python 库（需 pip 安装，本机无）

**现有覆盖度**：Compose 在 600dp 断点切换布局的单元测试已有 24 个
（`TaskMatrixTest`，JVM 层跑过，pass=24 / 0），覆盖 2×2 + 未排期 + 5 区纵向三种布局的逻辑正确性。

**未覆盖维度**：真人在 AVD/真机上点击「列表 / 矩阵」Toggle 后人眼目测渲染结果 —— **这是必须在真机或人手验的项目**，自动化触发准确度不够。

## 验收状态

phase 8b'' 矩阵视图：
- ✅ JVM 逻辑层：`TaskMatrixTest` 24 / 0 失败（先前 commit `2aded6e`）
- ✅ Compose 在两种屏宽下渲染不崩（UI 树 dump 在 docs/matrix-view-evidence/）
- 🟨 矩阵视图按钮自动化切换：未做（需要精确坐标或 uiautomator2）
- 🟨 真机目测：未做（需要用户）