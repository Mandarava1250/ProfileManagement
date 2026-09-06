package com.example.personalassistant.feature.task.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Phase 6c：任务 ↔ 标签 关联 DAO
 *
 * - attach / detach：单条关联 / 取消关联（OnConflictStrategy.IGNORE 让重复 attach 静默）
 * - detachAllForTask：清空某任务下所有标签
 * - detachAllForTag：清空某标签下所有任务（标签删除时由 FK CASCADE 自动做，无需调此）
 * - observeTagsForTask：观察某任务关联的全部标签（含 TagEntity 全部字段）
 * - observeTaskIdsByTag：观察某标签关联的全部任务 id（DetailScreen 列表用）
 *
 * 设计取舍：attach / detach 都用 OnConflictStrategy.IGNORE，避免重复关联触发异常。
 */
@Dao
interface TaskTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(ref: TaskTagCrossRef)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun detach(taskId: Long, tagId: Long)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId")
    suspend fun detachAllForTask(taskId: Long)

    @Query("DELETE FROM task_tag_cross_ref WHERE tagId = :tagId")
    suspend fun detachAllForTag(tagId: Long)

    /**
     * 观察某任务关联的全部标签。
     * 用 INNER JOIN 保证只返回有标签实体存在的关联（孤儿关联行被 FK CASCADE 自动清掉）。
     */
    @Query(
        """
        SELECT t.id AS id, t.nameNormalized AS nameNormalized,
               t.displayName AS displayName, t.colorHex AS colorHex,
               t.colorSource AS colorSource, t.createdAt AS createdAt
        FROM task_tag_cross_ref r
        INNER JOIN tags t ON r.tagId = t.id
        WHERE r.taskId = :taskId
        ORDER BY t.createdAt ASC
        """
    )
    fun observeTagsForTask(taskId: Long): Flow<List<TagEntity>>

    /**
     * 观察某标签关联的全部任务 id（任务列表筛选用，repository 层把 id 集合应用到内存过滤）。
     */
    @Query(
        """
        SELECT r.taskId FROM task_tag_cross_ref r
        WHERE r.tagId = :tagId
        """
    )
    fun observeTaskIdsByTag(tagId: Long): Flow<List<Long>>

    /**
     * Phase 6c：观察全部 task_tag_cross_ref 关联行。
     *
     * 用于 [FilterExpression.ByTag] / 包含 ByTag 的复合 case：
     * Repository 收到此流后内存里构建 tagId → Set<taskId> 索引，
     * 然后对从 observeAll 拉到的任务做内存过滤。
     */
    @Query("SELECT taskId, tagId FROM task_tag_cross_ref")
    fun observeAllTaskTagPairs(): Flow<List<TaskTagCrossRef>>
}