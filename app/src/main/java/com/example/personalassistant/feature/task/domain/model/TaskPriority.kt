package com.example.personalassistant.feature.task.domain.model

/**
 * 任务优先级（Phase 8 Sub-step 2：扩展为 5 档）
 *
 * 数据库存储使用 Int：
 * - 0=低, 1=中, 2=高 —— 旧 3 档，存量数据保持原语义不迁移
 * - 3=普通, 4=紧急 —— 新增档位
 *
 * ⚠️ dbValue 与 UI 排序权重（rank）解耦：
 * - dbValue 是稳定的枚举序号（数据库兼容）
 * - rank 是 UI 排序权重（语义顺序）
 *
 * 排序语义：URGENT > HIGH > MEDIUM > NORMAL > LOW
 *
 * 标签 label 同时给 UI 显示和向后兼容（历史文案不变）。
 */
enum class TaskPriority(
    val dbValue: Int,
    val label: String,
    val rank: Int,
) {
    LOW(0, "低", rank = 1),
    MEDIUM(1, "中", rank = 5),
    HIGH(2, "高", rank = 7),
    NORMAL(3, "普通", rank = 3),
    URGENT(4, "紧急", rank = 9);

    companion object {
        /**
         * 从 DB int 加载。
         * - 0 / 1 / 2 → 旧 3 档，向后兼容存量数据
         * - 3 / 4     → 新档位
         * - 越界 → 默认 MEDIUM
         */
        fun fromDb(value: Int): TaskPriority =
            entries.firstOrNull { it.dbValue == value } ?: MEDIUM
    }
}
