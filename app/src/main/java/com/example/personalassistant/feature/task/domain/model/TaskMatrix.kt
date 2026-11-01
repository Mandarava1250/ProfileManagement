package com.example.personalassistant.feature.task.domain.model

/**
 * 任务矩阵视图的数据模型（Phase 8final G2）。
 *
 * ## 术语约束（必须遵守）
 *
 * UI、文档、注释一律使用「**矩阵视图 / 2×2 分组**」这一中性表述。
 * **禁止**把行/列描述成「重要 vs 不重要」「紧急 vs 不紧急」这类二元硬轴：
 * 现有 [TaskQuadrant] 是 5 档分类，且 `MEDIUM` 同时出现在 [TaskQuadrant.P2]
 * 与 [TaskQuadrant.P3]（见 [ObserveTasksByQuadrantUseCase.classifyOne]），
 * 任何「按 priority × due 重新二分」的说法都会与既有分类冲突。
 * MEDIUM 保持中性，不做强制映射。
 *
 * ## 设计要点：cell 不重新分类
 *
 * [MatrixCell] 只承担**位置编码**职责（第几个格子），内容直接复用既有
 * [TaskQuadrant] 的分组结果。也就是说：
 *
 * - cell 内容 == 对应象限的完整列表（不是子集，不是重新划分）
 * - 矩阵视图与 5 Tab 列表视图看到的数据**完全一致**，只是空间排布不同
 * - 因此本视图不需要、也没有引入任何新的分类逻辑
 *
 * 好处：不破坏 G3（矩阵遵守既有分类规则、不重新定义 quadrant），
 * 也让「切到矩阵」成为一个纯粹可逆的展示层变化。
 */
enum class MatrixCell(val position: Int, val quadrant: TaskQuadrant) {
    /** 左上 */
    CELL_0(0, TaskQuadrant.P1),

    /** 右上 */
    CELL_1(1, TaskQuadrant.P2),

    /** 左下 */
    CELL_2(2, TaskQuadrant.P3),

    /** 右下 */
    CELL_3(3, TaskQuadrant.P4),
    ;

    /** 行号（0=上，1=下），由 position 推导，不接受外部注入以保证一致性 */
    val row: Int get() = position / 2

    /** 列号（0=左，1=右） */
    val col: Int get() = position % 2

    companion object {
        /** 矩阵固定的 4 个格子，按 position 升序 */
        val CELLS: List<MatrixCell> = entries.sortedBy { it.position }

        /** 按行分组的渲染顺序：第 0 行 [CELL_0, CELL_1]，第 1 行 [CELL_2, CELL_3] */
        val ROWS: List<List<MatrixCell>> = CELLS.groupBy { it.row }.keys.sorted()
            .map { r -> CELLS.filter { it.row == r }.sortedBy { it.col } }
    }
}

/**
 * 矩阵视图的渲染区域：4 个格子 + 1 个未排期区，共 5 个。
 *
 * 与 [MatrixCell] 分开的理由：[MatrixCell] 只描述 2×2 内的位置，
 * 而未排期区**不属于矩阵**（独立区域，不参与格子计数）。
 * UI 用本枚举作为统一的「一个区块」抽象，两种布局（2×2 / 纵向 5 区）
 * 共用同一套取数与渲染代码。
 */
enum class MatrixRegion(val quadrant: TaskQuadrant) {
    /** 未排期区：独立区域，不属于 2×2 矩阵 */
    UNSCHEDULED(TaskQuadrant.UNSCHEDULED),

    /** 左上格 = [MatrixCell.CELL_0] */
    CELL_0(TaskQuadrant.P1),

    /** 右上格 = [MatrixCell.CELL_1] */
    CELL_1(TaskQuadrant.P2),

    /** 左下格 = [MatrixCell.CELL_2] */
    CELL_2(TaskQuadrant.P3),

    /** 右下格 = [MatrixCell.CELL_3] */
    CELL_3(TaskQuadrant.P4),
    ;

    /** 是否为 2×2 矩阵内的格子（未排期区 = false） */
    val isInMatrix: Boolean get() = this != UNSCHEDULED

    /** 对应的格子；未排期区返回 null */
    val cell: MatrixCell? get() = MatrixCell.CELLS.firstOrNull { it.quadrant == quadrant }

    companion object {
        /**
         * 纵向（可用宽度 < 600dp）布局的固定顺序：
         * 未排期区在最顶部（第 1 个区域），其后按 position 升序铺开 4 格。
         */
        val VERTICAL_ORDER: List<MatrixRegion> =
            listOf(UNSCHEDULED, CELL_0, CELL_1, CELL_2, CELL_3)

        /** 5 个区域的完整集合（用于「区域不遗漏」的断言与测试） */
        val ALL: List<MatrixRegion> = entries.toList()
    }
}

