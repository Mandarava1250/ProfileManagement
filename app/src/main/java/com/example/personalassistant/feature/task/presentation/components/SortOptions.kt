package com.example.personalassistant.feature.task.presentation.components

import com.example.personalassistant.feature.task.domain.model.TaskSort

/**
 * 排序选项的中文标签。
 *
 * Phase 14 抽出来的：原先 [SortMenu] 把 label 写死在自己文件里，
 * 筛选面板（[TaskFilterSheet]）要显示同一组选项时只能再抄一遍 ——
 * 两处文案一旦不同步，菜单和面板就会对不上。
 */
fun TaskSort.displayLabel(): String = when (this) {
    TaskSort.PRIORITY_DUE_ASC -> "优先级 + 截止日期"
    TaskSort.DUE_DATE_ASC -> "截止日期（早 → 晚）"
    TaskSort.DUE_DATE_DESC -> "截止日期（晚 → 早）"
    TaskSort.CREATED_DESC -> "创建时间（新 → 旧）"
    TaskSort.TITLE_ASC -> "标题（A → Z）"
    TaskSort.MANUAL_ORDER -> "自定义顺序（拖拽）"
}
