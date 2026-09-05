package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskGroups
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务象限分类的纯函数测试
 *
 * 重点覆盖：
 * - 单条任务在 3 个优先级 × 紧急/不紧急/未排期 三种状态下的分类
 * - todayEndMillis 的边界值（== / -1 / +1）
 * - 多个任务按象限分组 + completed 单独放
 * - 空输入
 */
class ObserveTasksByQuadrantUseCaseTest {

    // 任意一个 todayEnd 时刻；测试逻辑只关心相对大小
    private val todayEnd: Long = 1_700_000_000_000L

    private fun task(
        id: Long = 0L,
        title: String = "t$id",
        dueDate: Long? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
        isCompleted: Boolean = false,
    ) = Task(
        id = id,
        title = title,
        dueDate = dueDate,
        priority = priority,
        isCompleted = isCompleted,
    )

    // ===== classifyOne 单条任务判定 =====

    @Test
    fun `classifyOne HIGH + dueDate in past is P1`() {
        val t = task(dueDate = todayEnd - 1, priority = TaskPriority.HIGH)
        assertEquals(TaskQuadrant.P1, ObserveTasksByQuadrantUseCase.classifyOne(t, todayEnd))
    }

    @Test
    fun `classifyOne HIGH + dueDate exactly todayEnd is P1 (boundary inclusive)`() {
        val t = task(dueDate = todayEnd, priority = TaskPriority.HIGH)
        assertEquals(TaskQuadrant.P1, ObserveTasksByQuadrantUseCase.classifyOne(t, todayEnd))
    }

    @Test
    fun `classifyOne HIGH + dueDate after today is P2`() {
        val t = task(dueDate = todayEnd + 1, priority = TaskPriority.HIGH)
        assertEquals(TaskQuadrant.P2, ObserveTasksByQuadrantUseCase.classifyOne(t, todayEnd))
    }

    @Test
    fun `classifyOne MEDIUM + dueDate in past is P2`() {
        val t = task(dueDate = todayEnd - 1, priority = TaskPriority.MEDIUM)
        assertEquals(TaskQuadrant.P2, ObserveTasksByQuadrantUseCase.classifyOne(t, todayEnd))
    }

    @Test
    fun `classifyOne MEDIUM + dueDate after today is P3`() {
        val t = task(dueDate = todayEnd + 1, priority = TaskPriority.MEDIUM)
        assertEquals(TaskQuadrant.P3, ObserveTasksByQuadrantUseCase.classifyOne(t, todayEnd))
    }

    @Test
    fun `classifyOne LOW + dueDate in past is P3`() {
        val t = task(dueDate = todayEnd - 1, priority = TaskPriority.LOW)
        assertEquals(TaskQuadrant.P3, ObserveTasksByQuadrantUseCase.classifyOne(t, todayEnd))
    }

    @Test
    fun `classifyOne LOW + dueDate after today is P4`() {
        val t = task(dueDate = todayEnd + 1, priority = TaskPriority.LOW)
        assertEquals(TaskQuadrant.P4, ObserveTasksByQuadrantUseCase.classifyOne(t, todayEnd))
    }

    @Test
    fun `classifyOne null dueDate is UNSCHEDULED for every priority`() {
        TaskPriority.entries.forEach { p ->
            val t = task(dueDate = null, priority = p)
            assertEquals(
                "priority=$p should map to UNSCHEDULED when dueDate is null",
                TaskQuadrant.UNSCHEDULED,
                ObserveTasksByQuadrantUseCase.classifyOne(t, todayEnd),
            )
        }
    }

    // ===== classify 整体分组 =====

