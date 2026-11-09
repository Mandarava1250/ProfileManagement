package com.example.personalassistant.service.reminder

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 提醒模块 Hilt 绑定
 *
 * 目前只有课程提醒重排这一条（[CourseReminderRescheduler]）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    @Singleton
    abstract fun bindCourseReminderRescheduler(
        impl: DefaultCourseReminderRescheduler,
    ): CourseReminderRescheduler
}
