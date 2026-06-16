package com.javis.assistant.domain.repository

import com.javis.assistant.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessages(): Flow<List<ChatMessage>>
    suspend fun insertMessage(message: ChatMessage)
    suspend fun clearHistory()
    suspend fun sendMessage(
        userMessage: String,
        history: List<ChatMessage>
    ): Result<String>
}
