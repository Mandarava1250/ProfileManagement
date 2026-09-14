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

    /** 观察独立清单项（parentTaskId IS NULL） */
    @Query("SELECT * FROM sub_tasks WHERE parentTaskId IS NULL ORDER BY `order`, createdAt")
    fun observeStandaloneSubTasks(): Flow<List<SubTaskEntity>>

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

    // ===== Phase 8 Sub-step 4：备份导出快照 =====

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM sub_tasks")
    suspend fun getAllSubTasks(): List<SubTaskEntity>

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
