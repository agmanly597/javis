package com.javis.assistant.di

import android.content.Context
import androidx.room.Room
import com.javis.assistant.data.local.JavisDatabase
import com.javis.assistant.data.local.dao.ChatDao
import com.javis.assistant.data.local.dao.MemoryDao
import com.javis.assistant.data.local.dao.NotificationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JavisDatabase {
        return Room.databaseBuilder(
            context,
            JavisDatabase::class.java,
            "javis_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideChatDao(db: JavisDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMemoryDao(db: JavisDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideNotificationDao(db: JavisDatabase): NotificationDao = db.notificationDao()
}
