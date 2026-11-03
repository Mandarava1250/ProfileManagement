package com.example.personalassistant.feature.habit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Phase 23：打卡记录 DAO
 *
 * ⚠️ `date` / `count` 在 SQL 里是函数名，所有引用都用反引号包起来 ——
 * 写成裸 `date = :date` 某些 SQLite 版本会当成 `date()` 函数解析。
 */
@Dao
interface HabitCheckInDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(checkIn: HabitCheckInEntity): Long

    @Update
    suspend fun update(checkIn: HabitCheckInEntity)

    @Query("SELECT * FROM habit_checkins WHERE habitId = :habitId AND `date` = :date LIMIT 1")
    suspend fun getByHabitAndDate(habitId: Long, date: Long): HabitCheckInEntity?

    /** 某习惯在闭区间 [start, end] 内的打卡记录 */
    @Query(
        "SELECT * FROM habit_checkins WHERE habitId = :habitId " +
            "AND `date` BETWEEN :start AND :end ORDER BY `date` ASC",
    )
    fun observeRange(habitId: Long, start: Long, end: Long): Flow<List<HabitCheckInEntity>>

    /** 某习惯在闭区间 [start, end] 内的打卡记录（一次性） */
    @Query(
        "SELECT * FROM habit_checkins WHERE habitId = :habitId " +
            "AND `date` BETWEEN :start AND :end ORDER BY `date` ASC",
    )
    suspend fun getRange(habitId: Long, start: Long, end: Long): List<HabitCheckInEntity>

    /** 某一天所有习惯的打卡记录（「今日」列表用） */
    @Query("SELECT * FROM habit_checkins WHERE `date` = :date")
    fun observeByDate(date: Long): Flow<List<HabitCheckInEntity>>

    @Query("DELETE FROM habit_checkins WHERE habitId = :habitId AND `date` = :date")
    suspend fun deleteByHabitAndDate(habitId: Long, date: Long)

    /**
     * 打卡 / 撤销打卡：读-改-写必须在一个事务里。
     *
     * 拆成「先查再插/改」两步的话，两个并发点击会都读到旧值，
     * 后写的把前一个覆盖掉 —— 次数就少了一次。
     *
     * 减到 0 时**直接删行**：留一条 count = 0 的记录只会让
     * 「今天打过卡吗」的判断变复杂（月度表里会多出一个 0）。
     *
     * @return 变更后的当天次数
     */
    @Transaction
    suspend fun incrementOrCreate(
        habitId: Long,
        date: Long,
        delta: Int,
        now: Long,
    ): Int {
        val existing = getByHabitAndDate(habitId, date)
        val newCount = ((existing?.count ?: 0) + delta).coerceAtLeast(0)
        if (newCount == 0) {
            if (existing != null) {
                deleteByHabitAndDate(habitId, date)
            }
            return 0
        }
        if (existing == null) {
            insert(
                HabitCheckInEntity(
                    habitId = habitId,
                    date = date,
                    count = newCount,
                    createdAt = now,
                ),
            )
        } else {
            update(existing.copy(count = newCount))
        }
        return newCount
    }
}
