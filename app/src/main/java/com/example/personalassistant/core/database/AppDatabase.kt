package com.example.personalassistant.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.example.personalassistant.core.holiday.data.HolidayDao
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.feature.calendar.data.local.EventCategoryDao
import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity
import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.CoinPoolEntity
import com.example.personalassistant.feature.finance.data.local.CoinPoolSettlementEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.habit.data.local.HabitCheckInDao
import com.example.personalassistant.feature.habit.data.local.HabitCheckInEntity
import com.example.personalassistant.feature.habit.data.local.HabitDao
import com.example.personalassistant.feature.habit.data.local.HabitEntity
import com.example.personalassistant.feature.me.data.local.StudentProfileDao
import com.example.personalassistant.feature.me.data.local.StudentProfileEntity
import com.example.personalassistant.feature.profile.data.local.UserProfileDao
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskListDao
import com.example.personalassistant.feature.task.data.local.TaskListEntity
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.CourseSessionDao
import com.example.personalassistant.feature.timetable.data.local.CourseSessionEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity

/**
 * App 数据库 schema version（Phase 5：v10）。
 *
 * ## 为什么抽成常量而不是直接写在注解里
 * 备份模块的 `BackupFormatVersion.DB_VERSION` 必须与它同步，而之前的写法是
 * 两边各摆一个字面量、靠一条「assertEquals(9, DB_VERSION)」的测试提醒——
 * 那条测试**根本不读 AppDatabase.version**，改注解不会让它红。
 * 实测证据：v8 时 DB_VERSION 长期停在 7、升 v9 时也没人改它，都是它“应该报错”却没报的现场。
 * 现在注解引用本常量，测试拿 DB_VERSION 比本常量，才是真正的交叉引用。
 *
 * ⚠️ 升版时必须同步：本常量 + 新 Migration + `BackupFormatVersion.DB_VERSION`。
 */
const val APP_DATABASE_VERSION = 21

/**
 * App 主数据库
 *
 * 20 张表：
 * 1. courses          - 课程
 * 2. semesters        - 学期
 * 3. tasks            - 任务（Phase 16：加清单归属 / 父任务 / 金额 / 时间段 / 完成时间 / 置顶 7 列）
 * 4. task_lists       - 清单（Phase 16 新增：任务归属地，内置「收集箱」id=1）
 * 5. tags             - 标签（Phase 6c 新增）
 * 6. task_tag_cross_ref - 任务↔标签关联（Phase 6c 新增；Phase 16 吸收了原 sub_task_tag_cross_ref）
 * 7. events           - 日历事件
 * 8. event_categories - 日历事件分类（Phase 8 Sub-step 9 新增）
 * 9. transactions     - 记账流水
 * 10. categories       - 记账分类
 * 11. monthly_budgets  - 月度预算
 * 12. daily_pools      - 每日零钱池
 * 13. holidays        - 节假日（Phase 8 Sub-step 7 新增）
 * 14. user_profiles    - 账户（Phase 3 多账户新增，记账域 4 表靠 accountId 指向它）
 * 15. student_profile  - 「我」档案（Phase 12 新增）
 * 16. habits          - 习惯（Phase 23 新增：名称 / 图标 / 颜色 / 目标类型 / 目标次数 / 单位 / 顺序 / 归档）
 * 17. habit_checkins  - 习惯打卡记录（Phase 23 新增：习惯 + 日期 + 次数 + 备注）
 * 18. coin_pools      - 零钱池（批次 C：账户零用钱余额，可负；accountId 唯一 + 外键 → user_profiles）
 * 19. coin_pool_settlements - 零钱池日切结算流水（批次 D：每天一行；accountId+date 唯一 + 外键 → user_profiles）
 * 20. course_sessions - 课时（Phase 28 P1：一门课的多个上课时段；courseId 外键 → courses，删课连带删）
 *
 * ⚠️ schema 变更必须按以下流程：
 *   1. 修改对应 Entity
 *   2. version + 1
 *   3. 编写 Migration 对象并在 DatabaseModule.addMigrations(...) 注册
 *   4. 不要随意改动 exportSchema = false，否则丢失 schema 版本历史
 */
