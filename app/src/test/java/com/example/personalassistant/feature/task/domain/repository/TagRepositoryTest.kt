package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.data.repository.FakeTagRepository
import com.example.personalassistant.feature.task.domain.model.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 6c：[TagRepository] 单元测试（用 [FakeTagRepository]）。
 *
 * 覆盖：
 * - createTag：去重 / 大小写不敏感 / trim / 非空校验
 * - deleteTag：清掉标签 + 关联行（CASCADE 模拟）
 * - addTagToTask / removeTagToTask：attach/detach
 * - observeAllTagsWithCount：按使用频次降序
 * - observeTagsForTask：仅返回某任务的标签
 */
class TagRepositoryTest {

    private lateinit var repo: FakeTagRepository

    @Before
    fun setup() {
        repo = FakeTagRepository()
    }

    @Test
    fun `createTag with new name inserts and returns Tag`() = runTest {
        val tag = repo.createTag("Work", 0xFFFF69B4)
        assertEquals("Work", tag.displayName)
        assertEquals("work", tag.nameNormalized)
        assertEquals(0xFFFF69B4, tag.colorHex)
        assertTrue(tag.id > 0)
    }

    @Test
    fun `createTag with different case returns same tag (case-insensitive dedup)`() = runTest {
        val first = repo.createTag("Work", 0xFFFF69B4)
        val second = repo.createTag("WORK", 0xFF64B5F6)
        assertEquals(first.id, second.id)
        // 已存在时返回已存在的标签，颜色不变
        assertEquals(0xFFFF69B4, second.colorHex)
    }

    @Test
    fun `createTag with leading whitespace trims`() = runTest {
        val tag = repo.createTag("  Important  ", 0xFFFF69B4)
        assertEquals("Important", tag.displayName)
        assertEquals("important", tag.nameNormalized)
    }

    @Test
    fun `createTag with empty string throws IllegalArgumentException`() = runTest {
        try {
            repo.createTag("", 0xFFFF69B4)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `createTag with whitespace-only string throws`() = runTest {
        try {
            repo.createTag("   ", 0xFFFF69B4)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `deleteTag removes tag and cascades to pairs`() = runTest {
        val tag = repo.createTag("Work", 0xFFFF69B4)
        repo.addTagToTask(taskId = 1L, tagId = tag.id)
        repo.addTagToTask(taskId = 2L, tagId = tag.id)
        // 删除标签
        repo.deleteTag(tag.id)
        // 验证：标签没了 + 关联也没了（mock CASCADE 行为）
        val tagsAfter = repo.observeAllTags().first()
        val task1Tags = repo.observeTagsForTask(taskId = 1L).first()
        val task2Tags = repo.observeTagsForTask(taskId = 2L).first()
        assertTrue(tagsAfter.isEmpty())
        assertTrue(task1Tags.isEmpty())
        assertTrue(task2Tags.isEmpty())
    }

    @Test
    fun `observeAllTagsWithCount sorts by usageCount descending`() = runTest {
        // 3 个标签，tag-100 用 3 次，tag-200 用 1 次，tag-300 用 0 次
        val tag100 = repo.createTag("high-usage", 0xFFFF69B4)
        val tag200 = repo.createTag("medium-usage", 0xFF64B5F6)
        val tag300 = repo.createTag("unused", 0xFF81C784)
        repo.addTagToTask(1L, tag100.id)
        repo.addTagToTask(2L, tag100.id)
        repo.addTagToTask(3L, tag100.id)
        repo.addTagToTask(1L, tag200.id)

        val sorted = repo.observeAllTagsWithCount().first()
        assertEquals(3, sorted.size)
        assertEquals(tag100.id, sorted[0].id)
        assertEquals(3, sorted[0].usageCount)
        assertEquals(tag200.id, sorted[1].id)
        assertEquals(1, sorted[1].usageCount)
        assertEquals(tag300.id, sorted[2].id)
        assertEquals(0, sorted[2].usageCount)
    }

    @Test
    fun `observeTagsForTask returns only this task's tags`() = runTest {
        val tag1 = repo.createTag("a", 0xFFFF69B4)
        val tag2 = repo.createTag("b", 0xFF64B5F6)
        repo.addTagToTask(1L, tag1.id)
        repo.addTagToTask(2L, tag2.id)
        repo.addTagToTask(1L, tag2.id)

        val task1 = repo.observeTagsForTask(1L).first()
        val task2 = repo.observeTagsForTask(2L).first()
        assertEquals(2, task1.size)
        assertEquals(setOf(tag1.id, tag2.id), task1.map { it.id }.toSet())
        assertEquals(1, task2.size)
        assertEquals(tag2.id, task2[0].id)
    }

    @Test
    fun `addTagToTask then removeTagFromTask restores no-association state`() = runTest {
        val tag = repo.createTag("test", 0xFFFF69B4)
        repo.addTagToTask(1L, tag.id)
        assertEquals(1, repo.observeTagsForTask(1L).first().size)
        repo.removeTagFromTask(1L, tag.id)
        assertTrue(repo.observeTagsForTask(1L).first().isEmpty())
    }

    @Test
    fun `removeTagFromTask on non-existent pair is silent no-op`() = runTest {
        val tag = repo.createTag("test", 0xFFFF69B4)
        // 不抛错
        repo.removeTagFromTask(99L, tag.id)
        assertTrue(repo.observeTagsForTask(99L).first().isEmpty())
    }

    @Test
    fun `addTagToTask with same pair twice is idempotent`() = runTest {
        val tag = repo.createTag("test", 0xFFFF69B4)
        repo.addTagToTask(1L, tag.id)
        repo.addTagToTask(1L, tag.id)
        // mock 行为：去重后只有 1 条关联
        val pairs = repo.observeTagsForTask(1L).first()
        assertEquals(1, pairs.size)
        // usageCount = 1（不是 2）
        val withCount = repo.observeAllTagsWithCount().first()
        assertEquals(1, withCount[0].usageCount)
    }

    @Test
    fun `Tag normalize function trims and lowercases`() {
        assertEquals("work", Tag.normalize("Work"))
        assertEquals("work", Tag.normalize("  WORK  "))
        assertEquals("hello", Tag.normalize("Hello"))
    }

    @Test
    fun `createTag with different name gives different IDs`() = runTest {
        val a = repo.createTag("work", 0xFFFF69B4)
        val b = repo.createTag("life", 0xFF64B5F6)
        assertNotEquals(a.id, b.id)
    }
}