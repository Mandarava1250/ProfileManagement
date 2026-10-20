package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import javax.inject.Inject

/**
 * 编辑已有学期：重命名 / 改起止日期。
 *
 * 重命名是棘手点：学期 `name` 是 Course 表的字符串引用（不是外键约束，
 * 但语义上 Course.semester 必须跟 Semester.name 保持一致）。所以：
 *  1. 把 [Semester] 的 id / isCurrent 保留，只改 name / start / end
 *  2. 落库：upsert（id 已存在，REPLACE 用新 name）
 *  3. 把 Course.semester 引用从 oldName 改成 newName
 *  4. 如果这个学期原本是当前学期，clearCurrent + setCurrent 重新点亮
 *     （按 name 切换的现有实现天然兼容）
 */
class UpdateSemesterUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    suspend operator fun invoke(
        oldName: String,
        newName: String,
        startDate: Long,
        endDate: Long,
    ) {
        require(newName.isNotBlank()) { "学期名称不能为空" }
        require(endDate >= startDate) { "结束日期不能早于开始日期" }

        val existing = repository.getSemesterByName(oldName)
            ?: throw IllegalArgumentException("学期不存在：$oldName")

        val updated = existing.copy(
            name = newName.trim(),
            startDate = startDate,
            endDate = endDate,
        )
        repository.upsertSemester(updated)

        // name 变了才需要迁移 Course 引用
        if (oldName != newName) {
            repository.renameSemesterReferences(oldName = oldName, newName = newName.trim())
        }
    }
}
