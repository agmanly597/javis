package com.javis.assistant.data.repository

import android.content.Context
import com.javis.assistant.ai.AiProviderFactory
import com.javis.assistant.ai.JAVIS_SYSTEM_PROMPT_BASE
import com.javis.assistant.data.local.dao.ChatDao
import com.javis.assistant.data.local.entities.ChatMessageEntity
import com.javis.assistant.domain.model.ChatMessage
import com.javis.assistant.domain.model.MessageRole
import com.javis.assistant.domain.repository.ChatRepository
import com.javis.assistant.domain.repository.SettingsRepository
import com.javis.assistant.utils.AppLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val settingsRepository: SettingsRepository,
    private val aiProviderFactory: AiProviderFactory,
    @ApplicationContext private val context: Context
) : ChatRepository {

    override fun getMessages(): Flow<List<ChatMessage>> =
        chatDao.getMessages().map { it.map { e -> e.toDomain() } }

    override suspend fun insertMessage(message: ChatMessage) =
        chatDao.insertMessage(message.toEntity())

    override suspend fun clearHistory() = chatDao.clearAll()

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
            return Result.failure(
                IllegalStateException("No API key for ${settings.provider.displayName}. Add it in Settings.")
            )
        }
        val provider = aiProviderFactory.create(settings.provider, apiKey)
        val systemPrompt = buildDynamicPrompt(settings.userName)
        return provider.generateResponse(history, systemPrompt)
    }

    /**
     * Builds a fully dynamic system prompt that includes:
     * 1. JAVIS base personality
     * 2. The user's name if known
     * 3. ALL apps currently installed on the device — so "open OPay" always works
     */
    private fun buildDynamicPrompt(userName: String): String {
        val appList = try {
            AppLauncher.buildAppLabelList(context)
        } catch (_: Exception) { "" }

        return buildString {
            append(JAVIS_SYSTEM_PROMPT_BASE)
            if (userName.isNotBlank()) {
                append("\n\n══ USER INFO ══\nThe user's name is $userName. Always use it warmly but not robotically.")
            }
            if (appList.isNotBlank()) {
                append("\n\n══ INSTALLED APPS ON THIS DEVICE ══")
                append("\n$appList")
                append("\n\nWhen the user asks to open any app — including apps like OPay, Kuda, PalmPay, GTBank, Moniepoint, Baxibox, or any other — search the list above. If found, use LAUNCH_APP with the package name in parentheses. If not found, say so and suggest the Play Store.")
            }
        }
    }

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id, content = content, role = MessageRole.valueOf(role), timestamp = timestamp
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = id, content = content, role = role.name, timestamp = timestamp
    )
}
