package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.ui.graphics.Color
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.domain.model.Course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phase 15 P0-1.3：日视图时间轴的「完成态透传」单测（纯 JVM）
 *
 * 被测对象：`feature/calendar/presentation/components/DayTimelineView.kt` 里的
 * [buildTimelineBlocks] / [minuteOfDay]。
 *
 * ## 为什么要测这个
 *
 * 修复之前 [TimelineBlock] **根本没有完成态字段**，于是时间轴上的任务块永远不会变灰：
 * 任务在别处标了「已完成」，回到日视图还是老样子，看不出差别。
 * 修复之后任务块会带上 `isCompleted = task.isCompleted`，这块单测把这个透传钉死 ——
 * 以后谁再把这一行删了，测试会立刻红。
 *
 * 覆盖：
 * - 已完成的任务 → 产出的块 `isCompleted == true`
 * - 未完成的任务 → 产出的块 `isCompleted == false`
 * - 任务没有 `dueDate` → 不产出块（不落到轴上）
 * - 任务 `dueDate` 正好是本地 00:00 → 不产出块（[minuteOfDay] 对 00:00 返回 null）
 * - 课程块 / 事件块不是任务 → `isCompleted` 默认为 false
 * - 同一个轴上，完成态不会从已完成的任务串到未完成的任务
 *
 * Fake 优先：这里全是纯函数，直接喂真实的 domain 模型（Task / Course / Event），
 * 不引 Mockito / MockK。
 */
class DayTimelineBlocksTest {

    /** 主题主色 —— 任务块用的就是这个颜色（课程 / 事件用各自的 color） */
    private val primaryColor = Color(0xFF6750A4.toInt())

    // ===== 完成态透传 =====

    @Test
    fun `completed task produces a completed timeline block`() {
        val task = Task(
            id = 1L,
            title = "已完成的任务",
            dueDate = localMillisAt(hour = 9, minute = 30),
            isCompleted = true,
        )

        val blocks = buildTimelineBlocks(emptyList(), emptyList(), listOf(task), primaryColor)

        val block = blocks.singleOrNull { it.key == "task-1" }
        assertNotNull("已完成的任务应该产出块", block)
        assertTrue("已完成任务的块必须带上完成态（否则时间轴上不会变灰）", block!!.isCompleted)
        assertEquals("块要落在截止时间 09:30", 9 * 60 + 30, block.startMinute)
    }

    @Test
    fun `unfinished task produces a not completed timeline block`() {
        val task = Task(
            id = 2L,
            title = "还没做完的任务",
            dueDate = localMillisAt(hour = 9, minute = 30),
            isCompleted = false,
        )

        val blocks = buildTimelineBlocks(emptyList(), emptyList(), listOf(task), primaryColor)

        val block = blocks.singleOrNull { it.key == "task-2" }
        assertNotNull("未完成的任务也应该产出块", block)
        assertFalse("未完成任务的块不能是完成态", block!!.isCompleted)
    }

    @Test
    fun `completion flag does not leak to the sibling task block`() {
        // 同一个轴上放一个已完成、一个未完成 —— 完成态必须各归各的
        val tasks = listOf(
            Task(id = 5L, title = "已完成", dueDate = localMillisAt(9, 30), isCompleted = true),
            Task(id = 6L, title = "未完成", dueDate = localMillisAt(14, 0), isCompleted = false),
        )

        val blocks = buildTimelineBlocks(emptyList(), emptyList(), tasks, primaryColor)

        val done = blocks.singleOrNull { it.key == "task-5" }
        val undone = blocks.singleOrNull { it.key == "task-6" }
        assertNotNull("已完成的任务应该产出块", done)
        assertNotNull("未完成的任务应该产出块", undone)
        assertTrue("已完成任务的块要带完成态", done!!.isCompleted)
        assertFalse("未完成任务的块不能被带成完成态", undone!!.isCompleted)
    }

    // ===== 任务什么时候才落到轴上 =====