/**
 * 矩阵视图的一次渲染结果。
 *
 * 组成（共 5 个区域，缺一不可，见 [MatrixRegion.ALL]）：
 * - [cells]：4 个格子（[MatrixCell.CELL_0] ~ [MatrixCell.CELL_3]），
 *   **空格子也必须存在**（值为 emptyList），否则 UI 会因 key 缺失而布局跳动
 * - [unscheduled]：未排期区（[TaskQuadrant.UNSCHEDULED]）
 *
 * ## UNSCHEDULED 的边界（强约束）
 *
 * 1. 它是**独立区域**，不参与 4 个格子的计数与排布：[cellTotal] 不含它
 * 2. **不支持拖拽**，也**不可移入/移出格子**：改变它唯一的方式是编辑任务的
 *    dueDate / priority（走既有详情页 → 编辑流程）
 * 3. 排布位置固定：2×2 布局下在矩阵上方；纵向布局下在第 1 个区域（最顶部）
 */
data class TaskMatrix(
    val cells: Map<MatrixCell, List<Task>>,
    val unscheduled: List<Task>,
) {
    init {
        // 4 格必须齐全：缺 key 说明构造方走了捷径，直接失败比 UI 静默少一格好排查
        require(cells.keys == MatrixCell.CELLS.toSet()) {
            "TaskMatrix.cells 必须包含全部 4 个格子，实际：${cells.keys}"
        }
    }

    /** 单个格子的任务列表（渲染入口，保证非 null） */
    fun tasksOf(cell: MatrixCell): List<Task> = cells.getValue(cell)

    /** 单个格子的任务数（格子顶部计数用） */
    fun countOf(cell: MatrixCell): Int = tasksOf(cell).size

    /** 4 个格子的任务总数（**不含** [unscheduled]，见类文档边界 1） */
    val cellTotal: Int get() = cells.values.sumOf { it.size }

    /** 含未排期区在内的全部未完成任务数 */
    val totalActive: Int get() = cellTotal + unscheduled.size

    /** 全部区域是否为空（矩阵空态判定） */
    val isEmpty: Boolean get() = totalActive == 0

    /** 未排期区是否为空（为空时 UI 收起该区域，不显示空壳） */
    val hasUnscheduled: Boolean get() = unscheduled.isNotEmpty()

    /**
     * 按区域取任务列表（UI 两种布局共用同一取数入口，避免各自写一遍分支）。
     */
    fun tasksOf(region: MatrixRegion): List<Task> = when (region) {
        MatrixRegion.UNSCHEDULED -> unscheduled
        MatrixRegion.CELL_0 -> tasksOf(MatrixCell.CELL_0)
        MatrixRegion.CELL_1 -> tasksOf(MatrixCell.CELL_1)
        MatrixRegion.CELL_2 -> tasksOf(MatrixCell.CELL_2)
        MatrixRegion.CELL_3 -> tasksOf(MatrixCell.CELL_3)
    }

    companion object {

        /**
         * 断点：可用宽度 < 600dp 时改用纵向 5 区布局，避免 4 格挤压。
         *
         * 600dp 是 Android 平板/大屏的常用分界值，与 Material 窗口尺寸类别对齐。
         */
        const val MATRIX_BREAKPOINT_DP = 600

        /** 矩阵布局（2×2 还是纵向 5 区）的唯一判定入口。 */
        fun isVerticalLayout(screenWidthDp: Int): Boolean =
            screenWidthDp < MATRIX_BREAKPOINT_DP

        /**
         * 纵向布局下未排期区默认展开显示的任务条数上限。
         * 超出部分折叠，由「查看全部 N 条」入口展开——避免顶部区域把 4 格挤出屏幕。
         */
        const val UNSCHEDULED_PREVIEW_COUNT = 3

        /**
         * 矩阵内的固定排序：**不跟随全局 [TaskSort]**。
         *
         * 理由：矩阵是空间布局，若每格顺序随顶部 sort 菜单变化，
         * 用户会看到格子内容跳动而找不到原因。这里给出确定、可测的顺序：
         *
         * 1. `priority.rank` 降序（URGENT > HIGH > MEDIUM > NORMAL > LOW）
         * 2. 再按 dueDate 升序，null 排最后
         * 3. 再按 title（忽略大小写）升序
         * 4. 最后按 id 升序兜底，保证完全平局时顺序稳定
         */
        val MATRIX_ORDER: Comparator<Task> =
            compareByDescending<Task> { it.priority.rank }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenComparator { a, b ->
                    String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title)
                }
                .thenBy { it.id }

        /**
         * 由既有的 [TaskGroups] 构造矩阵视图数据。
         *
         * 纯函数，无副作用、不读系统时钟（分类已在 classify 阶段完成）。
         *
         * @param groups 上游 [ObserveTasksByQuadrantUseCase.classify] 的结果；
         *               其 `completed` 部分不参与矩阵（由列表底部折叠区承载）
         */
        fun from(groups: TaskGroups): TaskMatrix {
            val cells = MatrixCell.CELLS.associateWith { cell ->
                (groups.byQuadrant[cell.quadrant] ?: emptyList()).sortedWith(MATRIX_ORDER)
            }
            val unscheduled = (groups.byQuadrant[TaskQuadrant.UNSCHEDULED] ?: emptyList())
                .sortedWith(MATRIX_ORDER)
            return TaskMatrix(cells = cells, unscheduled = unscheduled)
        }
    }
}
