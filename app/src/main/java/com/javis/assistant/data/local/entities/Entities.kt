package com.javis.assistant.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val content: String,
    val role: String,
    val timestamp: Long
)

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val category: String,
    val timestamp: Long
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val isRead: Boolean
)