    @Test
    fun `classify distributes active tasks into quadrants and keeps completed separate`() {
        val active = listOf(
            task(id = 1L, dueDate = todayEnd - 1, priority = TaskPriority.HIGH),     // P1
            task(id = 2L, dueDate = todayEnd + 1, priority = TaskPriority.HIGH),     // P2
            task(id = 3L, dueDate = null, priority = TaskPriority.LOW),              // UNSCHEDULED
            task(id = 4L, dueDate = todayEnd - 1, priority = TaskPriority.MEDIUM),  // P2
            task(id = 5L, dueDate = todayEnd + 1, priority = TaskPriority.LOW),     // P4
        )
        val completed = listOf(
            task(id = 6L, dueDate = todayEnd - 1, isCompleted = true),
            task(id = 7L, dueDate = null, isCompleted = true),
        )

        val groups = ObserveTasksByQuadrantUseCase.classify(active, completed, todayEnd)

        // P1: 只有 id=1
        assertEquals(listOf(1L), groups.byQuadrant[TaskQuadrant.P1]?.map { it.id })
        // P2: id=2, 4
        assertEquals(listOf(2L, 4L), groups.byQuadrant[TaskQuadrant.P2]?.map { it.id })
        // P3: 空（没任务落进去）
        assertNull("P3 应当不在 map 中", groups.byQuadrant[TaskQuadrant.P3])
        // P4: id=5
        assertEquals(listOf(5L), groups.byQuadrant[TaskQuadrant.P4]?.map { it.id })
        // UNSCHEDULED: id=3
        assertEquals(listOf(3L), groups.byQuadrant[TaskQuadrant.UNSCHEDULED]?.map { it.id })

        // completed 单独保留，按传入顺序
        assertEquals(listOf(6L, 7L), groups.completed.map { it.id })
    }

    @Test
    fun `classify empty inputs yields empty groups`() {
        val groups = ObserveTasksByQuadrantUseCase.classify(emptyList(), emptyList(), todayEnd)
        assertTrue(groups.isEmpty)
        assertTrue(groups.completed.isEmpty())
        assertTrue(groups.byQuadrant.isEmpty())
    }

    @Test
    fun `classify all-completed leaves byQuadrant empty`() {
        val completed = listOf(
            task(id = 1L, dueDate = todayEnd - 1, priority = TaskPriority.HIGH, isCompleted = true),
            task(id = 2L, dueDate = null, isCompleted = true),
        )
        val groups = ObserveTasksByQuadrantUseCase.classify(emptyList(), completed, todayEnd)

        assertTrue(groups.byQuadrant.isEmpty())
        assertEquals(2, groups.completed.size)
    }

    @Test
    fun `classify many active tasks preserve order within quadrant`() {
        // DAO 已经按 priority DESC, dueDate ASC 排过序；classify 不应改顺序
        val active = listOf(
            task(id = 1L, dueDate = todayEnd - 1, priority = TaskPriority.HIGH),
            task(id = 2L, dueDate = todayEnd - 2, priority = TaskPriority.HIGH),  // 更早
            task(id = 3L, dueDate = todayEnd - 3, priority = TaskPriority.HIGH),  // 更更早
        )
        val groups = ObserveTasksByQuadrantUseCase.classify(active, emptyList(), todayEnd)
        assertEquals(listOf(1L, 2L, 3L), groups.byQuadrant[TaskQuadrant.P1]?.map { it.id })
    }

    // ===== TaskGroups 工具方法 =====

    @Test
    fun `TaskGroups isEmpty is true when both maps and completed are empty`() {
        val groups = TaskGroups(byQuadrant = emptyMap(), completed = emptyList())
        assertTrue(groups.isEmpty)
    }

    @Test
    fun `TaskGroups countByQuadrant returns 0 for missing quadrants and correct size otherwise`() {
        val task1 = task(id = 1L, dueDate = todayEnd - 1, priority = TaskPriority.HIGH)
        val groups = TaskGroups(
            byQuadrant = mapOf(TaskQuadrant.P1 to listOf(task1)),
            completed = emptyList(),
        )
        assertEquals(1, groups.countByQuadrant(TaskQuadrant.P1))
        assertEquals(0, groups.countByQuadrant(TaskQuadrant.P2))
        assertEquals(0, groups.countByQuadrant(TaskQuadrant.UNSCHEDULED))
        assertFalse(groups.isEmpty)
    }
}
