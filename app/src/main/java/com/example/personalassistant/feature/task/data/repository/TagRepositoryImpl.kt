package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.task.data.mapper.toDomain
import com.example.personalassistant.feature.task.data.mapper.toEntity
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 6c：标签仓库实现
 *
 * - 所有 IO 通过 [dispatcher]（IoDispatcher）执行
 * - [createTag] 走「fetch-or-create」策略：先按 normalized name 查，已存在就返回；不存在就 insert
 * - [addTagToTask] / [removeTagFromTask] 都用 OnConflictStrategy.IGNORE 风格的 silent 行为
 * - [deleteTag] 由 FK CASCADE 自动清掉关联行，调用方无需手动 detach
 */
@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val taskTagDao: TaskTagDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : TagRepository {

    // ===== 读流 =====

    override fun observeAllTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeAllTagsWithCount(): Flow<List<Tag>> =
        tagDao.observeAllWithCount().map { list -> list.map { it.toDomain() } }

    override fun observeTagsForTask(taskId: Long): Flow<List<Tag>> =
        taskTagDao.observeTagsForTask(taskId).map { list -> list.map { it.toDomain() } }

    // ===== 写操作 =====

    override suspend fun createTag(displayName: String, colorHex: Long): Tag = withContext(dispatcher) {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        val normalized = Tag.normalize(trimmed)
        // fetch-or-create：DB 已存在则直接返回
        tagDao.findByNormalizedName(normalized)?.let { existing ->
            return@withContext existing.toDomain()
        }
        val now = System.currentTimeMillis()
        val entity = TagEntity(
            id = 0L,
            nameNormalized = normalized,
            displayName = trimmed,
            colorHex = colorHex,
            colorSource = "default",
            createdAt = now,
        )
        val newRowId = tagDao.insert(entity)
        // insert 走 OnConflictStrategy.IGNORE，万一存在并发插入同一标签
        // - newRowId = -1：并发竞争时已被人插入，回查一次拿真实 id
        // - newRowId > 0：新插入成功
        if (newRowId <= 0L) {
            tagDao.findByNormalizedName(normalized)!!.toDomain()
        } else {
            entity.copy(id = newRowId).toDomain()
        }
    }

    override suspend fun deleteTag(id: Long) = withContext(dispatcher) {
        tagDao.deleteById(id)
    }

    override suspend fun addTagToTask(taskId: Long, tagId: Long) = withContext(dispatcher) {
        taskTagDao.attach(TaskTagCrossRef(taskId = taskId, tagId = tagId))
    }

    override suspend fun removeTagFromTask(taskId: Long, tagId: Long) = withContext(dispatcher) {
        taskTagDao.detach(taskId = taskId, tagId = tagId)
    }
}