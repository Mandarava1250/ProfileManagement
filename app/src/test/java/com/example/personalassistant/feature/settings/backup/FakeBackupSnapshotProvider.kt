package com.example.personalassistant.feature.settings.backup

import com.example.personalassistant.core.backup.diff.CurrentDbSnapshot
import com.example.personalassistant.core.backup.snapshot.BackupSnapshotProvider

/**
 * Phase 10 10a-1：备份快照 Provider 的 Fake
 *
 * 与生产实现 [com.example.personalassistant.core.backup.snapshot.BackupCurrentSnapshotProvider] 不同，
 * 本类不读 9 个 DAO，直接返回测试预设的 [CurrentDbSnapshot]，让 VM 的 diff 阶段拿到可控输入。
 *
 * ## 用法
 * - 默认 `snapshotValue = CurrentDbSnapshot()`（13 张表全空，diff 结果 13 个全空 TableDiff）
 * - 测试可调 [setSnapshot] 注入特定快照（验证 toAdd / toOverwrite / FK 级联）
 *
 * ## 设计取舍
 * - 不继承 `open class BackupCurrentSnapshotProvider` —— 后者继承 RoomDatabase，构造期会触发
 *   `createInvalidationTracker()` 等抽象方法，JVM 单测无法 new。
 * - 实现 [BackupSnapshotProvider] 接口（VM 真正依赖的就是这个接口）。
 */
class FakeBackupSnapshotProvider : BackupSnapshotProvider {

    private var snapshotValue: CurrentDbSnapshot = CurrentDbSnapshot()

    /** snapshot 被调用的次数（用于断言「load 时是否拉了快照」） */
    var snapshotCallCount: Int = 0
        private set

    override suspend fun snapshot(): CurrentDbSnapshot {
        snapshotCallCount++
        return snapshotValue
    }

    fun setSnapshot(value: CurrentDbSnapshot) {
        snapshotValue = value
    }
}