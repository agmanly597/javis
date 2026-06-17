package com.javis.assistant.data.model

data class AppInfo(
    val packageName: String,
    val appName: String,
    val launchIntent: String = "",
    val category: String = "app"
)

data class ContactInfo(
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)

data class AiMessage(
    val role: String,
    val content: String
)

data class VoiceState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialText: String = "",
    val isProcessing: Boolean = false
)

enum class AiProvider { GROQ, DEEPSEEK }
