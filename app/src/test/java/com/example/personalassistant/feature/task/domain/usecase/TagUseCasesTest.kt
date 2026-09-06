package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.data.repository.FakeTagRepository
import com.example.personalassistant.feature.task.domain.model.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 6c：标签相关 UseCase 单元测试。
 *
 * 覆盖：
 * - CreateTagUseCase：去重委托 / 非空校验
 * - AddTagToTaskUseCase：调用 Repository
 * - RemoveTagFromTaskUseCase：调用 Repository
 * - DeleteTagUseCase：调用 Repository + 模拟 CASCADE
 */
class TagUseCasesTest {

    private lateinit var repo: FakeTagRepository
    private lateinit var createTag: CreateTagUseCase
    private lateinit var deleteTag: DeleteTagUseCase
    private lateinit var addTagToTask: AddTagToTaskUseCase
    private lateinit var removeTagFromTask: RemoveTagFromTaskUseCase
    private lateinit var observeTagsForTask: ObserveTagsForTaskUseCase
    private lateinit var observeAllTagsWithCount: ObserveAllTagsWithCountUseCase

    @Before
    fun setup() {
        repo = FakeTagRepository()
        createTag = CreateTagUseCase(repo)
        deleteTag = DeleteTagUseCase(repo)
        addTagToTask = AddTagToTaskUseCase(repo)
        removeTagFromTask = RemoveTagFromTaskUseCase(repo)
        observeTagsForTask = ObserveTagsForTaskUseCase(repo)
        observeAllTagsWithCount = ObserveAllTagsWithCountUseCase(repo)
    }

    // ===== CreateTagUseCase =====

    @Test
    fun `CreateTagUseCase creates new tag`() = runTest {
        val tag = createTag("Work", 0xFFFF69B4)
        assertEquals("Work", tag.displayName)
        assertTrue(tag.id > 0)
    }

    @Test
    fun `CreateTagUseCase trims input`() = runTest {
        val tag = createTag("  Trim Me  ", 0xFFFF69B4)
        assertEquals("Trim Me", tag.displayName)
    }

    @Test
    fun `CreateTagUseCase propagates IllegalArgumentException for empty input`() = runTest {
        try {
            createTag("", 0xFFFF69B4)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `CreateTagUseCase returns existing tag for case-insensitive duplicate`() = runTest {
        val a = createTag("work", 0xFFFF69B4)
        val b = createTag("WORK", 0xFF64B5F6)
        assertEquals(a.id, b.id)
        assertEquals(0xFFFF69B4, b.colorHex)
    }

    // ===== AddTagToTaskUseCase =====

    @Test
    fun `AddTagToTaskUseCase calls repository`() = runTest {
        val tag = createTag("work", 0xFFFF69B4)
        addTagToTask(taskId = 1L, tagId = tag.id)
        val tags = observeTagsForTask(1L).first()
        assertEquals(1, tags.size)
        assertEquals(tag.id, tags[0].id)
    }

    // ===== RemoveTagFromTaskUseCase =====

    @Test
    fun `RemoveTagFromTaskUseCase removes association`() = runTest {
        val tag = createTag("work", 0xFFFF69B4)
        addTagToTask(1L, tag.id)
        assertEquals(1, observeTagsForTask(1L).first().size)
        removeTagFromTask(1L, tag.id)
        assertEquals(0, observeTagsForTask(1L).first().size)
    }

    // ===== DeleteTagUseCase =====

    @Test
    fun `DeleteTagUseCase removes tag and cascades pairs`() = runTest {
        val tag = createTag("work", 0xFFFF69B4)
        addTagToTask(1L, tag.id)
        addTagToTask(2L, tag.id)
        assertEquals(2, observeAllTagsWithCount().first()[0].usageCount)
        deleteTag(tag.id)
        assertTrue(observeTagsForTask(1L).first().isEmpty())
        assertTrue(observeTagsForTask(2L).first().isEmpty())
        assertTrue(observeAllTagsWithCount().first().isEmpty())
    }

    // ===== observeAllTagsWithCount =====

    @Test
    fun `ObserveAllTagsWithCountUseCase emits sorted by usageCount`() = runTest {
        val tagA = createTag("a", 0xFFFF69B4)
        val tagB = createTag("b", 0xFF64B5F6)
        addTagToTask(1L, tagA.id)
        addTagToTask(2L, tagA.id)
        addTagToTask(3L, tagA.id)
        addTagToTask(1L, tagB.id)
        val sorted = observeAllTagsWithCount().first()
        assertEquals(2, sorted.size)
        assertEquals(tagA.id, sorted[0].id)
        assertEquals(3, sorted[0].usageCount)
        assertEquals(tagB.id, sorted[1].id)
        assertEquals(1, sorted[1].usageCount)
    }
}