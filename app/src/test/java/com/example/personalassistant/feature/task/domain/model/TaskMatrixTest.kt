package com.example.personalassistant.feature.task.domain.model

import com.example.personalassistant.feature.task.domain.usecase.ObserveTasksByQuadrantUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 8final G2：矩阵视图数据模型单测。
 *
 * 覆盖三类契约（对应 plan v2 的 4 条修正）：
 * 1. **不重新分类**：格子内容必须等于对应分组的完整列表（G3）
 * 2. **未排期区独立**：不计入格子总数、有固定排布位置（修正 3）
 * 3. **断点与 5 区完整性**：< 600dp 走纵向，且未排期区在最顶部（修正 4）
 *
 * 另含术语约束（修正 2）：格子名只带位置信息，不带「重要/紧急」语义轴。
 */
class TaskMatrixTest {

    // ===== 测试夹具 =====

    private fun task(
        id: Long,
        title: String = "t$id",
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueDate: Long? = 1_000L,
    ) = Task(id = id, title = title, priority = priority, dueDate = dueDate)

    private val allCells: List<Task> = listOf(
        task(1, dueDate = 10L),   // 归 P1
        task(2, dueDate = 20L),   // 归 P2
        task(3, dueDate = 30L),   // 归 P3
        task(4, dueDate = 40L),   // 归 P4
    )

    private fun groupsOf(
        p1: List<Task> = emptyList(),
        p2: List<Task> = emptyList(),
        p3: List<Task> = emptyList(),
        p4: List<Task> = emptyList(),
        unscheduled: List<Task> = emptyList(),
        completed: List<Task> = emptyList(),
    ) = TaskGroups(
        byQuadrant = mapOf(
            TaskQuadrant.P1 to p1,
            TaskQuadrant.P2 to p2,
            TaskQuadrant.P3 to p3,
            TaskQuadrant.P4 to p4,
            TaskQuadrant.UNSCHEDULED to unscheduled,
        ),
        completed = completed,
    )

    // ===== 1. MatrixCell：位置编码 + 固定映射 =====

    @Test
    fun `MatrixCell 位置唯一且覆盖 0 到 3`() {
        assertEquals(listOf(0, 1, 2, 3), MatrixCell.CELLS.map { it.position })
    }

    @Test
    fun `MatrixCell 的 row col 由 position 推导`() {
        // 2×2：position 0,1 在第 0 行；2,3 在第 1 行
        assertEquals(0 to 0, MatrixCell.CELL_0.row to MatrixCell.CELL_0.col)
        assertEquals(0 to 1, MatrixCell.CELL_1.row to MatrixCell.CELL_1.col)
        assertEquals(1 to 0, MatrixCell.CELL_2.row to MatrixCell.CELL_2.col)
        assertEquals(1 to 1, MatrixCell.CELL_3.row to MatrixCell.CELL_3.col)
    }

    @Test
    fun `MatrixCell 到分组的映射锁定为 P1 P2 P3 P4 顺序`() {
        // 这条断言是「防误改」的锚点：改任何一个格子绑定的分组都会让列表与矩阵视图数据不一致
        assertEquals(TaskQuadrant.P1, MatrixCell.CELL_0.quadrant)
        assertEquals(TaskQuadrant.P2, MatrixCell.CELL_1.quadrant)
        assertEquals(TaskQuadrant.P3, MatrixCell.CELL_2.quadrant)
        assertEquals(TaskQuadrant.P4, MatrixCell.CELL_3.quadrant)
    }

    @Test
    fun `ROWS 渲染顺序为上行两格 下行两格`() {
        val rows = MatrixCell.ROWS
        assertEquals(2, rows.size)
        assertEquals(listOf(MatrixCell.CELL_0, MatrixCell.CELL_1), rows[0])
        assertEquals(listOf(MatrixCell.CELL_2, MatrixCell.CELL_3), rows[1])
    }

    @Test
    fun `术语合规 格子名只含位置不带语义轴`() {
        // plan v2 修正 2：禁止把格子命名成 IMPORTANT/URGENT 这类二元硬轴
        MatrixCell.entries.forEach { cell ->
            val n = cell.name
            assertTrue("格子名应为位置编码，实际：$n", n.startsWith("CELL_"))
            assertFalse("格子名不得含语义轴", n.contains("IMPORT") || n.contains("URGENT"))
        }
    }

    // ===== 2. from：不重新分类 + 4 格齐全 =====

    @Test
    fun `from 始终产出 4 个格子即使全部为空`() {
        val matrix = TaskMatrix.from(TaskGroups(emptyMap(), emptyList()))
        assertEquals(4, matrix.cells.size)
        MatrixCell.CELLS.forEach { cell ->
            assertTrue("空格子必须是空列表而非缺 key", matrix.tasksOf(cell).isEmpty())
        }
    }

