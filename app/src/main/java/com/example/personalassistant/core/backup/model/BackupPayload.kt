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
import kotlinx.serialization.Serializable

/**
 * Phase 8 Sub-step 4：内存中的完整备份数据快照
 *
 * [BackupSerializer] 从 Room 拉出全表数据，组装成 [BackupPayload]，
 * 再序列化为 JSON 写入 ZIP 各文件。
 *
 * - 12 张表，全部以 List<*Entity> 形式承载
 * - 空表也写空 JSON `[]`，让导入端知道表清单完整没遗漏
 * - 所有 List 字段默认空 list，不会出现 null
 */
@Serializable
data class BackupPayload(
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
        get() = semesters.size + courses.size + tags.size +
                tasks.size + subTasks.size + taskTagCrossRefs.size +
                events.size + categories.size + monthlyBudgets.size +
                dailyPools.size + transactions.size + holidays.size
}
