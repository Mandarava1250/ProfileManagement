package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.task.domain.model.ChecklistItem
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 15/16：清单仓库实现 [ChecklistRepositoryImpl] 的 JVM 单测。
 *
 * 用内存版 Fake DAO（[FakeChecklistTaskDao] / [FakeChecklistTagDao] / [FakeTaskTagDao]）跑，不碰真 Room。
 *
 * 覆盖这四件最容易回归的事：
 * 1. **新字段落库**：分类 / 截止时间 / 优先级 / 金额 / 嵌套父指针写得进去、读得出来
 * 2. **manualOrder 追加**：新增清单项是 `max(manualOrder) + 1`，不再全挤在 0
 * 3. **标签挂载 / 解绑**：`task_tag_cross_ref` 行的增删
 * 4. **嵌套组树**：顶层项 + 标签 + 子清单能正确组出来（含深度上限与自环保护）
 *
 * ## Phase 16
 *
 * `sub_tasks` 表已并入 `tasks`：清单项本体就是 [TaskEntity]，
 * 嵌套层级改用 `parentTaskId` 自引用表达（原 `parentSubTaskId`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistRepositoryImplTest {

    private val dao = FakeChecklistTaskDao()
    private val taskTagDao = FakeTaskTagDao()
    private val tagDao = FakeChecklistTagDao()

    /** 用 Unconfined 当 IO 调度器：仓库里的 withContext 不会把测试挂住。 */
    private val repository = ChecklistRepositoryImpl(dao, taskTagDao, tagDao, Dispatchers.Unconfined)

    private fun topLevel(
        id: Long = 0L,
        title: String = "项",
        manualOrder: Long = 0L,
    ): TaskEntity = TaskEntity(
        id = id,
        parentTaskId = null,
        title = title,
        manualOrder = manualOrder,
        createdAt = id,
    )

    private fun child(
        id: Long,
        parentTaskId: Long,
        title: String = "子项",
        manualOrder: Long = 0L,
    ): TaskEntity = TaskEntity(
        id = id,
        parentTaskId = parentTaskId,
        title = title,
        manualOrder = manualOrder,
        createdAt = id,
    )

    private fun tag(id: Long, name: String): TagEntity = TagEntity(
        id = id,
        nameNormalized = name.lowercase(),
        displayName = name,
        colorHex = 0xFF000000,
    )

    // ===== 1. 新字段持久化 =====

    @Test
    fun addItemPersistsCategoryDueDatePriorityAndAmount() = runTest {
        val id = repository.addItem(
            title = "买牛奶",
            category = "生活",
            dueDate = 1_700_000_000_000L,
            priority = TaskPriority.HIGH,
            amount = 9.9,
        )

        val stored = dao.storedTasks.single { it.id == id }
        assertEquals("买牛奶", stored.title)
        assertEquals("生活", stored.category)
        assertEquals(1_700_000_000_000L, stored.dueDate)
        assertEquals(TaskPriority.HIGH.dbValue, stored.priority)
        assertEquals(9.9, stored.amount!!, 0.0001)
        assertNull("顶层清单项不该有 parentTaskId", stored.parentTaskId)
    }

    @Test
    fun addItemWithoutDetailsKeepsNullableFieldsNull() = runTest {
        val id = repository.addItem(title = "只有标题")

        val stored = dao.storedTasks.single { it.id == id }
        assertNull(stored.category)
        assertNull(stored.dueDate)
        assertNull(stored.amount)
        assertEquals("默认优先级应为中", TaskPriority.MEDIUM.dbValue, stored.priority)
    }

    @Test
    fun updateItemPersistsNewFields() = runTest {
        dao.seedTask(topLevel(id = 1L, title = "旧标题"))
        val current = repository.getItem(1L)!!

        repository.updateItem(
            current.copy(
                title = "新标题",
                category = "工作",
                dueDate = 42L,
                priority = TaskPriority.URGENT,
                amount = 3.5,
            ),
        )

        val stored = dao.storedTasks.single()
        assertEquals("新标题", stored.title)
        assertEquals("工作", stored.category)
        assertEquals(42L, stored.dueDate)
        assertEquals(TaskPriority.URGENT.dbValue, stored.priority)
        assertEquals(3.5, stored.amount!!, 0.0001)
    }

    @Test
    fun getItemMapsNewFieldsToDomain() = runTest {
        dao.seedTask(
            TaskEntity(
                id = 1L,
                parentTaskId = null,
                title = "带字段",
                category = "学习",
                dueDate = 7L,
                priority = TaskPriority.LOW.dbValue,
                amount = 1.25,
            ),
        )

        val item = repository.getItem(1L)!!
        assertEquals("学习", item.category)
        assertEquals(7L, item.dueDate)
        assertEquals(TaskPriority.LOW, item.priority)
        assertEquals(1.25, item.amount!!, 0.0001)
    }

    // ===== 2. manualOrder 追加 =====

    @Test
    fun addItemAppendsAfterExistingTopLevelItems() = runTest {
        dao.seedTask(topLevel(id = 1L, title = "第一", manualOrder = 0L))
        dao.seedTask(topLevel(id = 2L, title = "第二", manualOrder = 1L))

        val id = repository.addItem(title = "第三")

        assertEquals("新项应排在末尾（max + 1）", 2L, dao.storedTasks.single { it.id == id }.manualOrder)
    }

    @Test
    fun addItemStartsAtOrderZeroOnEmptyTable() = runTest {
        val id = repository.addItem(title = "第一个")
        assertEquals(0L, dao.storedTasks.single { it.id == id }.manualOrder)
    }

    @Test
    fun addNestedItemAppendsWithinTheSameParent() = runTest {
        dao.seedTask(topLevel(id = 1L, title = "父", manualOrder = 0L))

        val first = repository.addNestedItem(parentSubTaskId = 1L, title = "子一")
        val second = repository.addNestedItem(parentSubTaskId = 1L, title = "子二")
        val otherParentChild = repository.addNestedItem(parentSubTaskId = 2L, title = "另一个父的子")

        assertEquals(1L, dao.storedTasks.single { it.id == first }.parentTaskId)
        assertEquals(0L, dao.storedTasks.single { it.id == first }.manualOrder)
        assertEquals(1L, dao.storedTasks.single { it.id == second }.manualOrder)
        assertEquals("不同父项下各自从 0 开始", 0L, dao.storedTasks.single { it.id == otherParentChild }.manualOrder)
    }

    // ===== 3. 标签挂载 / 解绑 =====

    @Test
    fun attachTagCreatesCrossRefRow() = runTest {
        repository.attachTag(subTaskId = 1L, tagId = 7L)

        assertEquals(
            listOf(TaskTagCrossRef(taskId = 1L, tagId = 7L)),
            taskTagDao.storedPairs,
        )
    }

    @Test
    fun attachTagTwiceKeepsSingleRow() = runTest {
        repository.attachTag(subTaskId = 1L, tagId = 7L)
        repository.attachTag(subTaskId = 1L, tagId = 7L)

        assertEquals(1, taskTagDao.storedPairs.size)
    }

    @Test
    fun detachTagRemovesCrossRefRow() = runTest {
        repository.attachTag(subTaskId = 1L, tagId = 7L)
        repository.detachTag(subTaskId = 1L, tagId = 7L)

        assertTrue("解绑后不该还留着关联行", taskTagDao.storedPairs.isEmpty())
    }

    // ===== 4. 组树（顶层 + 标签 + 子清单） =====

    @Test
    fun observeChecklistItemsBuildsTreeWithTagsAndChildren() = runTest {
        dao.seedTask(topLevel(id = 1L, title = "父", manualOrder = 0L))
        dao.seedTask(child(id = 2L, parentTaskId = 1L, title = "子"))
        tagDao.seedTag(tag(id = 7L, name = "Work"))
        taskTagDao.seedTag(tag(id = 7L, name = "Work"))
        repository.attachTag(subTaskId = 1L, tagId = 7L)

        val items = repository.observeChecklistItems().first()

        assertEquals("只应有一个顶层项", 1, items.size)
        val root = items.single()
        assertEquals("父", root.title)
        assertEquals(listOf(7L), root.tags.map { it.id })
        assertEquals(1, root.children.size)
        assertEquals("子", root.children.single().title)
        assertEquals(1L, root.children.single().task.parentTaskId)
    }

    @Test
    fun observeChecklistItemsSortsTopLevelByManualOrder() = runTest {
        dao.seedTask(topLevel(id = 1L, title = "第一", manualOrder = 1L))
        dao.seedTask(topLevel(id = 2L, title = "第二", manualOrder = 0L))

        val items = repository.observeChecklistItems().first()

        assertEquals(listOf("第二", "第一"), items.map { it.title })
    }

    @Test
    fun buildChecklistTreeStopsAtMaxDepth() {
        val rows = (1L..5L).map { id ->
            TaskEntity(
                id = id,
                parentTaskId = if (id == 1L) null else id - 1,
                title = "级$id",
                manualOrder = 0L,
                createdAt = id,
            )
        }

        val tree = buildChecklistTree(rows, emptyList(), emptyList())

        assertEquals(1, tree.size)
        val root = tree.single()
        assertEquals(1, root.children.size)
        assertEquals(1, root.children.single().children.size)
        assertEquals(1, root.children.single().children.single().children.size)

        // Phase 16：深度到顶（MAX_CHECKLIST_DEPTH = 3）后，更深的子孙被**拍平**挂到这一层，
        // 而不是被静默丢掉 —— 数据还在，界面上也得看得见。
        val depthTop = root.children.single().children.single().children.single()
        assertEquals("超深项应被拍平挂到深度上限那一层", 1, depthTop.children.size)
        assertEquals(5L, depthTop.children.single().id)
        assertTrue("拍平项不再往下展开", depthTop.children.single().children.isEmpty())

        // 全部 5 行都还在树里 —— 一行都没丢
        val idsInTree = mutableListOf<Long>()
        fun collect(item: ChecklistItem) {
            idsInTree += item.id
            item.children.forEach { collect(it) }
        }
        tree.forEach { collect(it) }
        assertEquals("所有行都应出现在树里", (1L..5L).toSet(), idsInTree.toSet())
    }

    @Test
    fun buildChecklistTreeIgnoresSelfParentedRow() {
        val rows = listOf(
            topLevel(id = 1L, title = "根"),
            child(id = 2L, parentTaskId = 2L, title = "自环"),
        )

        val tree = buildChecklistTree(rows, emptyList(), emptyList())

        assertEquals("自环行没有顶层祖先，不该出现在树里", 1, tree.size)
        assertEquals(1L, tree.single().id)
    }

    // ===== 杂项 =====

    @Test
    fun toggleCompleteFlipsStoredFlag() = runTest {
        dao.seedTask(topLevel(id = 1L))

        repository.toggleComplete(id = 1L, completed = true)

        assertTrue(dao.storedTasks.single().isCompleted)
    }

    @Test
    fun deleteItemRemovesStoredRow() = runTest {
        dao.seedTask(topLevel(id = 1L))
        val item = repository.getItem(1L)!!

        repository.deleteItem(item)

        assertTrue(dao.storedTasks.isEmpty())
    }
}