    @Test
    fun `from 格子内容等于对应分组的完整列表`() {
        // 核心契约：格子不做任何二次筛选/重新划分
        val source = groupsOf(
            p1 = listOf(task(11), task(12)),
            p2 = listOf(task(21)),
            p3 = listOf(task(31)),
            p4 = listOf(task(41)),
        )
        val matrix = TaskMatrix.from(source)

        assertEquals(
            source.byQuadrant.getValue(TaskQuadrant.P1).map { it.id }.toSet(),
            matrix.tasksOf(MatrixCell.CELL_0).map { it.id }.toSet(),
        )
        listOf(
            MatrixCell.CELL_1 to TaskQuadrant.P2,
            MatrixCell.CELL_2 to TaskQuadrant.P3,
            MatrixCell.CELL_3 to TaskQuadrant.P4,
        ).forEach { (cell, quadrant) ->
            assertEquals(
                "格子 $cell 应等于分组 $quadrant",
                source.byQuadrant.getValue(quadrant).map { it.id }.toSet(),
                matrix.tasksOf(cell).map { it.id }.toSet(),
            )
        }
    }

    @Test
    fun `from 不丢失任何未完成任务`() {
        val source = groupsOf(
            p1 = listOf(task(1)),
            p2 = listOf(task(2), task(3)),
            p3 = listOf(task(4)),
            p4 = emptyList(),
            unscheduled = listOf(task(5), task(6)),
        )
        val matrix = TaskMatrix.from(source)
        assertEquals(6, matrix.totalActive)
        assertEquals(4, matrix.cellTotal)
        assertEquals(2, matrix.unscheduled.size)
    }

    @Test
    fun `completed 不参与矩阵`() {
        // 已完成由列表底部折叠区承载，矩阵视图只处理 active
        val source = groupsOf(
            p1 = listOf(task(1)),
            completed = listOf(task(90), task(91), task(92)),
        )
        val matrix = TaskMatrix.from(source)
        assertEquals(1, matrix.totalActive)
        MatrixCell.CELLS.forEach { cell ->
            assertTrue(
                "已完成任务不应出现在格子 ${cell.name} 内",
                matrix.tasksOf(cell).none { it.id >= 90L },
            )
        }
    }

    // ===== 3. 计数边界：cellTotal 不含未排期区 =====

    @Test
    fun `cellTotal 不含未排期区`() {
        val matrix = TaskMatrix.from(
            groupsOf(
                p1 = listOf(task(1), task(2)),
                unscheduled = listOf(task(3), task(4), task(5)),
            ),
        )
        assertEquals(2, matrix.cellTotal)
        assertEquals(5, matrix.totalActive)
    }

    @Test
    fun `未排期区有任务时 hasUnscheduled 为 true`() {
        val withUnscheduled = TaskMatrix.from(groupsOf(unscheduled = listOf(task(1))))
        assertTrue(withUnscheduled.hasUnscheduled)

        val without = TaskMatrix.from(groupsOf(p1 = listOf(task(2))))
        assertFalse(without.hasUnscheduled)
    }

    @Test
    fun `isEmpty 只在全部区域皆空时为 true`() {
        assertTrue(TaskMatrix.from(TaskGroups(emptyMap(), emptyList())).isEmpty)
        assertFalse(TaskMatrix.from(groupsOf(p1 = listOf(task(1)))).isEmpty)
        // 只有未排期区有任务时，矩阵也不算空（它属于 5 个区域之一）
        assertFalse(TaskMatrix.from(groupsOf(unscheduled = listOf(task(1)))).isEmpty)
    }

    // ===== 4. 排序规则（矩阵内固定顺序，不跟随全局 sort） =====

    @Test
    fun `格子内按 priority rank 降序排列`() {
        val matrix = TaskMatrix.from(
            groupsOf(
                p1 = listOf(
                    task(1, priority = TaskPriority.HIGH, dueDate = 10L),
                    task(2, priority = TaskPriority.URGENT, dueDate = 20L),
                ),
            ),
        )
        assertEquals(listOf(2L, 1L), matrix.tasksOf(MatrixCell.CELL_0).map { it.id })
    }

