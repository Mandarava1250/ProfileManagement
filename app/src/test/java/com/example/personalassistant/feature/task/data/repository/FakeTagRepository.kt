package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TagWithCount
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.task.domain.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 单元测试用的内存版 [TagRepository]（不依赖 Room）。
 *
 * - [tagsFlow] + [pairsFlow] 模拟两张表的 observe 流
 * - 写操作（insert/delete/attach/detach）同步更新内存 + 触发 flow 重新发射
 * - 线程安全：本测试类不并发调用，无需 Mutex
 */
class FakeTagRepository : com.example.personalassistant.feature.task.domain.repository.TagRepository {

    private val tagsState = MutableStateFlow<List<TagEntity>>(emptyList())
    private val pairsState = MutableStateFlow<List<TaskTagCrossRef>>(emptyList())
    private var nextId = 1L

    // ===== 暴露为 DAO-like 接口方便测试 =====
    val tagsFlow: Flow<List<TagEntity>> get() = tagsState
    val pairsFlow: Flow<List<TaskTagCrossRef>> get() = pairsState

    // ===== TagRepository 接口 =====

    override fun observeAllTags(): Flow<List<Tag>> =
        tagsState.map { list -> list.map { it.toDomain() } }

    override fun observeAllTagsWithCount(): Flow<List<Tag>> {
        // 实时 join：tag → count
        return kotlinx.coroutines.flow.combine(tagsState, pairsState) { tags, pairs ->
            tags.map { entity ->
                val count = pairs.count { it.tagId == entity.id }
                entity.toDomain().copy(usageCount = count)
            }.sortedWith(compareByDescending<Tag> { it.usageCount }.thenBy { it.createdAt })
        }
    }

    override fun observeTagsForTask(taskId: Long): Flow<List<Tag>> =
        kotlinx.coroutines.flow.combine(tagsState, pairsState) { tags, pairs ->
            val tagIds = pairs.filter { it.taskId == taskId }.map { it.tagId }.toSet()
            tags.filter { it.id in tagIds }.map { it.toDomain() }
        }

    override suspend fun createTag(displayName: String, colorHex: Long): Tag {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        val normalized = Tag.normalize(trimmed)
        // fetch-or-create
        val existing = tagsState.value.find { it.nameNormalized == normalized }
        if (existing != null) return existing.toDomain()
        val newEntity = TagEntity(
            id = nextId++,
            nameNormalized = normalized,
            displayName = trimmed,
            colorHex = colorHex,
            colorSource = "default",
            createdAt = System.currentTimeMillis(),
        )
        tagsState.value = tagsState.value + newEntity
        return newEntity.toDomain()
    }

    override suspend fun deleteTag(id: Long) {
        tagsState.value = tagsState.value.filter { it.id != id }
        pairsState.value = pairsState.value.filter { it.tagId != id }
    }

    override suspend fun addTagToTask(taskId: Long, tagId: Long) {
        val exists = pairsState.value.any { it.taskId == taskId && it.tagId == tagId }
        if (!exists) {
            pairsState.value = pairsState.value + TaskTagCrossRef(taskId, tagId)
        }
    }

    override suspend fun removeTagFromTask(taskId: Long, tagId: Long) {
        pairsState.value = pairsState.value.filter {
            !(it.taskId == taskId && it.tagId == tagId)
        }
    }

    // ===== 测试辅助 =====

    /** 重置全部状态（测试间互不污染） */
    fun reset() {
        tagsState.value = emptyList()
        pairsState.value = emptyList()
        nextId = 1L
    }
}

/**
 * 把 TagEntity 转 Tag（避免依赖真实 Mapper，测试只需字段对应）
 */
private fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    nameNormalized = nameNormalized,
    displayName = displayName,
    colorHex = colorHex,
    colorSource = colorSource,
    createdAt = createdAt,
    usageCount = 0,
)

private fun TagWithCount.toDomain(): Tag = Tag(
    id = id,
    nameNormalized = nameNormalized,
    displayName = displayName,
    colorHex = colorHex,
    colorSource = colorSource,
    createdAt = createdAt,
    usageCount = usageCount,
)