package com.javis.assistant.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.javis.assistant.accessibility.JavisAccessibilityService
import com.javis.assistant.domain.model.ChatMessage
import com.javis.assistant.domain.model.MessageRole
import com.javis.assistant.domain.repository.ChatRepository
import com.javis.assistant.domain.repository.SettingsRepository
import com.javis.assistant.memory.MemoryManager
import com.javis.assistant.service.JavisActivationBus
import com.javis.assistant.utils.AppLauncher
import com.javis.assistant.utils.SystemCommandHandler
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
    val speechRate: Float = 0.93f,
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
    private val systemCommandHandler: SystemCommandHandler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    /** Multi-turn conversation state machine */
    private enum class ConvState {
        IDLE,
        AWAITING_WHATSAPP_REPLY_BODY,   // user just said "reply to John" — waiting for message
        AWAITING_WHATSAPP_SEND_CONFIRM,  // message typed in WhatsApp — waiting for "send"
        AWAITING_CALL_DISAMBIGUATION     // multiple contacts found — waiting for which one
    }

    private var convState = ConvState.IDLE
    private var pendingTarget: String? = null      // Contact name for pending action
    private var pendingMessage: String? = null     // Message body for pending WhatsApp reply
    private val lastResponsesCache = ArrayDeque<String>(5)  // avoid repeating exact responses

    init {
        observeMessages()
        observeVoiceState()
        observeSettings()
        warmAppCache()
        observeActivationBus()
    }

    private fun observeActivationBus() {
        viewModelScope.launch {
            JavisActivationBus.activations.collect {
                if (_uiState.value.voiceState is VoiceState.Idle) {
                    speakGreetingThenListen()
                }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.getMessages().collect { msgs -> _uiState.update { it.copy(messages = msgs) } }
        }
    }

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceManager.voiceState.collect { state -> _uiState.update { it.copy(voiceState = state) } }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { s ->
                _uiState.update { it.copy(speechRate = s.speechRate, continuousMode = s.continuousMode, userName = s.userName) }
                voiceManager.elevenLabsApiKey = s.elevenLabsApiKey
                voiceManager.setSpeechRate(s.speechRate)
                if (s.ttsVoice.isNotBlank()) voiceManager.setVoice(s.ttsVoice)
            }
        }
    }

    private fun warmAppCache() {
        viewModelScope.launch { AppLauncher.getInstalledApps(context) }
    }

    // ─── Mic Tap ──────────────────────────────────────────────────────────────

    fun onMicTapped() {
        when (_uiState.value.voiceState) {
            is VoiceState.Listening -> stopListening()
            is VoiceState.Speaking -> {
                voiceManager.stopSpeaking()
                viewModelScope.launch { delay(200); startListening() }
            }
            is VoiceState.Processing -> {}
            else -> speakGreetingThenListen()
        }
    }

    /** Called from MainActivity when activated externally (tile / notification / intent) */
    fun activateFromExternal() {
        if (_uiState.value.voiceState is VoiceState.Idle) {
            speakGreetingThenListen()
        }
    }

    private fun speakGreetingThenListen() {
        val name = _uiState.value.userName.trim()
        val greeting = buildGreeting(name)
        voiceManager.speak(greeting, _uiState.value.speechRate) {
            viewModelScope.launch { delay(150); startListening() }
        }
    }

    private fun buildGreeting(name: String): String {
        val withName = listOf(
            "I'm here, $name.",
            "At your service, $name.",
            "Yes, $name?",
            "Ready when you are, $name.",
            "You rang, $name?",
            "JAVIS online. What do you need, $name?"
        )
        val noName = listOf(
            "I'm here.",
            "At your service.",
            "Yes?",
            "Ready.",
            "JAVIS online.",
            "You have my attention."
        )
        return (if (name.isNotBlank()) withName else noName).random()
    }

    fun startListening() {
        voiceManager.startListening { text -> processInput(text) }
    }

    fun stopListening() = voiceManager.stopListening()
    fun stopSpeaking() = voiceManager.stopSpeaking()

    private fun autoRestart() {
        if (_uiState.value.continuousMode) {
            viewModelScope.launch {
                delay(700)
                if (_uiState.value.voiceState is VoiceState.Idle && !_uiState.value.isLoading) {
                    startListening()
                }
            }
        }
    }

    // ─── Input Processing ─────────────────────────────────────────────────────

    private fun processInput(text: String) {
        val lower = text.lowercase().trim()

        // State-machine handlers for multi-turn flows
        when (convState) {
            ConvState.AWAITING_WHATSAPP_REPLY_BODY -> {
                val target = pendingTarget ?: run { convState = ConvState.IDLE; sendMessage(text); return }
                pendingMessage = text
                convState = ConvState.AWAITING_WHATSAPP_SEND_CONFIRM
                openWhatsAppWithTyping(target, text)
                return
            }
            ConvState.AWAITING_WHATSAPP_SEND_CONFIRM -> {
                if (lower.contains("send") || lower.contains("yes") || lower.contains("ok") ||
                    lower.contains("do it") || lower.contains("go") || lower.contains("confirm")) {
                    doWhatsAppSend()
                } else if (lower.contains("cancel") || lower.contains("stop") || lower.contains("no")) {
                    convState = ConvState.IDLE
                    JavisAccessibilityService.instance?.resetWhatsAppState()
                    respond("Cancelled. The message has been discarded.", restart = false)
                } else {
                    // Treat new input as a new message to type
                    val target = pendingTarget
                    if (target != null) {
                        pendingMessage = text
                        openWhatsAppWithTyping(target, text)
                    } else {
                        convState = ConvState.IDLE
                        sendMessage(text)
                    }
                }
                return
            }
            ConvState.AWAITING_CALL_DISAMBIGUATION -> {
                convState = ConvState.IDLE
                val result = systemCommandHandler.callContact(text)
                respond(result.message, restart = !result.requiresFollowUp)
                return
            }
            else -> {}
        }

        sendMessage(text)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            chatRepository.insertMessage(ChatMessage(content = text, role = MessageRole.USER))
            _uiState.update { it.copy(isLoading = true, error = null) }

            val lower = text.lowercase()

            // ── WhatsApp: check messages ──
            if (isCheckMessagesCommand(lower)) {
                val msgs = whatsAppReplyManager.getRecentMessages(10)
                val spoken = whatsAppReplyManager.buildSpokenSummary(msgs)
                respond(spoken, restart = true)
                return@launch
            }

            // ── WhatsApp: reply / send message ──
            val replyTarget = extractWhatsAppTarget(lower)
            if (replyTarget != null) {
                val inlineMessage = extractInlineMessage(text)
                if (inlineMessage != null) {
                    // "Reply to John saying hello" → go straight to typing
                    pendingTarget = replyTarget
                    convState = ConvState.AWAITING_WHATSAPP_SEND_CONFIRM
                    openWhatsAppWithTyping(replyTarget, inlineMessage)
                } else {
                    // "Reply to John" → ask what to say
                    pendingTarget = replyTarget
                    convState = ConvState.AWAITING_WHATSAPP_REPLY_BODY
                    respond("What would you like me to say to $replyTarget?", restart = true)
                }
                return@launch
            }

            // ── Fast-path app launch ──
            val fastLaunch = AppLauncher.parseNaturalLanguageCommand(text, context)
            if (fastLaunch != null) {
                val action = AppLauncher.tryParseLaunchAction(fastLaunch)
                if (action != null) {
                    val label = action.label ?: "the app"
                    respond(launchQuip(label), restart = false) {
                        AppLauncher.launchApp(context, action)
                        memoryManager.rememberAppUsage(label)
                    }
                    return@launch
                }
            }

            // ── AI response ──
            val history = _uiState.value.messages
            chatRepository.sendMessage(text, history).fold(
                onSuccess = { raw ->
                    memoryManager.extractAndSaveFromConversation(text, raw)
                    handleAiResponse(raw)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Something went wrong, sir."
                    respond(msg, restart = false)
                }
            )
        }
    }

    private suspend fun handleAiResponse(raw: String) {
        // Extract the JSON action block if present
        val jsonBlock = extractJsonBlock(raw)
        val spoken = if (jsonBlock != null) {
            raw.substring(0, raw.indexOf(jsonBlock)).trim().ifBlank {
                raw.substring(raw.indexOf(jsonBlock) + jsonBlock.length).trim()
            }.ifBlank { "Right away." }
        } else raw

        if (jsonBlock != null) {
            val obj = runCatching { gson.fromJson(jsonBlock, JsonObject::class.java) }.getOrNull()
            val action = obj?.get("action")?.asString

            when (action) {
                "LAUNCH_APP" -> {
                    val pkg = obj.get("package")?.asString
                    val label = obj.get("label")?.asString ?: "the app"
                    respond(spoken, restart = false) {
                        AppLauncher.launchApp(context, AppLauncher.LaunchAction("LAUNCH_APP", pkg, label))
                        memoryManager.rememberAppUsage(label)
                    }
                }
                "SEARCH_IN_APP" -> {
                    val result = systemCommandHandler.searchInApp(
                        obj.get("package")?.asString ?: "",
                        obj.get("label")?.asString ?: "App",
                        obj.get("query")?.asString ?: ""
                    )
                    respond(spoken.ifBlank { result.message }, restart = false)
                }
                "CALL_CONTACT" -> {
                    val result = systemCommandHandler.callContact(obj.get("name")?.asString ?: "")
                    if (result.requiresFollowUp) convState = ConvState.AWAITING_CALL_DISAMBIGUATION
                    respond(result.message, restart = !result.requiresFollowUp)
                }
                "CALL_NUMBER" -> {
                    systemCommandHandler.dialNumber(obj.get("number")?.asString ?: "")
                    respond(spoken, restart = false)
                }
                "WHATSAPP_CHAT" -> {
                    val name = obj.get("name")?.asString ?: ""
                    val message = obj.get("message")?.asString ?: ""
                    if (message.isNotBlank()) {
                        pendingTarget = name
                        convState = ConvState.AWAITING_WHATSAPP_SEND_CONFIRM
                        openWhatsAppWithTyping(name, message)
                    } else {
                        val result = systemCommandHandler.openWhatsAppChat(name)
                        respond(result.message, restart = false)
                    }
                }
                "SET_ALARM" -> {
                    val result = systemCommandHandler.setAlarm(
                        obj.get("hour")?.asInt ?: 7,
                        obj.get("minute")?.asInt ?: 0,
                        obj.get("label")?.asString ?: "JAVIS Alarm"
                    )
                    respond(result.message, restart = false)
                }
                "SET_TIMER" -> {
                    val result = systemCommandHandler.setTimer(obj.get("minutes")?.asInt ?: 1)
                    respond(result.message, restart = false)
                }
                "WEB_SEARCH" -> {
                    val result = systemCommandHandler.webSearch(obj.get("query")?.asString ?: "")
                    respond(spoken.ifBlank { result.message }, restart = false)
                }
                "SEND_SMS" -> {
                    val result = systemCommandHandler.sendSms(
                        obj.get("name")?.asString ?: "",
                        obj.get("message")?.asString ?: ""
                    )
                    respond(result.message, restart = false)
                }
                else -> respond(spoken, restart = true)
            }
        } else {
            respond(spoken, restart = true)
        }
    }

    // ─── WhatsApp Typing via Accessibility ────────────────────────────────────

    private fun openWhatsAppWithTyping(contactName: String, message: String) {
        viewModelScope.launch {
            val accessibility = JavisAccessibilityService.instance

            if (accessibility != null) {
                // Open WhatsApp to the contact's chat
                val waResult = systemCommandHandler.openWhatsAppChat(contactName, "")
                val responseText = "Opening ${contactName}'s chat and typing your message."
                respond(responseText, restart = false)

                // Wait for WhatsApp to load, then type
                delay(2000)
                accessibility.prepareToTypeInWhatsApp(message) { typed ->
                    viewModelScope.launch {
                        val confirmMsg = "I've typed \"$typed\" to $contactName. Say 'send' to send it, or say something else to change the message."
                        respond(confirmMsg, restart = true)
                    }
                }
            } else {
                // Accessibility not enabled — use deep link with pre-filled text
                val result = systemCommandHandler.openWhatsAppChat(contactName, message)
                val responseText = if (result.success) {
                    "Opened ${contactName}'s chat with your message pre-filled. Tap send when ready."
                } else {
                    result.message
                }
                convState = ConvState.IDLE
                respond(responseText, restart = false)
            }
        }
    }

    private fun doWhatsAppSend() {
        val accessibility = JavisAccessibilityService.instance
        val sent = accessibility?.tapSendInWhatsApp() ?: false
        convState = ConvState.IDLE
        pendingTarget = null
        pendingMessage = null
        if (sent) {
            respond("Sent. Anything else?", restart = true)
        } else {
            respond("I couldn't tap send — please tap it manually. The message should be ready in the chat.", restart = false)
        }
    }

    // ─── Response Helpers ─────────────────────────────────────────────────────

    private fun respond(text: String, restart: Boolean, afterSpeak: (() -> Unit)? = null) {
        viewModelScope.launch {
            chatRepository.insertMessage(ChatMessage(content = text, role = MessageRole.ASSISTANT))
            _uiState.update { it.copy(isLoading = false) }

            // Track to avoid exact repetition
            if (text !in lastResponsesCache) {
                lastResponsesCache.addLast(text)
                if (lastResponsesCache.size > 5) lastResponsesCache.removeFirst()
            }

            voiceManager.speak(text, _uiState.value.speechRate) {
                afterSpeak?.invoke()
                if (restart) autoRestart()
            }
        }
    }

    // ─── Command Parsing ──────────────────────────────────────────────────────

    private fun isCheckMessagesCommand(lower: String): Boolean {
        val msgWord = lower.contains("message") || lower.contains("whatsapp") ||
                lower.contains("notif") || lower.contains("inbox") || lower.contains("chat")
        val checkWord = lower.contains("check") || lower.contains("read") || lower.contains("show") ||
                lower.contains("any") || lower.contains("new") || lower.contains("do i have") ||
                lower.contains("what") || lower.contains("who")
        return msgWord && checkWord
    }

    private fun extractWhatsAppTarget(lower: String): String? {
        if (!lower.contains("reply") && !lower.contains("respond") &&
            !lower.contains("send") && !lower.contains("message") && !lower.contains("text")
        ) return null

        val patterns = listOf(
            Regex("reply to (\\w+)"),
            Regex("respond to (\\w+)"),
            Regex("send (\\w+) a message"),
            Regex("send a (?:whatsapp )?message to (\\w+)"),
            Regex("message (\\w+) on whatsapp"),
            Regex("whatsapp (\\w+)"),
            Regex("text (\\w+)")
        )
        val stopWords = setOf("a", "the", "my", "me", "him", "her", "them", "it", "this", "that")
        for (p in patterns) {
            val m = p.find(lower) ?: continue
            val name = m.groupValues[1]
            if (name !in stopWords && name.length > 1) return name.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun extractInlineMessage(text: String): String? {
        val patterns = listOf(
            Regex("(?:reply|send|message|text) \\w+ (?:saying|that|with|:)\\s+(.+)", RegexOption.IGNORE_CASE),
            Regex("(?:reply|send|message|text) \\w+ \"(.+)\"", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val msg = m.groupValues[1].trim()
            if (msg.isNotBlank()) return msg
        }
        return null
    }

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        val end = text.lastIndexOf('}')
        if (end <= start) return null
        return runCatching {
            val candidate = text.substring(start, end + 1)
            gson.fromJson(candidate, JsonObject::class.java)
            candidate
        }.getOrNull()
    }

    private fun launchQuip(label: String): String = listOf(
        "Opening $label.",
        "Launching $label now.",
        "Right away — $label coming up.",
        "Consider it done. Opening $label.",
        "On it. $label, launching."
    ).random()

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    fun clearHistory() { viewModelScope.launch { chatRepository.clearHistory() } }
    fun dismissError() { _uiState.update { it.copy(error = null) } }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
