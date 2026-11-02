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
 * 任务 DAO
 *
 * ## Phase 16：`sub_tasks` 已并入 `tasks`
 *
 * 原来这张 DAO 覆盖 `tasks` + `sub_tasks` 两张表。Phase 16 把两套模型合成一套后，
 * **只剩 `tasks` 一张表**，层级靠 `parentTaskId` 自引用表达：
 *
 * - 顶层任务：`parentTaskId IS NULL`（含原「独立清单项」）
 * - 子任务：`parentTaskId = 某父任务 id`（含原「嵌套子清单」）
 *
 * 所以原来那批 `sub_tasks` 查询全部改成 `tasks` 上的 `parentTaskId` 查询，
 * 排序权重从 `sub_tasks.order` 换成 `tasks.manualOrder`。
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

    /**
     * 全部**顶层**任务（`parentTaskId IS NULL`）。
     *
     * ⚠️ Phase 16：必须带 `parentTaskId IS NULL` —— 合并后子任务也在 `tasks` 表里，
     * 不加这个条件会把子项当顶层项显示出来。
     */
    @Query(
        "SELECT * FROM tasks WHERE parentTaskId IS NULL " +
            "ORDER BY isCompleted ASC, dueDate ASC, priority DESC, createdAt DESC",
    )
    fun observeAll(): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM tasks WHERE parentTaskId IS NULL AND isCompleted = 0 " +
            "ORDER BY priority DESC, dueDate ASC",
    )
    fun observeActive(): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM tasks WHERE parentTaskId IS NULL AND isCompleted = 1 " +
            "ORDER BY dueDate DESC",
    )
    fun observeCompleted(): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM tasks WHERE parentTaskId IS NULL " +
            "AND dueDate BETWEEN :start AND :end AND isCompleted = 0 " +
            "ORDER BY priority DESC, dueDate ASC",
    )
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
    @Query(
        "SELECT * FROM tasks WHERE parentTaskId IS NULL " +
            "AND dueDate BETWEEN :start AND :end ORDER BY priority DESC, dueDate ASC",
    )
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
        WHERE parentTaskId IS NULL
          AND (title LIKE :keyword
            OR description LIKE :keyword
            OR category LIKE :keyword)
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

    // ===== 子任务（Phase 16：与任务同表，靠 parentTaskId 表达层级） =====

    /**
     * Phase 16：观察**全部**任务（含子任务）。
     *
     * 清单页组树用：顶层任务（`parentTaskId IS NULL`）+ 它们的子任务（`parentTaskId = 父 id`）
     * 拼成一棵树，所以得一次把父子都拿全 —— 分两次查是 N+1。
     *
     * ⚠️ 与 [observeAll] 的区别：那个只要顶层（`parentTaskId IS NULL`），这个要全部。
     */
    @Query("SELECT * FROM tasks ORDER BY manualOrder, createdAt")
    fun observeAllIncludingChildren(): Flow<List<TaskEntity>>

    /**
     * Phase 6b：观察某个任务下的子清单（parentTaskId = taskId）实时刷新。
     */
    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY manualOrder, createdAt")
    fun observeSubTasks(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY manualOrder, createdAt")
    suspend fun getSubTasks(parentId: Long): List<TaskEntity>

    /**
     * Phase 6b：返回任务下当前最大 manualOrder（null = 无子项）。
     *
     * 用于新增子项时自动追加到末尾，避免显示错乱。
     */
    @Query("SELECT MAX(manualOrder) FROM tasks WHERE parentTaskId = :parentId")
    suspend fun getMaxSubTaskOrder(parentId: Long): Long?

    @Query("DELETE FROM tasks WHERE parentTaskId = :parentId")
    suspend fun deleteSubTasksByParent(parentId: Long)

    /**
     * Phase 16：顶层任务的当前最大 manualOrder（null = 一个都没有）。
     *
     * 用于新增任务时**追加到末尾**（max + 1）。
     */
    @Query("SELECT MAX(manualOrder) FROM tasks WHERE parentTaskId IS NULL")
    suspend fun getMaxTopLevelOrder(): Long?

    /** Phase 15：按分类筛顶层任务。 */
    @Query(
        "SELECT * FROM tasks WHERE parentTaskId IS NULL AND category = :category " +
            "ORDER BY manualOrder, createdAt",
    )
    fun observeTopLevelByCategory(category: String): Flow<List<TaskEntity>>

    /** Phase 15：按优先级筛顶层任务（priority 存 TaskPriority.dbValue）。 */
    @Query(
        "SELECT * FROM tasks WHERE parentTaskId IS NULL AND priority = :priority " +
            "ORDER BY manualOrder, createdAt",
    )
    fun observeTopLevelByPriority(priority: Int): Flow<List<TaskEntity>>

    // ===== Phase 8 Sub-step 4：备份导出快照 =====

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    // ==================== 备份导入批量操作 ====================
    //
    // 导入备份时整表清空 + 整表灌入，原来走的是「读回全表 → 逐行删 / 逐行插」，
    // N 行就是 N 次 IO 往返。这里的批量接口把它压成 1 次。
    //
    // ⚠️ 冲突策略必须与对应的单条方法**逐字一致**（REPLACE ↔ REPLACE）。

    /** 批量插入任务（REPLACE，与 [insert] 一致）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    /** 清空任务表（备份导入用）。 */
    @Query("DELETE FROM tasks")
    suspend fun clearTasks(): Int

    // ===== 统计查询 =====

    /** 今日完成率：[0.0, 1.0] */
    @Query(
        """
        SELECT
          CAST(SUM(CASE WHEN isCompleted = 1 THEN 1 ELSE 0 END) AS REAL) / COUNT(*)
        FROM tasks
        WHERE parentTaskId IS NULL AND createdAt >= :todayStart
        """
    )
    suspend fun getTodayCompletionRate(todayStart: Long): Double?

    @Query("SELECT COUNT(*) FROM tasks WHERE parentTaskId IS NULL AND isCompleted = 0")
    fun observeActiveCount(): Flow<Int>

    /**
     * 删除任务。子任务由 FK SET NULL 自动提升为顶层（不删除子项内容）。
     */
    @Transaction
    suspend fun deleteTaskWithChildren(task: TaskEntity) {
        delete(task)
    }
}
