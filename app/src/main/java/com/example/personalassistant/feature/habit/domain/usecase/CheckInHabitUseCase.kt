package com.example.personalassistant.feature.habit.domain.usecase

import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import com.example.personalassistant.feature.habit.domain.util.HabitDates
import javax.inject.Inject

/**
 * Phase 23：打卡 / 撤销打卡。
 *
 * - [date] 默认今天；传过去的日期就是**补卡**
 * - [delta] 默认 +1（点一下加一次）；传 -1 是撤销一次
 *
 * @return 变更后的当天次数
 */
class CheckInHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(
        habitId: Long,
        date: Long = HabitDates.todayStartMillis(),
        delta: Int = 1,
    ): Int {
        require(habitId > 0L) { "习惯 id 无效" }
        require(delta != 0) { "打卡增量不能为 0" }
        return repository.checkIn(habitId, date, delta)
    }
}
