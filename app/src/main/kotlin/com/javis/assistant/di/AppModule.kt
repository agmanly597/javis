package com.javis.assistant.di

import android.content.Context
import androidx.room.Room
import com.javis.assistant.ai.CommandParser
import com.javis.assistant.ai.DeepSeekProvider
import com.javis.assistant.ai.GroqProvider
import com.javis.assistant.data.db.JavisDatabase
import com.javis.assistant.data.db.MemoryDao
import com.javis.assistant.data.db.MessageDao
import com.javis.assistant.data.db.NotificationDao
import com.javis.assistant.memory.MemoryManager
import com.javis.assistant.storage.JavisPreferences
import com.javis.assistant.voice.AndroidTtsFallback
import com.javis.assistant.voice.ElevenLabsTts
import com.javis.assistant.voice.JavisSpeechRecognizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JavisDatabase =
        Room.databaseBuilder(context, JavisDatabase::class.java, "javis_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMessageDao(db: JavisDatabase): MessageDao = db.messageDao()
    @Provides fun provideMemoryDao(db: JavisDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideNotificationDao(db: JavisDatabase): NotificationDao = db.notificationDao()

    @Provides @Singleton
    fun provideGroqProvider(): GroqProvider = GroqProvider()

    @Provides @Singleton
    fun provideDeepSeekProvider(): DeepSeekProvider = DeepSeekProvider()

    @Provides @Singleton
    fun provideCommandParser(@ApplicationContext ctx: Context): CommandParser = CommandParser(ctx)

    @Provides @Singleton
    fun providePreferences(@ApplicationContext ctx: Context): JavisPreferences = JavisPreferences(ctx)

    @Provides @Singleton
    fun provideElevenLabsTts(@ApplicationContext ctx: Context): ElevenLabsTts = ElevenLabsTts(ctx)

    @Provides @Singleton
    fun provideAndroidTts(@ApplicationContext ctx: Context): AndroidTtsFallback = AndroidTtsFallback(ctx)

    @Provides @Singleton
    fun provideSpeechRecognizer(@ApplicationContext ctx: Context): JavisSpeechRecognizer = JavisSpeechRecognizer(ctx)

    @Provides @Singleton
    fun provideMemoryManager(memoryDao: MemoryDao): MemoryManager = MemoryManager(memoryDao)
}