    @Test
    fun `同优先级按 dueDate 升序且 null 排最后`() {
        val sorted = listOf(
            task(1, priority = TaskPriority.HIGH, dueDate = 500L),
            task(2, priority = TaskPriority.HIGH, dueDate = 100L),
            task(3, priority = TaskPriority.HIGH, dueDate = null),
            task(4, priority = TaskPriority.HIGH, dueDate = 300L),
        ).sortedWith(TaskMatrix.MATRIX_ORDER)
        assertEquals(listOf(2L, 4L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun `dueDate 相同时按标题忽略大小写升序`() {
        val sorted = listOf(
            task(1, title = "cherry", dueDate = 100L),
            task(2, title = "Apple", dueDate = 100L),
            task(3, title = "banana", dueDate = 100L),
        ).sortedWith(TaskMatrix.MATRIX_ORDER)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `标题也相同时按 id 升序保证顺序稳定`() {
        val sorted = listOf(
            task(30, title = "same", dueDate = 100L),
            task(10, title = "same", dueDate = 100L),
            task(20, title = "same", dueDate = 100L),
        ).sortedWith(TaskMatrix.MATRIX_ORDER)
        assertEquals(listOf(10L, 20L, 30L), sorted.map { it.id })
    }

    @Test
    fun `未排期区也应用同一套排序`() {
        val matrix = TaskMatrix.from(
            groupsOf(
                unscheduled = listOf(
                    task(1, priority = TaskPriority.LOW),
                    task(2, priority = TaskPriority.URGENT),
                    task(3, priority = TaskPriority.MEDIUM),
                ),
            ),
        )
        assertEquals(listOf(2L, 3L, 1L), matrix.unscheduled.map { it.id })
    }

    // ===== 5. 断点与布局选择（plan v2 修正 4） =====

    @Test
    fun `断点判定 小于 600dp 走纵向`() {
        assertTrue(TaskMatrix.isVerticalLayout(599))
        assertFalse("边界值 600dp 应走 2×2", TaskMatrix.isVerticalLayout(600))
        assertFalse(TaskMatrix.isVerticalLayout(840))
        assertTrue("极窄屏仍走纵向", TaskMatrix.isVerticalLayout(320))
    }

    @Test
    fun `纵向顺序第一个区域是未排期区且五区完整`() {
        val order = MatrixRegion.VERTICAL_ORDER
        assertEquals("纵向布局必须覆盖 5 个区域", 5, order.size)
        assertEquals(MatrixRegion.UNSCHEDULED, order.first())
        assertEquals(
            listOf(MatrixRegion.CELL_0, MatrixRegion.CELL_1, MatrixRegion.CELL_2, MatrixRegion.CELL_3),
            order.drop(1),
        )
        // 无重复、无遗漏
        assertEquals(order.size, order.distinct().size)
        assertEquals(MatrixRegion.ALL.toSet(), order.toSet())
    }

    @Test
    fun `未排期区不属于矩阵格子`() {
        assertFalse(MatrixRegion.UNSCHEDULED.isInMatrix)
        assertNull(MatrixRegion.UNSCHEDULED.cell)
        MatrixRegion.entries.filter { it.isInMatrix }.forEach { region ->
            assertTrue("格子区域必须能映射回 MatrixCell", region.cell != null)
        }
    }

    @Test
    fun `MatrixRegion 与 MatrixCell 映射互逆`() {
        MatrixCell.CELLS.forEach { cell ->
            val region = MatrixRegion.entries.first { it.cell == cell }
            assertEquals("region ${region.name} 与 cell ${cell.name} 应指向同一分组", cell.quadrant, region.quadrant)
        }
    }

    @Test
    fun `tasksOf 按区域取数与按格子取数结果一致`() {
        val source = groupsOf(p1 = listOf(task(1)), p4 = listOf(task(2)), unscheduled = listOf(task(3)))
        val matrix = TaskMatrix.from(source)
        assertEquals(matrix.tasksOf(MatrixCell.CELL_0), matrix.tasksOf(MatrixRegion.CELL_0))
        assertEquals(matrix.tasksOf(MatrixCell.CELL_3), matrix.tasksOf(MatrixRegion.CELL_3))
        assertEquals(matrix.unscheduled, matrix.tasksOf(MatrixRegion.UNSCHEDULED))
    }

    // ===== 6. 构造约束 =====

    @Test
    fun `cells 缺少任一格子时构造失败`() {
        val incomplete = MatrixCell.CELLS.drop(1).associateWith { emptyList<Task>() }
        try {
            TaskMatrix(cells = incomplete, unscheduled = emptyList())
            fail("缺格子应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("4 个格子"))
        }
    }

    // ===== 7. 与既有分类的一致性（回归护栏） =====

    @Test
    fun `矩阵数据来源于既有分类结果 两者任务集合一致`() {
        // 用真实的 classify 产数据，再比较矩阵与分组的并集，确保矩阵没有凭空增删任务
        val active = allCells + task(5, dueDate = null)
        val groups = ObserveTasksByQuadrantUseCase.classify(active, completed = emptyList())
        val matrix = TaskMatrix.from(groups)

        val fromGroups = groups.byQuadrant.values.flatten().map { it.id }.toSet()
        val fromMatrix = MatrixRegion.ALL.flatMap { matrix.tasksOf(it) }.map { it.id }.toSet()
        assertEquals(fromGroups, fromMatrix)
        assertEquals(5, matrix.totalActive)
    }
}
