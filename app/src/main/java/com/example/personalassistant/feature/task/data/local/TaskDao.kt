package com.example.personalassistant.feature.task.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 任务 DAO：覆盖 tasks + sub_tasks 两张表
 */
@Dao
interface TaskDao {

    // ===== Task CRUD =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    /** 按截止日期分组：今天 / 明天 / 未来 / 已过期 / 已完成 */
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDate ASC, priority DESC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority DESC, dueDate ASC")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY dueDate DESC")
    fun observeCompleted(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end AND isCompleted = 0 ORDER BY priority DESC, dueDate ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<TaskEntity>>

    /**
     * Phase 14：区间内的全部任务，**含已完成**。
     *
     * 日历里勾完的任务要继续留着 —— 除非用户手动删除。
     *
     * 区间是**闭区间** `[start, end]`。要表达「某一天」时调用方得传
     * `end = 当天结束 - 1ms`，否则次日 00:00 的任务会被相邻两天同时收录
     * （同一条任务在周视图里横跨两列）。
     */
    @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end ORDER BY priority DESC, dueDate ASC")
    fun observeBetweenIncludingCompleted(start: Long, end: Long): Flow<List<TaskEntity>>

    /**
     * Phase 8 Sub-step 3：全局搜索 — 在 tasks 表 title / description / category 任一字段 LIKE 匹配。
     *
     * - :keyword 为原始关键字，调用方负责 prepend/append `%`
     * - 大小写敏感（与 FinanceDao.searchTransactions 一致）
     * - 排序：priority DESC, dueDate ASC NULLS LAST, updatedAt DESC
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE title LIKE :keyword
           OR description LIKE :keyword
           OR category LIKE :keyword
        ORDER BY priority DESC, dueDate IS NULL, dueDate ASC, updatedAt DESC
        """
    )
    fun searchTasks(keyword: String): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET isCompleted = :completed, updatedAt = :now WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, now: Long = System.currentTimeMillis())

    // ===== Phase 6a：拖拽手动排序 =====

    /**
     * 更新单条任务的 manualOrder（拖拽过程中单条更新场景）
     */
    @Query("UPDATE tasks SET manualOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun updateManualOrder(id: Long, order: Long, now: Long = System.currentTimeMillis())

    // ===== SubTask CRUD =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTask(subTask: SubTaskEntity): Long

    @Update
    suspend fun updateSubTask(subTask: SubTaskEntity)

    @Delete
    suspend fun deleteSubTask(subTask: SubTaskEntity)

    @Query("UPDATE sub_tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setSubTaskCompleted(id: Long, completed: Boolean)

    /**
     * 观察独立清单项（parentTaskId IS NULL）。
     *
     * ⚠️ Phase 15：这里**包含嵌套子清单项**（parentSubTaskId 非空的行）——
     * 仓库层靠 parentSubTaskId 组树，所以这里要一次拿全。
     * 只要顶层项请用 [observeTopLevelSubTasks]。
     */
    @Query("SELECT * FROM sub_tasks WHERE parentTaskId IS NULL ORDER BY `order`, createdAt")
    fun observeStandaloneSubTasks(): Flow<List<SubTaskEntity>>

    /**
     * Phase 15：观察**顶层**独立清单项（既不属于任务，也不是别人的子清单）。
     */
    @Query(
        "SELECT * FROM sub_tasks WHERE parentTaskId IS NULL AND parentSubTaskId IS NULL " +
            "ORDER BY `order`, createdAt",
    )
    fun observeTopLevelSubTasks(): Flow<List<SubTaskEntity>>

    /** Phase 15：观察某个清单项下的嵌套子清单。 */
    @Query("SELECT * FROM sub_tasks WHERE parentSubTaskId = :parentSubTaskId ORDER BY `order`, createdAt")
    fun observeNestedSubTasks(parentSubTaskId: Long): Flow<List<SubTaskEntity>>

    /** 观察任务的所有子清单 */
    @Query("SELECT * FROM sub_tasks WHERE parentTaskId = :parentId ORDER BY `order`, createdAt")
    fun observeSubTasks(parentId: Long): Flow<List<SubTaskEntity>>

    @Query("SELECT * FROM sub_tasks WHERE parentTaskId = :parentId ORDER BY `order`, createdAt")
    suspend fun getSubTasks(parentId: Long): List<SubTaskEntity>

    /**
     * Phase 6b：返回任务下当前最大 order（null = 无子清单）。
     *
     * 用于 [addSubTaskToTask] 时自动追加到末尾，避免显示错乱。
     */
    @Query("SELECT MAX(`order`) FROM sub_tasks WHERE parentTaskId = :parentId")
    suspend fun getMaxSubTaskOrder(parentId: Long): Int?

    @Query("DELETE FROM sub_tasks WHERE parentTaskId = :parentId")
    suspend fun deleteSubTasksByParent(parentId: Long)

    // ===== Phase 15：清单项扩展（分类 / 截止 / 优先级 / 金额 / 嵌套 / 标签） =====

    /** 按 id 取单个清单项。 */
    @Query("SELECT * FROM sub_tasks WHERE id = :id")
    suspend fun getSubTaskById(id: Long): SubTaskEntity?

    /**
     * Phase 15：顶层独立清单项的当前最大 order（null = 一个都没有）。
     *
     * 用于新增清单项时**追加到末尾**（max + 1），
     * 修掉「独立清单新增时 order 恒为 0、新项全挤在最前面」的旧问题。
     */
    @Query("SELECT MAX(`order`) FROM sub_tasks WHERE parentTaskId IS NULL AND parentSubTaskId IS NULL")
    suspend fun getMaxTopLevelSubTaskOrder(): Int?

    /** Phase 15：某个父清单项下嵌套子项的当前最大 order（null = 一个都没有）。 */
    @Query("SELECT MAX(`order`) FROM sub_tasks WHERE parentSubTaskId = :parentSubTaskId")
    suspend fun getMaxNestedSubTaskOrder(parentSubTaskId: Long): Int?

    /** Phase 15：按分类筛顶层清单项。 */
    @Query(
        "SELECT * FROM sub_tasks WHERE parentTaskId IS NULL AND parentSubTaskId IS NULL " +
            "AND category = :category ORDER BY `order`, createdAt",
    )
    fun observeTopLevelSubTasksByCategory(category: String): Flow<List<SubTaskEntity>>

    /** Phase 15：按优先级筛顶层清单项（priority 存 TaskPriority.dbValue）。 */
    @Query(
        "SELECT * FROM sub_tasks WHERE parentTaskId IS NULL AND parentSubTaskId IS NULL " +
            "AND priority = :priority ORDER BY `order`, createdAt",
    )
    fun observeTopLevelSubTasksByPriority(priority: Int): Flow<List<SubTaskEntity>>

    // ===== Phase 15：清单项 ↔ 标签 =====

    /** 挂标签（重复挂载由联合主键 + IGNORE 静默忽略）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachSubTaskTag(ref: SubTaskTagCrossRef)

    /** 解绑单个标签。 */
    @Query("DELETE FROM sub_task_tag_cross_ref WHERE subTaskId = :subTaskId AND tagId = :tagId")
    suspend fun detachSubTaskTag(subTaskId: Long, tagId: Long)

    /** 清空某清单项下的全部标签。 */
    @Query("DELETE FROM sub_task_tag_cross_ref WHERE subTaskId = :subTaskId")
    suspend fun detachAllSubTaskTags(subTaskId: Long)

    /**
     * 观察某清单项关联的全部标签。
     * INNER JOIN 保证只返回标签实体仍存在的关联（孤儿行由 FK CASCADE 清掉）。
     */
    @Query(
        """
        SELECT t.id AS id, t.nameNormalized AS nameNormalized,
               t.displayName AS displayName, t.colorHex AS colorHex,
               t.colorSource AS colorSource, t.createdAt AS createdAt
        FROM sub_task_tag_cross_ref r
        INNER JOIN tags t ON r.tagId = t.id
        WHERE r.subTaskId = :subTaskId
        ORDER BY t.createdAt ASC
        """,
    )
    fun observeTagsForSubTask(subTaskId: Long): Flow<List<TagEntity>>

    /** 观察全部清单项 ↔ 标签关联行（仓库层据此给每个清单项装配标签）。 */
    @Query("SELECT subTaskId, tagId FROM sub_task_tag_cross_ref")
    fun observeAllSubTaskTagPairs(): Flow<List<SubTaskTagCrossRef>>

    /** 观察某标签关联的全部清单项 id。 */
    @Query("SELECT subTaskId FROM sub_task_tag_cross_ref WHERE tagId = :tagId")
    fun observeSubTaskIdsByTag(tagId: Long): Flow<List<Long>>

    /** Phase 8 Sub-step 4 同款：备份导出快照用的全量关联行。 */
    @Query("SELECT subTaskId, tagId FROM sub_task_tag_cross_ref")
    suspend fun getAllSubTaskTagCrossRefs(): List<SubTaskTagCrossRef>

    // ===== Phase 8 Sub-step 4：备份导出快照 =====

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM sub_tasks")
    suspend fun getAllSubTasks(): List<SubTaskEntity>

    // ==================== 备份导入批量操作 ====================
    //
    // 导入备份时整表清空 + 整表灌入，原来走的是「读回全表 → 逐行删 / 逐行插」，
    // N 行就是 N 次 IO 往返。这里的批量接口把它压成 1 次。
    //
    // ⚠️ 冲突策略必须与对应的单条方法**逐字一致**（REPLACE ↔ REPLACE）。

    /** 批量插入任务（REPLACE，与 [insert] 一致）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    /** 批量插入清单项（REPLACE，与 [insertSubTask] 一致）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(subTasks: List<SubTaskEntity>): List<Long>

    /** 清空任务表（备份导入用）。 */
    @Query("DELETE FROM tasks")
    suspend fun clearTasks(): Int

    /** 清空清单项表（备份导入用）。 */
    @Query("DELETE FROM sub_tasks")
    suspend fun clearSubTasks(): Int

    // ===== 统计查询 =====

    /** 今日完成率：[0.0, 1.0] */
    @Query(
        """
        SELECT
          CAST(SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) AS REAL) / COUNT(*)
        FROM tasks
        WHERE createdAt >= :todayStart
        """
    )
    suspend fun getTodayCompletionRate(todayStart: Long): Double?

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
    fun observeActiveCount(): Flow<Int>

    /**
     * 删除任务。子清单由 FK SET_NULL 自动转为独立项（不删除清单内容）。
     */
    @Transaction
    suspend fun deleteTaskWithChildren(task: TaskEntity) {
        delete(task)
    }
}
