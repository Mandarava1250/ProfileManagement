package com.example.personalassistant.feature.habit.domain.repository

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitCheckIn
import kotlinx.coroutines.flow.Flow

/**
 * Phase 23：习惯领域仓库接口
 *
 * 由 Data 层的 [com.example.personalassistant.feature.habit.data.repository.HabitRepositoryImpl] 实现。
 */
interface HabitRepository {

    /**
     * 观察习惯列表。
     *
     * @param includeArchived true = 含归档；false = 只看未归档（「今日」列表）
     */
    fun observeHabits(includeArchived: Boolean = false): Flow<List<Habit>>

    /** 观察单个习惯（id 不存在时 emit null） */
    fun observeHabitById(id: Long): Flow<Habit?>

    /** 取单个习惯；不存在时返回 null */
    suspend fun getHabit(id: Long): Habit?

    /**
     * 新增或更新习惯。
     *
     * - id = 0L 视为新增（sortOrder 自动追加到末尾），返回新 rowId
     * - id > 0L 视为更新，返回原 id
     */
    suspend fun upsertHabit(habit: Habit): Long

    /** 删除习惯（其打卡记录由 FK CASCADE 自动清掉） */
    suspend fun deleteHabit(habit: Habit)

    /** 归档 / 取消归档（历史打卡保留） */
    suspend fun setArchived(id: Long, archived: Boolean)

    /**
     * 打卡 / 撤销打卡。
     *
     * @param date 当天 00:00 的毫秒时间戳；传过去的日期就是**补卡**
     * @param delta 增量（+1 打卡 / -1 撤销）
     * @return 变更后的当天次数
     */
    suspend fun checkIn(habitId: Long, date: Long, delta: Int = 1): Int

    /** 观察某习惯在闭区间 [start, end] 内的打卡记录 */
    fun observeCheckIns(habitId: Long, start: Long, end: Long): Flow<List<HabitCheckIn>>

    /** 观察某一天**所有习惯**的打卡记录（「今日」列表用） */
    fun observeCheckInsOn(date: Long): Flow<List<HabitCheckIn>>
}
