package com.javis.assistant.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javis.assistant.domain.model.ChatMessage
import com.javis.assistant.domain.model.MessageRole
import com.javis.assistant.domain.repository.ChatRepository
import com.javis.assistant.domain.repository.SettingsRepository
import com.javis.assistant.memory.MemoryManager
import com.javis.assistant.utils.AppLauncher
import com.javis.assistant.voice.VoiceManager
import com.javis.assistant.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val voiceState: VoiceState = VoiceState.Idle,
    val error: String? = null,
    val pendingLaunchLabel: String? = null,
    val speechRate: Float = 1.0f,
    val continuousMode: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val voiceManager: VoiceManager,
    private val memoryManager: MemoryManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
        observeVoiceState()
        observeSettings()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.getMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceManager.voiceState.collect { state ->
                _uiState.update { it.copy(voiceState = state) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.update { it.copy(speechRate = settings.speechRate, continuousMode = settings.continuousMode) }
                voiceManager.setVoice(settings.ttsVoice)
            }
        }
    }

    fun startListening() {
        voiceManager.startListening(
            onResult = { text -> sendMessage(text) },
            onEnd = {
                if (_uiState.value.continuousMode) {
                    // auto restart in continuous mode after speaking
                }
            }
        )
    }

    fun stopListening() {
        voiceManager.stopListening()
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val userMessage = ChatMessage(content = text, role = MessageRole.USER)
            chatRepository.insertMessage(userMessage)
            _uiState.update { it.copy(isLoading = true, error = null) }

            val history = _uiState.value.messages

            // Check for direct app launch command
            val directLaunch = AppLauncher.parseNaturalLanguageCommand(text)
            if (directLaunch != null) {
                val launchAction = AppLauncher.tryParseLaunchAction(directLaunch)
                if (launchAction != null) {
                    val label = launchAction.label ?: "the app"
                    val response = "Opening $label for you."
                    val assistantMessage = ChatMessage(content = response, role = MessageRole.ASSISTANT)
                    chatRepository.insertMessage(assistantMessage)
                    _uiState.update { it.copy(isLoading = false) }
                    speak(response)
                    AppLauncher.launchApp(context, launchAction)
                    memoryManager.rememberAppUsage(label)
                    return@launch
                }
            }

            val result = chatRepository.sendMessage(text, history)
            result.fold(
                onSuccess = { response ->
                    memoryManager.extractAndSaveFromConversation(text, response)

                    val launchAction = AppLauncher.tryParseLaunchAction(response)
                    val spokenText = AppLauncher.extractTextFromResponse(response).ifBlank { response }
                    val displayText = if (launchAction != null) {
                        spokenText.ifBlank { "Opening ${launchAction.label ?: "the app"}." }
                    } else response

                    val assistantMessage = ChatMessage(content = displayText, role = MessageRole.ASSISTANT)
                    chatRepository.insertMessage(assistantMessage)
                    _uiState.update { it.copy(isLoading = false) }

                    if (launchAction != null) {
                        val confirmText = displayText.ifBlank { "Opening ${launchAction.label ?: "the app"}." }
                        speak(confirmText) {
                            AppLauncher.launchApp(context, launchAction)
                            launchAction.label?.let { memoryManager.rememberAppUsage(it) }
                        }
                    } else {
                        speak(displayText)
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                    val errMsg = error.message ?: "Something went wrong"
                    val assistantMessage = ChatMessage(content = errMsg, role = MessageRole.ASSISTANT)
                    chatRepository.insertMessage(assistantMessage)
                    speak(errMsg)
                }
            )
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        voiceManager.speak(text, _uiState.value.speechRate, onDone)
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepository.clearHistory() }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