@Database(
    entities = [
        // 课表
        CourseEntity::class,
        SemesterEntity::class,
        // 课表 · 课时（Phase 28 P1：一门课多课时）
        CourseSessionEntity::class,
        // 任务（Phase 16：sub_tasks 已并入 tasks）
        TaskEntity::class,
        TaskListEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        // 日历（Phase 8 Sub-step 9 加 event_categories）
        EventEntity::class,
        EventCategoryEntity::class,
        // 记账
        TransactionEntity::class,
        CategoryEntity::class,
        MonthlyBudgetEntity::class,
        DailyPoolEntity::class,
        // 节假日（Phase 8 Sub-step 7）
        HolidayEntity::class,
        // 账户（Phase 3）
        UserProfileEntity::class,
        // 「我」档案（Phase 12）
        StudentProfileEntity::class,
        // 习惯打卡（Phase 23）
        HabitEntity::class,
        HabitCheckInEntity::class,
        // 记账（批次 C：零钱池）
        CoinPoolEntity::class,
        // 记账（批次 D：零钱池日切结算流水）
        CoinPoolSettlementEntity::class,
    ],
    // v10（Phase 5）：transactions.transferAccountId + user_profiles.initialBalance
    // v11（Phase 12）：courses.credit + courses.gradePoint + student_profile 表
    // v12（Phase 12 GPA 规则）：student_profile.gpaRuleJson
    // v13（Phase 15 清单扩展）：sub_tasks 加 5 列 + sub_task_tag_cross_ref 新表
    // v14（性能）：补 4 条索引 —— transactions ×2（transferPairId / accountId+categoryId）、
    // tasks ×1（isCompleted+priority+dueDate）、sub_tasks ×1（category）。零行为变更。
    // v15（Phase 16 清单体系）：新建 task_lists + tasks 加 7 列 + **sub_tasks 整表并入 tasks**
    // + sub_task_tag_cross_ref 并入 task_tag_cross_ref。数据搬迁用恒定 id 偏移。
    // v16（Phase 21 清单↔记账）：transactions 加 linkedTaskId 列 + 索引（弱引用，不加外键）。
    // v17（Phase 23 习惯打卡）：新建 habits + habit_checkins 两张表（见 Migration_16_17）。
    // v18（批次 C 零钱池）：新建 coin_pools（账户零用钱余额，可负；accountId 唯一 + 外键 → user_profiles）。
    // v19（批次 D 零钱池日切结算）：coin_pools 加 lastSettledDate（水位线）
    // + 新建 coin_pool_settlements（每日结算流水，accountId+date 唯一）。
    // v20（Phase 28 P1 课表一课多课时）：新建 course_sessions（课时表，courseId 外键 → courses）。
    // courses 表**结构不动** —— 它的 dayOfWeek/startTime/endTime 变成「主时段镜像」。
    // v21（Phase 28 P10 教师/地点下沉到课时）：course_sessions 加 teacher / location 两列
    // （空串 = 沿用课程级），并把课程级的值回填进存量课时。
    version = APP_DATABASE_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun courseSessionDao(): CourseSessionDao
    abstract fun semesterDao(): SemesterDao
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun tagDao(): TagDao
    abstract fun taskTagDao(): TaskTagDao
    abstract fun eventDao(): EventDao
    abstract fun eventCategoryDao(): EventCategoryDao
    abstract fun financeDao(): FinanceDao
    abstract fun holidayDao(): HolidayDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun habitDao(): HabitDao
    abstract fun habitCheckInDao(): HabitCheckInDao

    companion object {
        const val DATABASE_NAME = "personal_assistant.db"

        /**
         * 全部 Migration 列表（v1 → v2+）
         *
         * 在 CoreModule.provideAppDatabase() 里通过 addMigrations(...) 注册。
         * ⚠️ 不允许删除任何已发布的 Migration，否则线上用户数据库会崩。
         */
        val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(
                Migration_1_2,
                Migration_2_3,
                Migration_3_4,
                Migration_4_5,
                Migration_5_6,
                Migration_6_7,
                Migration_7_8,
                Migration_8_9,
                Migration_9_10,
                Migration_10_11,
                Migration_11_12,
                Migration_12_13,
                Migration_13_14,
                Migration_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
                MIGRATION_20_21,
            )
    }
}
