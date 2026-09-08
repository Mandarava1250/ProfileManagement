package com.example.personalassistant.core.di

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.personalassistant.core.database.AppDatabase
import com.example.personalassistant.core.database.DatabaseCallback
import com.example.personalassistant.core.database.DatabaseCallbackHolder
import com.example.personalassistant.core.holiday.data.HolidayDao
import com.example.personalassistant.feature.calendar.data.local.EventCategoryDao
import com.example.personalassistant.feature.calendar.data.local.EventDao
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.task.data.local.TagDao
import com.example.personalassistant.feature.task.data.local.TaskDao
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

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()

    @Provides
    fun provideTaskTagDao(db: AppDatabase): TaskTagDao = db.taskTagDao()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideFinanceDao(db: AppDatabase): FinanceDao = db.financeDao()

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
