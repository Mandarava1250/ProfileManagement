package com.example.personalassistant.feature.habit.data.repository

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.habit.data.local.HabitCheckInDao
import com.example.personalassistant.feature.habit.data.local.HabitDao
import com.example.personalassistant.feature.habit.data.mapper.toDomain
import com.example.personalassistant.feature.habit.data.mapper.toEntity
import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitCheckIn
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 23：习惯仓库实现
 *
 * - 所有 IO 通过 [dispatcher]（IoDispatcher）执行
 * - 打卡次数的读-改-写在 DAO 的 @Transaction 里完成（[HabitCheckInDao.incrementOrCreate]），
 *   这样并发点击不会丢次数
 */
@Singleton
class HabitRepositoryImpl @Inject constructor(
    private val habitDao: HabitDao,
    private val checkInDao: HabitCheckInDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : HabitRepository {

    override fun observeHabits(includeArchived: Boolean): Flow<List<Habit>> {
        val source = if (includeArchived) habitDao.observeAll() else habitDao.observeActive()
        return source.map { list -> list.map { it.toDomain() } }
    }

    override fun observeHabitById(id: Long): Flow<Habit?> =
        habitDao.observeById(id).map { it?.toDomain() }

    override suspend fun getHabit(id: Long): Habit? = withContext(dispatcher) {
        habitDao.getById(id)?.toDomain()
    }

    override suspend fun upsertHabit(habit: Habit): Long = withContext(dispatcher) {
        if (habit.id == 0L) {
            // 新建：sortOrder 追加到末尾（不追加的话新习惯会全挤在 sortOrder = 0 的老习惯前面）
            val nextOrder = (habitDao.getMaxSortOrder() ?: -1L) + 1L
            habitDao.insert(habit.copy(sortOrder = nextOrder).toEntity())
        } else {
            habitDao.update(habit.toEntity())
            habit.id
        }
    }

    override suspend fun deleteHabit(habit: Habit) = withContext(dispatcher) {
        habitDao.delete(habit.toEntity())
        // 该习惯的打卡记录由 FK CASCADE 自动清掉
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = withContext(dispatcher) {
        habitDao.setArchived(id, archived)
    }

    override suspend fun checkIn(habitId: Long, date: Long, delta: Int): Int = withContext(dispatcher) {
        checkInDao.incrementOrCreate(habitId, date, delta, System.currentTimeMillis())
    }

    override fun observeCheckIns(
        habitId: Long,
        start: Long,
        end: Long,
    ): Flow<List<HabitCheckIn>> =
        checkInDao.observeRange(habitId, start, end).map { list -> list.map { it.toDomain() } }

    override fun observeCheckInsOn(date: Long): Flow<List<HabitCheckIn>> =
        checkInDao.observeByDate(date).map { list -> list.map { it.toDomain() } }

    override fun observeCheckInsInRange(start: Long, end: Long): Flow<List<HabitCheckIn>> =
        checkInDao.observeRangeAll(start, end).map { list -> list.map { it.toDomain() } }
}
