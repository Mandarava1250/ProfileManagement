# Phase 8 真跑验证证据

> **用途**：归档 Phase 8 一口气收口阶段（T1~T7）的真机/AVD 验证产物。
> **原则**：每份产物对应一次 `connectedDebugAndroidTest` 或 `adb` 验证，
> 提交 hash 在 DEV_LOG 同步登记。**不虚标 ✅，标了 ✅ 就必须能在本目录找到对应证据**。

---

## 目录索引

| 任务 | 文件 | 验证类型 | 时间戳 | 备注 |
|---|---|---|---|---|
| T1 (8c.1) | `t1-migration-9-10-result.xml` | instrumented | 2026-09-15 11:06 | 9→10 迁移在 AVD 真跑通过 |
| T1 (8c.1) | `t1-gradle-run.log` | gradle | 2026-09-15 11:06 | BUILD SUCCESSFUL in 46s |

---

## T1 · Migration_9_10 真跑（8c.1）

### 验证目标
[Migration_9_10]（跨账户转账 + 账户余额，Phase 5 工作产物）在真实 SQLite 上按序执行成功，
结果符合 v10 Room schema 校验。

### 验证结果（XML 摘要）
```
testsuite name="com.example.personalassistant.core.database.AppDatabaseMigrationsTest"
           tests="4" failures="0" errors="0" skipped="0" time="3.985"

testcase name="migrationIsSafeForFreshV8DatabaseWithoutAnyTransactions"        time="0.789"
testcase name="migrate9To10ValidatesSchemaAndAddsTransferFields"               time="0.463"
testcase name="migrate8To9ValidatesSchemaAndKeepsLegacyRowsOnDefaultAccount"  time="0.487"
testcase name="migrate9To10IsSafeForFreshV9DatabaseWithoutAnyTransfer"        time="0.259"
```

### 关键结论
- **8→9 迁移 + 9→10 迁移** 在 AVD `pm_ui36`（API 36, x86_64）真跑通过
- 验证项目全部覆盖：
  - 迁移后 schema 与导出的 10.json 完全一致
  - 旧 v9 数据无损落 default account
  - `user_profiles.initialBalance` 默认 0
  - `transactions.transferAccountId` / `transferPairId` 默认 NULL
  - Entity 声明的复合索引 `index_transactions_accountId_type` 建立
  - 默认账户被补 type=2 转账分类（来自 `DefaultCategories`）
  - PRAGMA foreign_key_check 收尾守卫无违规

### 关联 commit
- 测试代码：`feature/phase-8-verification` 分支 `bc92a55`（test）+ `19d384a`（fix 适配）
- 验证证据：本目录 `t1-migration-9-10-result.xml` + `t1-gradle-run.log`