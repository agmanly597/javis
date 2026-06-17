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
import com.javis.assistant.utils.VoiceNoteHelper
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
    val userName: String = "",
    val isRecordingVoiceNote: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val voiceManager: VoiceManager,
    private val memoryManager: MemoryManager,
    private val whatsAppReplyManager: WhatsAppReplyManager,
    private val systemCommandHandler: SystemCommandHandler,
    private val voiceNoteHelper: VoiceNoteHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    private enum class ConvState {
        IDLE,
        AWAITING_WHATSAPP_REPLY_BODY,
        AWAITING_WHATSAPP_SEND_CONFIRM,
        AWAITING_CALL_DISAMBIGUATION,
        RECORDING_VOICE_NOTE
    }

    private var convState = ConvState.IDLE
    private var pendingTarget: String? = null
    private var pendingMessage: String? = null
    private val recentResponses = ArrayDeque<String>(6)

    init {
        observeMessages()
        observeVoiceState()
        observeSettings()
        observeActivationBus()
        viewModelScope.launch { AppLauncher.getInstalledApps(context) } // warm cache
    }

    private fun observeActivationBus() = viewModelScope.launch {
        JavisActivationBus.activations.collect {
            if (_uiState.value.voiceState is VoiceState.Idle) speakGreetingThenListen()
        }
    }

    private fun observeMessages() = viewModelScope.launch {
        chatRepository.getMessages().collect { msgs -> _uiState.update { it.copy(messages = msgs) } }
    }

    private fun observeVoiceState() = viewModelScope.launch {
        voiceManager.voiceState.collect { state -> _uiState.update { it.copy(voiceState = state) } }
    }

    private fun observeSettings() = viewModelScope.launch {
        settingsRepository.getSettings().collect { s ->
            _uiState.update { it.copy(speechRate = s.speechRate, continuousMode = s.continuousMode, userName = s.userName) }
            voiceManager.elevenLabsApiKey = s.elevenLabsApiKey
            voiceManager.elevenLabsVoiceId = s.elevenLabsVoiceId.ifBlank { com.javis.assistant.voice.ElevenLabsTtsService.DEFAULT_VOICE }
            voiceManager.setSpeechRate(s.speechRate)
            if (s.ttsVoice.isNotBlank()) voiceManager.setVoice(s.ttsVoice)
        }
    }

    // ─── Mic ──────────────────────────────────────────────────────────────────

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

    fun activateFromExternal() {
        if (_uiState.value.voiceState is VoiceState.Idle) speakGreetingThenListen()
    }

    private fun speakGreetingThenListen() {
        val name = _uiState.value.userName.trim()
        voiceManager.speak(buildGreeting(name), _uiState.value.speechRate) {
            viewModelScope.launch { delay(150); startListening() }
        }
    }

    private fun buildGreeting(name: String): String {
        val greetings = if (name.isNotBlank()) listOf(
            "I'm here, $name.", "At your service, $name.", "Yes, $name?",
            "Ready when you are, $name.", "You rang, $name?",
            "JAVIS online. What do you need, $name?", "Listening, $name.",
            "All ears, $name.", "What's up, $name?"
        ) else listOf(
            "I'm here.", "At your service.", "Yes?", "Ready.", "Listening.",
            "JAVIS online.", "You have my attention.", "What's the word?",
            "All ears.", "Go ahead."
        )
        return greetings.random()
    }

    fun startListening() = voiceManager.startListening { processInput(it) }
    fun stopListening() = voiceManager.stopListening()
    fun stopSpeaking() = voiceManager.stopSpeaking()

    private fun autoRestart() {
        if (_uiState.value.continuousMode) {
            viewModelScope.launch {
                delay(700)
                if (_uiState.value.voiceState is VoiceState.Idle && !_uiState.value.isLoading) startListening()
            }
        }
    }

    // ─── Input routing ────────────────────────────────────────────────────────

    private fun processInput(text: String) {
        val lower = text.lowercase().trim()

        // State machine — multi-turn flows
        when (convState) {
            ConvState.AWAITING_WHATSAPP_REPLY_BODY -> {
                val target = pendingTarget ?: run { convState = ConvState.IDLE; sendMessage(text); return }
                pendingMessage = text
                convState = ConvState.AWAITING_WHATSAPP_SEND_CONFIRM
                openWhatsAppWithTyping(target, text)
                return
            }
            ConvState.AWAITING_WHATSAPP_SEND_CONFIRM -> {
                if (isConfirmWord(lower)) { doWhatsAppSend(); return }
                if (isCancelWord(lower)) {
                    convState = ConvState.IDLE
                    JavisAccessibilityService.instance?.resetWhatsAppState()
                    respond("Cancelled. Message discarded.", restart = false)
                    return
                }
                val target = pendingTarget
                if (target != null) { pendingMessage = text; openWhatsAppWithTyping(target, text) }
                else { convState = ConvState.IDLE; sendMessage(text) }
                return
            }
            ConvState.AWAITING_CALL_DISAMBIGUATION -> {
                convState = ConvState.IDLE
                val result = systemCommandHandler.callContact(text)
                respond(result.message, restart = !result.requiresFollowUp)
                return
            }
            ConvState.RECORDING_VOICE_NOTE -> {
                if (isStopWord(lower)) {
                    val audioFile = voiceNoteHelper.stopRecording()
                    _uiState.update { it.copy(isRecordingVoiceNote = false) }
                    convState = ConvState.IDLE
                    val target = pendingTarget
                    if (audioFile != null && target != null) {
                        voiceNoteHelper.sendVoiceNoteViaWhatsApp(target, audioFile)
                        respond("Voice note sent to $target.", restart = false)
                    } else {
                        respond("Recording stopped.", restart = false)
                    }
                } else {
                    // Continue recording — reassure the user
                    respond("Still recording. Say 'stop' when done.", restart = false)
                }
                return
            }
            ConvState.IDLE -> {}
        }

        sendMessage(text)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val lower = text.lowercase().trim()

        viewModelScope.launch {
            chatRepository.insertMessage(ChatMessage(content = text, role = MessageRole.USER))
            _uiState.update { it.copy(isLoading = true, error = null) }

            // ── Answer call ──
            if (isAnswerCallCommand(lower)) {
                val answered = JavisAccessibilityService.instance?.answerCall() ?: false
                respond(if (answered) "Call answered." else "Couldn't tap the answer button — tap it manually.", restart = false)
                return@launch
            }

            // ── Decline call ──
            if (isDeclineCallCommand(lower)) {
                val declined = JavisAccessibilityService.instance?.declineCall() ?: false
                respond(if (declined) "Call declined." else "Couldn't decline — tap it manually.", restart = false)
                return@launch
            }

            // ── Type in current search bar ──
            val typeText = extractTypeInFieldText(lower)
            if (typeText != null) {
                val typed = JavisAccessibilityService.instance?.typeInFocusedField(typeText) ?: false
                respond(
                    if (typed) "Typed \"$typeText\" in the search bar."
                    else "Enable JAVIS Accessibility Service first so I can type on screen.",
                    restart = false
                )
                return@launch
            }

            // ── Check WhatsApp messages ──
            if (isCheckMessagesCommand(lower)) {
                val msgs = whatsAppReplyManager.getRecentMessages(10)
                val spoken = whatsAppReplyManager.buildSpokenSummary(msgs)
                respond(spoken, restart = true)
                return@launch
            }

            // ── Voice note recording ──
            val voiceNoteTarget = extractVoiceNoteTarget(lower)
            if (voiceNoteTarget != null) {
                pendingTarget = voiceNoteTarget
                convState = ConvState.RECORDING_VOICE_NOTE
                val started = voiceNoteHelper.startRecording()
                _uiState.update { it.copy(isRecordingVoiceNote = started) }
                respond(
                    if (started) "Recording now. Speak your message to $voiceNoteTarget — say 'stop' when you're done."
                    else "Couldn't start recording. Check microphone permission.",
                    restart = false
                )
                return@launch
            }

            // ── WhatsApp reply ──
            val replyTarget = extractWhatsAppTarget(lower)
            if (replyTarget != null) {
                val inlineMessage = extractInlineMessage(text)
                if (inlineMessage != null) {
                    pendingTarget = replyTarget
                    convState = ConvState.AWAITING_WHATSAPP_SEND_CONFIRM
                    openWhatsAppWithTyping(replyTarget, inlineMessage)
                } else {
                    pendingTarget = replyTarget
                    convState = ConvState.AWAITING_WHATSAPP_REPLY_BODY
                    respond("What would you like me to say to $replyTarget?", restart = true)
                }
                return@launch
            }

            // ── Fast-path: resolve open/launch for ANY installed app before hitting AI ──
            val appAction = AppLauncher.parseNaturalLanguageCommand(text, context)
            if (appAction != null) {
                val action = AppLauncher.tryParseLaunchAction(appAction)
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
                onFailure = { err -> respond(err.message ?: "Something went wrong.", restart = false) }
            )
        }
    }

    // ─── AI response dispatcher ───────────────────────────────────────────────

    private suspend fun handleAiResponse(raw: String) {
        val jsonBlock = extractJsonBlock(raw)
        val spoken = if (jsonBlock != null) {
            val beforeJson = raw.substring(0, raw.indexOf(jsonBlock)).trim()
            beforeJson.ifBlank { "Right away." }
        } else raw

        if (jsonBlock != null) {
            val obj = runCatching { gson.fromJson(jsonBlock, JsonObject::class.java) }.getOrNull()
            when (obj?.get("action")?.asString) {
                "LAUNCH_APP" -> {
                    val pkg = obj.get("package")?.asString
                    val label = obj.get("label")?.asString ?: "the app"
                    respond(spoken, restart = false) {
                        // Try the package name first, then fuzzy match label against installed apps
                        val launched = if (!pkg.isNullOrBlank()) {
                            AppLauncher.launchApp(context, AppLauncher.LaunchAction("LAUNCH_APP", pkg, label))
                        } else false
                        if (!launched) AppLauncher.launchByName(context, label)
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
                        obj.get("hour")?.asInt ?: 7, obj.get("minute")?.asInt ?: 0,
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
                        obj.get("name")?.asString ?: "", obj.get("message")?.asString ?: ""
                    )
                    respond(result.message, restart = false)
                }
                "TYPE_IN_FIELD" -> {
                    val textToType = obj.get("text")?.asString ?: ""
                    val typed = JavisAccessibilityService.instance?.typeInFocusedField(textToType) ?: false
                    respond(
                        if (typed) spoken.ifBlank { "Typed." }
                        else "Enable JAVIS Accessibility Service so I can type on screen.",
                        restart = false
                    )
                }
                "ANSWER_CALL" -> {
                    val answered = JavisAccessibilityService.instance?.answerCall() ?: false
                    respond(if (answered) "Answered." else "Tap the answer button manually.", restart = false)
                }
                "VOICE_NOTE" -> {
                    val name = obj.get("name")?.asString ?: ""
                    pendingTarget = name.ifBlank { null }
                    convState = ConvState.RECORDING_VOICE_NOTE
                    val started = voiceNoteHelper.startRecording()
                    _uiState.update { it.copy(isRecordingVoiceNote = started) }
                    respond(
                        if (started) "Recording your voice note${if (name.isNotBlank()) " for $name" else ""}. Say 'stop' when you're done."
                        else "Couldn't start recording.", restart = false
                    )
                }
                else -> respond(spoken, restart = true)
            }
        } else {
            respond(spoken, restart = true)
        }
    }

    // ─── WhatsApp Typing ──────────────────────────────────────────────────────

    private fun openWhatsAppWithTyping(contactName: String, message: String) {
        viewModelScope.launch {
            val accessibility = JavisAccessibilityService.instance
            systemCommandHandler.openWhatsAppChat(contactName, "")
            respond("Opening ${contactName}'s chat and typing your message.", restart = false)

            if (accessibility != null) {
                delay(2000)
                accessibility.prepareToTypeInWhatsApp(message) { typed ->
                    viewModelScope.launch {
                        respond(
                            "Typed \"$typed\" to $contactName. Say 'send' to send it.",
                            restart = true
                        )
                    }
                }
            } else {
                // No accessibility — use deep link with pre-filled text
                systemCommandHandler.openWhatsAppChat(contactName, message)
                convState = ConvState.IDLE
                respond("Opened ${contactName}'s chat with your message ready. Tap send.", restart = false)
            }
        }
    }

    private fun doWhatsAppSend() {
        val sent = JavisAccessibilityService.instance?.tapSendInWhatsApp() ?: false
        convState = ConvState.IDLE
        pendingTarget = null
        pendingMessage = null
        if (sent) respond("Sent.", restart = true)
        else respond("Tap send manually — the message is already typed.", restart = false)
    }

    // ─── respond helper ───────────────────────────────────────────────────────

    private fun respond(text: String, restart: Boolean, afterSpeak: (() -> Unit)? = null) {
        viewModelScope.launch {
            chatRepository.insertMessage(ChatMessage(content = text, role = MessageRole.ASSISTANT))
            _uiState.update { it.copy(isLoading = false) }
            if (text !in recentResponses) {
                recentResponses.addLast(text)
                if (recentResponses.size > 6) recentResponses.removeFirst()
            }
            voiceManager.speak(text, _uiState.value.speechRate) {
                afterSpeak?.invoke()
                if (restart) autoRestart()
            }
        }
    }

    // ─── Command parsers ──────────────────────────────────────────────────────

    private fun isAnswerCallCommand(lower: String) =
        (lower.contains("answer") || lower.contains("pick up") || lower.contains("accept")) &&
        (lower.contains("call") || lower.contains("phone"))

    private fun isDeclineCallCommand(lower: String) =
        (lower.contains("decline") || lower.contains("reject") || lower.contains("ignore")) &&
        (lower.contains("call") || lower.contains("phone"))

    private fun isCheckMessagesCommand(lower: String): Boolean {
        val msgWord = lower.contains("message") || lower.contains("whatsapp") ||
                lower.contains("notif") || lower.contains("inbox") || lower.contains("chat")
        val checkWord = lower.contains("check") || lower.contains("read") || lower.contains("show") ||
                lower.contains("any new") || lower.contains("what") || lower.contains("who messaged")
        return msgWord && checkWord
    }

    private fun isConfirmWord(lower: String) =
        lower.contains("send") || lower.contains("yes") || lower.contains("ok") ||
        lower.contains("do it") || lower.contains("go ahead") || lower.contains("confirm") ||
        lower.contains("sure") || lower == "yeah" || lower == "yep"

    private fun isCancelWord(lower: String) =
        lower.contains("cancel") || lower.contains("stop") || lower.contains("abort") ||
        lower.contains("no") || lower.contains("forget")

    private fun isStopWord(lower: String) =
        lower.contains("stop") || lower.contains("done") || lower.contains("finish") ||
        lower.contains("end") || lower.contains("that's it") || lower.contains("send it")

    private fun extractTypeInFieldText(lower: String): String? {
        val patterns = listOf(
            Regex("type (.+) (?:in|into|on) (?:the )?(?:search|field|bar|input|box)", RegexOption.IGNORE_CASE),
            Regex("(?:search|type) (?:for )?[\"'](.+)[\"']", RegexOption.IGNORE_CASE),
            Regex("(?:write|enter|put) (.+) (?:in|into) (?:the )?(?:search|field|bar)", RegexOption.IGNORE_CASE),
            Regex("type (.+) here", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(lower) ?: continue
            val t = m.groupValues[1].trim()
            if (t.isNotBlank()) return t
        }
        return null
    }

    private fun extractVoiceNoteTarget(lower: String): String? {
        if (!lower.contains("voice note") && !lower.contains("audio message") &&
            !lower.contains("voice message") && !lower.contains("record")) return null

        val patterns = listOf(
            Regex("(?:send|record|make) (?:a )?voice (?:note|message) (?:to|for) (\\w+)", RegexOption.IGNORE_CASE),
            Regex("(?:send|record) (?:a )?audio (?:message|note) (?:to|for) (\\w+)", RegexOption.IGNORE_CASE)
        )
        val stopWords = setOf("a", "the", "my", "me", "this")
        for (p in patterns) {
            val m = p.find(lower) ?: continue
            val name = m.groupValues[1].trim()
            if (name !in stopWords && name.length > 1) return name.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun extractWhatsAppTarget(lower: String): String? {
        if (!lower.contains("reply") && !lower.contains("respond") && !lower.contains("send") &&
            !lower.contains("message") && !lower.contains("text") && !lower.contains("whatsapp")
        ) return null

        val patterns = listOf(
            Regex("reply to (\\w+)"),
            Regex("respond to (\\w+)"),
            Regex("send (\\w+) a (?:whatsapp )?message"),
            Regex("send a (?:whatsapp )?message to (\\w+)"),
            Regex("message (\\w+) on whatsapp"),
            Regex("whatsapp (\\w+)"),
            Regex("text (\\w+)")
        )
        val stopWords = setOf("a", "the", "my", "me", "him", "her", "them", "it", "this", "that", "an")
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

    private fun launchQuip(label: String) = listOf(
        "Opening $label.", "Launching $label.", "On it — $label coming up.",
        "Opening $label now.", "Done. $label is launching."
    ).random()

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    fun clearHistory() = viewModelScope.launch { chatRepository.clearHistory() }
    fun dismissError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
        voiceNoteHelper.cleanup()
    }
}
