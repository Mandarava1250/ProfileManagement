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
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity

/**
 * App 主数据库
 *
 * 13 张表：
 * 1. courses          - 课程
 * 2. semesters        - 学期
 * 3. tasks            - 任务
 * 4. sub_tasks        - 子任务
 * 5. tags             - 标签（Phase 6c 新增）
 * 6. task_tag_cross_ref - 任务↔标签关联（Phase 6c 新增）
 * 7. events           - 日历事件
 * 8. event_categories - 日历事件分类（Phase 8 Sub-step 9 新增）
 * 9. transactions     - 记账流水
 * 10. categories       - 记账分类
 * 11. monthly_budgets  - 月度预算
 * 12. daily_pools      - 每日零钱池
 * 13. holidays        - 节假日（Phase 8 Sub-step 7 新增）
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
        // 任务
        TaskEntity::class,
        SubTaskEntity::class,
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
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun semesterDao(): SemesterDao
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun taskTagDao(): TaskTagDao
    abstract fun eventDao(): EventDao
    abstract fun eventCategoryDao(): EventCategoryDao
    abstract fun financeDao(): FinanceDao
    abstract fun holidayDao(): HolidayDao

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
            )
    }
}
