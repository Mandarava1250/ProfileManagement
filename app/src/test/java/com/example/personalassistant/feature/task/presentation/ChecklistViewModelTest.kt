package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.ChecklistFilter
import com.example.personalassistant.feature.task.domain.model.ChecklistGroup
import com.example.personalassistant.feature.task.domain.model.ChecklistItem
import com.example.personalassistant.feature.task.domain.model.ChecklistSort
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import com.example.personalassistant.feature.task.domain.repository.TagRepository
import com.example.personalassistant.feature.task.domain.usecase.AddChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.AddNestedChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.AttachTagToChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.DetachTagFromChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveAllTagsWithCountUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveChecklistItemsUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.UpdateChecklistItemUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 15：[ChecklistViewModel] 的 JVM 单测。
 *
 * 覆盖：
 * - 增删改查动作是否把参数原样交给仓库（新字段：分类 / 截止时间 / 优先级 / 金额 / 嵌套父指针 / 标签）
 * - 筛选状态（分类 / 标签）、分组、排序是否落到 [ChecklistUiState]
 * - [ChecklistUiState.itemDetails] 是否带上了标签与嵌套子清单
 *
 * 沿用项目「Fake 优先」风格：Repository / TagRepository 全部手写 Fake，不引 Mockito。
 *
 * ## 为什么用 Unconfined 当 Main dispatcher
 * ViewModel 的动作都走 `viewModelScope.launch { }`，而 uiState 用的是
 * `SharingStarted.WhileSubscribed` —— 只有真的订阅了才会拿到 combine 之后的值。
 * Unconfined 让 `launch` 的 bodies 立刻同步跑完，测试断言不用等调度器。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ===== Fake =====

    /** 内存版清单仓库：存 SubTask + 标签映射，读流自己组树。 */
    private class FakeChecklistRepository : ChecklistRepository {

        private val itemsState = MutableStateFlow<List<SubTask>>(emptyList())
        private val tagsState = MutableStateFlow<Map<Long, List<Tag>>>(emptyMap())
        private var nextId = 1L

        /** 当前存着的全部清单项（含嵌套子项）。 */
        val items: List<SubTask> get() = itemsState.value

        /** 直接塞清单项（构造测试场景）。 */
        fun setItems(items: List<SubTask>) {
            itemsState.value = items
            // 自增游标要跳过已存在的 id —— 否则后面 addItem 会撞 id
            val maxId = items.maxOfOrNull { it.id } ?: 0L
            if (maxId >= nextId) nextId = maxId + 1
        }

        /** 直接塞「清单项 id → 标签列表」。 */
        fun setTags(tags: Map<Long, List<Tag>>) {
            tagsState.value = tags
        }

        override fun observeChecklistItems(): Flow<List<ChecklistItem>> =
            combine(itemsState, tagsState) { items, tags -> buildTree(items, tags) }

        override suspend fun getItem(id: Long): SubTask? = itemsState.value.find { it.id == id }

        override suspend fun addItem(
            title: String,
            category: String?,
            dueDate: Long?,
            priority: TaskPriority,
            amount: Double?,
        ): Long {
            val id = nextId++
            itemsState.value = itemsState.value + SubTask(
                id = id,
                parentTaskId = null,
                title = title,
                order = itemsState.value.size,
                category = category,
                dueDate = dueDate,
                priority = priority,
                amount = amount,
            )
            return id
        }

        override suspend fun addNestedItem(parentSubTaskId: Long, title: String): Long {
            val id = nextId++
            itemsState.value = itemsState.value + SubTask(
                id = id,
                parentTaskId = null,
                title = title,
                parentSubTaskId = parentSubTaskId,
            )
            return id
        }

        override suspend fun updateItem(item: SubTask) {
            itemsState.value = itemsState.value.map { if (it.id == item.id) item else it }
        }

        override suspend fun deleteItem(item: SubTask) {
            itemsState.value = itemsState.value.filterNot { it.id == item.id }
        }

        override suspend fun toggleComplete(id: Long, completed: Boolean) {
            itemsState.value = itemsState.value.map {
                if (it.id == id) it.copy(isCompleted = completed) else it
            }
        }

        override suspend fun attachTag(subTaskId: Long, tagId: Long) {
            val current = tagsState.value[subTaskId].orEmpty()
            if (current.none { it.id == tagId }) {
                tagsState.value = tagsState.value +
                    (subTaskId to (current + Tag(id = tagId, nameNormalized = "tag$tagId", displayName = "Tag$tagId", colorHex = 0L)))
            }
        }

        override suspend fun detachTag(subTaskId: Long, tagId: Long) {
            val current = tagsState.value[subTaskId].orEmpty().filterNot { it.id == tagId }
            tagsState.value = tagsState.value + (subTaskId to current)
        }

        /** 扁平列表 → 「顶层项 + 标签 + 子清单」树。 */
        private fun buildTree(items: List<SubTask>, tags: Map<Long, List<Tag>>): List<ChecklistItem> {
            val childrenByParent = items.filter { it.parentSubTaskId != null }.groupBy { it.parentSubTaskId!! }
            fun build(item: SubTask): ChecklistItem = ChecklistItem(
                subTask = item,
                tags = tags[item.id].orEmpty(),
                children = childrenByParent[item.id].orEmpty().map { build(it) },
            )
            return items.filter { it.parentSubTaskId == null }.map { build(it) }
        }
    }

    /** 内存版标签仓库（只服务于 ViewModel 的标签 chip 行）。 */
    private class FakeTagRepository : TagRepository {
        private val tagsState = MutableStateFlow<List<Tag>>(emptyList())

        override fun observeAllTags(): Flow<List<Tag>> = tagsState

        override fun observeAllTagsWithCount(): Flow<List<Tag>> = tagsState

        override fun observeTagsForTask(taskId: Long): Flow<List<Tag>> = tagsState

        override suspend fun createTag(displayName: String, colorHex: Long): Tag =
            Tag(id = 1L, nameNormalized = displayName.lowercase(), displayName = displayName, colorHex = colorHex)

        override suspend fun deleteTag(id: Long) = Unit

        override suspend fun addTagToTask(taskId: Long, tagId: Long) = Unit

        override suspend fun removeTagFromTask(taskId: Long, tagId: Long) = Unit
    }

    // ===== 辅助 =====

    private fun makeVm(
        repo: FakeChecklistRepository = FakeChecklistRepository(),
        tagRepo: TagRepository = FakeTagRepository(),
    ): ChecklistViewModel = ChecklistViewModel(
        observeChecklistItems = ObserveChecklistItemsUseCase(repo),
        addChecklistItem = AddChecklistItemUseCase(repo),
        addNestedChecklistItem = AddNestedChecklistItemUseCase(repo),
        updateChecklistItem = UpdateChecklistItemUseCase(repo),
        toggleChecklistItem = ToggleChecklistItemUseCase(repo),
        deleteChecklistItem = DeleteChecklistItemUseCase(repo),
        attachTagToItem = AttachTagToChecklistItemUseCase(repo),
        detachTagFromItem = DetachTagFromChecklistItemUseCase(repo),
        observeAllTagsWithCount = ObserveAllTagsWithCountUseCase(tagRepo),
    )

    private fun tag(id: Long): Tag = Tag(
        id = id,
        nameNormalized = "tag$id",
        displayName = "Tag$id",
        colorHex = 0xFF000000,
    )

    // ===== 动作 =====

    @Test
    fun addItemWithDetailsForwardsAllFieldsToRepository() = runTest {
        val repo = FakeChecklistRepository()
        val vm = makeVm(repo)

        vm.addItemWithDetails(
            title = "买牛奶",
            category = "生活",
            dueDate = 1_700_000_000_000L,
            priority = TaskPriority.HIGH,
            amount = 9.9,
        )

        val stored = repo.items.single()
        assertEquals("买牛奶", stored.title)
        assertEquals("生活", stored.category)
        assertEquals(1_700_000_000_000L, stored.dueDate)
        assertEquals(TaskPriority.HIGH, stored.priority)
        assertEquals(9.9, stored.amount!!, 0.0001)
    }

    @Test
    fun addItemWithoutDetailsUsesDefaults() = runTest {
        val repo = FakeChecklistRepository()
        val vm = makeVm(repo)

        vm.addItem("只有标题")

        val stored = repo.items.single()
        assertEquals("只有标题", stored.title)
        assertNull(stored.category)
        assertNull(stored.dueDate)
        assertNull(stored.amount)
        assertEquals(TaskPriority.MEDIUM, stored.priority)
    }

    @Test
    fun addSubTaskStoresNestedParentPointer() = runTest {
        val repo = FakeChecklistRepository()
        val vm = makeVm(repo)

        vm.addSubTask(parentSubTaskId = 1L, title = "子项")

        val stored = repo.items.single()
        assertEquals("子项", stored.title)
        assertEquals(1L, stored.parentSubTaskId)
        assertNull(stored.parentTaskId)
    }

    @Test
    fun updateItemOnlyChangesGivenFields() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(
                    id = 1L,
                    parentTaskId = null,
                    title = "旧标题",
                    category = "工作",
                    priority = TaskPriority.HIGH,
                    amount = 5.0,
                ),
            ),
        )
        val vm = makeVm(repo)

        vm.updateItem(id = 1L, title = "新标题")

        val stored = repo.items.single()
        assertEquals("新标题", stored.title)
        assertEquals("没传的字段要保持原值：分类", "工作", stored.category)
        assertEquals("没传的字段要保持原值：优先级", TaskPriority.HIGH, stored.priority)
        assertEquals("没传的字段要保持原值：金额", 5.0, stored.amount!!, 0.0001)
    }

    @Test
    fun clearItemFieldsRemovesNullableFields() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(
                    id = 1L,
                    parentTaskId = null,
                    title = "标题",
                    category = "工作",
                    dueDate = 42L,
                    amount = 5.0,
                ),
            ),
        )
        val vm = makeVm(repo)

        vm.clearItemFields(id = 1L, clearCategory = true, clearDueDate = true, clearAmount = true)

        val stored = repo.items.single()
        assertNull(stored.category)
        assertNull(stored.dueDate)
        assertNull(stored.amount)
        assertEquals("标题不该被动", "标题", stored.title)
    }

    @Test
    fun toggleCompleteIsForwardedToRepository() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(listOf(SubTask(id = 1L, parentTaskId = null, title = "项")))
        val vm = makeVm(repo)

        vm.toggleComplete(id = 1L, completed = true)

        assertTrue(repo.items.single().isCompleted)
    }

    @Test
    fun deleteItemRemovesItemFromRepository() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(listOf(SubTask(id = 1L, parentTaskId = null, title = "项")))
        val vm = makeVm(repo)

        vm.deleteItem(repo.items.single())

        assertTrue(repo.items.isEmpty())
    }

    @Test
    fun attachTagShowsTagInUiState() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(listOf(SubTask(id = 1L, parentTaskId = null, title = "项")))
        val vm = makeVm(repo)

        vm.attachTag(subTaskId = 1L, tagId = 7L)

        val state = vm.uiState.first { ui -> ui.itemDetails.any { it.tags.any { tag -> tag.id == 7L } } }
        assertEquals(listOf(7L), state.itemDetails.single().tags.map { it.id })
    }

    @Test
    fun detachTagRemovesTagFromUiState() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(listOf(SubTask(id = 1L, parentTaskId = null, title = "项")))
        val vm = makeVm(repo)
        vm.attachTag(subTaskId = 1L, tagId = 7L)
        // 先等挂载生效，再解绑
        vm.uiState.first { ui -> ui.itemDetails.any { it.tags.any { tag -> tag.id == 7L } } }

        vm.detachTag(subTaskId = 1L, tagId = 7L)

        val state = vm.uiState.first { ui -> ui.itemDetails.none { it.tags.any { tag -> tag.id == 7L } } }
        assertTrue(state.itemDetails.single().tags.isEmpty())
    }

    // ===== 筛选 / 分组 / 排序 =====

    @Test
    fun categoryFilterNarrowsVisibleItems() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(id = 1L, parentTaskId = null, title = "工作项", category = "工作", order = 0),
                SubTask(id = 2L, parentTaskId = null, title = "生活项", category = "生活", order = 1),
            ),
        )
        val vm = makeVm(repo)

        vm.setCategoryFilter("工作")

        val state = vm.uiState.first { it.filter.category == "工作" }
        assertEquals(listOf("工作项"), state.items.map { it.title })
        assertEquals("工作", state.filter.category)
    }

    @Test
    fun tagFilterNarrowsVisibleItems() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(id = 1L, parentTaskId = null, title = "带标签", order = 0),
                SubTask(id = 2L, parentTaskId = null, title = "没标签", order = 1),
            ),
        )
        repo.setTags(mapOf(1L to listOf(tag(7L))))
        val vm = makeVm(repo)

        vm.setTagFilter(7L)

        val state = vm.uiState.first { it.filter.tagId == 7L }
        assertEquals(listOf("带标签"), state.items.map { it.title })
        assertEquals(7L, state.filter.tagId)
    }

    @Test
    fun groupByCategoryProducesSections() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(id = 1L, parentTaskId = null, title = "工作项", category = "工作", order = 0),
                SubTask(id = 2L, parentTaskId = null, title = "生活项", category = "生活", order = 1),
            ),
        )
        val vm = makeVm(repo)

        vm.setGroup(ChecklistGroup.BY_CATEGORY)

        val state = vm.uiState.first { it.filter.group == ChecklistGroup.BY_CATEGORY }
        assertEquals(2, state.sections.size)
        assertEquals(
            setOf("category:工作", "category:生活"),
            state.sections.map { it.key }.toSet(),
        )
    }

    @Test
    fun sortByTitleOrdersVisibleItems() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(id = 1L, parentTaskId = null, title = "B 项", order = 0),
                SubTask(id = 2L, parentTaskId = null, title = "A 项", order = 1),
            ),
        )
        val vm = makeVm(repo)

        vm.setSort(ChecklistSort.TITLE_ASC)

        val state = vm.uiState.first { it.filter.sort == ChecklistSort.TITLE_ASC }
        assertEquals(listOf("A 项", "B 项"), state.items.map { it.title })
    }

    @Test
    fun resetFilterRestoresDefaultFilter() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(id = 1L, parentTaskId = null, title = "工作项", category = "工作", order = 0),
                SubTask(id = 2L, parentTaskId = null, title = "生活项", category = "生活", order = 1),
            ),
        )
        val vm = makeVm(repo)
        vm.setCategoryFilter("工作")
        vm.uiState.first { it.filter.category == "工作" }

        vm.resetFilter()

        val state = vm.uiState.first { it.filter == ChecklistFilter() }
        assertEquals(ChecklistFilter(), state.filter)
        assertEquals("重置后所有项都回来了", 2, state.items.size)
    }

    // ===== UiState 形态 =====

    @Test
    fun uiStateExposesTagsAndNestedChildren() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(id = 1L, parentTaskId = null, title = "父", order = 0),
                SubTask(id = 2L, parentTaskId = null, title = "子", order = 0, parentSubTaskId = 1L),
            ),
        )
        repo.setTags(mapOf(1L to listOf(tag(7L))))
        val vm = makeVm(repo)

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(1, state.itemDetails.size)
        val root = state.itemDetails.single()
        assertEquals("父", root.title)
        assertEquals(listOf(7L), root.tags.map { it.id })
        assertEquals(1, root.children.size)
        assertEquals("子", root.children.single().title)
        assertEquals(1L, root.children.single().parentSubTaskId)
    }

    @Test
    fun uiStateKeepsFlatItemsForLegacyUi() = runTest {
        val repo = FakeChecklistRepository()
        repo.setItems(
            listOf(
                SubTask(id = 1L, parentTaskId = null, title = "父", order = 0),
                SubTask(id = 2L, parentTaskId = null, title = "子", order = 1),
            ),
        )
        val vm = makeVm(repo)

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(listOf("父", "子"), state.items.map { it.title })
    }
}
