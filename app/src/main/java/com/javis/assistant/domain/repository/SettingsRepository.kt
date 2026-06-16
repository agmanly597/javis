package com.javis.assistant.domain.repository

import com.javis.assistant.domain.model.AiProvider
import com.javis.assistant.domain.model.AiSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<AiSettings>
    suspend fun updateProvider(provider: AiProvider)
    suspend fun updateGroqApiKey(key: String)
    suspend fun updateDeepSeekApiKey(key: String)
    suspend fun updateSpeechRate(rate: Float)
    suspend fun updateTtsVoice(voice: String)
    suspend fun updateNotificationsEnabled(enabled: Boolean)
    suspend fun updateContinuousMode(enabled: Boolean)
    suspend fun updateUserName(name: String)
}
