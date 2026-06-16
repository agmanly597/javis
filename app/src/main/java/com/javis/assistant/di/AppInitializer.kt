package com.javis.assistant.di

import com.javis.assistant.BuildConfig
import com.javis.assistant.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds default API keys from BuildConfig on first launch.
 * Keys can always be overridden in Settings.
 */
@Singleton
class AppInitializer @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = settingsRepository.getSettings().first()
            if (settings.groqApiKey.isBlank() && BuildConfig.GROQ_API_KEY.isNotBlank()) {
                settingsRepository.updateGroqApiKey(BuildConfig.GROQ_API_KEY)
            }
            if (settings.deepSeekApiKey.isBlank() && BuildConfig.DEEPSEEK_API_KEY.isNotBlank()) {
                settingsRepository.updateDeepSeekApiKey(BuildConfig.DEEPSEEK_API_KEY)
            }
        }
    }
}
