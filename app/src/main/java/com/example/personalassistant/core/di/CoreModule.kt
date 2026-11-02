package com.example.personalassistant.core.di

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.database.DatabaseCallback
import com.example.personalassistant.core.database.DatabaseCallbackHolder
import com.example.personalassistant.core.holiday.data.HolidayDao
import com.example.personalassistant.feature.calendar.data.local.EventCategoryDao
import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.me.data.local.StudentProfileDao
import com.example.personalassistant.feature.profile.data.local.UserProfileDao
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TaskDao
import com.example.personalassistant.feature.task.data.local.TaskListDao
import com.example.personalassistant.feature.task.data.local.TaskTagDao
import com.example.personalassistant.feature.timetable.data.local.CourseDao
import com.example.personalassistant.feature.timetable.data.local.SemesterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 全局依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    // ========== 调度器 ==========

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    // ========== 系统服务 ==========

    /**
     * Phase 5：AlarmManager 由 Hilt 注入给 AlarmManagerReminderScheduler。
     * 用 @ApplicationContext 取系统服务，避免内存泄漏。
     */
    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ========== 数据库 ==========

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        holder: DatabaseCallbackHolder,
    ): AppDatabase {
        // 1. 拿 callback（Hilt @Singleton 注入的是同一个实例）
        val callback = holder.callback

        // 2. 构建 db，绑定 callback
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .addCallback(callback)
            // 性能优化：显式声明 WAL 日志模式。
            //
            // Room 2.x 的 AUTOMATIC 在 API 16+ 本来就会选 WAL，所以这一步**不改变实际行为**，
            // 写出来只是把「靠框架默认值」变成「代码里写明」——
            // 读不阻塞写、崩溃后不易丢数据这两条收益以后不会因为有人改 builder 而悄悄丢掉。
            //
            // ⚠️ 本项目不启用多进程（没开 enableMultiInstanceInvalidation），
            // 备份走的是 JSON 导出/导入而非拷贝 db 文件，所以 WAL 的 -wal/-shm 副产物不影响备份。
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

        // 3. db 建好之后回填给 callback，让 callback.onCreate 能访问 dao
        callback.attachDatabase(db)
        return db
    }

    @Provides
    fun provideCourseDao(db: AppDatabase): CourseDao = db.courseDao()

    @Provides
    fun provideSemesterDao(db: AppDatabase): SemesterDao = db.semesterDao()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    /** Phase 17：清单 DAO（任务的归属地）。 */
    @Provides
    fun provideTaskListDao(db: AppDatabase): TaskListDao = db.taskListDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    fun provideTaskTagDao(db: AppDatabase): TaskTagDao = db.taskTagDao()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideFinanceDao(db: AppDatabase): FinanceDao = db.financeDao()

    /**
     * Phase 12：「我」档案 DAO 绑定（与其它 DAO 模式一致）。
     */
    @Provides
    fun provideStudentProfileDao(db: AppDatabase): StudentProfileDao = db.studentProfileDao()

    /**
     * Phase 8 Sub-step 7：节假日 DAO 绑定（与其它 DAO 模式一致）。
     */
    @Provides
    fun provideHolidayDao(db: AppDatabase): HolidayDao = db.holidayDao()

    /**
     * Phase 8 Sub-step 9：事件分类 DAO 绑定（与其它 DAO 模式一致）。
     */
    @Provides
    fun provideEventCategoryDao(db: AppDatabase): EventCategoryDao = db.eventCategoryDao()

    /**
     * Phase 3：账户 DAO 绑定（与其它 DAO 同款 @Provides，从 AppDatabase 取实例）。
     *
     * 注：计划稿里写过「Hilt 通过 @DatabaseView 暴露」—— @DatabaseView 是只读 SQL 视图注解，
     * 与 Hilt 无关，那样写会编译失败。按现有 9 个 DAO 的一致做法走。
     */
    @Provides
    fun provideUserProfileDao(db: AppDatabase): UserProfileDao = db.userProfileDao()

    // ========== DataStore（SettingsRepository 用）==========

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("settings") }
    )
}

// ========== Dispatcher 限定符 ==========

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MainDispatcher
