package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import javax.inject.Inject

/**
 * 创建（或重命名）一个学期。
 *
 * 关键不变量：
 *  - name 唯一。如果用户给了一个已存在的 name，复用其行、覆盖 startDate/endDate/isCurrent，
 *    用 [Semester.copy] 不破坏 id（避免级联 FK 失效）。
 *  - isCurrent：调用方决定。传 true 表示"用户想立刻切到这个学期"，
 *    由 [TimetableRepositoryImpl.upsertSemester] + UI 层负责清掉其它 isCurrent。
 *
 * 这里只校验"用户输入是否合法"，不负责事务切换（事务在 repository 上做）。
 */
class CreateSemesterUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    suspend operator fun invoke(
        name: String,
        startDate: Long,
        endDate: Long,
        makeCurrent: Boolean = true,
    ): Semester {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "学期名称不能为空" }
        require(endDate >= startDate) { "学期结束日期不能早于开始日期" }

        // 已存在同名学期 → 复用 + 更新（保留 id 让 FK 链不断）
        val existing = repository.getSemesterByName(trimmed)
        val toSave = if (existing != null) {
            existing.copy(
                startDate = startDate,
                endDate = endDate,
                isCurrent = if (makeCurrent) true else existing.isCurrent,
            )
        } else {
            Semester(
                name = trimmed,
                startDate = startDate,
                endDate = endDate,
                isCurrent = makeCurrent,
            )
        }
        repository.upsertSemester(toSave)
        if (makeCurrent) {
            repository.switchSemester(toSave.name)
        }
        return toSave
    }
}
