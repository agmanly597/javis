package com.javis.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageRole { USER, ASSISTANT, SYSTEM }
enum class MessageStatus { SENDING, SENT, ERROR }

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val isVoice: Boolean = false
)
