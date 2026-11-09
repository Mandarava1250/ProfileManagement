package com.example.personalassistant.feature.timetable.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 课时 DAO —— 「一门课」的多个上课时段
 *
 * 与 [com.example.personalassistant.feature.timetable.data.local.CourseDao] 同款风格：
 * 写操作 `suspend`，观察用 `Flow`，同步查询用 `suspend fun`。
 *
 * ⚠️ 这里的每一条查询都是**按课时**的。想问「这门课周几上」请查课时表，
 * 不要用 `courses.dayOfWeek`（那只是主时段镜像）。
 */
@Dao
interface CourseSessionDao {

    // ===== 写入 =====

    /** 插入一个课时，返回新行 id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: CourseSessionEntity): Long

    /** 批量插入课时（REPLACE，与 [insert] 一致） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<CourseSessionEntity>)

    // ===== 删除 =====

    /**
     * 删掉某门课的全部课时。
     *
     * 「整门课一次性提交」时用：先全删再全插，事务内保证中间态不可见。
     */
    @Query("DELETE FROM course_sessions WHERE courseId = :courseId")
    suspend fun deleteByCourseId(courseId: Long)

    /** 一次删掉多门课的全部课时（删课批量清理用） */
    @Query("DELETE FROM course_sessions WHERE courseId IN (:courseIds)")
    suspend fun deleteByCourseIds(courseIds: List<Long>)

    // ===== 查询 =====

    /** 某门课的全部课时，按课程内顺序（sortOrder, id）排 */
    @Query("SELECT * FROM course_sessions WHERE courseId = :courseId ORDER BY sortOrder, id")
    suspend fun getByCourseId(courseId: Long): List<CourseSessionEntity>

    /** 观察某门课的全部课时（编辑页用） */
    @Query("SELECT * FROM course_sessions WHERE courseId = :courseId ORDER BY sortOrder, id")
    fun observeByCourseId(courseId: Long): Flow<List<CourseSessionEntity>>

    /**
     * 观察多门课的全部课时（课表主页面用 —— 一次查询拿全量，再在内存里按 courseId 分组）。
     *
     * ⚠️ 传空列表是安全的：Room 对 `IN (:list)` 生成的是「单占位符 + 逐个绑定」，
     * 列表为空时循环体一次都不执行，不会拼出非法的 `IN ()`。
     */
    @Query("SELECT * FROM course_sessions WHERE courseId IN (:courseIds) ORDER BY sortOrder, id")
    fun observeByCourseIds(courseIds: List<Long>): Flow<List<CourseSessionEntity>>

    /** 全表扫（备份导出快照用；P2 接入备份时用得上） */
    @Query("SELECT * FROM course_sessions")
    suspend fun getAll(): List<CourseSessionEntity>
}
