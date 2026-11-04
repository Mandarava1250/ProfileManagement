package com.example.personalassistant.core.backup.model

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
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskListEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import kotlinx.serialization.Serializable

/**
 * Phase 8 Sub-step 4：内存中的完整备份数据快照
 *
 * [BackupSerializer] 从 Room 拉出全表数据，组装成 [BackupPayload]，
 * 再序列化为 JSON 写入 ZIP 各文件。
 *
 * - 17 张表，全部以 List<*Entity> 形式承载
 *   （Phase 3 新增 user_profiles；Phase 16 删 sub_tasks、加 task_lists / event_categories / student_profile；
 *   Phase 23 加 habits / habit_checkins）
 * - 空表也写空 JSON `[]`，让导入端知道表清单完整没遗漏
 * - 所有 List 字段默认空 list，不会出现 null
 *
 * ## Phase 3：为什么 [userProfiles] 必须入备份
 * 记账 4 表现在按 accountId 分区。若只备份数据不备份账户列表，
 * 恢复后会出现「accountId=2 的流水，但 user_profiles 里只有 id=1」的孤儿数据：
 * 数据实际存在但 UI 永远看不到，也删不掉。所以账户表必须跟着备份走。
 *
 * 旧备份（formatVersion=1）没有 user_profiles.json → [userProfiles] 解出空列表，
 * [com.example.personalassistant.core.backup.importer.BackupImporter] 对这种情况有专门兼容。
 *
 * ## Phase 16：为什么 `subTasks` 字段被删掉
 * 「清单项」不再是独立表 —— 它的字段（金额 / 起止时间 / 嵌套父指针 / 置顶）
 * 全部并进 `tasks`，子项改由 `tasks.parentTaskId` 自引用表达。
 * 少一张表 = 少一份需要同步的 schema、少一条导入顺序约束。
 *
 * ## Phase 23：习惯打卡两张表
 * `habits` 是习惯本体，`habit_checkins` 是打卡记录（`habitId` → `habits.id`，CASCADE）。
 * 习惯数据是用户长期积累的成果，不备份就等于清零 —— 所以两张表都要进备份。
 *
 * 两个字段都带 `= emptyList()` 默认值：旧备份（formatVersion ≤ 2）里根本没有
 * 这两个 JSON 条目，解出来是空列表，导入端据此判断「备份没带这两张表」→
 * 不清空用户现有的习惯数据（见 [com.example.personalassistant.core.backup.importer.BackupImporter]）。
 */
@Serializable
data class BackupPayload(
    // Phase 3：账户表（记账域数据的归属根表，必须排在最前参与导入顺序）
    val userProfiles: List<UserProfileEntity> = emptyList(),
    val semesters: List<SemesterEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    // Phase 16：清单表（tasks.listId 的父表，必须排在 tasks 前）
    val taskLists: List<TaskListEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val taskTagCrossRefs: List<TaskTagCrossRef> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    // Phase 16：日历事件分类（events.categoryId 的父表）
    val eventCategories: List<EventCategoryEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val monthlyBudgets: List<MonthlyBudgetEntity> = emptyList(),
    val dailyPools: List<DailyPoolEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    // Phase 8 Sub-step 7：节假日表（v6 新增）
    val holidays: List<HolidayEntity> = emptyList(),
    // Phase 16：「我」档案（一账户一行，主键 accountId → user_profiles.id）
    val studentProfile: List<StudentProfileEntity> = emptyList(),
    // Phase 23：习惯本体（habit_checkins 的父表，必须排在 habit_checkins 前）
    val habits: List<HabitEntity> = emptyList(),
    // Phase 23：习惯打卡记录（habitId → habits.id，CASCADE）
    val habitCheckIns: List<HabitCheckInEntity> = emptyList(),
    // 批次 C：零钱池（accountId → user_profiles.id）
    // 默认 emptyList()：旧备份里根本没有这个条目 → 解出空列表 →
    // 导入端据此判断「备份没带这张表」→ 不清空用户现有的零钱池。
    val coinPools: List<CoinPoolEntity> = emptyList(),
) {
    /**
     * 总记录数（用于 manifest 报告 / 调试 / UI 展示）
     */
    val totalRecordCount: Int
        get() = userProfiles.size + semesters.size + courses.size + tags.size +
                taskLists.size + tasks.size + taskTagCrossRefs.size +
                events.size + eventCategories.size + categories.size + monthlyBudgets.size +
                dailyPools.size + transactions.size + holidays.size + studentProfile.size +
                habits.size + habitCheckIns.size + coinPools.size
}
