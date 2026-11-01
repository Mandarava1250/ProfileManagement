package com.example.personalassistant.feature.calendar.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Phase 8 Sub-step 9：event_categories 表 DAO
 *
 * 设计要点：
 * - 默认用 OnConflictStrategy.IGNORE（migration seed 多次执行也安全）
 * - 排序统一按 sortOrder ASC, id ASC（保证插入顺序兜底）
 * - 不在 DAO 里做业务校验（name 非空等由 Domain 强制）
 */
@Dao
interface EventCategoryDao {

    /** 全部观察（Flow）— 分类管理 Sheet 用 */
    @Query("SELECT * FROM event_categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<EventCategoryEntity>>

    /** 全部观察（一次性）— 备份导出 / 同步 */
    @Query("SELECT * FROM event_categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<EventCategoryEntity>

    @Query("SELECT * FROM event_categories WHERE id = :id")
    suspend fun getById(id: Long): EventCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: EventCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<EventCategoryEntity>)

    @Update
    suspend fun update(category: EventCategoryEntity)

    @Delete
    suspend fun delete(category: EventCategoryEntity)

    @Query("DELETE FROM event_categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 按 name 查重（用于「重命名时判冲突」） */
    @Query("SELECT * FROM event_categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): EventCategoryEntity?

    /** 备份用：清空全部（仅在事务中调用） */
    @Query("DELETE FROM event_categories")
    suspend fun clearAll()

    /**
     * 当分类被删除/重命名时，把事件的 categoryId 设为 null。
     * （旧字段 `category: String` 不在 Sub-step 9 维护；重命名不同步到 events.category）
     */
    @Query("UPDATE events SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachEvents(categoryId: Long)
}
