package com.example.personalassistant.core.backup.diff

import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.CoinPoolEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.habit.data.local.HabitCheckInEntity
import com.example.personalassistant.feature.habit.data.local.HabitEntity
import com.example.personalassistant.feature.me.data.local.StudentProfileEntity
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskListEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity

/**
 * Phase 8 Sub-step 4'（plan 8f'）：当前数据库 15 张表的内存快照
 *
 * 给 [BackupDiffCalculator] 提供「现在库里有什么」——
 * 由 [com.example.personalassistant.core.backup.snapshot.BackupCurrentSnapshotProvider] 在
 * [com.example.personalassistant.feature.settings.backup.BackupPreviewViewModel.load]
 * 阶段一次性从各 DAO 拉出来打包。
 *
 * 为什么不让 [BackupDiffCalculator] 直接注入 13 个 DAO？
 * - 让 calculator 保持纯函数（无副作用、易测、不依赖 Hilt）
 * - 让 ViewModel/SnapshotProvider 是「唯一与 DB 交互的入口」——测试里用 FakeProvider 即可
 *
 * 字段顺序与 [com.example.personalassistant.core.backup.model.BackupManifest.DEFAULT_TABLES]
 * 保持一致，便于差异计算器一一对照。
 *
 * Phase 16：15 张表（原 13 张 - sub_tasks + task_lists + event_categories + student_profile）。
 * Phase 23：17 张表（+ habits + habit_checkins）。
 */
data class CurrentDbSnapshot(
    val userProfiles: List<UserProfileEntity> = emptyList(),
    val semesters: List<SemesterEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val taskLists: List<TaskListEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val taskTagCrossRefs: List<TaskTagCrossRef> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val eventCategories: List<EventCategoryEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val monthlyBudgets: List<MonthlyBudgetEntity> = emptyList(),
    val dailyPools: List<DailyPoolEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val holidays: List<HolidayEntity> = emptyList(),
    val studentProfile: List<StudentProfileEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitCheckIns: List<HabitCheckInEntity> = emptyList(),
    // 批次 C：零钱池
    val coinPools: List<CoinPoolEntity> = emptyList(),
)
