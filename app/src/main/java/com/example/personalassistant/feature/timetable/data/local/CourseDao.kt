package com.example.personalassistant.feature.timetable.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 课程 DAO
 */
@Dao
interface CourseDao {

    // ===== 课程 CRUD =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Update
    suspend fun update(course: CourseEntity)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getById(id: Long): CourseEntity?

    @Query("SELECT * FROM courses WHERE semester = :semester ORDER BY dayOfWeek, startTime")
    fun observeBySemester(semester: String): Flow<List<CourseEntity>>

    /**
     * Phase 12：观察全部学期所有课程（用于「我」页 GPA 计算）。
     * 不带学期过滤，等价于全局扫表；GPA 计算只在「我」Tab 用一次，可接受。
     */
    @Query("SELECT * FROM courses")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE semester = :semester AND dayOfWeek = :dayOfWeek ORDER BY startTime")
    fun observeBySemesterAndDay(semester: String, dayOfWeek: Int): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE semester = :semester ORDER BY dayOfWeek, startTime")
    suspend fun getBySemester(semester: String): List<CourseEntity>

    @Query("SELECT * FROM courses WHERE dayOfWeek = :dayOfWeek ORDER BY startTime")
    fun observeByDayOfWeek(dayOfWeek: Int): Flow<List<CourseEntity>>

    /**
     * 检测同一学期、同一星期的时间冲突
     * 返回与给定时间段重叠的课程
     */
    @Query(
        """
        SELECT * FROM courses
        WHERE semester = :semester
          AND dayOfWeek = :dayOfWeek
          AND id != :excludeId
          AND startTime < :endTime
          AND endTime > :startTime
        """
    )
    suspend fun findConflicts(
        semester: String,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
        excludeId: Long = -1L
    ): List<CourseEntity>

    @Query("SELECT COUNT(*) FROM courses WHERE semester = :semester")
    suspend fun countBySemester(semester: String): Int

    /**
     * Phase 8 Sub-step 3：全局搜索 — 在 courses 表 name / teacher / location / note 任一字段 LIKE 匹配。
     *
     * - :keyword 为已包好 `%...%` 的字符串
     * - 大小写敏感
     * - 排序：dayOfWeek ASC, startTime ASC
     */
    @Query(
        """
        SELECT * FROM courses
        WHERE name LIKE :keyword
           OR teacher LIKE :keyword
           OR location LIKE :keyword
           OR note LIKE :keyword
        ORDER BY dayOfWeek ASC, startTime ASC
        """
    )
    fun searchCourses(keyword: String): Flow<List<CourseEntity>>

    // ===== Phase 8 Sub-step 4：备份导出快照 =====

    @Query("SELECT * FROM courses")
    suspend fun getAllCourses(): List<CourseEntity>

    /**
     * 一次拿多个 dayOfWeek 的课程（性能优化）。
     *
     * 月汇总原本要对 1..7 各 observe 一次（共 7 条独立 Room 查询），
     * 这里改成单条 `IN` 查询 —— 查询次数 7 → 1，结果等价。
     *
     * ⚠️ 排序与 [observeByDayOfWeek] 保持一致：两者都不加 ORDER BY，
     * 走 SQLite 自然序（rowid），所以同一个 dayOfWeek 内部的相对顺序前后完全相同。
     */
    @Query("SELECT * FROM courses WHERE dayOfWeek IN (:days)")
    fun observeByDayOfWeekIn(days: List<Int>): Flow<List<CourseEntity>>

    /**
     * 清空课程表（备份导入用；等价于「读回全表再逐行删」，但只有 1 次 IO）。
     */
    @Query("DELETE FROM courses")
    suspend fun clearAll(): Int

    /**
     * 把 semester 列中等于 [oldName] 的引用替换为 [newName]。
     * 用 @Query 直接做批量更新，避免一条条 update 的 N 次事务。
     */
    @Query("UPDATE courses SET semester = :newName WHERE semester = :oldName")
    suspend fun renameSemesterReferences(oldName: String, newName: String)
}

/**
 * 学期 DAO
 */
@Dao
interface SemesterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(semester: SemesterEntity): Long

    /**
     * 批量插入学期（REPLACE，与 [insert] 一致）。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(semesters: List<SemesterEntity>): List<Long>

    /**
     * 清空学期表（备份导入用；等价于「读回全表再逐行删」，但只有 1 次 IO）。
     */
    @Query("DELETE FROM semesters")
    suspend fun clearAll(): Int

    @Update
    suspend fun update(semester: SemesterEntity)

    @Delete
    suspend fun delete(semester: SemesterEntity)

    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    fun observeAll(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters ORDER BY startDate DESC")
    suspend fun getAll(): List<SemesterEntity>

    @Query("SELECT * FROM semesters WHERE name = :name")
    suspend fun getByName(name: String): SemesterEntity?

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrent(): SemesterEntity?

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    fun observeCurrent(): Flow<SemesterEntity?>

    // ===== Phase 8 Sub-step 4：按 id 删除（导入时清理旧数据）=====

    @Query("DELETE FROM semesters WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 切换当前学期：在事务内先把所有 isCurrent 设为 false，再把目标设为 true
     */
    @Query("UPDATE semesters SET isCurrent = 0")
    suspend fun clearCurrent()

    @Query("UPDATE semesters SET isCurrent = 1 WHERE name = :name")
    suspend fun setCurrent(name: String)
}
