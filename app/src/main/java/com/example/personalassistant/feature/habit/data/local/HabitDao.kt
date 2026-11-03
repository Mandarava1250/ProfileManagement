package com.example.personalassistant.feature.habit.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Phase 23：习惯 DAO
 */
@Dao
interface HabitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitEntity?>

    /** 未归档的习惯（「今日」列表用） */
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    /** 全部习惯（含归档） */
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    /** 已归档的习惯 */
    @Query("SELECT * FROM habits WHERE isArchived = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeArchived(): Flow<List<HabitEntity>>

    /** 当前最大 sortOrder（null = 一个都没有） */
    @Query("SELECT MAX(sortOrder) FROM habits")
    suspend fun getMaxSortOrder(): Long?

    @Query("UPDATE habits SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}
