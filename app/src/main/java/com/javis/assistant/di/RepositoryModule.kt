package com.javis.assistant.di

import com.javis.assistant.data.repository.ChatRepositoryImpl
import com.javis.assistant.data.repository.MemoryRepositoryImpl
import com.javis.assistant.data.repository.NotificationRepositoryImpl
import com.javis.assistant.data.repository.SettingsRepositoryImpl
import com.javis.assistant.domain.repository.ChatRepository
import com.javis.assistant.domain.repository.MemoryRepository
import com.javis.assistant.domain.repository.NotificationRepository
import com.javis.assistant.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
