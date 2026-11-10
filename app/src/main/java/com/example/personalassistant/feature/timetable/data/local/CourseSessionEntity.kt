package com.example.personalassistant.feature.timetable.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 课时实体 —— 「一门课」下面的**一个上课时段**
 *
 * ## 课程 = 一门课 + 多个课时
 *
 * 改造前 `courses` 表一行 = 一门课 = 一个上课时段（星期几 + 起止时间都在这一行里）。
 * 改造后拆成两张表：
 * - `courses` —— 课程本体（名字 / 老师 / 地点 / 颜色 / 学期 / 周次 / 学分绩点 …）
 * - `course_sessions` —— 课时（星期几 + 起止时间 + 课程内顺序），多行指回一门课
 *
 * 例：高等数学每周一 1-2 节、周三 3-4 节、周五 5-6 节
 * → `courses` 一行 + `course_sessions` 三行。
 *
 * ## ⚠️ courses 表的 dayOfWeek / startTime / endTime 三列
 *
 * 这三列**保留不动**（不删列、不重建表），语义收窄为：
 * **「课程主时段（= 第一个课时）的冗余镜像」**，由 Repository 单点维护。
 *
 * - 保留的理由：存量数据零损失（每门课自动变成「有一个课时的课」）、
 *   迁移不用重建 courses 表（SQLite 删列要重建，风险高）、老备份导入有落点。
 * - 业务判断**一律走 `course_sessions`**。别再拿 `courses.dayOfWeek` 当
 *   「这门课周几上」—— 一门课可能有多个星期几，那是**多个**课时的事。
 *
 * ## 设计要点
 * - courseId: 外键 → `courses.id`，`ON DELETE CASCADE`（删课连带清课时）
 * - dayOfWeek: 1-7（周一=1，周日=7），与 [CourseEntity] 同一套口径
 * - startTime / endTime: 当天分钟数（0-1439），与日期无关，避免时区干扰
 * - sortOrder: 课程内的显示顺序（0 起），配合 (courseId, sortOrder) 唯一索引
 *
 * ⚠️ 本实体加了 `@Serializable`（Phase 28 P2 起）—— 备份必须带上课时表，
 * 否则导出的备份不含课时，导入后课程会变成「没有课时」。
 */
@Entity(
    tableName = "course_sessions",
    indices = [
        Index("courseId"),
        Index(value = ["courseId", "sortOrder"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
@Serializable
data class CourseSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 所属课程 id（→ courses.id，删课连带删课时） */
    @ColumnInfo(name = "courseId")
    val courseId: Long,

    /** 课程内的显示顺序（0 起） */
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int = 0,

    /** 1=周一, 7=周日 */
    @ColumnInfo(name = "dayOfWeek")
    val dayOfWeek: Int,

    /** 当天分钟数，0-1439 */
    @ColumnInfo(name = "startTime")
    val startTime: Int,

    /** 当天分钟数，0-1439 */
    @ColumnInfo(name = "endTime")
    val endTime: Int,

    /**
     * 这个课时自己的授课教师；**空串 = 沿用课程级的**（Phase 28 P10）
     *
     * ⚠️ `defaultValue = "''"` 必须与
     * [com.example.personalassistant.core.database.Migration_20_21] 里的
     * `ADD COLUMN teacher TEXT NOT NULL DEFAULT ''` **逐字一致** ——
     * Room 2.6 的列比对是**双向**的：数据库有 DEFAULT 而实体没声明，同样算不匹配，
     * 运行期直接抛 `Migration didn't properly handle: course_sessions`。
     */
    @ColumnInfo(name = "teacher", defaultValue = "''")
    val teacher: String = "",

    /**
     * 这个课时自己的上课地点；**空串 = 沿用课程级的**（Phase 28 P10）
     *
     * ⚠️ `defaultValue = "''"` 与迁移里的 `DEFAULT ''` 逐字一致（理由同上）。
     */
    @ColumnInfo(name = "location", defaultValue = "''")
    val location: String = "",
)
