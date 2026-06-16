package com.javis.assistant.domain.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false
)

enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

data class UserMemory(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val category: MemoryCategory,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MemoryCategory {
    PERSONAL, PREFERENCE, APP_USAGE, COMMAND, GENERAL
}

data class NotificationItem(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class AiSettings(
    val provider: AiProvider = AiProvider.GROQ,
    val groqApiKey: String = "",
    val deepSeekApiKey: String = "",
    val speechRate: Float = 1.0f,
    val ttsVoice: String = "",
    val notificationsEnabled: Boolean = true,
    val continuousMode: Boolean = false,
    val userName: String = ""
)

enum class AiProvider(val displayName: String) {
    GROQ("Groq"),
    DEEPSEEK("DeepSeek")
}

data class AppAction(
    val intent: String,
    val packageName: String? = null,
    val action: String? = null
)
