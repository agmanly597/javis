package com.javis.assistant.di

import com.javis.assistant.data.db.NotificationDao
import com.javis.assistant.notifications.NotificationPersistenceWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    /**
     * NotificationPersistenceWorker does NOT have @Inject constructor
     * so we provide it here. It starts the notification collection coroutine
     * as soon as it is instantiated.
     */
    @Provides
    @Singleton
    fun provideNotificationPersistenceWorker(
        notificationDao: NotificationDao
    ): NotificationPersistenceWorker = NotificationPersistenceWorker(notificationDao)
}
