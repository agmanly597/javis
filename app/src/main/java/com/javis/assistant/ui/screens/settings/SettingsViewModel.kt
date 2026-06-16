package com.javis.assistant.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javis.assistant.domain.model.AiProvider
import com.javis.assistant.domain.model.AiSettings
import com.javis.assistant.domain.repository.ChatRepository
import com.javis.assistant.domain.repository.MemoryRepository
import com.javis.assistant.domain.repository.SettingsRepository
import com.javis.assistant.voice.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    val voiceManager: VoiceManager
) : ViewModel() {

    val settings = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiSettings())

    fun setProvider(provider: AiProvider) = viewModelScope.launch {
        settingsRepository.updateProvider(provider)
    }

    fun setGroqApiKey(key: String) = viewModelScope.launch {
        settingsRepository.updateGroqApiKey(key)
    }

    fun setDeepSeekApiKey(key: String) = viewModelScope.launch {
        settingsRepository.updateDeepSeekApiKey(key)
    }

    fun setSpeechRate(rate: Float) = viewModelScope.launch {
        settingsRepository.updateSpeechRate(rate)
    }

    fun setTtsVoice(voice: String) = viewModelScope.launch {
        settingsRepository.updateTtsVoice(voice)
    }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.updateNotificationsEnabled(enabled)
    }

    fun setContinuousMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.updateContinuousMode(enabled)
    }

    fun setUserName(name: String) = viewModelScope.launch {
        settingsRepository.updateUserName(name)
    }

    fun clearMemory() = viewModelScope.launch {
        memoryRepository.clearAll()
    }

    fun clearChatHistory() = viewModelScope.launch {
        chatRepository.clearHistory()
    }
}
