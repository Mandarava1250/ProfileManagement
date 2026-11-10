package com.example.personalassistant.feature.calendar.domain.usecase.fake

import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.CourseWithSessionRow
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

    /**
     * Phase 12：观察全部课程
     */
    override fun observeAll(): Flow<List<CourseEntity>> = flowOf(store.values.toList())

    override fun observeBySemesterAndDay(semester: String, dayOfWeek: Int): Flow<List<CourseEntity>> =
        flowOf(store.values.filter { it.semester == semester && it.dayOfWeek == dayOfWeek })

    override suspend fun getBySemester(semester: String): List<CourseEntity> =
        store.values.filter { it.semester == semester }

    override fun observeByDayOfWeek(dayOfWeek: Int): Flow<List<CourseEntity>> =
        flowOf(store.values.filter { it.dayOfWeek == dayOfWeek })

    /**
     * Phase 28 P6：按天查课（JOIN 课时表）
     *
     * fake 里没有真实的课时表 —— 用**主时段镜像**顶一个课时，
     * 语义上等价于「这门课只有主时段那一个课时」。
     */
    override fun observeBySessionDay(dayOfWeek: Int): Flow<List<CourseWithSessionRow>> =
        flowOf(store.values.filter { it.dayOfWeek == dayOfWeek }.map { it.toSessionRow() })

    /** Phase 28 P6：一次拿多天（语义同 [observeBySessionDay]） */
    override fun observeBySessionDays(days: List<Int>): Flow<List<CourseWithSessionRow>> =
        flowOf(store.values.filter { it.dayOfWeek in days }.map { it.toSessionRow() })

    /** 把课程行包装成「课程 × 课时」行（课时 = 主时段镜像） */
    private fun CourseEntity.toSessionRow(): CourseWithSessionRow = CourseWithSessionRow(
        course = this,
        sessionId = 0L,
        sessionDayOfWeek = dayOfWeek,
        sessionStartTime = startTime,
        sessionEndTime = endTime,
        sessionSortOrder = 0,
        // Phase 28 P10：课时自己的教师 / 地点（这里跟课程级一致）
        sessionTeacher = teacher,
        sessionLocation = location,
    )

    /** 性能优化：一次拿多个 dayOfWeek 的课程（与真 DAO 的 IN 查询语义一致）。 */
    override fun observeByDayOfWeekIn(days: List<Int>): Flow<List<CourseEntity>> =
        flowOf(store.values.filter { it.dayOfWeek in days })

    override suspend fun countBySemester(semester: String): Int =
        store.values.count { it.semester == semester }

    override fun searchCourses(keyword: String): Flow<List<CourseEntity>> = flowOf(emptyList())

    override suspend fun getAllCourses(): List<CourseEntity> = store.values.toList()

    /**
     * 性能优化（v14）：CourseDao 新增的整表清空（备份导入用）。
     */
    override suspend fun clearAll(): Int {
        val size = store.size
        store.clear()
        return size
    }

    /**
     * Phase 8 Sub-step 4 备用：批量重命名学期引用
     */
    override suspend fun renameSemesterReferences(oldName: String, newName: String) {
        store.replaceAll { _, v -> if (v.semester == oldName) v.copy(semester = newName) else v }
    }
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

    /**
     * 性能优化（v14）：SemesterDao 新增的批量插入接口（备份导入用）。
     */
    override suspend fun insertAll(semesters: List<SemesterEntity>): List<Long> {
        semesters.forEach { store[it.name] = it }
        return semesters.map { it.name.hashCode().toLong() }
    }

    /** 性能优化（v14）：整表清空（备份导入用）。 */
    override suspend fun clearAll(): Int {
        val size = store.size
        store.clear()
        return size
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