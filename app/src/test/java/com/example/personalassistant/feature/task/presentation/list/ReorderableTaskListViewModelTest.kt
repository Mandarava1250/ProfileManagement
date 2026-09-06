package com.example.personalassistant.feature.task.presentation.list

import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.TaskWithSubTasks
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6a：ReorderableTaskListViewModel 单测。
 *
 * 重点：
 * - beginDrag / moveLocal / endDrag 三段生命周期
 * - onDragEnd 后 repository.reorderTasks 被调一次（不是每次 move 都调）
 * - 顺序未变时 endDrag 跳过 DB 写入（幂等优化）
 * - 拖拽中收到 DB 新 snapshot 不重置本地顺序
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReorderableTaskListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Fake TaskRepository —— 记录 reorderTasks 调用次数与参数 */
    private class FakeTaskRepository : TaskRepository {
        var reorderCalls: Int = 0; private set
        var lastReorderIds: List<Long>? = null; private set

        override fun observeActive() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeCompleted() = MutableStateFlow<List<Task>>(emptyList())
        override fun observeTaskById(id: Long) = MutableStateFlow<Task?>(null)
        override fun observeStandaloneChecklist() = MutableStateFlow<List<SubTask>>(emptyList())
        override fun observeSubTasks(parentId: Long) = MutableStateFlow<List<SubTask>>(emptyList())
        override fun observeTasks(filter: TaskFilter, sort: TaskSort) =
            MutableStateFlow<List<Task>>(emptyList())
        // Phase 6c：FilterExpression 重载
        override fun observeTasks(filter: FilterExpression, sort: TaskSort) =
            MutableStateFlow<List<Task>>(emptyList())
        override suspend fun getTaskWithSubTasks(id: Long): TaskWithSubTasks? = null
        override suspend fun upsertTask(task: Task, subTasks: List<SubTask>): Long = -1L
        override suspend fun deleteTask(task: Task) {}
        override suspend fun toggleTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun toggleSubTaskComplete(id: Long, completed: Boolean) {}
        override suspend fun addStandaloneSubTask(title: String): Long = -1L
        override suspend fun addSubTaskToTask(parentId: Long, title: String): Long = -1L
        override suspend fun deleteSubTask(subTask: SubTask) {}
        override suspend fun reorderTasks(orderedIds: List<Long>) {
            reorderCalls++
            lastReorderIds = orderedIds.toList()
        }
    }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `beginDrag 后 isDragging=true，localOrder 保留原 snapshot 顺序`() = runTest {
        val repo = FakeTaskRepository()
        val vm = ReorderableTaskListViewModel(repo)

        val snapshot = listOf(1L, 2L, 3L)
        vm.beginDrag(snapshot)

        assertTrue("isDragging should be true after beginDrag", vm.isDragging.value)
        assertEquals(snapshot, vm.localOrder.value)
    }

    @Test
    fun `moveLocal 不触发 DB 写（拖拽中纯内存）`() = runTest {
        val repo = FakeTaskRepository()
        val vm = ReorderableTaskListViewModel(repo)

        vm.beginDrag(listOf(1L, 2L, 3L))
        vm.moveLocal(fromIndex = 0, toIndex = 2)
        vm.moveLocal(fromIndex = 2, toIndex = 0)
        vm.moveLocal(fromIndex = 1, toIndex = 1) // 同位置 noop

        // 多次 move 不应该触发 reorderCalls（关键验证：纯内存）
        assertEquals(0, repo.reorderCalls)
        // localOrder 已被修改过（不为空即表示 move 生效；具体值不重要）
        assertEquals(3, vm.localOrder.value.size)
    }

    @Test
    fun `endDrag 后 repository 被调一次，最终顺序作为参数`() = runTest {
        val repo = FakeTaskRepository()
        val vm = ReorderableTaskListViewModel(repo)

        val original = listOf(1L, 2L, 3L, 4L)
        vm.beginDrag(original)
        vm.moveLocal(fromIndex = 3, toIndex = 0)  // [4,1,2,3]
        vm.moveLocal(fromIndex = 3, toIndex = 0)  // [3,4,1,2]
        vm.endDrag(original)

        advanceUntilIdle()

        assertEquals(1, repo.reorderCalls)
        assertEquals(listOf(3L, 4L, 1L, 2L), repo.lastReorderIds)
        assertFalse(vm.isDragging.value)
    }

    @Test
    fun `endDrag 时顺序未变 跳过 DB 写（幂等优化）`() = runTest {
        val repo = FakeTaskRepository()
        val vm = ReorderableTaskListViewModel(repo)

        val original = listOf(1L, 2L, 3L)
        vm.beginDrag(original)
        // 不调 moveLocal，顺序保持原样
        vm.endDrag(original)

        advanceUntilIdle()

        assertEquals(0, repo.reorderCalls)
        assertFalse(vm.isDragging.value)
    }

    @Test
    fun `moveLocal 越界 index 静默忽略，不抛异常`() = runTest {
        val repo = FakeTaskRepository()
        val vm = ReorderableTaskListViewModel(repo)

        vm.beginDrag(listOf(1L, 2L, 3L))
        vm.moveLocal(fromIndex = -1, toIndex = 0)
        vm.moveLocal(fromIndex = 0, toIndex = 99)
        // 原顺序不变
        assertEquals(listOf(1L, 2L, 3L), vm.localOrder.value)
    }

    @Test
    fun `resetLocalOrder 不在拖拽中时才生效（拖拽中保护本地状态）`() = runTest {
        val repo = FakeTaskRepository()
        val vm = ReorderableTaskListViewModel(repo)

        vm.beginDrag(listOf(1L, 2L, 3L))
        vm.moveLocal(0, 2)  // localOrder: [2,3,1]

        // 模拟 DB 更新（拖拽中）—— 不应重置本地顺序
        vm.resetLocalOrder(listOf(1L, 2L, 3L))
        assertEquals("拖拽中收到新 snapshot 不应重置 localOrder", listOf(2L, 3L, 1L), vm.localOrder.value)

        // 结束拖拽后再 reset —— 应生效
        vm.endDrag(listOf(1L, 2L, 3L))
        vm.resetLocalOrder(listOf(5L, 6L, 7L))
        assertEquals(listOf(5L, 6L, 7L), vm.localOrder.value)
    }

    @Test
    fun `endDrag 不在拖拽态时是 no-op`() = runTest {
        val repo = FakeTaskRepository()
        val vm = ReorderableTaskListViewModel(repo)

        vm.endDrag(listOf(1L, 2L, 3L))

        advanceUntilIdle()
        assertEquals(0, repo.reorderCalls)
    }
}
