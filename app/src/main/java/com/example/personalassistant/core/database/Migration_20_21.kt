package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 20 → 21：课表「教师 / 地点下沉到课时」（Phase 28 · P10 数据层）
 *
 * ## 目标
 *
 * 给 `course_sessions` 加两列 —— `teacher` / `location`：
 * 同一门课不同周次可能换老师、换教室（分组实验、外教课），
 * 这两个字段挂在课程上就不够用了。
 *
 * 1. `ALTER TABLE course_sessions ADD COLUMN teacher TEXT NOT NULL DEFAULT ''`
 * 2. `ALTER TABLE course_sessions ADD COLUMN location TEXT NOT NULL DEFAULT ''`
 * 3. 回填：把课程级的教师 / 地点抄进每条课时（存量数据零损失）
 *
 * ## ⚠️ ALTER TABLE 必须带 DEFAULT
 *
 * 两个原因，缺一不可：
 *
 * 1. **SQLite 层面**：SQLite 不允许给已有表加「NOT NULL 且没有默认值的列」——
 *    `ADD COLUMN x TEXT NOT NULL` 会直接报
 *    `Cannot add a NOT NULL column with default value NULL`。
 *    （[Migration_18_19] 踩过这坑，本仓 v18 因此加过一轮修复。）
 *
 * 2. **Room schema 校验层面**（更要命）：
 *    Room 2.6 的列比对是**双向**的 —— 数据库有 DEFAULT 而实体没声明，同样算不匹配，
 *    运行期直接抛 `Migration didn't properly handle: course_sessions`。
 *    所以实体侧必须写 `@ColumnInfo(defaultValue = "''")`，
 *    与这里的 `DEFAULT ''` **逐字一致**（见 [com.example.personalassistant.feature.timetable.data.local.CourseSessionEntity]）。
 *
 * ## 回填为什么用子查询而不是 JOIN UPDATE
 *
 * SQLite 的 `UPDATE ... FROM` 是 3.33+ 才有的语法，老版本 Android 的 SQLite 不一定支持。
 * 相关子查询（`SELECT c.teacher FROM courses c WHERE c.id = course_sessions.courseId`）
 * 是最保守的写法，任何 SQLite 版本都能跑。
 *
 * ## 回填之后为什么仍然是「空串 = 沿用课程级」
 *
 * 回填把课程级的值**抄**进了课时，于是迁移后的每条课时都「看起来有自己的教师」。
 * 但语义上它跟课程级一模一样 —— 领域层的回落规则（[com.example.personalassistant.feature.timetable.domain.model.resolvedTeacher]）
 * 读的是「课时的值非空就用它」，所以回填的值就是当前课程级的值，渲染结果与改造前完全一致。
 *
 * 编辑页载入时会把「值等于课程级」的课时**归一化回空串**（当成「没单独设置过」），
 * 这样用户后来改课程级教师，这些课时会跟着变 —— 详见
 * [com.example.personalassistant.feature.timetable.presentation.edit.CourseEditViewModel]。
 *
 * ## 幂等
 *
 * `ALTER TABLE ADD COLUMN` 在 SQLite 里没有 `IF NOT EXISTS` 语法 ——
 * 迁移本来就只跑一次，不重复执行。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migration20To21Statements().forEach { database.execSQL(it) }
    }
}

/**
 * 迁移语句序列（抽成函数供 JVM 单测逐条断言 —— 沿用 [MIGRATION_19_20] 的做法）。
 */
internal fun migration20To21Statements(): List<String> = listOf(
    // ① 课时的授课教师（空串 = 沿用课程级）
    "ALTER TABLE `course_sessions` ADD COLUMN `teacher` TEXT NOT NULL DEFAULT ''",
    // ② 课时的上课地点（空串 = 沿用课程级）
    "ALTER TABLE `course_sessions` ADD COLUMN `location` TEXT NOT NULL DEFAULT ''",
    // ③ 回填：把课程级的教师 / 地点抄进每条课时（存量数据零损失）
    backfillSessionTeacherLocationSql(),
)

/**
 * 回填语句：课时没有自己的教师 / 地点时，用课程级的那份。
 *
 * 相关子查询写法（SQLite 全版本支持）。
 */
internal fun backfillSessionTeacherLocationSql(): String =
    "UPDATE course_sessions SET " +
        "teacher = (SELECT c.teacher FROM courses c WHERE c.id = course_sessions.courseId), " +
        "location = (SELECT c.location FROM courses c WHERE c.id = course_sessions.courseId)"
