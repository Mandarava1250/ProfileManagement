package com.example.personalassistant.core.backup.snapshot

import com.example.personalassistant.core.backup.diff.CurrentDbSnapshot

/**
 * Phase 9 A2：备份当前快照的最小契约接口
 *
 * ## 为什么从 [BackupCurrentSnapshotProvider]（open class）抽出
 * - `BackupCurrentSnapshotProvider` 依赖 9 个 DAO + 1 个 [androidx.room.RoomDatabase]，
 *   父类 `RoomDatabase` 构造函数会调 `createInvalidationTracker()` / `createOpenHelper()` 等抽象方法
 * - 测试想 fake 它需要覆盖所有 abstract + 让父类构造不炸 → whack-a-mole
 * - 抽出接口后，VM 只依赖这一个方法；测试可以写个 3 行的 `object : BackupSnapshotProvider`
 *
 * ## 与原 open class 的关系
 * - [BackupCurrentSnapshotProvider] 仍是 `open class @Inject @Singleton`，实现这个接口
 * - Hilt 模块 [com.example.personalassistant.core.backup.di.BackupModule] 用 `@Binds` 绑接口到实现
 * - 生产代码不需改业务逻辑，只是把 VM 的依赖从具体类换成接口
 */
interface BackupSnapshotProvider {
    /**
     * 拉取当前数据库 13 张表的全量快照。
     *
     * 必须在 IO 线程调用（包含 13 次 DB 读）；调用方负责选择协程上下文。
     */
    suspend fun snapshot(): CurrentDbSnapshot
}
