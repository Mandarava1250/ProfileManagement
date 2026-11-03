package com.example.personalassistant.feature.globalsearch.data.repository

import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHit
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHitType
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchResult
import com.example.personalassistant.feature.globalsearch.domain.repository.GlobalSearchRepository
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Phase 8 Sub-step 3：全局搜索仓库实现
 *
 * 聚合 4 个模块的 Repository.searchXxx 方法，把 Entity 流转成 [GlobalSearchHit] 流。
 *
 * ## 关键实现
 * - 关键字预处理：`"%${trimmed}%"`
 * - 空查询短路：返回空 [GlobalSearchResult]
 * - 响应式：4 个 Flow combine，任一数据源变更触发重新聚合
 * - 去重：distinctUntilChanged 防止重复触发
 */
@Singleton
class GlobalSearchRepositoryImpl @Inject constructor(
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
    private val financeRepository: FinanceRepository,
    private val timetableRepository: TimetableRepository,
) : GlobalSearchRepository {

    override fun search(query: String): Flow<GlobalSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            // 空查询：返回空结果，节省 4 路 IO
            return flowOf(GlobalSearchResult(query = ""))
        }

        val likeKeyword = "%$trimmed%"

        // 4 路 Flow combine，任一数据源变更触发重发
        return combine(
            taskRepository.searchTasks(likeKeyword),
            calendarRepository.searchEvents(likeKeyword),
            financeRepository.searchTransactions(trimmed), // 财务沿用原签名（已自行包 %）
            timetableRepository.searchCourses(likeKeyword),
        ) { tasks, events, transactions, courses ->
            GlobalSearchResult(
                query = trimmed,
                tasks = tasks.map { task ->
                    GlobalSearchHit(
                        type = GlobalSearchHitType.TASK,
                        entityId = task.id,
                        title = task.title,
                        subtitle = task.description ?: task.category,
                        highlight = task.description,
                        timestamp = task.dueDate ?: task.updatedAt,
                    )
                },
                events = events.map { event ->
                    GlobalSearchHit(
                        type = GlobalSearchHitType.EVENT,
                        entityId = event.id,
                        title = event.title,
                        subtitle = event.note ?: event.category,
                        highlight = event.note,
                        timestamp = event.date,
                    )
                },
                transactions = transactions.map { tx ->
                    GlobalSearchHit(
                        type = GlobalSearchHitType.TRANSACTION,
                        entityId = tx.id,
                        title = tx.note ?: "(无备注)",
                        // Phase 22.4：走 MoneyFormat —— 裸数字（"12.5"）看不出金额格式，
                        // 也分不清是收入还是支出
                        subtitle = buildString {
                            append(if (tx.type == TransactionType.EXPENSE) "支出 " else "收入 ")
                            append(MoneyFormat.format(tx.amount))
                        },
                        highlight = tx.note,
                        timestamp = tx.date,
                    )
                },
                courses = courses.map { course ->
                    GlobalSearchHit(
                        type = GlobalSearchHitType.COURSE,
                        entityId = course.id,
                        title = course.name,
                        subtitle = course.teacher.ifBlank { course.location.ifBlank { course.note } },
                        highlight = course.note,
                        timestamp = 0L,
                    )
                },
            )
        }.distinctUntilChanged()
    }
}
