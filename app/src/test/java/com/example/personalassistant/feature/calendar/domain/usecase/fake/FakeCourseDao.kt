package com.example.personalassistant.feature.calendar.domain.usecase.fake

import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Phase 8 Sub-step 9 Step 4：测试用 FakeCourseDao
 *
 * 只 stub 了 [findConflicts]，其他方法返回占位 / 抛错。
 *
 * 设计取舍：手写 fake 而不是引入 mockk（项目目前未依赖），
 * fake 接收 [CourseEntity] 列表作为种子数据，findConflicts 按 DAO 的语义过滤。
 */
class FakeCourseDao : CourseDao {
    private val store = mutableMapOf<Long, CourseEntity>()

    fun seed(vararg entities: CourseEntity) {
        entities.forEach { store[it.id] = it }
    }

    fun seed(entities: List<CourseEntity>) {
        entities.forEach { store[it.id] = it }
    }

    override suspend fun findConflicts(
        semester: String,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
        excludeId: Long,
    ): List<CourseEntity> = store.values.filter { c ->
        c.semester == semester &&
            c.dayOfWeek == dayOfWeek &&
            c.id != excludeId &&
            c.startTime < endTime &&
            c.endTime > startTime
    }

    // ===== 占位实现（未在 UseCase 测试中使用） =====

    override suspend fun insert(course: CourseEntity): Long {
        store[course.id] = course
        return course.id
    }

    override suspend fun insertAll(courses: List<CourseEntity>) {
        courses.forEach { store[it.id] = it }
    }

    override suspend fun update(course: CourseEntity) {
        store[course.id] = course
    }

    override suspend fun delete(course: CourseEntity) {
        store.remove(course.id)
    }

    override suspend fun deleteById(id: Long) {
        store.remove(id)
    }

    override suspend fun getById(id: Long): CourseEntity? = store[id]

    override fun observeBySemester(semester: String): Flow<List<CourseEntity>> =
        flowOf(store.values.filter { it.semester == semester })

    override fun observeBySemesterAndDay(semester: String, dayOfWeek: Int): Flow<List<CourseEntity>> =
        flowOf(store.values.filter { it.semester == semester && it.dayOfWeek == dayOfWeek })

    override suspend fun getBySemester(semester: String): List<CourseEntity> =
        store.values.filter { it.semester == semester }

    override fun observeByDayOfWeek(dayOfWeek: Int): Flow<List<CourseEntity>> =
        flowOf(store.values.filter { it.dayOfWeek == dayOfWeek })

    override suspend fun countBySemester(semester: String): Int =
        store.values.count { it.semester == semester }

    override fun searchCourses(keyword: String): Flow<List<CourseEntity>> = flowOf(emptyList())

    override suspend fun getAllCourses(): List<CourseEntity> = store.values.toList()
}

/**
 * Phase 8 Sub-step 9 Step 4：测试用 FakeSemesterDao
 *
 * 只 stub 了 [getCurrent]，其他方法返回占位 / 抛错。
 */
class FakeSemesterDao(
    private val current: SemesterEntity? = null,
) : SemesterDao {
    private val store = mutableMapOf<String, SemesterEntity>().apply {
        current?.let { put(it.name, it) }
    }

    override suspend fun getCurrent(): SemesterEntity? = current

    // ===== 占位实现 =====

    override suspend fun insert(semester: SemesterEntity): Long {
        store[semester.name] = semester
        return semester.name.hashCode().toLong()
    }

    override suspend fun update(semester: SemesterEntity) {
        store[semester.name] = semester
    }

    override suspend fun delete(semester: SemesterEntity) {
        store.remove(semester.name)
    }

    override fun observeAll(): Flow<List<SemesterEntity>> = flowOf(store.values.toList())

    override suspend fun getAll(): List<SemesterEntity> = store.values.toList()

    override suspend fun getByName(name: String): SemesterEntity? = store[name]

    override fun observeCurrent(): Flow<SemesterEntity?> = flowOf(current)

    override suspend fun deleteById(id: Long) {
        store.values.removeAll { it.name.hashCode().toLong() == id }
    }

    override suspend fun clearCurrent() {
        store.replaceAll { _, v -> v.copy(isCurrent = false) }
    }

    override suspend fun setCurrent(name: String) {
        store.replaceAll { _, v -> v.copy(isCurrent = v.name == name) }
    }
}