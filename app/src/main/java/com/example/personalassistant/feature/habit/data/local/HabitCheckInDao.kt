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

    /**
     * 闭区间 [start, end] 内**所有习惯**的打卡记录
     *
     * Phase 24B：日历月视图的角标要的是「这个月哪天有习惯打卡」，
     * 它横跨了好几个习惯 —— 单习惯的 [observeRange] 和单日的 [observeByDate] 都答不了，
     * 所以这里补一条跨习惯的区间查询。
     */
    @Query(
        "SELECT * FROM habit_checkins WHERE `date` BETWEEN :start AND :end ORDER BY `date` ASC",
    )
    fun observeRangeAll(start: Long, end: Long): Flow<List<HabitCheckInEntity>>

    @Query("DELETE FROM habit_checkins WHERE habitId = :habitId AND `date` = :date")
    suspend fun deleteByHabitAndDate(habitId: Long, date: Long)

    // ===== 备份导出 / 导入（Phase 23 接入备份） =====

    /**
     * 全部打卡记录 —— 备份导出用。
     *
     * 按 id 升序，保证同一份库导出的备份字节稳定（便于校验和比对）。
     */
    @Query("SELECT * FROM habit_checkins ORDER BY id ASC")
    suspend fun getAll(): List<HabitCheckInEntity>

    /**
     * 批量插入打卡记录（备份导入用）。
     *
     * ⚠️ 冲突策略必须与 [insert] **逐字一致**（IGNORE ↔ IGNORE）。
     *
     * 这里是 IGNORE 而不是 REPLACE：`habit_checkins` 上有 `(habitId, date)` 唯一索引，
     * IGNORE 让「合并模式」的 ADD 策略保持保守（同一天重复打卡不覆盖已有行），
     * 与单条 [insert] 语义完全对齐。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(checkIns: List<HabitCheckInEntity>): List<Long>

    /** 清空打卡记录表（备份导入用）。 */
    @Query("DELETE FROM habit_checkins")
    suspend fun clearAll(): Int

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