    @Test
    fun `task without due date does not produce a block`() {
        val task = Task(
            id = 3L,
            title = "没写截止时间的任务",
            dueDate = null,
        )

        val blocks = buildTimelineBlocks(emptyList(), emptyList(), listOf(task), primaryColor)

        assertTrue("没有 dueDate 的任务不该落到轴上：$blocks", blocks.none { it.key.startsWith("task-") })
        assertTrue("轴上应该一个块都没有：$blocks", blocks.isEmpty())
    }

    @Test
    fun `task due exactly at midnight does not produce a block`() {
        val midnight = localMillisAt(hour = 0, minute = 0)
        // 先把前提钉住：minuteOfDay 对 00:00 返回 null（没有时刻的任务不该占轴）
        assertNull("本地 00:00 在 minuteOfDay 里应该被判为 null", minuteOfDay(midnight))

        val task = Task(
            id = 4L,
            title = "零点截止的任务",
            dueDate = midnight,
            isCompleted = true,
        )

        val blocks = buildTimelineBlocks(emptyList(), emptyList(), listOf(task), primaryColor)

        assertTrue("00:00 截止的任务不该落到轴上：$blocks", blocks.none { it.key.startsWith("task-") })
        assertTrue("轴上应该一个块都没有：$blocks", blocks.isEmpty())
    }

    // ===== 课程 / 事件不是任务 =====

    @Test
    fun `course and event blocks are not completed by default`() {
        val course = Course(
            id = 10L,
            name = "高等数学",
            teacher = "李老师",
            location = "教三 201",
            dayOfWeek = 1,
            startTime = 8 * 60,
            endTime = 9 * 60 + 40,
            color = 0xFF6750A4.toInt(),
        )
        // 注意：buildTimelineBlocks 把 event.startTime 当「当天分钟数」用，
        // 所以这里就按分钟给值（14:00 - 15:00），而不是 epoch 毫秒。
        val event = Event(
            id = 20L,
            title = "社团例会",
            date = localMillisAt(hour = 0, minute = 0),
            startTime = 14 * 60L,
            endTime = 15 * 60L,
            color = 0xFF9CCC65.toInt(),
        )

        val blocks = buildTimelineBlocks(listOf(course), listOf(event), emptyList(), primaryColor)

        val courseBlock = blocks.singleOrNull { it.key == "course-10" }
        assertNotNull("课程应该产出块", courseBlock)
        assertFalse("课程块不是任务，不该有完成态", courseBlock!!.isCompleted)

        val eventBlock = blocks.singleOrNull { it.key == "event-20" }
        assertNotNull("事件应该产出块", eventBlock)
        assertFalse("事件块不是任务，不该有完成态", eventBlock!!.isCompleted)
    }

    // ===== 时间戳 helper =====

    /**
     * 找一个「本地时间确实是 hh:mm」的时间戳。
     *
     * 直接用 `LocalDate.atTime(hh, mm)` 在夏令时「跳过某个时刻」的时区会算错
     * （比如当地 00:00 被跳过时，`atStartOfDay` 给出的是 01:00），
     * 所以这里逐分钟扫两天，找到墙上时钟真的是 hh:mm 的那一刻。
     */
    private fun localMillisAt(hour: Int, minute: Int): Long {
        var instant: Instant = scanStart()
        repeat(MINUTES_IN_TWO_DAYS) {
            val local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            if (local.hour == hour && local.minute == minute) return instant.toEpochMilli()
            instant = instant.plusSeconds(60)
        }
        error("找不到本地 $hour:$minute 的时间戳（当前时区异常）")
    }

    /** 扫描起点：本地零点的前一小时（对齐到整分钟，扫描时不会漏掉整分钟） */
    private fun scanStart(): Instant =
        LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .minusSeconds(3600)

    private companion object {
        /** 扫两天（分钟）—— 足以跨过一次夏令时跳变 */
        const val MINUTES_IN_TWO_DAYS = 60 * 48
    }
}
