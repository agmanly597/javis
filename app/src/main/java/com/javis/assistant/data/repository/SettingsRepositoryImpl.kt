package com.javis.assistant.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.javis.assistant.domain.model.AiProvider
import com.javis.assistant.domain.model.AiSettings
import com.javis.assistant.domain.repository.SettingsRepository
import com.javis.assistant.voice.ElevenLabsTtsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        val KEY_PROVIDER = stringPreferencesKey("ai_provider")
        val KEY_GROQ_KEY = stringPreferencesKey("groq_api_key")
        val KEY_DEEPSEEK_KEY = stringPreferencesKey("deepseek_api_key")
        val KEY_ELEVENLABS_KEY = stringPreferencesKey("elevenlabs_api_key")
        val KEY_ELEVENLABS_VOICE = stringPreferencesKey("elevenlabs_voice_id")
        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_CONTINUOUS = booleanPreferencesKey("continuous_mode")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
    }

    override fun getSettings(): Flow<AiSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            AiSettings(
                provider = prefs[KEY_PROVIDER]?.let {
                    runCatching { AiProvider.valueOf(it) }.getOrDefault(AiProvider.GROQ)
                } ?: AiProvider.GROQ,
                groqApiKey = prefs[KEY_GROQ_KEY] ?: "",
                deepSeekApiKey = prefs[KEY_DEEPSEEK_KEY] ?: "",
                elevenLabsApiKey = prefs[KEY_ELEVENLABS_KEY] ?: "",
                elevenLabsVoiceId = prefs[KEY_ELEVENLABS_VOICE] ?: ElevenLabsTtsService.DEFAULT_VOICE,
                speechRate = prefs[KEY_SPEECH_RATE] ?: 0.93f,
                ttsVoice = prefs[KEY_TTS_VOICE] ?: "",
                notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
                continuousMode = prefs[KEY_CONTINUOUS] ?: true,
                userName = prefs[KEY_USER_NAME] ?: ""
            )
        }

    override suspend fun updateProvider(provider: AiProvider) {
        dataStore.edit { it[KEY_PROVIDER] = provider.name }
    }

    override suspend fun updateGroqApiKey(key: String) {
        dataStore.edit { it[KEY_GROQ_KEY] = key }
    }

    override suspend fun updateDeepSeekApiKey(key: String) {
        dataStore.edit { it[KEY_DEEPSEEK_KEY] = key }
    }

    override suspend fun updateElevenLabsApiKey(key: String) {
        dataStore.edit { it[KEY_ELEVENLABS_KEY] = key }
    }

    override suspend fun updateElevenLabsVoiceId(id: String) {
        dataStore.edit { it[KEY_ELEVENLABS_VOICE] = id }
    }

    override suspend fun updateSpeechRate(rate: Float) {
        dataStore.edit { it[KEY_SPEECH_RATE] = rate }
    }

    override suspend fun updateTtsVoice(voice: String) {
        dataStore.edit { it[KEY_TTS_VOICE] = voice }
    }

    override suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    override suspend fun updateContinuousMode(enabled: Boolean) {
        dataStore.edit { it[KEY_CONTINUOUS] = enabled }
    }

    override suspend fun updateUserName(name: String) {
        dataStore.edit { it[KEY_USER_NAME] = name }
    }
}
