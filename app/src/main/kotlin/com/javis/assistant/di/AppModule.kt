package com.javis.assistant.di

import android.content.Context
import androidx.room.Room
import com.javis.assistant.data.db.JavisDatabase
import com.javis.assistant.data.db.MemoryDao
import com.javis.assistant.data.db.MessageDao
import com.javis.assistant.data.db.NotificationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides only what Hilt cannot construct automatically.
 * Classes annotated with @Inject constructor are handled by Hilt directly —
 * do NOT add @Provides for those or Hilt will throw a duplicate binding error.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JavisDatabase =
        Room.databaseBuilder(context, JavisDatabase::class.java, "javis_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMessageDao(db: JavisDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryDao(db: JavisDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideNotificationDao(db: JavisDatabase): NotificationDao = db.notificationDao()
}
