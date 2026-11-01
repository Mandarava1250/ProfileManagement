package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TagWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 内存版 [TagDao]（只服务于清单测试）。
 *
 * 清单仓库读标签只需要 [observeAll]；其余方法要么顺手实现，要么直接 `error()`。
 */
class FakeChecklistTagDao : TagDao {

    private val tagsState = MutableStateFlow<List<TagEntity>>(emptyList())
    private var nextTagId = 1L

    /** 当前存着的全部标签。 */
    val storedTags: List<TagEntity> get() = tagsState.value

    /** 直接塞一个标签（模拟库里已有的标签），返回最终 id。 */
    fun seedTag(tag: TagEntity): Long {
        val id = if (tag.id == 0L) nextTagId++ else tag.id
        // 显式塞了 id 的话，自增游标要跳过它 —— 否则后面 insert 会撞 id
        if (tag.id != 0L && tag.id >= nextTagId) nextTagId = tag.id + 1
        tagsState.value = tagsState.value.filterNot { it.id == id } + tag.copy(id = id)
        return id
    }

    override fun observeAll(): Flow<List<TagEntity>> = tagsState

    override suspend fun insert(tag: TagEntity): Long {
        val id = if (tag.id == 0L) nextTagId++ else tag.id
        tagsState.value = tagsState.value.filterNot { it.id == id } + tag.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        tagsState.value = tagsState.value.filterNot { it.id == id }
    }

    override suspend fun findByNormalizedName(nameNormalized: String): TagEntity? =
        tagsState.value.find { it.nameNormalized == nameNormalized }

    override suspend fun searchByDisplayNameLike(query: String): List<TagEntity> =
        tagsState.value.filter { it.displayName.contains(query) }

    override suspend fun getAllTags(): List<TagEntity> = tagsState.value

    /**
     * 性能优化（v14）：TagDao 新增的批量接口（备份导入用）。
     *
     * ⚠️ 逐字复刻 [insert] 的 id 分配逻辑，**不**额外推进自增游标 ——
     * 批量与逐条两条路径必须给出完全一样的 id，否则备份导入的行为会分叉。
     */
    override suspend fun insertTags(tags: List<TagEntity>): List<Long> {
        val ids = mutableListOf<Long>()
        tagsState.value = tagsState.value + tags.map { tag ->
            val id = if (tag.id == 0L) nextTagId++ else tag.id
            ids += id
            tag.copy(id = id)
        }
        return ids
    }

    /** 性能优化（v14）：整表清空（备份导入用）。 */
    override suspend fun clearTags(): Int {
        val size = tagsState.value.size
        tagsState.value = emptyList()
        return size
    }

    override fun observeAllWithCount(): Flow<List<TagWithCount>> =
        error("FakeChecklistTagDao: observeAllWithCount 不该在清单测试里被调用")
}
