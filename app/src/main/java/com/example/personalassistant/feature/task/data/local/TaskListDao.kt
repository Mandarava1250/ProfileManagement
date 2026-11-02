package com.example.personalassistant.feature.task.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Phase 16：清单 DAO
 *
 * - insert：插入新清单；`OnConflictStrategy.IGNORE` 让重名（同一 normalized name）静默成功
 *   （上层 Repository 拿到 -1 rowId 时再按 normalized name 查找已存在的那条 —— 与 [TagDao] 同款）
 * - observeAll：按 sortOrder 升序观察全部清单（含归档）
 * - observeActive：只观察未归档的清单（侧边栏用）
 * - observeWithPendingCount：清单 + 未完成任务数（侧边栏徽标用）
 * - deleteById：删除清单（FK CASCADE 会把它的任务一起带走 —— 上层要在删之前问用户）
 *
 * ⚠️ 内置收集箱（[TaskListEntity.INBOX_ID]）不可删除，删除前必须在 Repository 层拦。
 */
@Dao
interface TaskListDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(taskList: TaskListEntity): Long

    @Update
    suspend fun update(taskList: TaskListEntity)

    @Query("DELETE FROM task_lists WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 按 id 取单个清单 */
    @Query("SELECT * FROM task_lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskListEntity?

    /** 按规范化名取清单（大小写不敏感去重用） */
    @Query("SELECT * FROM task_lists WHERE nameNormalized = :nameNormalized LIMIT 1")
    suspend fun findByNormalizedName(nameNormalized: String): TaskListEntity?

    /** 取内置收集箱 */
    @Query("SELECT * FROM task_lists WHERE isSystem = 1 LIMIT 1")
    suspend fun getInbox(): TaskListEntity?

    /** 全部清单（含归档），按 sortOrder 升序 */
    @Query("SELECT * FROM task_lists ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<TaskListEntity>>

    /** 未归档清单（侧边栏显示） */
    @Query("SELECT * FROM task_lists WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActive(): Flow<List<TaskListEntity>>

    /**
     * 清单 + 未完成任务数（侧边栏徽标）
     *
     * LEFT JOIN + `t.isCompleted = 0` 写在 ON 上而不是 WHERE 上：
     * 写成 WHERE 会把「0 个未完成任务」的清单整行滤掉，徽标就消失了。
     */
    @Query(
        """
        SELECT l.id AS id, l.nameNormalized AS nameNormalized,
               l.displayName AS displayName, l.colorHex AS colorHex,
               l.iconKey AS iconKey, l.sortOrder AS sortOrder,
               l.isSystem AS isSystem, l.isArchived AS isArchived,
               l.createdAt AS createdAt,
               COUNT(t.id) AS pendingCount
        FROM task_lists l
        LEFT JOIN tasks t ON t.listId = l.id AND t.isCompleted = 0
        GROUP BY l.id
        ORDER BY l.sortOrder ASC, l.createdAt ASC
        """
    )
    fun observeWithPendingCount(): Flow<List<TaskListWithCountRow>>

    /** 归档 / 取消归档 */
    @Query("UPDATE task_lists SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /** 改排序权重 */
    @Query("UPDATE task_lists SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** 当前最大排序权重（新建清单时排到末尾用） */
    @Query("SELECT MAX(sortOrder) FROM task_lists")
    suspend fun getMaxSortOrder(): Int?

    // ===== 备份导出 / 导入 =====

    @Query("SELECT * FROM task_lists")
    suspend fun getAllTaskLists(): List<TaskListEntity>

    /**
     * 批量插入清单。
     *
     * ⚠️ 冲突策略必须与 [insert] **逐字一致**（IGNORE ↔ IGNORE）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskLists(taskLists: List<TaskListEntity>): List<Long>

    /** 清空清单表（备份导入用） */
    @Query("DELETE FROM task_lists")
    suspend fun clearTaskLists(): Int
}

/**
 * 带未完成数的清单（侧边栏投影用）
 *
 * 计数由 JOIN + GROUP BY 算出，0 表示该清单下没有未完成任务。
 *
 * ⚠️ 名字带 `Row` 后缀：domain 层也有一个 `TaskListWithCount`（带 `list: TaskList`），
 * 同名会让 mapper 的 import 冲突、类型推断崩掉。这里是**扁平行**（直接摊平字段），
 * domain 那个是**嵌套行**（list + pendingCount），故用 Row 区分。
 */
data class TaskListWithCountRow(
    val id: Long,
    val nameNormalized: String,
    val displayName: String,
    val colorHex: Long,
    val iconKey: String,
    val sortOrder: Int,
    val isSystem: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val pendingCount: Int,
)
