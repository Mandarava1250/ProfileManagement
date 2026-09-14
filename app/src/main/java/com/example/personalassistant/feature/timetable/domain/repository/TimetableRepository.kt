package com.example.personalassistant.feature.timetable.domain.repository

import com.example.personalassistant.feature.timetable.domain.model.Course
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