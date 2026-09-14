package com.example.personalassistant.feature.calendar.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeById(id: Long): Flow<EventEntity?>

    /** 单日事件 */
    @Query("SELECT * FROM events WHERE date = :date ORDER BY isAllDay DESC, startTime ASC")
    fun observeByDate(date: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE date = :date ORDER BY isAllDay DESC, startTime ASC")
    suspend fun getByDate(date: Long): List<EventEntity>

    /** 月视图：查询 [monthStart, monthEnd) 范围内的事件 */
    @Query("SELECT * FROM events WHERE date >= :monthStart AND date < :monthEnd ORDER BY date ASC, startTime ASC")
    fun observeMonthRange(monthStart: Long, monthEnd: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE date >= :monthStart AND date < :monthEnd ORDER BY date ASC, startTime ASC")
    suspend fun getMonthRange(monthStart: Long, monthEnd: Long): List<EventEntity>

    /** 未来 N 天事件（首页"接下来"列表用）*/
    @Query("SELECT * FROM events WHERE date >= :today AND date < :futureEnd ORDER BY date ASC, startTime ASC")
    fun observeUpcoming(today: Long, futureEnd: Long): Flow<List<EventEntity>>

    @Query("DELETE FROM events WHERE date < :date")
    suspend fun deleteBefore(date: Long)

    /**
     * Phase 8 Sub-step 3：全局搜索 — 在 events 表 title / note / category 任一字段 LIKE 匹配。
     *
     * - :keyword 为已包好 `%...%` 的字符串
     * - 大小写敏感
     * - 排序：date ASC, startTime ASC
     */
    @Query(
        """
        SELECT * FROM events
        WHERE title LIKE :keyword
           OR note LIKE :keyword
           OR category LIKE :keyword
        ORDER BY date ASC, startTime ASC
        """
    )
    fun searchEvents(keyword: String): Flow<List<EventEntity>>

    // ===== Phase 8 Sub-step 4：备份导出快照 =====

    @Query("SELECT * FROM events ORDER BY date ASC, startTime ASC")
    suspend fun getAllEvents(): List<EventEntity>

    // ===== Phase 8 Sub-step 9：按分类查询 =====

    /** 按 categoryId 观察事件列表 */
    @Query("SELECT * FROM events WHERE categoryId = :categoryId ORDER BY date ASC, startTime ASC")
    fun observeByCategory(categoryId: Long): Flow<List<EventEntity>>

    /** 按 categoryId 一次性查询 */
    @Query("SELECT * FROM events WHERE categoryId = :categoryId ORDER BY date ASC, startTime ASC")
    suspend fun getByCategory(categoryId: Long): List<EventEntity>
}
