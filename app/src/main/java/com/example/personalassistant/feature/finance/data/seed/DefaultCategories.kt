package com.example.personalassistant.feature.finance.data.seed

import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.profile.data.local.AccountIds

/**
 * Phase 7a：内置分类种子（Phase 3 起按账户各一套）
 *
 * - 支出分类 8 个：餐饮 / 交通 / 购物 / 娱乐 / 居住 / 医疗 / 学习 / 其他
 * - 收入分类 1 个：工资
 *
 * 设计原则：
 * - 与日常用户使用场景对齐（参考说明书 5.3 记账模块）
 * - 内置分类 `isDefault = true`，UI 上不可删除，可改名
 * - 颜色采用 Material 调色板（与 tag palette 一致：pink/purple/blue/green/orange/grey）
 * - iconName 用 Material Icon 名称字符串，运行时由 UI IconRegistry 解析成 ImageVector
 *
 * ## Phase 3：为什么 build 要收 accountId
 * 分类是**每个账户一套**（多账户隔离方案的代价）：
 * - `id` 由 Room autoGenerate，跨账户会重复，必须靠 accountId 区分；
 * - 用户在新账户里改「餐饮」的名字，不应影响默认账户的同名分类。
 *
 * 触发时机：
 * 1. [com.example.personalassistant.core.database.DatabaseCallback.onCreate] —— 首次安装，
 *    给默认账户（[AccountIds.DEFAULT_ACCOUNT_ID]）seed 一套；
 * 2. 账户管理 UI 里**新建账户**时 —— 给新账户 seed 一套。
 *
 * 二次启动（onUpgrade）不会重复 seed，避免覆盖用户后续修改。
 */
object DefaultCategories {

    /**
     * 返回指定账户的内置分类 Entity 列表（id=0，由 Room autoGenerate）。
     *
     * 排序按 (type, sortOrder)：
     * - type=0 (EXPENSE)：餐饮(0), 交通(1), 购物(2), 娱乐(3), 居住(4), 医疗(5), 学习(6), 其他(7)
     * - type=1 (INCOME)：工资(0)
     */
    fun build(accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID): List<CategoryEntity> {
        return buildList {
            // ===== 支出分类 =====
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "餐饮",
                    type = TransactionType.EXPENSE.storageValue,
                    iconName = "Restaurant",
                    color = 0xFFFF8A65.toInt(), // 暖橙红
                    isDefault = true,
                    sortOrder = 0,
                )
            )
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "交通",
                    type = TransactionType.EXPENSE.storageValue,
                    iconName = "DirectionsBus",
                    color = 0xFF64B5F6.toInt(), // 蓝
                    isDefault = true,
                    sortOrder = 1,
                )
            )
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "购物",
                    type = TransactionType.EXPENSE.storageValue,
                    iconName = "ShoppingCart",
                    color = 0xFFFF69B4.toInt(), // 粉
                    isDefault = true,
                    sortOrder = 2,
                )
            )
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "娱乐",
                    type = TransactionType.EXPENSE.storageValue,
                    iconName = "SportsEsports",
                    color = 0xFFB388FF.toInt(), // 紫
                    isDefault = true,
                    sortOrder = 3,
                )
            )
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "居住",
                    type = TransactionType.EXPENSE.storageValue,
                    iconName = "Home",
                    color = 0xFF81C784.toInt(), // 绿
                    isDefault = true,
                    sortOrder = 4,
                )
            )
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "医疗",
                    type = TransactionType.EXPENSE.storageValue,
                    iconName = "LocalHospital",
                    color = 0xFFE57373.toInt(), // 浅红
                    isDefault = true,
                    sortOrder = 5,
                )
            )
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "学习",
                    type = TransactionType.EXPENSE.storageValue,
                    iconName = "MenuBook",
                    color = 0xFFFFB74D.toInt(), // 橙
                    isDefault = true,
                    sortOrder = 6,
                )
            )
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "其他",
                    type = TransactionType.EXPENSE.storageValue,
                    iconName = "Category",
                    color = 0xFF9E9E9E.toInt(), // 灰
                    isDefault = true,
                    sortOrder = 7,
                )
            )
            // ===== 收入分类 =====
            add(
                CategoryEntity(
                    id = 0L,
                    accountId = accountId,
                    name = "工资",
                    type = TransactionType.INCOME.storageValue,
                    iconName = "AccountBalanceWallet",
                    color = 0xFF4DB6AC.toInt(), // 青绿
                    isDefault = true,
                    sortOrder = 0,
                )
            )
        }
    }
}
