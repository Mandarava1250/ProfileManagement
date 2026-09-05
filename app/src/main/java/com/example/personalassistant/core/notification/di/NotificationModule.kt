package com.example.personalassistant.core.notification.di

import com.example.personalassistant.core.notification.AndroidNotificationPermissionRequester
import com.example.personalassistant.core.notification.NotificationPermissionRequester
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 5：通知权限绑定到 [AndroidNotificationPermissionRequester]。
 *
 * 单测可在测试 module 里 @Binds 替换为 FakeNotificationPermissionRequester。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindNotificationPermissionRequester(
        impl: AndroidNotificationPermissionRequester
    ): NotificationPermissionRequester
}
