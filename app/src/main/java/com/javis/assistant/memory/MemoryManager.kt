package com.javis.assistant.memory

import com.javis.assistant.domain.model.MemoryCategory
import com.javis.assistant.domain.model.UserMemory
import com.javis.assistant.domain.repository.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val memoryRepository: MemoryRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun rememberUserName(name: String) {
        scope.launch {
            memoryRepository.upsertMemory(
                UserMemory(key = "user_name", value = name, category = MemoryCategory.PERSONAL)
            )
        }
    }

    fun rememberPreference(key: String, value: String) {
        scope.launch {
            memoryRepository.upsertMemory(
                UserMemory(key = key, value = value, category = MemoryCategory.PREFERENCE)
            )
        }
    }

    fun rememberAppUsage(appName: String) {
        scope.launch {
            memoryRepository.upsertMemory(
                UserMemory(
                    key = "app_$appName",
                    value = appName,
                    category = MemoryCategory.APP_USAGE
                )
            )
        }
    }

    fun rememberCommand(command: String, action: String) {
        scope.launch {
            memoryRepository.upsertMemory(
                UserMemory(
                    key = "cmd_${command.take(30)}",
                    value = action,
                    category = MemoryCategory.COMMAND
                )
            )
        }
    }

    fun rememberFact(key: String, value: String) {
        scope.launch {
            memoryRepository.upsertMemory(
                UserMemory(key = key, value = value, category = MemoryCategory.GENERAL)
            )
        }
    }

    fun extractAndSaveFromConversation(userMessage: String, assistantResponse: String) {
        scope.launch {
            extractNameFromMessage(userMessage)
            extractPreferenceFromMessage(userMessage)
        }
    }

    private suspend fun extractNameFromMessage(message: String) {
        val lower = message.lowercase()
        val namePatterns = listOf("my name is ", "i am ", "i'm ", "call me ")
        for (pattern in namePatterns) {
            if (lower.contains(pattern)) {
                val idx = lower.indexOf(pattern) + pattern.length
                val name = message.substring(idx).split(" ", ",", ".", "!").firstOrNull()?.trim()
                if (!name.isNullOrBlank() && name.length > 1) {
                    rememberUserName(name.replaceFirstChar { it.uppercase() })
                    return
                }
            }
        }
    }

    private suspend fun extractPreferenceFromMessage(message: String) {
        val lower = message.lowercase()
        val prefPatterns = mapOf(
            "i like " to "preference_like",
            "i love " to "preference_love",
            "i prefer " to "preference",
            "i use " to "preference_app"
        )
        for ((pattern, key) in prefPatterns) {
            if (lower.contains(pattern)) {
                val idx = lower.indexOf(pattern) + pattern.length
                val value = message.substring(idx).split(",", ".", "!").firstOrNull()?.trim()
                if (!value.isNullOrBlank()) {
                    rememberPreference("${key}_${System.currentTimeMillis()}", value)
                    return
                }
            }
        }
    }
}
