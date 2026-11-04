package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitCheckIn
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Phase 24B：单元测试用的内存版 [HabitRepository]
 *
 * 日历侧要读习惯（渲染打卡 / 补卡），所以给它一个假实现。
 *
 * 打卡语义与真实实现一致：**一天一条 + count 累加**，减到 0 直接删行。
 */
class FakeHabitRepository : HabitRepository {

    private val habitsState = MutableStateFlow<List<Habit>>(emptyList())
    private val checkInsState = MutableStateFlow<List<HabitCheckIn>>(emptyList())

    /** 直接塞一批习惯 */
    fun seedHabits(habits: List<Habit>) {
        habitsState.value = habits
    }

    /** 直接塞一批打卡记录 */
    fun seedCheckIns(checkIns: List<HabitCheckIn>) {
        checkInsState.value = checkIns
    }

    override fun observeHabits(includeArchived: Boolean): Flow<List<Habit>> =
        habitsState.map { list ->
            if (includeArchived) list else list.filter { !it.isArchived }
        }

    override fun observeHabitById(id: Long): Flow<Habit?> =
        habitsState.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getHabit(id: Long): Habit? =
        habitsState.value.firstOrNull { it.id == id }

    override suspend fun upsertHabit(habit: Habit): Long = 0L

    override suspend fun deleteHabit(habit: Habit) = Unit

    override suspend fun setArchived(id: Long, archived: Boolean) = Unit

    override suspend fun checkIn(habitId: Long, date: Long, delta: Int): Int {
        val existing = checkInsState.value.firstOrNull { it.habitId == habitId && it.date == date }
        val newCount = ((existing?.count ?: 0) + delta).coerceAtLeast(0)
        val kept = checkInsState.value.filterNot { it.habitId == habitId && it.date == date }
        checkInsState.value = if (newCount > 0) {
            kept + HabitCheckIn(
                id = 0L,
                habitId = habitId,
                date = date,
                count = newCount,
            )
        } else {
            kept
        }
        return newCount
    }

    override fun observeCheckIns(habitId: Long, start: Long, end: Long): Flow<List<HabitCheckIn>> =
        checkInsState.map { list -> list.filter { it.habitId == habitId && it.date in start..end } }

    override fun observeCheckInsOn(date: Long): Flow<List<HabitCheckIn>> =
        checkInsState.map { list -> list.filter { it.date == date } }

    override fun observeCheckInsInRange(start: Long, end: Long): Flow<List<HabitCheckIn>> =
        checkInsState.map { list -> list.filter { it.date in start..end } }
}
