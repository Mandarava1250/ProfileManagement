package com.example.personalassistant.feature.finance.data.seed

import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.profile.data.local.AccountIds

/**
 * Phase 7a：内置分类种子（Phase 3 起按账户各一套）
 *
 * - 支出分类 8 个：餐饮 / 交通 / 购物 / 娱乐 / 居住 / 医疗 / 学习 / 其他
 * - 收入分类 1 个：工资
 * - Phase 5 转账分类 1 个：转账（`type = 2`，**不在任何分类选择器里出现**，
 *   只作为转账流水 `categoryId` 外键的落点，理由见
 *   [com.example.personalassistant.feature.finance.data.local.CategoryEntity] 注释）
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
 *
 * ⚠️ Phase 5 例外：**转账分类**同时由 [com.example.personalassistant.core.database.Migration_9_10]
 * 给所有存量账户补齐。因为转账流水必须挂到一个真实存在的分类行上，
 * 老库若不补，第一次转账就会撞外键 RESTRICT 直接失败。
 * 新装走本文件的 seed，老库走迁移，两条路都用下面同一组常量，避免两处手写歪。
 */
object DefaultCategories {

    /** 转账分类名（Phase 5）；迁移 SQL 与本文件共用同一常量 */
    const val TRANSFER_CATEGORY_NAME: String = "转账"

    /** 转账分类 Material Icon 名 */
    const val TRANSFER_ICON_NAME: String = "SwapHoriz"

    /**
     * 转账分类颜色：蓝灰 `0xFF78909C`（有符号 int = -8875876）。
     *
     * 为什么暴露成常量而不是字面量：[com.example.personalassistant.core.database.Migration_9_10]
     * 里那条 `INSERT ... SELECT FROM user_profiles` 要把同一个颜色写进 SQL。
     * 两处各写一个十六进制字面量的话，改一处忘一处不会报错，只会让老库和新装
     * 的转账分类颜色悄悄不一样。
     */
    val TRANSFER_CATEGORY_COLOR: Int = 0xFF78909C.toInt()

    /**
     * 返回指定账户的内置分类 Entity 列表（id=0，由 Room autoGenerate）。
     *
     * 排序按 (type, sortOrder)：
     * - type=0 (EXPENSE)：餐饮(0), 交通(1), 购物(2), 娱乐(3), 居住(4), 医疗(5), 学习(6), 其他(7)
     * - type=1 (INCOME)：工资(0)
     * - type=[CATEGORY_TYPE_TRANSFER]：转账(0) —— 见 [transferCategory]
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
            // ===== 转账分类（Phase 5）=====
            add(transferCategory(accountId))
        }
    }

    /**
     * 单造转账分类（Phase 5）。
     *
     * 拆成单独方法是为了让**新建账户**能把分类集合补齐：
     * [com.example.personalassistant.feature.profile.data.AccountRepositoryImpl.createAccount]
     * 走的是 [build]，已经带上它；而任何只需要「补一样」的场景（例如日后发现
     * 某账户因异常缺失转账分类）可以只调本方法，不用重播 8 个收支分类。
     */
    fun transferCategory(accountId: Long): CategoryEntity = CategoryEntity(
        id = 0L,
        accountId = accountId,
        name = TRANSFER_CATEGORY_NAME,
        type = CATEGORY_TYPE_TRANSFER,
        iconName = TRANSFER_ICON_NAME,
        color = TRANSFER_CATEGORY_COLOR,
        isDefault = true,
        sortOrder = 0,
    )

    /**
     * 转账分类的 `categories.type` 取值（Phase 5）。
     *
     * ## 为什么不写 `TransactionType.TRANSFER_OUT.storageValue`
     * 转账**流水**有两个值（2=转出 / 3=转入），而转账**分类**全库只需一条
     * （两个方向的流水都挂它）。借 TRANSFER_OUT 来表达「转账分类」是语义错误，
     * 下一个人读到会以为这条分类只服务转出方向。
     *
     * 但它必须与收支分类的编号空间对齐（0=支出、1=收入 已经和
     * [TransactionType.EXPENSE] / [TransactionType.INCOME] 的 storageValue 重合），
     * 所以取 2 = 紧跟 TRANSFER_OUT。**漂移风险由测试锁住**，见
     * `DefaultCategoriesTest.转账分类 type 与流水编号空间不重叠`。
     */
    const val CATEGORY_TYPE_TRANSFER: Int = 2
}
