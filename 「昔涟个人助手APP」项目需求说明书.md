# 多功能个人助手 APP — 项目需求说明书

> **应用名称**：个人助手（Personal Assistant）  
> **平台**：Android 8.0+ (API 26+)  
> **网络需求**：纯离线，无需网络权限  
> **目标用户**：学生、自由职业者、个人财务管理用户  
> **核心模块**：课表管理、任务待办、记账理财、日历日程

---

## 目录

1. [项目概述](#一项目概述)
2. [技术栈选型](#二技术栈选型)
3. [推荐项目结构](#三推荐项目结构clean-architecture)
4. [数据库设计](#四数据库设计room)
5. [功能模块详细设计](#五功能模块详细设计)
6. [关键实现注意事项](#六关键实现注意事项)
7. [开发阶段建议](#七开发阶段建议)
8. [Gradle 依赖参考](#八gradle-依赖参考)

---

## 一、项目概述

| 项目 | 说明 |
|------|------|
| **应用名称** | 个人助手（暂定） |
| **平台** | Android 8.0+ (API 26+) |
| **网络需求** | 纯离线，无需网络权限 |
| **目标用户** | 学生、自由职业者、个人财务管理用户 |
| **核心模块** | 课表管理、任务待办、记账理财、日历日程 |

### 离线特性声明

- ❌ `INTERNET` — 不需要网络权限
- ❌ `ACCESS_NETWORK_STATE` — 不需要网络状态权限
- ✅ 仅需：`WRITE_EXTERNAL_STORAGE`（可选，用于数据备份导出到本地）

---

## 二、技术栈选型

### 2.1 开发语言与框架

| 层级 | 技术选型 | 说明 |
|------|----------|------|
| **编程语言** | Kotlin | Google 官方首选，协程支持优秀 |
| **UI 框架** | Jetpack Compose | 声明式 UI，开发效率高，Material Design 3 |
| **架构模式** | MVVM + Clean Architecture | 关注点分离，便于测试和维护 |
| **依赖注入** | Hilt (Dagger) | 编译期生成，性能优秀 |
| **异步处理** | Kotlin Coroutines + Flow | 响应式数据流，替代 RxJava |
| **导航** | Jetpack Navigation Compose | 单 Activity 架构，类型安全导航 |

### 2.2 数据持久化

| 组件 | 用途 | 说明 |
|------|------|------|
| **Room** | 主数据库 | SQLite ORM，支持编译期 SQL 校验 |
| **DataStore** | 偏好设置 | 替代 SharedPreferences，类型安全 |
| **SQLite FTS** | 搜索功能 | 全文搜索（任务、记账备注） |

### 2.3 第三方库

| 库 | 用途 |
|----|------|
| **MPAndroidChart** | 记账统计图表（饼图、折线图、柱状图） |
| **Kotlinx Serialization** | 数据序列化（备份导出） |
| **Material Icons Extended** | 丰富图标资源 |

---

## 三、推荐项目结构（Clean Architecture）

采用 **功能模块化 + 分层架构**，按模块组织代码：

```
app/
├── src/main/java/com/example/personalassistant/
│   ├── MainActivity.kt
│   ├── PersonalAssistantApp.kt          // Application 类
│   ├── navigation/
│   │   └── AppNavHost.kt               // 全局导航图
│   │
│   ├── core/                           // 核心公共层
│   │   ├── database/
│   │   │   ├── AppDatabase.kt
│   │   │   ├── converters/             // 类型转换器（Date, List等）
│   │   │   └── dao/                    // 所有 DAO 接口
│   │   ├── di/                         // Hilt 模块
│   │   ├── ui/
│   │   │   ├── components/             // 通用 UI 组件
│   │   │   └── theme/                  // 主题、颜色、字体
│   │   └── utils/
│   │       ├── DateUtils.kt
│   │       ├── CurrencyFormatter.kt
│   │       └── NotificationHelper.kt
│   │
│   ├── feature/
│   │   ├── timetable/                  // 课表模块
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── TimetableDao.kt
│   │   │   │   │   └── TimetableEntity.kt
│   │   │   │   └── repository/
│   │   │   │       └── TimetableRepositoryImpl.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   └── Course.kt
│   │   │   │   ├── repository/
│   │   │   │   │   └── TimetableRepository.kt
│   │   │   │   └── usecase/
│   │   │   │       ├── GetCoursesUseCase.kt
│   │   │   │       ├── AddCourseUseCase.kt
│   │   │   │       └── DeleteCourseUseCase.kt
│   │   │   └── presentation/
│   │   │       ├── TimetableScreen.kt
│   │   │       ├── TimetableViewModel.kt
│   │   │       ├── TimetableUiState.kt
│   │   │       └── components/
│   │   │           ├── CourseCard.kt
│   │   │           └── WeekView.kt
│   │   │
│   │   ├── task/                       // 任务模块
│   │   │   ├── data/
│   │   │   ├── domain/
│   │   │   └── presentation/
│   │   │
│   │   ├── finance/                    // 记账模块
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── FinanceDao.kt
│   │   │   │   │   ├── TransactionEntity.kt
│   │   │   │   │   ├── CategoryEntity.kt
│   │   │   │   │   ├── MonthlyBudgetEntity.kt
│   │   │   │   │   └── DailyPoolEntity.kt
│   │   │   │   └── repository/
│   │   │   │       └── FinanceRepositoryImpl.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── Transaction.kt
│   │   │   │   │   ├── Category.kt
│   │   │   │   │   ├── MonthlyBudget.kt
│   │   │   │   │   └── DailyPool.kt
│   │   │   │   ├── repository/
│   │   │   │   │   └── FinanceRepository.kt
│   │   │   │   └── usecase/
│   │   │   │       ├── AddTransactionUseCase.kt
│   │   │   │       ├── GetDailyPoolUseCase.kt
│   │   │   │       ├── RefreshDailyPoolUseCase.kt
│   │   │   │       └── GetMonthlyReportUseCase.kt
│   │   │   └── presentation/
│   │   │       ├── FinanceScreen.kt
│   │   │       ├── FinanceViewModel.kt
│   │   │       ├── QuickAddScreen.kt
│   │   │       └── components/
│   │   │           ├── DailyPoolCard.kt
│   │   │           └── CategoryGrid.kt
│   │   │
│   │   ├── calendar/                   // 日历日程模块
│   │   │   ├── data/
│   │   │   ├── domain/
│   │   │   └── presentation/
│   │   │
│   │   └── settings/                   // 设置模块
│   │       ├── data/
│   │       └── presentation/
│   │
│   └── service/                        // 后台服务
│       └── reminder/
│           ├── ReminderWorker.kt       // WorkManager 定时提醒
│           └── BootReceiver.kt         // 开机重设定时器
│
├── src/main/res/
│   └── values/
│       ├── colors.xml
│       └── themes.xml
│
└── build.gradle.kts
```

### 结构说明

| 层级 | 职责 |
|------|------|
| **Presentation** | Compose UI、ViewModel、UiState |
| **Domain** | 业务模型、Repository 接口、UseCase（纯 Kotlin，无 Android 依赖） |
| **Data** | Room Entity、DAO、Repository 实现、数据映射 |
| **Core** | 跨模块共享的数据库、工具类、主题 |

---

## 四、数据库设计（Room）

### 4.1 实体关系图（ERD）

```
+-----------------+       +-----------------+       +-----------------+
|     Course      |       |     Task        |       |  Transaction    |
+-----------------+       +-----------------+       +-----------------+
| PK id: Long     |       | PK id: Long     |       | PK id: Long     |
| name: String    |       | title: String   |       | amount: Double  |
| teacher: String |       | description: Str|       | type: Int       |
| location: String|       | dueDate: Long   |       | categoryId: Long|<--+
| dayOfWeek: Int  |       | priority: Int   |       | date: Long      |   |
| startTime: Long |       | isCompleted: Bool|      | note: String    |   |
| endTime: Long   |       | createdAt: Long |       | createdAt: Long |   |
| color: Int      |       | category: String|       +-----------------+   |
| semester: String|       | reminderTime: Lg|                             |
+-----------------+       +-----------------+       +-----------------+   |
                                                    |    Category     |   |
+-----------------+       +-----------------+       +-----------------+   |
|     Event       |       |   Semester      |       | PK id: Long     |   |
+-----------------+       +-----------------+       | name: String    |   |
| PK id: Long     |       | PK id: Long     |       | type: Int       |   |
| title: String   |       | name: String    |       | iconName: String|   |
| date: Long      |       | startDate: Long |       | color: Int      |---+
| isAllDay: Bool  |       | endDate: Long   |       | isDefault: Bool |
| startTime: Long?|       | isCurrent: Bool |       +-----------------+
| endTime: Long?  |       +-----------------+
| color: Int      |
| reminder: Long? |
+-----------------+

+-------------------------+       +-------------------------+
|    MonthlyBudget        |       |      DailyPool          |
+-------------------------+       +-------------------------+
| PK id: Long             |       | PK id: Long             |
| yearMonth: String       |       | date: Long              |
| totalAmount: Double     |       | dailyBudget: Double     |
| dailyAmount: Double     |       | remainingAmount: Double |
| createdAt: Long         |       | totalSpentToday: Double |
+-------------------------+       | lastUpdated: Long       |
                                  +-------------------------+
```

### 4.2 详细表结构

#### 4.2.1 课程表 `courses`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, Auto | 主键 |
| `name` | String | NOT NULL | 课程名称 |
| `teacher` | String | | 教师姓名 |
| `location` | String | | 上课地点 |
| `dayOfWeek` | Int | NOT NULL | 星期几 (1-7) |
| `startTime` | Long | NOT NULL | 开始时间（当天分钟数，如 8:00 = 480） |
| `endTime` | Long | NOT NULL | 结束时间 |
| `color` | Int | | 课程卡片颜色 |
| `semesterId` | Long | FK | 所属学期 |
| `weekPattern` | String | | 周次模式，如 "1,2,3,4,5,6,7,8" |

#### 4.2.2 任务 `tasks`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, Auto | 主键 |
| `title` | String | NOT NULL | 任务标题 |
| `description` | String | | 详细描述 |
| `dueDate` | Long | NOT NULL | 截止日期 (timestamp) |
| `dueTime` | Long | | 截止时间 (分钟数) |
| `priority` | Int | NOT NULL | 优先级 (0=低, 1=中, 2=高) |
| `isCompleted` | Boolean | DEFAULT false | 是否完成 |
| `category` | String | | 分类标签 |
| `createdAt` | Long | NOT NULL | 创建时间 |
| `completedAt` | Long | | 完成时间 |
| `reminderTime` | Long | | 提醒时间 |

#### 4.2.3 记账记录 `transactions`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, Auto | 主键 |
| `amount` | Double | NOT NULL | 金额 |
| `type` | Int | NOT NULL | 0=支出, 1=收入 |
| `categoryId` | Long | FK | 关联分类 |
| `date` | Long | NOT NULL | 交易日期 |
| `note` | String | | 备注 |
| `createdAt` | Long | NOT NULL | 创建时间 |

#### 4.2.4 记账分类 `categories`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, Auto | 主键 |
| `name` | String | NOT NULL | 分类名称 |
| `type` | Int | NOT NULL | 0=支出分类, 1=收入分类 |
| `iconName` | String | | 图标标识 |
| `color` | Int | | 颜色 |
| `isDefault` | Boolean | DEFAULT false | 是否内置分类 |
| `sortOrder` | Int | DEFAULT 0 | 排序 |

#### 4.2.5 日程事件 `events`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, Auto | 主键 |
| `title` | String | NOT NULL | 事件标题 |
| `date` | Long | NOT NULL | 日期 |
| `isAllDay` | Boolean | DEFAULT false | 是否全天 |
| `startTime` | Long | | 开始时间 |
| `endTime` | Long | | 结束时间 |
| `color` | Int | | 颜色标记 |
| `reminderMinutes` | Int | | 提前提醒分钟数 |

#### 4.2.6 学期 `semesters`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, Auto | 主键 |
| `name` | String | NOT NULL | 学期名称 |
| `startDate` | Long | NOT NULL | 开始日期 |
| `endDate` | Long | NOT NULL | 结束日期 |
| `isCurrent` | Boolean | DEFAULT false | 是否为当前学期 |

#### 4.2.7 月预算 `monthly_budgets`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, Auto | 主键 |
| `yearMonth` | String | NOT NULL, UNIQUE | 年月标识，如 "2026-09" |
| `totalAmount` | Double | NOT NULL | 月可支配总金额 |
| `dailyAmount` | Double | NOT NULL | 日均预算（缓存，刷新时更新） |
| `isAutoRefresh` | Boolean | DEFAULT true | 是否启用动态刷新 |
| `createdAt` | Long | NOT NULL | 创建时间 |
| `updatedAt` | Long | NOT NULL | 最后更新时间 |

#### 4.2.8 每日零钱池 `daily_pools`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, Auto | 主键 |
| `date` | Long | NOT NULL, UNIQUE | 日期（当天 00:00 时间戳） |
| `dailyBudget` | Double | NOT NULL | 当日预算金额 |
| `remainingAmount` | Double | NOT NULL | 当日剩余金额（可为负数） |
| `totalSpentToday` | Double | NOT NULL | 当日总支出 |
| `totalIncomeToday` | Double | DEFAULT 0 | 当日总收入 |
| `carryOver` | Double | DEFAULT 0 | 昨日结转金额 |
| `lastUpdated` | Long | NOT NULL | 最后更新时间 |

### 4.3 Room 数据库定义示例

```kotlin
@Database(
    entities = [
        CourseEntity::class,
        TaskEntity::class,
        TransactionEntity::class,
        CategoryEntity::class,
        EventEntity::class,
        SemesterEntity::class,
        MonthlyBudgetEntity::class,
        DailyPoolEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(LocalDateTimeConverter::class, StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun taskDao(): TaskDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun eventDao(): EventDao
    abstract fun semesterDao(): SemesterDao
    abstract fun financeDao(): FinanceDao
}
```

### 4.4 零钱池相关 DAO 接口

```kotlin
@Dao
interface FinanceDao {
    // ========== 月预算 ==========
    @Query("SELECT * FROM monthly_budgets WHERE yearMonth = :yearMonth")
    suspend fun getMonthlyBudget(yearMonth: String): MonthlyBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMonthlyBudget(budget: MonthlyBudgetEntity)

    // ========== 每日零钱池 ==========
    @Query("SELECT * FROM daily_pools WHERE date = :date")
    suspend fun getDailyPool(date: Long): DailyPoolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyPool(pool: DailyPoolEntity)

    @Query("UPDATE daily_pools SET remainingAmount = remainingAmount - :amount, totalSpentToday = totalSpentToday + :amount, lastUpdated = :now WHERE date = :date")
    suspend fun deductFromPool(date: Long, amount: Double, now: Long)

    @Query("UPDATE daily_pools SET remainingAmount = remainingAmount + :amount, totalIncomeToday = totalIncomeToday + :amount, lastUpdated = :now WHERE date = :date")
    suspend fun addIncomeToPool(date: Long, amount: Double, now: Long)

    // ========== 统计查询 ==========
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 0 AND date >= :monthStart AND date < :monthEnd")
    suspend fun getMonthlyExpense(monthStart: Long, monthEnd: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 1 AND date >= :monthStart AND date < :monthEnd")
    suspend fun getMonthlyIncome(monthStart: Long, monthEnd: Long): Double?

    @Query("SELECT categoryId, SUM(amount) as total FROM transactions WHERE type = 0 AND date BETWEEN :start AND :end GROUP BY categoryId")
    fun getExpenseByCategory(start: Long, end: Long): Flow<List<CategorySum>>
}
```

---

## 五、功能模块详细设计

### 5.1 课表管理模块

#### 功能列表

| 功能 | 描述 |
|------|------|
| **周视图展示** | 以网格形式展示一周课程，支持横向滑动切换周次 |
| **课程添加/编辑** | 填写课程名、教师、地点、时间、颜色 |
| **冲突检测** | 添加课程时自动检测时间冲突 |
| **多学期管理** | 支持创建多个学期，切换当前学期 |
| **周次设置** | 设置单双周、指定周次上课 |
| **课程提醒** | 上课前 N 分钟提醒 |

#### UI 设计要点
- 使用 `LazyVerticalGrid` 或自定义 Canvas 绘制时间网格
- 左侧显示时间轴（08:00 - 22:00）
- 顶部显示星期一到星期日
- 课程卡片按时间定位，支持重叠检测

---

### 5.2 任务待办模块

#### 功能列表

| 功能 | 描述 |
|------|------|
| **任务列表** | 按日期分组展示（今天、明天、未来、已完成） |
| **快速添加** | 底部输入框一键添加任务 |
| **优先级标记** | 高/中/低三级，不同颜色标识 |
| **子任务** | 支持添加子任务清单 |
| **重复任务** | 每日/每周/每月重复 |
| **筛选排序** | 按优先级、截止日期、分类筛选 |
| **完成统计** | 今日完成率、历史完成趋势 |

#### 数据模型扩展

```kotlin
data class Task(
    val id: Long,
    val title: String,
    val subTasks: List<SubTask>,  // 1:N 关系
    val priority: Priority,
    val isCompleted: Boolean,
    val dueDate: LocalDateTime?
)

data class SubTask(
    val id: Long,
    val parentTaskId: Long,
    val title: String,
    val isCompleted: Boolean
)
```

---

### 5.3 记账理财模块

#### 5.3.1 核心功能列表

| 功能 | 描述 |
|------|------|
| **快速记账** | 选择分类 -> 输入金额 -> 保存（3步完成） |
| **收支分类** | 内置常见分类（餐饮、交通、购物等），支持自定义 |
| **统计图表** | 饼图（分类占比）、折线图（趋势）、柱状图（对比） |
| **月度报表** | 每月收支汇总、预算超支提醒 |
| **账单搜索** | 按关键词、金额范围、日期范围搜索 |
| **数据导出** | 导出为 CSV/JSON 文件到本地存储 |

#### 5.3.2 每日零钱池（核心特色功能）

##### 功能概述
用户设定每月可支配金额后，系统自动按当月剩余天数动态均分日预算。每日显示"今日零钱池"，消费后实时扣除，支持超支（可为负数）。每天凌晨或重新打开 App 时自动刷新零钱池。

##### 业务规则

| 规则 | 说明 |
|------|------|
| **月预算设定** | 用户每月初（或任意时间）设定当月可支配总金额 |
| **动态日预算** | 日预算 = (月总预算 - 本月已支出总额) / 当月剩余天数 |
| **零钱池显示** | 首页/记账页顶部常驻显示"今日零钱池：¥XX.XX" |
| **消费扣除** | 记录支出时，自动从当日零钱池扣除对应金额 |
| **收入处理** | 记录收入时，可选择是否补充到当日零钱池 |
| **跨天刷新** | 每天首次打开 App 或跨天时，重新计算日预算并生成新的零钱池 |
| **负数支持** | 零钱池可为负数，表示当日已超支 |
| **昨日结转** | 支持配置是否将昨日剩余金额结转到今日 |

##### 零钱池计算流程

```
[用户设定月预算 M]
        |
        v
[获取本月已支出总额 S]  <-- 查询 transactions 表 type=0
        |
        v
[计算剩余预算 R = M - S]
        |
        v
[获取当月剩余天数 D]  <-- 今天到月末的天数
        |
        v
[计算日预算 B = R / D]
        |
        v
[生成/更新 DailyPool]
   - dailyBudget = B
   - remainingAmount = B + carryOver（昨日结转）
   - totalSpentToday = 0
   - carryOver = 昨日 remainingAmount（若启用结转）
```

##### 消费时扣除流程

```
[用户记录支出金额 X]
        |
        v
[插入 transaction 记录]
        |
        v
[更新 daily_pools]
   - remainingAmount -= X
   - totalSpentToday += X
   - lastUpdated = now
        |
        v
[触发 UI 刷新]  <-- Flow 自动通知 Compose 重组
```

##### 跨天刷新触发条件

| 触发时机 | 处理方式 |
|----------|----------|
| **App 冷启动** | 检查 `daily_pools.date`，若不是今天则执行刷新 |
| **WorkManager 定时任务** | 每天 00:01 执行刷新（需 `RECEIVE_BOOT_COMPLETED` 权限） |
| **用户手动刷新** | 提供下拉刷新按钮，强制重新计算 |

##### 零钱池刷新算法（UseCase）

```kotlin
class RefreshDailyPoolUseCase @Inject constructor(
    private val financeRepository: FinanceRepository
) {
    suspend operator fun invoke() {
        val today = LocalDate.now()
        val yearMonth = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 1. 获取月预算
        val monthlyBudget = financeRepository.getMonthlyBudget(yearMonth)
            ?: return // 用户未设置预算，跳过

        // 2. 获取本月已支出
        val monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthEnd = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val spent = financeRepository.getMonthlyExpense(monthStart, monthEnd) ?: 0.0

        // 3. 计算剩余预算和天数
        val remainingBudget = monthlyBudget.totalAmount - spent
        val remainingDays = today.lengthOfMonth() - today.dayOfMonth + 1

        // 4. 计算新日预算
        val dailyBudget = if (remainingDays > 0) remainingBudget / remainingDays else 0.0

        // 5. 获取昨日结转（若启用）
        val yesterday = today.minusDays(1)
        val yesterdayStart = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yesterdayPool = financeRepository.getDailyPool(yesterdayStart)
        val carryOver = if (monthlyBudget.isAutoRefresh && yesterdayPool != null) {
            yesterdayPool.remainingAmount
        } else 0.0

        // 6. 创建/更新今日零钱池
        val dailyPool = DailyPoolEntity(
            date = todayStart,
            dailyBudget = dailyBudget,
            remainingAmount = dailyBudget + carryOver,
            totalSpentToday = 0.0,
            totalIncomeToday = 0.0,
            carryOver = carryOver,
            lastUpdated = System.currentTimeMillis()
        )
        financeRepository.insertDailyPool(dailyPool)
    }
}
```

##### 消费扣除算法（UseCase）

```kotlin
class AddTransactionUseCase @Inject constructor(
    private val financeRepository: FinanceRepository
) {
    suspend operator fun invoke(transaction: Transaction) {
        // 1. 插入交易记录
        financeRepository.insertTransaction(transaction)

        // 2. 更新零钱池
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        when (transaction.type) {
            TransactionType.EXPENSE -> {
                financeRepository.deductFromPool(
                    date = todayStart,
                    amount = transaction.amount,
                    now = System.currentTimeMillis()
                )
            }
            TransactionType.INCOME -> {
                financeRepository.addIncomeToPool(
                    date = todayStart,
                    amount = transaction.amount,
                    now = System.currentTimeMillis()
                )
            }
        }
    }
}
```

##### UI 设计 — 零钱池卡片

```kotlin
@Composable
fun DailyPoolCard(
    dailyPool: DailyPool,
    onRefresh: () -> Unit
) {
    val isNegative = dailyPool.remainingAmount < 0
    val cardColor = if (isNegative) MaterialTheme.colorScheme.errorContainer 
                    else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isNegative) MaterialTheme.colorScheme.onErrorContainer 
                    else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "今日零钱池",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }

            Text(
                text = formatCurrency(dailyPool.remainingAmount),
                style = MaterialTheme.typography.headlineLarge,
                color = textColor,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "日预算 ${formatCurrency(dailyPool.dailyBudget)} | " +
                       "今日已花 ${formatCurrency(dailyPool.totalSpentToday)}",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f)
            )

            if (dailyPool.carryOver != 0.0) {
                Text(
                    text = "昨日结转 ${formatCurrency(dailyPool.carryOver)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
```

##### 零钱池设置项

| 设置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| **月可支配金额** | Double | 0.0 | 每月总预算，每月独立设置 |
| **启用动态刷新** | Boolean | true | 是否每天自动重新计算日预算 |
| **启用昨日结转** | Boolean | true | 昨日剩余金额是否自动加入今日预算 |
| **超支警告阈值** | Double | 0.0 | 零钱池低于此值时显示警告 |

#### 5.3.3 统计查询示例（Room）

```kotlin
@Query("SELECT categoryId, SUM(amount) as total FROM transactions WHERE type = 0 AND date BETWEEN :start AND :end GROUP BY categoryId")
fun getExpenseByCategory(start: Long, end: Long): Flow<List<CategorySum>>
```

---

### 5.4 日历日程模块

#### 功能列表

| 功能 | 描述 |
|------|------|
| **月视图/周视图/日视图** | 三种视图切换 |
| **事件管理** | 添加、编辑、删除日程事件 |
| **与任务联动** | 日历中显示任务截止日期 |
| **与课表联动** | 日历中显示当日课程 |
| **节假日显示** | 内置常见节假日（纯本地数据） |
| **提醒通知** | 事件前弹出通知 |

---

### 5.5 设置与数据管理

| 功能 | 描述 |
|------|------|
| **主题切换** | 浅色/深色/跟随系统 |
| **数据备份** | 导出数据库为 JSON/加密文件 |
| **数据恢复** | 从本地文件导入恢复 |
| **清除数据** | 重置所有数据（需二次确认） |
| **提醒设置** | 全局提醒开关、默认提醒时间 |
| **月预算设置** | 设定每月可支配金额 |
| **零钱池偏好** | 动态刷新开关、昨日结转开关 |

---

## 六、关键实现注意事项

### 6.1 时间处理

| 注意点 | 方案 |
|--------|------|
| 时区问题 | 全部使用 `Instant` 或 `LocalDateTime`，存储为 UTC 时间戳 |
| 课表时间 | 存储为当天分钟数（0-1439），避免日期干扰 |
| 周起始日 | 根据地区设置，默认周一为第一天 |
| **零钱池日期** | 统一使用当天 00:00 的时间戳作为日期标识 |

### 6.2 数据一致性

| 场景 | 处理方案 |
|------|----------|
| 删除分类 | 使用软删除或禁止删除有交易记录的分类 |
| 学期切换 | 课表视图自动过滤当前学期课程 |
| 重复任务 | 生成未来 N 天的实例，避免无限数据 |
| **删除支出记录** | 同步回滚 daily_pools 的 remainingAmount 和 totalSpentToday |
| **修改支出金额** | 计算差额，同步更新 daily_pools |

### 6.3 性能优化

| 优化点 | 方案 |
|--------|------|
| 大数据量查询 | Room 返回 `Flow`，配合 Compose `collectAsState()` 自动刷新 |
| 日历月视图 | 分页加载，只查询当前月数据 |
| 图表渲染 | 使用 `remember` 缓存计算结果，避免重复计算 |
| 数据库索引 | 在 `date`、`categoryId`、`dayOfWeek` 等高频查询字段加索引 |
| **零钱池查询** | `daily_pools.date` 加唯一索引，确保快速定位当日记录 |

### 6.4 离线特性保障

- **无需网络权限**：`AndroidManifest.xml` 中不声明 `INTERNET`
- **无第三方云服务**：所有数据仅存本地
- **备份方案**：通过 `Storage Access Framework` 让用户选择本地目录导出
- **数据安全**：可选支持数据库加密（SQLCipher）

### 6.5 通知与后台

| 功能 | 技术方案 |
|------|----------|
| 定时提醒 | `WorkManager` + `AlarmManager`（精确提醒） |
| 开机重设 | `BroadcastReceiver` 监听 `BOOT_COMPLETED` |
| 通知渠道 | Android 8.0+ 必须创建通知渠道 |
| **零钱池跨天刷新** | `WorkManager` 每日 00:01 执行 `RefreshDailyPoolWorker` |

### 6.6 Compose 最佳实践

```kotlin
// 状态提升示例
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState) {
        is TimetableUiState.Loading -> LoadingScreen()
        is TimetableUiState.Success -> TimetableContent(
            courses = (uiState as TimetableUiState.Success).courses,
            onCourseClick = viewModel::onCourseClick
        )
        is TimetableUiState.Error -> ErrorScreen()
    }
}
```

### 6.7 零钱池专项注意点

| 注意点 | 说明 |
|--------|------|
| **事务一致性** | 插入 transaction 和更新 daily_pools 必须在同一个 Room 事务中执行，使用 `@Transaction` 注解 |
| **并发安全** | 快速连续记账时，使用数据库级别的原子操作（`remainingAmount = remainingAmount - :amount`）避免竞态条件 |
| **跨月处理** | 进入新月份时，自动创建新的 `monthly_budgets` 记录，提醒用户设定新预算 |
| **历史修改** | 修改/删除历史日期的支出记录时，不应回滚历史日期的零钱池（历史已封存），仅影响月度统计 |
| **首次使用** | 未设置月预算时，零钱池卡片显示"点击设置月预算"引导入口 |

---

## 七、开发阶段建议

| 阶段 | 周期 | 交付物 |
|------|------|--------|
| **Phase 1** | 1-2 周 | 基础架构搭建 + 数据库设计 + 主题框架 |
| **Phase 2** | 2-3 周 | 课表模块（核心功能） |
| **Phase 3** | 1-2 周 | 任务模块 |
| **Phase 4** | 2-3 周 | 记账模块（含零钱池、图表） |
| **Phase 5** | 1-2 周 | 日历模块 + 模块联动 |
| **Phase 6** | 1 周 | 设置、备份导出、通知提醒 |
| **Phase 7** | 1 周 | 测试优化、UI 打磨 |

---

## 八、Gradle 依赖参考

```kotlin
dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
```

---

> 本文档涵盖从架构到细节的完整规划，可作为项目开发的基准需求说明书使用。
