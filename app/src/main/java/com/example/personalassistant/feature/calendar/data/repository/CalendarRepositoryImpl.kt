package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.calendar.data.mapper.toDomain
import com.example.personalassistant.feature.calendar.data.mapper.toEntity
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7c：CalendarRepository 的 Room 实现
 *
 * 唯一职责：把 EventDao 的 Entity 流映射成 Event domain 模型。
 * 与 Task / Course 的联动由 ObserveDayDetailUseCase 在联动层组合。
 *
 * @Inject + @Singleton 让 Hilt 识别为单例，
 * 由 [com.example.personalassistant.feature.calendar.di.CalendarModule] 的 @Binds 绑定到接口。
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
) : CalendarRepository {

    override fun observeEventsByDate(date: Long): Flow<List<Event>> =
        eventDao.observeByDate(date).map { list -> list.map { it.toDomain() } }

    override fun observeEventsByMonthRange(monthStart: Long, monthEnd: Long): Flow<List<Event>> =
        eventDao.observeMonthRange(monthStart, monthEnd).map { list -> list.map { it.toDomain() } }

    override fun observeUpcoming(today: Long, futureEnd: Long): Flow<List<Event>> =
        eventDao.observeUpcoming(today, futureEnd).map { list -> list.map { it.toDomain() } }

    override fun observeEventById(id: Long): Flow<Event?> =
        eventDao.observeById(id).map { it?.toDomain() }

    override suspend fun getEventById(id: Long): Event? =
        eventDao.getById(id)?.toDomain()

    override suspend fun addEvent(event: Event): Long =
        eventDao.insert(event.toEntity())

    override suspend fun updateEvent(event: Event) {
        // 只更新已存在事件；id=0 视为无效
        if (event.id <= 0L) return
        eventDao.update(event.toEntity())
    }

    override suspend fun deleteEvent(id: Long) {
        eventDao.deleteById(id)
    }

    override suspend fun deleteEventsBefore(date: Long) {
        eventDao.deleteBefore(date)
    }
}