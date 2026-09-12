package com.example.personalassistant.core.backup.model

import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
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
 * - 13 张表，全部以 List<*Entity> 形式承载
 *   （Phase 3 新增 user_profiles，共 13 张参与备份；task 相关的 tags/cross_ref 等已计入）
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
 */
@Serializable
data class BackupPayload(
    // Phase 3：账户表（记账域数据的归属根表，必须排在最前参与导入顺序）
    val userProfiles: List<UserProfileEntity> = emptyList(),
    val semesters: List<SemesterEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val subTasks: List<SubTaskEntity> = emptyList(),
    val taskTagCrossRefs: List<TaskTagCrossRef> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val monthlyBudgets: List<MonthlyBudgetEntity> = emptyList(),
    val dailyPools: List<DailyPoolEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    // Phase 8 Sub-step 7：节假日表（v6 新增）
    val holidays: List<HolidayEntity> = emptyList(),
) {
    /**
     * 总记录数（用于 manifest 报告 / 调试 / UI 展示）
     */
    val totalRecordCount: Int
        get() = userProfiles.size + semesters.size + courses.size + tags.size +
                tasks.size + subTasks.size + taskTagCrossRefs.size +
                events.size + categories.size + monthlyBudgets.size +
                dailyPools.size + transactions.size + holidays.size
}
