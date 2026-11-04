package com.example.personalassistant.feature.task.presentation.list

import kotlin.math.abs

/**
 * Phase 24：拖拽排序的**命中判定**（纯函数）。
 *
 * 规则：**拖过半行就换位** —— 位移的绝对值超过目标行高度的一半才算越过。
 *
 * ## 为什么要抽成纯函数
 *
 * 这条规则很容易在重构里被改坏。老实现（Phase 6a）把「项自身高度的一半」
 * 当成了视口坐标传给调用方，于是所有行的命中窗口完全重叠，
 * 命中判定退化成「位移超过一整行高度才换位」。
 * 抽成纯函数之后可以用 JVM 单测把规则钉死。
 *
 * 与清单侧（[com.example.personalassistant.feature.task.presentation.ChecklistTab]）
 * 的「拖过半行就换位」是同一套逻辑。
 */
internal object ReorderHitResolver {

    /**
     * 位移是否已经越过目标行。
     *
     * @param dragOffsetY 累计 Y 位移（像素，向下为正、向上为负）
     * @param targetHeight 目标行的高度（像素）
     * @return true = 越过（该换位了）
     */
    fun shouldSwap(dragOffsetY: Float, targetHeight: Int): Boolean =
        targetHeight > 0 && abs(dragOffsetY) > targetHeight / 2f
}
