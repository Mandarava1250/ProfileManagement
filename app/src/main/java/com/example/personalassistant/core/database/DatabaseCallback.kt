package com.example.personalassistant.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.personalassistant.core.holiday.model.PresetHolidays
import com.example.personalassistant.feature.calendar.data.seed.DefaultEventCategories
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.seed.DefaultCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7a + 8 Sub-step 7：数据库首次创建时的初始化回调
 *
 * - onCreate：数据库文件首次创建（即首次安装）时插入默认数据
 *   - finance 内置分类（8 支出 + 1 收入）
 *   - 节假日预置数据 2025 + 2026（Phase 8 Sub-step 7 新增）
 *
 * - onOpen / onDestructiveMigrate：暂不做事，留扩展点
 *
 * ## 设计动机
 * - 用户首次打开 Finance tab 看到空分类列表会一脸懵，"快速记账"体验差
 * - 用 Room Callback + IO 协程异步 seed，不阻塞 UI 启动
 *
 * ## 依赖注入说明
 * - callback 在 Room.build() 时被传入；那时 AppDatabase 还没建好，不能直接 inject
 * - 解法：CoreModule.provideAppDatabase 里先构建 holder，db 建好后 set back
 *   callback 真正 onCreate 时再从 holder 拿 db
 *
 * ## 重要约定
 * - 此回调只在数据库**首次创建**时触发，不要往里写"应用每次启动都要跑的逻辑"
 *   （那种应该放在 PersonalAssistantApp.onCreate() 里）
 */
@Singleton
class DatabaseCallback @Inject constructor() : RoomDatabase.Callback() {

    private var database: AppDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 由 CoreModule 在 db.build() 完成后回填 */
    fun attachDatabase(db: AppDatabase) {
        database = db
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // 数据库首次创建时，异步插入默认数据
        scope.launch {
            seedDefaultCategories()
            seedEventCategories()
            seedHolidays()
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // 暂不做事；扩展点：未来可在这里检查 schema 版本兼容性等
    }

    private suspend fun seedDefaultCategories() {
        val db = database ?: return  // 极端兜底：attachDatabase 还没跑
        val financeDao: FinanceDao = db.financeDao()
        // onCreate 只在数据库首次创建时触发，此时 categories 表为空；
        // 直接 REPLACE 插入即可（不存在冲突目标）。
        // 若未来需要迁移时也 seed，请改用 OnConflictStrategy.IGNORE 风格的 DAO 方法。
        DefaultCategories.build().forEach { entity ->
            financeDao.insertCategory(entity)
        }
    }

    /**
     * Phase 8 Sub-step 9：日历事件分类预置数据 seed。
     *
     * - [DefaultEventCategories.build] 含 8 个内置分类（工作/学习/生活/健康/财务/社交/生日/其他）
     * - EventCategoryDao 用 OnConflictStrategy.IGNORE，迁移场景安全
     * - 首次安装时 event_categories 表为空 → 全部插入
     */
    private suspend fun seedEventCategories() {
        val db = database ?: return
        db.eventCategoryDao().insertAll(DefaultEventCategories.build())
    }

    /**
     * Phase 8 Sub-step 7：节假日预置数据 seed。
     *
     * - [PresetHolidays.PRESETS] 含 2025 + 2026 两年完整节假日（农历 + 公历 + 调休）
     * - HolidayDao 用 OnConflictStrategy.IGNORE，已有数据会被跳过，迁移场景安全
     * - 首次安装时 holidays 表为空 → 全部插入
     */
    private suspend fun seedHolidays() {
        val db = database ?: return
        db.holidayDao().insertAll(PresetHolidays.PRESETS)
    }
}

/**
 * Hilt 注入的中间 holder：让 [DatabaseCallback] 能拿到 AppDatabase
 * 而不破坏 Room.build() 的时序约束。
 *
 * Hilt @Inject 构造时拿到 DatabaseCallback singleton 实例，存进来供 CoreModule 读取。
 */
@Singleton
class DatabaseCallbackHolder @Inject constructor(
    val callback: DatabaseCallback,
)
