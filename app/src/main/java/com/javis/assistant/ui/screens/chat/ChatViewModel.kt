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
import com.javis.assistant.whatsapp.WhatsAppReplyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
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
    val speechRate: Float = 1.0f,
    val continuousMode: Boolean = true,
    val userName: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val voiceManager: VoiceManager,
    private val memoryManager: MemoryManager,
    private val whatsAppReplyManager: WhatsAppReplyManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Tracks if we're waiting for user to dictate the reply body to a WhatsApp contact */
    private var pendingWhatsAppReplySender: String? = null

    init {
        observeMessages()
        observeVoiceState()
        observeSettings()
        warmAppCache()
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
                _uiState.update {
                    it.copy(
                        speechRate = settings.speechRate,
                        continuousMode = settings.continuousMode,
                        userName = settings.userName
                    )
                }
                voiceManager.setSpeechRate(settings.speechRate)
                if (settings.ttsVoice.isNotBlank()) voiceManager.setVoice(settings.ttsVoice)
            }
        }
    }

    /** Pre-warm the installed app cache so the first "open X" command is instant */
    private fun warmAppCache() {
        viewModelScope.launch {
            AppLauncher.getInstalledApps(context)
        }
    }

    // ─── Mic Button ────────────────────────────────────────────────────────────

    /**
     * Called when the user taps the mic button.
     * - If idle: speak greeting → then listen
     * - If listening: stop
     * - If speaking: stop speaking → immediately listen
     */
    fun onMicTapped() {
        when (_uiState.value.voiceState) {
            is VoiceState.Listening -> stopListening()
            is VoiceState.Speaking -> {
                voiceManager.stopSpeaking()
                viewModelScope.launch {
                    delay(250)
                    startListening()
                }
            }
            is VoiceState.Processing -> { /* wait for result */ }
            else -> speakGreetingThenListen()
        }
    }

    private fun speakGreetingThenListen() {
        val name = _uiState.value.userName.trim()
        val greeting = randomGreeting(name)
        voiceManager.speak(greeting, _uiState.value.speechRate) {
            viewModelScope.launch {
                delay(200)
                startListening()
            }
        }
    }

    private fun randomGreeting(name: String?): String {
        val greetings = if (!name.isNullOrBlank()) listOf(
            "I'm here, $name.",
            "At your service, $name.",
            "Yes, $name?",
            "Ready, $name.",
            "How can I assist, $name?"
        ) else listOf(
            "I'm here.",
            "At your service.",
            "Yes?",
            "Ready.",
            "How can I help?"
        )
        return greetings.random()
    }

    // ─── Listening ─────────────────────────────────────────────────────────────

    fun startListening() {
        voiceManager.startListening(
            onResult = { text -> processInput(text) }
        )
    }

    fun stopListening() {
        voiceManager.stopListening()
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    /** After JAVIS finishes speaking, re-open mic if conversation mode is on */
    private fun autoRestartIfNeeded() {
        if (_uiState.value.continuousMode) {
            viewModelScope.launch {
                delay(600)
                if (_uiState.value.voiceState is VoiceState.Idle && !_uiState.value.isLoading) {
                    startListening()
                }
            }
        }
    }

    // ─── Input Processing ──────────────────────────────────────────────────────

    private fun processInput(text: String) {
        // If awaiting a WhatsApp reply body, treat input as the reply text
        val replyTarget = pendingWhatsAppReplySender
        if (replyTarget != null) {
            pendingWhatsAppReplySender = null
            sendWhatsAppReply(replyTarget, text)
            return
        }
        sendMessage(text)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // Add user message to chat
            chatRepository.insertMessage(ChatMessage(content = text, role = MessageRole.USER))
            _uiState.update { it.copy(isLoading = true, error = null) }

            val lower = text.lowercase()

            // ── Check WhatsApp / messages ──
            if (isCheckMessagesCommand(lower)) {
                val msgs = whatsAppReplyManager.getRecentMessages(10)
                val spoken = whatsAppReplyManager.buildSpokenSummary(msgs)
                deliverAssistantResponse(spoken, restartMic = true)
                return@launch
            }

            // ── Reply to someone on WhatsApp ──
            val replySender = extractReplyTarget(lower)
            if (replySender != null) {
                val existing = whatsAppReplyManager.getMessagesFrom(replySender)
                if (existing.isNotEmpty()) {
                    val realName = existing.first().sender
                    pendingWhatsAppReplySender = realName
                    deliverAssistantResponse("What would you like me to say to $realName?", restartMic = true)
                } else {
                    deliverAssistantResponse(
                        "I don't have a recent message from $replySender. " +
                        "Make sure WhatsApp notification access is granted.",
                        restartMic = false
                    )
                }
                return@launch
            }

            // ── Fast-path: known app launch (hardcoded) ──
            val fastLaunch = AppLauncher.parseNaturalLanguageCommand(text, context)
            if (fastLaunch != null) {
                val action = AppLauncher.tryParseLaunchAction(fastLaunch)
                if (action != null) {
                    val label = action.label ?: "the app"
                    val quip = launchQuip(label)
                    deliverAssistantResponse(quip, restartMic = false) {
                        AppLauncher.launchApp(context, action)
                        memoryManager.rememberAppUsage(label)
                    }
                    return@launch
                }
            }

            // ── AI response ──
            val history = _uiState.value.messages
            chatRepository.sendMessage(text, history).fold(
                onSuccess = { response ->
                    memoryManager.extractAndSaveFromConversation(text, response)

                    val launchAction = AppLauncher.tryParseLaunchAction(response)
                    val spoken = AppLauncher.extractTextFromResponse(response).ifBlank { response }

                    if (launchAction != null) {
                        val displayText = spoken.ifBlank { "Opening ${launchAction.label}." }
                        deliverAssistantResponse(displayText, restartMic = false) {
                            AppLauncher.launchApp(context, launchAction)
                            launchAction.label?.let { memoryManager.rememberAppUsage(it) }
                        }
                    } else {
                        deliverAssistantResponse(spoken, restartMic = true)
                    }
                },
                onFailure = { error ->
                    val msg = error.message ?: "Something went wrong, sir."
                    deliverAssistantResponse(msg, restartMic = false)
                }
            )
        }
    }

    private suspend fun deliverAssistantResponse(
        text: String,
        restartMic: Boolean,
        afterSpeak: (() -> Unit)? = null
    ) {
        chatRepository.insertMessage(ChatMessage(content = text, role = MessageRole.ASSISTANT))
        _uiState.update { it.copy(isLoading = false) }
        voiceManager.speak(text, _uiState.value.speechRate) {
            afterSpeak?.invoke()
            if (restartMic) autoRestartIfNeeded()
        }
    }

    private fun sendWhatsAppReply(sender: String, replyText: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = whatsAppReplyManager.sendReply(context, sender, replyText)
            val response = if (success) {
                "Message sent to $sender."
            } else {
                "Couldn't send that. The notification may have expired — try opening WhatsApp directly."
            }
            deliverAssistantResponse(response, restartMic = true)
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun launchQuip(label: String): String = listOf(
        "Opening $label.",
        "Launching $label now.",
        "Right away. $label, coming up.",
        "Consider it done. Opening $label.",
        "On it. Launching $label."
    ).random()

    private fun isCheckMessagesCommand(lower: String): Boolean {
        val hasMessageWord = lower.contains("message") || lower.contains("whatsapp") ||
                lower.contains("notif") || lower.contains("inbox")
        val hasCheckWord = lower.contains("check") || lower.contains("read") ||
                lower.contains("show") || lower.contains("any") || lower.contains("what") ||
                lower.contains("do i have") || lower.contains("new")
        return hasMessageWord && hasCheckWord
    }

    private fun extractReplyTarget(lower: String): String? {
        if (!lower.contains("reply") && !lower.contains("respond") &&
            !lower.contains("send") && !lower.contains("text") && !lower.contains("message")
        ) return null
        val patterns = listOf(
            Regex("reply to (\\w+)"),
            Regex("respond to (\\w+)"),
            Regex("send (\\w+) a message"),
            Regex("send a message to (\\w+)"),
            Regex("text (\\w+)"),
            Regex("message (\\w+)")
        )
        for (p in patterns) {
            val m = p.find(lower) ?: continue
            val name = m.groupValues[1]
            if (name !in listOf("a", "the", "my", "me", "him", "her", "them")) return name
        }
        return null
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
