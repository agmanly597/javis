package com.javis.assistant.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import com.javis.assistant.accessibility.JavisAccessibilityService
import com.javis.assistant.ai.*
import com.javis.assistant.data.db.MessageDao
import com.javis.assistant.data.model.AiMessage
import com.javis.assistant.data.model.AiProvider
import com.javis.assistant.data.model.Message
import com.javis.assistant.data.model.MessageRole
import com.javis.assistant.memory.MemoryManager
import com.javis.assistant.notifications.JavisNotificationListenerService
import com.javis.assistant.storage.JavisPreferences
import com.javis.assistant.voice.AndroidTtsFallback
import com.javis.assistant.voice.ElevenLabsTts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavisRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val groqProvider: GroqProvider,
    private val deepSeekProvider: DeepSeekProvider,
    private val messageDao: MessageDao,
    private val memoryManager: MemoryManager,
    private val prefs: JavisPreferences,
    private val elevenLabsTts: ElevenLabsTts,
    private val androidTts: AndroidTtsFallback,
    private val commandParser: CommandParser
) {
    val currentSessionId: String = UUID.randomUUID().toString()

    fun getMessages(sessionId: String): Flow<List<Message>> =
        messageDao.getMessagesBySession(sessionId)

    suspend fun sendMessage(userText: String, sessionId: String): Result<String> {
        // Persist user message
        messageDao.insert(Message(sessionId = sessionId, role = MessageRole.USER, content = userText))

        // Background memory learning
        memoryManager.learnFromConversation(userText)

        // Parse for direct device commands
        val parsed = commandParser.parse(userText)
        if (parsed.type != CommandType.UNKNOWN && !parsed.requiresConfirmation) {
            executeCommand(parsed)
        }

        // Build AI context
        val recentMessages = messageDao.getRecentMessages(20)
        val contextSummary = memoryManager.buildContextSummary()
        val systemPrompt = buildString {
            append(JavisPersonality.SYSTEM_PROMPT)
            if (contextSummary.isNotBlank()) append("\n\nWhat you know about this user: $contextSummary")
        }

        val aiMessages = recentMessages
            .filter { it.role != MessageRole.SYSTEM }
            .takeLast(16)
            .map { AiMessage(it.role.name.lowercase(), it.content) }

        // Choose AI provider
        val providerPref = prefs.aiProvider.first()
        val provider: AiProvider = providerPref

        // Sync API keys from prefs
        groqProvider.apply { GroqProvider.apiKey = prefs.groqApiKey.first() }
        deepSeekProvider.apply { DeepSeekProvider.apiKey = prefs.deepSeekApiKey.first() }

        val activeProvider: com.javis.assistant.ai.AiProvider = when (provider) {
            AiProvider.GROQ -> groqProvider
            AiProvider.DEEPSEEK -> deepSeekProvider
        }

        val result = activeProvider.chat(aiMessages, systemPrompt)

        result.onSuccess { reply ->
            messageDao.insert(
                Message(sessionId = sessionId, role = MessageRole.ASSISTANT, content = reply)
            )
            speak(reply)
        }

        result.onFailure { error ->
            // Fallback to other provider
            val fallbackProvider: com.javis.assistant.ai.AiProvider =
                if (provider == AiProvider.GROQ) deepSeekProvider else groqProvider
            val fallback = fallbackProvider.chat(aiMessages, systemPrompt)
            fallback.onSuccess { reply ->
                messageDao.insert(
                    Message(sessionId = sessionId, role = MessageRole.ASSISTANT, content = reply)
                )
                speak(reply)
                return fallback
            }
        }

        return result
    }

    suspend fun speak(text: String) {
        val useElevenLabs = prefs.useElevenLabs.first()
        val elKey = prefs.elevenLabsApiKey.first()

        if (useElevenLabs && elKey.isNotBlank()) {
            elevenLabsTts.apiKey = elKey
            elevenLabsTts.voiceId = prefs.elevenLabsVoiceId.first().ifBlank {
                ElevenLabsTts.DEFAULT_VOICE_ID
            }
            elevenLabsTts.speak(text)
        } else {
            androidTts.speechRate = prefs.speechRate.first()
            androidTts.pitch = prefs.ttsPitch.first()
            androidTts.speak(text)
        }
    }

    fun stopSpeaking() {
        elevenLabsTts.stopSpeaking()
        androidTts.stop()
    }

    fun executeCommand(cmd: ParsedCommand) {
        when (cmd.type) {
            CommandType.OPEN_APP -> launchApp(cmd.target)
            CommandType.SEARCH_WEB -> searchWeb(cmd.target)
            CommandType.SEARCH_YOUTUBE -> searchYoutube(cmd.target)
            CommandType.OPEN_SETTINGS -> openSettings()
            CommandType.TAKE_PHOTO -> openCamera()
            CommandType.OPEN_WHATSAPP_CHAT -> openWhatsAppChat(cmd.target)
            else -> {}
        }
    }

    suspend fun executeConfirmedCommand(cmd: ParsedCommand) {
        when (cmd.type) {
            CommandType.CALL_CONTACT -> callContact(cmd.target)
            CommandType.CALL_NUMBER -> callNumber(cmd.target)
            CommandType.SET_ALARM -> setAlarm(cmd.target)
            CommandType.SEND_WHATSAPP -> sendWhatsAppMessage(cmd.target, cmd.extra)
            else -> executeCommand(cmd)
        }
    }

    fun launchApp(packageNameOrAlias: String) {
        val pkg = commandParser.getPackageName(packageNameOrAlias)
        val intent = commandParser.getLaunchIntent(pkg)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (e: Exception) {}
    }

    fun searchWeb(query: String) {
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (e: Exception) {}
    }

    fun searchYoutube(query: String) {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    fun openSettings() {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (e: Exception) {}
    }

    fun openWhatsAppChat(contactName: String) {
        launchApp("com.whatsapp")
    }

    suspend fun sendWhatsAppMessage(contact: String, message: String): Boolean {
        // Step 1: Open WhatsApp
        launchApp("com.whatsapp")
        kotlinx.coroutines.delay(2000)

        val acc = JavisAccessibilityService.getInstance() ?: return false

        // Step 2: Try to find and open chat by name
        val opened = acc.openWhatsAppChatByName(contact)
        if (!opened) return false

        kotlinx.coroutines.delay(1500)

        // Step 3: Type and send
        return acc.whatsAppSendMessage(message)
    }

    fun callContact(name: String) {
        val uri = Uri.parse("tel:")
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Search contacts
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val number = it.getString(0)
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(callIntent) } catch (e: Exception) {}
                return
            }
        }

        // Fallback: open dialer with search
        try { context.startActivity(intent) } catch (e: Exception) {}
    }

    fun callNumber(number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (e: Exception) {}
    }

    fun setAlarm(description: String) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_MESSAGE, "JAVIS Alarm")
            // TODO: parse time from description with NLP
        }
        try { context.startActivity(intent) } catch (e: Exception) {}
    }

    fun readNotifications(): String {
        val service = JavisNotificationListenerService.getInstance()
        if (service == null) return "Notification access isn't enabled. Go to Settings to allow it."
        val items = service.getActiveNotifications()
        if (items.isEmpty()) return "You have no active notifications right now."
        return items.take(10).joinToString("\n") { "From ${it.appName}: ${it.title} — ${it.text}" }
    }

    suspend fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val userName = memoryManager.getUserName()
        val name = if (userName != null) ", $userName" else ""
        return when (hour) {
            in 5..11 -> "Good morning$name. JAVIS online. How can I assist?"
            in 12..16 -> "Good afternoon$name. JAVIS ready. What do you need?"
            in 17..20 -> "Good evening$name. JAVIS at your service."
            else -> "Hey$name, it's late. JAVIS is here though — what's up?"
        }
    }

    suspend fun clearHistory() = messageDao.deleteAll()
}
