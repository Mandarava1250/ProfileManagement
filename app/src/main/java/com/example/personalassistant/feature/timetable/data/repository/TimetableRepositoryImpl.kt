package com.example.personalassistant.feature.timetable.data.repository

import androidx.room.withTransaction
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseSessionDao
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.mapper.toDomain
import com.example.personalassistant.feature.timetable.data.mapper.toDomainWithSessions
import com.example.personalassistant.feature.timetable.data.mapper.toEntity
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 *
 * ## Phase 28：课时（sessions）
 *
 * 「一门课多课时」的数据落在两张表上（`courses` + `course_sessions`），
 * 所以读路径要做**合并**、写路径要做**事务**：
 * - 读：合并时用 [toDomainWithSessions]，它在「没有课时记录」时会用主时段镜像兜底出单课时
 * - 写：[upsertCourseWithSessions] 在一个事务里完成「upsert 课程 + 换掉课时 + 同步三列镜像」
 */
@Singleton
class TimetableRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val courseDao: CourseDao,
    private val courseSessionDao: CourseSessionDao,
    private val semesterDao: SemesterDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TimetableRepository {

    override fun observeCoursesBySemester(semester: String): Flow<List<Course>> =
        courseDao.observeBySemester(semester).map { list -> list.map { it.toDomain() } }

    override fun observeAllCourses(): Flow<List<Course>> =
        courseDao.observeAll().map { list -> list.map { it.toDomain() } }

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

    // ===== Phase 28：一门课多课时 =====

    /**
     * 观察课程（含全部课时）
     *
     * ## 为什么用 combine(课程流, 全量课时流) 而不是 flatMapLatest(按 id 查课时)
     *
     * 课表里的课程数量是几十级别，一次把课时全量拿回来在内存里按 courseId 分组，
     * 比「先查课程 → 再按 id 查课时 → 再合并」少一次往返，
     * 而且 flow 链只有两层（不会越串越深）。
     *
     * @param semester null = 全部学期
     */
    override fun observeCoursesWithSessions(semester: String?): Flow<List<Course>> {
        val courseFlow = if (semester == null) {
            courseDao.observeAll()
        } else {
            courseDao.observeBySemester(semester)
        }
        return combine(courseFlow, courseSessionDao.observeAll()) { courseEntities, sessionEntities ->
            val sessionsByCourse = sessionEntities.groupBy { it.courseId }
            courseEntities.map { entity ->
                entity.toDomainWithSessions(sessionsByCourse[entity.id].orEmpty())
            }
        }
    }

    override suspend fun getCourseWithSessions(courseId: Long): Course? = withContext(ioDispatcher) {
        val entity = courseDao.getById(courseId) ?: return@withContext null
        entity.toDomainWithSessions(courseSessionDao.getByCourseId(courseId))
    }

    /**
     * 整门课一次性保存（事务）
     *
     * 事务内：upsert 课程 → 删旧课时 → 插新课时 → 同步三列镜像。
     * 中间态对外不可见（要么全成功，要么全回滚）。
     */
    override suspend fun upsertCourseWithSessions(course: Course): Long = withContext(ioDispatcher) {
        appDatabase.withTransaction {
            // 按 sortOrder 排序后**重新编号**（0,1,2…）：
            // - 保证 `(courseId, sortOrder)` 唯一索引不炸（调用方传重复 sortOrder 也不会崩）
            // - 保留调用方的相对顺序（sortOrder 的本意就是「课程内的显示顺序」）
            val sessions = course.sessions.sortedBy { it.sortOrder }
            val first = sessions.firstOrNull()

            // 三列镜像 = 第一个课时；没有课时时保持课程自身的值（= 语义上「只有主时段」）
            val entity = course.toEntity().copy(
                dayOfWeek = first?.dayOfWeek ?: course.dayOfWeek,
                startTime = first?.startTime ?: course.startTime,
                endTime = first?.endTime ?: course.endTime,
            )
            val courseId = courseDao.insert(entity)

            courseSessionDao.deleteByCourseId(courseId)
            if (sessions.isNotEmpty()) {
                courseSessionDao.insertAll(
                    sessions.mapIndexed { index, session ->
                        session.toEntity().copy(id = 0L, courseId = courseId, sortOrder = index)
                    },
                )
            }
            courseId
        }
    }

    /**
     * 找出与给定课时冲突的其它课程
     *
     * ## ⚠️ semester == null 的语义
     *
     * `semester == null` 表示「这节课没归学期」→ **撞了任何学期的课都要报**。
     * 所以这里走 `courseDao.getAllCourses()`（全表）而不是
     * `WHERE semester = :semester` —— 后者在 semester 为 null 时匹配不到任何行，
     * 冲突检测会**静默失效**（这正是既有 `findConflicts` 用「取一次 Flow 首值 + 内存过滤」
     * 而不是 SQL 的原因，见上方同类实现）。
     *
     * 候选课程没有课时记录时 → 用主时段镜像兜底出一个单课时再比。
     */
    override suspend fun findSessionConflicts(
        semester: String?,
        session: CourseSession,
        excludeCourseId: Long,
    ): List<Course> = withContext(ioDispatcher) {
        val candidates = if (semester == null) {
            courseDao.getAllCourses()
        } else {
            courseDao.getBySemester(semester)
        }.filter { it.id != excludeCourseId }

        val sessionsByCourse = courseSessionDao.getAll().groupBy { it.courseId }
        candidates.mapNotNull { entity ->
            val theirSessions = sessionsByCourse[entity.id].orEmpty()
            val hit = theirSessions.any { it.toDomain().overlaps(session) }
            if (hit) entity.toDomainWithSessions(theirSessions) else null
        }
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

    override suspend fun upsertSemester(semester: Semester): Unit = withContext(ioDispatcher) {
        semesterDao.insert(semester.toEntity())
    }

    override suspend fun renameSemesterReferences(oldName: String, newName: String): Unit = withContext(ioDispatcher) {
        if (oldName == newName) return@withContext
        courseDao.renameSemesterReferences(oldName = oldName, newName = newName)
    }

    override suspend fun deleteSemester(name: String) = withContext(ioDispatcher) {
        semesterDao.getByName(name)?.let { entity ->
            semesterDao.delete(entity)
        }
        Unit
    }

    // ===== Phase 8 Sub-step 3 全局搜索 =====

    override fun searchCourses(keyword: String): Flow<List<Course>> =
        courseDao.searchCourses(keyword).map { list -> list.map { it.toDomain() } }
}