package com.javis.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javis.assistant.ai.CommandParser
import com.javis.assistant.ai.CommandType
import com.javis.assistant.ai.ParsedCommand
import com.javis.assistant.data.model.Message
import com.javis.assistant.data.model.VoiceState
import com.javis.assistant.domain.JavisRepository
import com.javis.assistant.voice.JavisSpeechRecognizer
import com.javis.assistant.voice.SpeechEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val voiceState: VoiceState = VoiceState(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pendingCommand: ParsedCommand? = null,
    val inputText: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: JavisRepository,
    private val speechRecognizer: JavisSpeechRecognizer,
    private val commandParser: CommandParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val sessionId: String = repository.currentSessionId

    init {
        observeMessages()
        observeSpeech()
        sendGreeting()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            repository.getMessages(sessionId).collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
    }

    private fun observeSpeech() {
        viewModelScope.launch {
            speechRecognizer.events.collect { event ->
                when (event) {
                    is SpeechEvent.Started -> {
                        _uiState.update { it.copy(voiceState = it.voiceState.copy(isListening = true)) }
                    }
                    is SpeechEvent.Partial -> {
                        _uiState.update { it.copy(voiceState = it.voiceState.copy(partialText = event.text)) }
                    }
                    is SpeechEvent.Result -> {
                        _uiState.update {
                            it.copy(
                                voiceState = it.voiceState.copy(isListening = false, partialText = ""),
                                inputText = event.text
                            )
                        }
                        sendMessage(event.text)
                    }
                    is SpeechEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                voiceState = it.voiceState.copy(isListening = false, partialText = ""),
                                error = if (event.code == 7 || event.code == 6) null else event.message
                            )
                        }
                    }
                    is SpeechEvent.Ended -> {
                        _uiState.update { it.copy(voiceState = it.voiceState.copy(isListening = false)) }
                    }
                    null -> {}
                }
            }
        }
    }

    private fun sendGreeting() {
        viewModelScope.launch {
            val greeting = repository.getGreeting()
            repository.sendMessage(greeting, sessionId)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val parsed = commandParser.parse(text)
        if (parsed.requiresConfirmation) {
            _uiState.update { it.copy(pendingCommand = parsed, inputText = "") }
            viewModelScope.launch {
                repository.sendMessage(
                    parsed.confirmationMessage.ifBlank { "Should I do that?" },
                    sessionId
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, inputText = "", error = null) }
            repository.sendMessage(text, sessionId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun confirmPendingCommand(confirmed: Boolean) {
        val cmd = _uiState.value.pendingCommand ?: return
        _uiState.update { it.copy(pendingCommand = null) }

        if (!confirmed) {
            viewModelScope.launch {
                repository.sendMessage("Got it, I won't do that.", sessionId)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.executeConfirmedCommand(cmd)

            val reply = when (cmd.type) {
                CommandType.CALL_CONTACT -> "Calling ${cmd.target}…"
                CommandType.SEND_WHATSAPP -> "Sending to ${cmd.target}…"
                CommandType.SET_ALARM -> "Setting alarm…"
                else -> "Done."
            }
            repository.sendMessage(reply, sessionId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun startListening() {
        repository.stopSpeaking()
        speechRecognizer.startListening()
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        speechRecognizer.destroy()
        super.onCleared()
    }
}
