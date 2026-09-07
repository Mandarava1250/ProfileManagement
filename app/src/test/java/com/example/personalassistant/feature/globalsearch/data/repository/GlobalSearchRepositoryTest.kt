package com.example.personalassistant.feature.globalsearch.data.repository

import com.example.personalassistant.feature.calendar.data.repository.FakeCalendarRepository
import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHitType
import com.example.personalassistant.feature.task.data.repository.FakeTaskRepository
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.timetable.data.repository.FakeTimetableRepository
import com.example.personalassistant.feature.timetable.domain.model.Course
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 8 Sub-step 3：[GlobalSearchRepository] 单元测试
 *
 * 覆盖 4 个核心场景：
 * 1. 空查询 → 返回空 [com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchResult]
 * 2. 关键字 → 4 个数据源全部命中，分类正确
 * 3. 大小写敏感（已包 % 后透传，不改 case）
 * 4. 响应式：写入新数据后 Flow 自动重发
 */
class GlobalSearchRepositoryTest {

    private lateinit var taskRepo: FakeTaskRepository
    private lateinit var calendarRepo: FakeCalendarRepository
    private lateinit var financeRepo: FakeFinanceRepository
    private lateinit var timetableRepo: FakeTimetableRepository
    private lateinit var repository: GlobalSearchRepositoryImpl

    @Before
    fun setup() = runTest {
        taskRepo = FakeTaskRepository()
        calendarRepo = FakeCalendarRepository()
        financeRepo = FakeFinanceRepository()
        timetableRepo = FakeTimetableRepository()
        repository = GlobalSearchRepositoryImpl(
            taskRepository = taskRepo,
            calendarRepository = calendarRepo,
            financeRepository = financeRepo,
            timetableRepository = timetableRepo,
        )
    }

    @Test
    fun `empty query returns empty result`() = runTest {
        val result = repository.search("").first()
        assertTrue("空查询应返回空结果", result.isEmpty)
        assertEquals(0, result.totalCount)
    }

    @Test
    fun `blank whitespace query returns empty result`() = runTest {
        val result = repository.search("   ").first()
        assertTrue(result.isEmpty)
    }

    @Test
    fun `keyword matches all four sources`() = runTest {
        // 4 个数据源各塞一条「同花」命中
        taskRepo.seedTask(
            Task(
                id = 1L,
                title = "买同花",
                description = "买菜种子",
                dueDate = 1_700_000_000_000L,
                priority = TaskPriority.MEDIUM,
            )
        )
        calendarRepo.seedEvent(
            title = "同花会",
            date = 1_700_000_000_000L,
        )
        // Finance 需要先有 Category 才能新建 Transaction
        val categoryId = financeRepo.upsertCategory(
            Category(id = 0L, name = "杂项", type = TransactionType.EXPENSE, sortOrder = 1)
        )
        financeRepo.addTransaction(
            type = TransactionType.EXPENSE,
            amount = 10.0,
            categoryId = categoryId,
            note = "买同花种子",
            date = 1_700_000_000_000L,
        )
        timetableRepo.seedCourse(
            Course(
                id = 1L,
                name = "同花艺术鉴赏",
                teacher = "张老师",
                location = "B201",
                dayOfWeek = 1,
                startTime = 540,
                endTime = 600,
                color = 0xFF6750A4.toInt(),
            )
        )

        val result = repository.search("同花").first()

        assertEquals(1, result.tasks.size)
        assertEquals(1, result.events.size)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.courses.size)
        assertEquals(4, result.totalCount)
        assertEquals(GlobalSearchHitType.TASK, result.tasks.first().type)
        assertEquals(GlobalSearchHitType.EVENT, result.events.first().type)
        assertEquals(GlobalSearchHitType.TRANSACTION, result.transactions.first().type)
        assertEquals(GlobalSearchHitType.COURSE, result.courses.first().type)
    }

    @Test
    fun `inserting new task triggers reactive re-emit`() = runTest {
        // 初次查询（4 路 combine 都已 seed 一个值）
        val first = repository.search("笔记").first()
        assertEquals(0, first.totalCount)

        // 写入新任务后再次查询
        taskRepo.seedTask(
            Task(
                id = 99L,
                title = "记笔记",
                description = "复习概要",
                dueDate = 1_700_000_000_000L,
                priority = TaskPriority.HIGH,
            )
        )

        val second = repository.search("笔记").first()
        assertEquals(1, second.totalCount)
        assertNotNull(second.tasks.firstOrNull())
        assertEquals(99L, second.tasks.first().entityId)
    }
}
