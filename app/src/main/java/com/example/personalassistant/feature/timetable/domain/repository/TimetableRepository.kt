package com.example.personalassistant.feature.timetable.domain.repository

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.Semester
import kotlinx.coroutines.flow.Flow

/**
 * 课表仓储接口
 *
 * - 所有返回 Flow 的方法响应数据库变更
 * - 所有写入方法为 suspend，事务边界由实现层负责
 */
interface TimetableRepository {

    // ===== Course =====

    /** 获取某学期全部课程（响应式）*/
    fun observeCoursesBySemester(semester: String): Flow<List<Course>>

    /**
     * Phase 12：观察全部学期所有课程（用于「我」页 GPA 计算）。
     * 不带学期过滤。
     */
    fun observeAllCourses(): Flow<List<Course>>

    /** 获取某学期某天的课程（响应式）*/
    fun observeCoursesByDay(semester: String, dayOfWeek: Int): Flow<List<Course>>

    /** 按 id 查询 */
    suspend fun getCourseById(id: Long): Course?

    /** 检测冲突（返回与给定时间段重叠的课程；excludeId 通常是被编辑课程自身的 id）*/
    suspend fun findConflicts(
        semester: String?,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
        excludeId: Long = -1L,
    ): List<Course>

    /** 新增或更新课程；返回新 id 或已存在 id */
    suspend fun upsertCourse(course: Course): Long

    // ===== Phase 28：一门课多课时 =====

    /**
     * 观察课程（含**全部课时**）
     *
     * @param semester 学期名；**null = 全部学期**（与「未归学期的课撞所有学期」的口径一致）
     *
     * 返回的每个 [Course] 的 [Course.sessions] 至少有一个元素 ——
     * 课程在课时表里没有记录时，用 `courses` 表的三列镜像兜底出一个单课时。
     */
    fun observeCoursesWithSessions(semester: String?): Flow<List<Course>>

    /** 按 id 取单门课（含全部课时）；课程不存在返回 null */
    suspend fun getCourseWithSessions(courseId: Long): Course?

    /**
     * 整门课一次性保存（**事务**）
     *
     * 事务内做四件事：
     * 1. upsert `courses` 行 → 拿到 courseId
     * 2. 删掉这门课原有的全部课时
     * 3. 插入新的全部课时
     * 4. 回写 `courses` 表的三列镜像 = 第一个课时的 dayOfWeek / startTime / endTime
     *
     * @return 课程 id
     */
    suspend fun upsertCourseWithSessions(course: Course): Long

    /**
     * 找出与给定课时冲突的其它课程（逐课时检测）
     *
     * @param semester 学期名；**null = 跨全部学期**
     *   （未归学期的课，撞了任何学期的课都要报 —— 这是既有 [findConflicts] 的口径，不能丢）
     * @param session 要检测的课时
     * @param excludeCourseId 要排除的课程 id（通常是被编辑的课程自己）
     *
     * ⚠️ 排除整门课意味着「同一门课的两个课时互相重叠」**查不出来** ——
     * 那类冲突要在 [com.example.personalassistant.feature.timetable.domain.usecase.UpsertCourseUseCase]
     * 里单独检查。
     */
    suspend fun findSessionConflicts(
        semester: String?,
        session: CourseSession,
        excludeCourseId: Long,
    ): List<Course>

    /** 删除课程 */
    suspend fun deleteCourse(id: Long)

    /** 切换学期（事务：清空所有 isCurrent，置目标为 true）*/
    suspend fun switchSemester(name: String)

    // ===== Semester =====

    /** 观察全部学期 */
    fun observeAllSemesters(): Flow<List<Semester>>

    /** 观察当前学期 */
    fun observeCurrentSemester(): Flow<Semester?>

    /** 按名称取学期 */
    suspend fun getSemesterByName(name: String): Semester?

    /** 新增或更新学期 */
    suspend fun upsertSemester(semester: Semester)

    /**
     * 把 Course.semester 列中所有 `oldName` 引用替换为 `newName`。
     * 配合 [UpdateSemesterUseCase] 用于"重命名学期"时保持引用一致。
     */
    suspend fun renameSemesterReferences(oldName: String, newName: String)

    /** 删除学期 */
    suspend fun deleteSemester(name: String)

    // ===== Phase 8 Sub-step 3 全局搜索 =====

    /**
     * 关键字搜索课程（在 name / teacher / location / note 任一字段 LIKE 匹配）。
     *
     * - [keyword] 接收**已包好 `%...%` 通配符**的字符串
     * - 区分大小写
     */
    fun searchCourses(keyword: String): Flow<List<Course>>
}