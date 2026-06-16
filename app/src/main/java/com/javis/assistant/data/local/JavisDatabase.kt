package com.javis.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.javis.assistant.data.local.dao.ChatDao
import com.javis.assistant.data.local.dao.MemoryDao
import com.javis.assistant.data.local.dao.NotificationDao
import com.javis.assistant.data.local.entities.ChatMessageEntity
import com.javis.assistant.data.local.entities.NotificationEntity
import com.javis.assistant.data.local.entities.UserMemoryEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        UserMemoryEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class JavisDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
    abstract fun notificationDao(): NotificationDao
}
