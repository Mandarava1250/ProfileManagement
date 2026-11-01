package com.example.personalassistant.feature.task.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Phase 6c：标签 DAO
 *
 * - insert：插入新标签；OnConflictStrategy.IGNORE 让重复插入（同一 normalized name）静默成功
 *   （上层 Repository 拿到 -1 rowId 时再按 normalized name 查找已存在的那条）
 * - deleteById：删除标签（FK CASCADE 自动清掉关联行）
 * - observeAll：按创建时间升序观察全部标签
 * - observeAllWithCount：按使用频次降序观察标签 + 计数（标签云用）
 * - findByNormalizedName：根据规范化名（不区分大小写）查标签
 * - searchByDisplayNameLike：按 displayName 模糊搜索（任务详情选标签弹窗备用）
 */
@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tags ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TagEntity>>

    /**
     * 标签云：按使用频次降序，同频次按 createdAt 升序。
     * LEFT JOIN 保证 0 关联的标签也会出现（频次 = 0）。
     */
    @Query(
        """
        SELECT t.id AS id, t.nameNormalized AS nameNormalized,
               t.displayName AS displayName, t.colorHex AS colorHex,
               t.colorSource AS colorSource, t.createdAt AS createdAt,
               COUNT(r.taskId) AS usageCount
        FROM tags t
        LEFT JOIN task_tag_cross_ref r ON t.id = r.tagId
        GROUP BY t.id
        ORDER BY usageCount DESC, t.createdAt ASC
        """
    )
    fun observeAllWithCount(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE nameNormalized = :nameNormalized LIMIT 1")
    suspend fun findByNormalizedName(nameNormalized: String): TagEntity?

    @Query("SELECT * FROM tags WHERE displayName LIKE '%' || :query || '%' ORDER BY createdAt ASC")
    suspend fun searchByDisplayNameLike(query: String): List<TagEntity>

    // ===== Phase 8 Sub-step 4：备份导出快照 =====

    @Query("SELECT * FROM tags")
    suspend fun getAllTags(): List<TagEntity>

    // ==================== 备份导入批量操作 ====================
    //
    // ⚠️ 冲突策略必须与 [insert] **逐字一致**（IGNORE ↔ IGNORE）。

    /** 批量插入标签（IGNORE，与 [insert] 一致）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>): List<Long>

    /** 清空标签表（备份导入用）。 */
    @Query("DELETE FROM tags")
    suspend fun clearTags(): Int
}

/**
 * 带使用频次的标签（投影用）。
 *
 * 频次由 JOIN + GROUP BY 算出，0 表示标签尚未被任何任务使用（LEFT JOIN 兜底）。
 */
data class TagWithCount(
    val id: Long,
    val nameNormalized: String,
    val displayName: String,
    val colorHex: Long,
    val colorSource: String,
    val createdAt: Long,
    val usageCount: Int,
)