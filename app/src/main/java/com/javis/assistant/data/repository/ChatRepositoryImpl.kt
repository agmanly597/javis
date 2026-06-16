package com.javis.assistant.data.repository

import com.javis.assistant.ai.AiProviderFactory
import com.javis.assistant.ai.JAVIS_SYSTEM_PROMPT
import com.javis.assistant.data.local.dao.ChatDao
import com.javis.assistant.data.local.entities.ChatMessageEntity
import com.javis.assistant.domain.model.ChatMessage
import com.javis.assistant.domain.model.MessageRole
import com.javis.assistant.domain.repository.ChatRepository
import com.javis.assistant.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val settingsRepository: SettingsRepository,
    private val aiProviderFactory: AiProviderFactory
) : ChatRepository {

    override fun getMessages(): Flow<List<ChatMessage>> {
        return chatDao.getMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertMessage(message: ChatMessage) {
        chatDao.insertMessage(message.toEntity())
    }

    override suspend fun clearHistory() {
        chatDao.clearAll()
    }

    override suspend fun sendMessage(
        userMessage: String,
        history: List<ChatMessage>
    ): Result<String> {
        val settings = settingsRepository.getSettings().first()
        val apiKey = when (settings.provider) {
            com.javis.assistant.domain.model.AiProvider.GROQ -> settings.groqApiKey
            com.javis.assistant.domain.model.AiProvider.DEEPSEEK -> settings.deepSeekApiKey
        }
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("API key not configured for ${settings.provider.displayName}. Please add your API key in Settings."))
        }
        val provider = aiProviderFactory.create(settings.provider, apiKey)
        val systemPrompt = buildString {
            append(JAVIS_SYSTEM_PROMPT)
            if (settings.userName.isNotBlank()) {
                append("\n\nThe user's name is ${settings.userName}.")
            }
        }
        return provider.generateResponse(history, systemPrompt)
    }

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        content = content,
        role = MessageRole.valueOf(role),
        timestamp = timestamp
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = id,
        content = content,
        role = role.name,
        timestamp = timestamp
    )
}
