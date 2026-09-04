package com.example.personalassistant.feature.timetable.data.repository

import androidx.room.withTransaction
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.mapper.toDomain
import com.example.personalassistant.feature.timetable.data.mapper.toEntity
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 课表仓储实现
 *
 *  - CourseDao.findConflicts 接受非空 semester 与 excludeId
 *  - 切换学期必须事务：清空 isCurrent + 置目标
 *  - upsertCourse 复用 CourseDao.insert（OnConflictStrategy.REPLACE）天然支持新增/更新
 *  - semester 为 null 时，跨学期按 dayOfWeek + 时间区间查冲突（取一次 Flow 首值）
 */
@Singleton
class TimetableRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val courseDao: CourseDao,
    private val semesterDao: SemesterDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TimetableRepository {

    override fun observeCoursesBySemester(semester: String): Flow<List<Course>> =
        courseDao.observeBySemester(semester).map { list -> list.map { it.toDomain() } }

    override fun observeCoursesByDay(semester: String, dayOfWeek: Int): Flow<List<Course>> =
        courseDao.observeBySemesterAndDay(semester, dayOfWeek).map { list -> list.map { it.toDomain() } }

    override suspend fun getCourseById(id: Long): Course? = withContext(ioDispatcher) {
        courseDao.getById(id)?.toDomain()
    }

    override suspend fun findConflicts(
        semester: String?,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
        excludeId: Long,
    ): List<Course> = withContext(ioDispatcher) {
        if (semester == null) {
            // 跨学期：取所有同 dayOfWeek 的课程，再按时间区间手工过滤
            courseDao.observeByDayOfWeek(dayOfWeek).first()
                .filter { it.startTime < endTime && it.endTime > startTime && it.id != excludeId }
        } else {
            courseDao.findConflicts(
                semester = semester,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                excludeId = excludeId,
            )
        }.map { it.toDomain() }
    }

    override suspend fun upsertCourse(course: Course): Long = withContext(ioDispatcher) {
        courseDao.insert(course.toEntity())
    }

    override suspend fun deleteCourse(id: Long) = withContext(ioDispatcher) {
        courseDao.deleteById(id)
    }

    override suspend fun switchSemester(name: String) = withContext(ioDispatcher) {
        appDatabase.withTransaction {
            semesterDao.clearCurrent()
            semesterDao.setCurrent(name)
        }
    }

    override fun observeAllSemesters(): Flow<List<Semester>> =
        semesterDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCurrentSemester(): Flow<Semester?> =
        semesterDao.observeCurrent().map { it?.toDomain() }

    override suspend fun getSemesterByName(name: String): Semester? = withContext(ioDispatcher) {
        semesterDao.getByName(name)?.toDomain()
    }

    override suspend fun upsertSemester(semester: Semester) = withContext(ioDispatcher) {
        semesterDao.insert(semester.toEntity())
    }

    override suspend fun deleteSemester(name: String) = withContext(ioDispatcher) {
        semesterDao.getByName(name)?.let { entity ->
            semesterDao.delete(entity)
        }
        Unit
    }
}