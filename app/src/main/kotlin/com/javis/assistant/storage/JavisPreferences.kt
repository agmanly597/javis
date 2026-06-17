package com.javis.assistant.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.javis.assistant.data.model.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "javis_prefs")

@Singleton
class JavisPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.dataStore

    companion object {
        val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val KEY_ELEVENLABS_API_KEY = stringPreferencesKey("elevenlabs_api_key")
        val KEY_DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        val KEY_ELEVENLABS_VOICE_ID = stringPreferencesKey("elevenlabs_voice_id")
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        val KEY_USE_ELEVENLABS = booleanPreferencesKey("use_elevenlabs")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val KEY_USER_ONBOARDED = booleanPreferencesKey("user_onboarded")
        val KEY_CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode")
    }

    val groqApiKey: Flow<String> = ds.data.map { it[KEY_GROQ_API_KEY] ?: "" }
    val elevenLabsApiKey: Flow<String> = ds.data.map { it[KEY_ELEVENLABS_API_KEY] ?: "" }
    val deepSeekApiKey: Flow<String> = ds.data.map { it[KEY_DEEPSEEK_API_KEY] ?: "" }
    val elevenLabsVoiceId: Flow<String> = ds.data.map { it[KEY_ELEVENLABS_VOICE_ID] ?: "" }
    val aiProvider: Flow<AiProvider> = ds.data.map {
        try { AiProvider.valueOf(it[KEY_AI_PROVIDER] ?: AiProvider.GROQ.name) }
        catch (e: Exception) { AiProvider.GROQ }
    }
    val speechRate: Flow<Float> = ds.data.map { it[KEY_SPEECH_RATE] ?: 1.0f }
    val ttsPitch: Flow<Float> = ds.data.map { it[KEY_TTS_PITCH] ?: 0.9f }
    val useElevenLabs: Flow<Boolean> = ds.data.map { it[KEY_USE_ELEVENLABS] ?: false }
    val notificationsEnabled: Flow<Boolean> = ds.data.map { it[KEY_NOTIFICATIONS_ENABLED] ?: true }
    val continuousMode: Flow<Boolean> = ds.data.map { it[KEY_CONTINUOUS_MODE] ?: false }
    val userOnboarded: Flow<Boolean> = ds.data.map { it[KEY_USER_ONBOARDED] ?: false }

    suspend fun setGroqApiKey(key: String) = ds.edit { it[KEY_GROQ_API_KEY] = key }
    suspend fun setElevenLabsApiKey(key: String) = ds.edit { it[KEY_ELEVENLABS_API_KEY] = key }
    suspend fun setDeepSeekApiKey(key: String) = ds.edit { it[KEY_DEEPSEEK_API_KEY] = key }
    suspend fun setElevenLabsVoiceId(id: String) = ds.edit { it[KEY_ELEVENLABS_VOICE_ID] = id }
    suspend fun setAiProvider(p: AiProvider) = ds.edit { it[KEY_AI_PROVIDER] = p.name }
    suspend fun setSpeechRate(r: Float) = ds.edit { it[KEY_SPEECH_RATE] = r }
    suspend fun setTtsPitch(p: Float) = ds.edit { it[KEY_TTS_PITCH] = p }
    suspend fun setUseElevenLabs(b: Boolean) = ds.edit { it[KEY_USE_ELEVENLABS] = b }
    suspend fun setNotificationsEnabled(b: Boolean) = ds.edit { it[KEY_NOTIFICATIONS_ENABLED] = b }
    suspend fun setContinuousMode(b: Boolean) = ds.edit { it[KEY_CONTINUOUS_MODE] = b }
    suspend fun setUserOnboarded(b: Boolean) = ds.edit { it[KEY_USER_ONBOARDED] = b }
}
