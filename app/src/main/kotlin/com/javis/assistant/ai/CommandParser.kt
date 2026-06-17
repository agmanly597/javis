package com.javis.assistant.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class CommandType {
    OPEN_APP, CALL_CONTACT, CALL_NUMBER, SET_ALARM, SEARCH_WEB,
    SEND_WHATSAPP, READ_NOTIFICATIONS, OPEN_SETTINGS, TAKE_PHOTO,
    SEARCH_YOUTUBE, OPEN_WHATSAPP_CHAT, UNKNOWN
}

data class ParsedCommand(
    val type: CommandType,
    val target: String = "",
    val extra: String = "",
    val requiresConfirmation: Boolean = false,
    val confirmationMessage: String = ""
)

@Singleton
class CommandParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val appAliases = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "camera" to "android.media.action.IMAGE_CAPTURE",
        "calculator" to "com.android.calculator2",
        "settings" to "android.settings.SETTINGS",
        "contacts" to "com.android.contacts",
        "files" to "com.android.documentsui",
        "gallery" to "com.android.gallery3d",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "phone" to "com.android.dialer",
        "messages" to "com.android.messaging",
        "facebook" to "com.facebook.katana",
        "instagram" to "com.instagram.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "telegram" to "org.telegram.messenger",
        "twitter" to "com.twitter.android",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "snapchat" to "com.snapchat.android",
        "deepseek" to "com.deepseek.chat",
        "chatgpt" to "com.openai.chatgpt",
        "clock" to "com.android.deskclock",
        "alarm" to "com.android.deskclock",
        "calendar" to "com.android.calendar",
        "browser" to "com.android.chrome",
        "music" to "com.google.android.music",
        "amazon" to "com.amazon.mShop.android.shopping"
    )

    fun parse(input: String): ParsedCommand {
        val lower = input.lowercase().trim()

        return when {
            lower.matches(Regex(".*(open|launch|start|go to|take me to)\\s+(\\w+).*")) -> {
                val app = extractAppName(lower, listOf("open", "launch", "start", "go to", "take me to"))
                ParsedCommand(CommandType.OPEN_APP, target = app)
            }

            lower.matches(Regex(".*(call|dial|ring|phone)\\s+(.+)")) -> {
                val contact = extractAfter(lower, listOf("call", "dial", "ring", "phone"))
                ParsedCommand(
                    CommandType.CALL_CONTACT,
                    target = contact,
                    requiresConfirmation = true,
                    confirmationMessage = "Call $contact?"
                )
            }

            lower.matches(Regex(".*set.*alarm.*for.*|.*wake me.*at.*|.*remind me.*at.*")) -> {
                ParsedCommand(CommandType.SET_ALARM, target = lower, requiresConfirmation = true,
                    confirmationMessage = "Set an alarm?")
            }

            lower.matches(Regex(".*(search|look up|google|find)\\s+(.+)(on youtube|on yt).*")) -> {
                val query = extractSearchQuery(lower)
                ParsedCommand(CommandType.SEARCH_YOUTUBE, target = query)
            }

            lower.matches(Regex(".*(search|look up|google|find|browse)\\s+(.+)")) -> {
                val query = extractSearchQuery(lower)
                ParsedCommand(CommandType.SEARCH_WEB, target = query)
            }

            lower.matches(Regex(".*(reply|send|message|text|whatsapp).*to\\s+(\\w+).*|.*send.*message.*to\\s+(\\w+).*")) -> {
                val to = extractRecipient(lower)
                val msg = extractMessage(lower)
                ParsedCommand(
                    CommandType.SEND_WHATSAPP,
                    target = to,
                    extra = msg,
                    requiresConfirmation = true,
                    confirmationMessage = "Send \"$msg\" to $to on WhatsApp?"
                )
            }

            lower.contains("notifications") || lower.contains("what did i miss") ||
                    lower.contains("any messages") -> {
                ParsedCommand(CommandType.READ_NOTIFICATIONS)
            }

            lower.contains("settings") || lower.contains("wi-fi") || lower.contains("wifi") ||
                    lower.contains("bluetooth") -> {
                ParsedCommand(CommandType.OPEN_SETTINGS)
            }

            else -> ParsedCommand(CommandType.UNKNOWN)
        }
    }

    private fun extractAppName(input: String, prefixes: List<String>): String {
        var result = input
        for (prefix in prefixes) {
            result = result.replace(prefix, "").trim()
        }
        return appAliases[result.trim()] ?: result.trim()
    }

    private fun extractAfter(input: String, words: List<String>): String {
        var result = input
        for (word in words) {
            val idx = result.indexOf(word)
            if (idx >= 0) {
                result = result.substring(idx + word.length).trim()
                break
            }
        }
        return result.trim()
    }

    private fun extractSearchQuery(input: String): String {
        return input
            .replace(Regex("(search|look up|google|find|browse|for|on youtube|on yt)"), "")
            .trim()
    }

    private fun extractRecipient(input: String): String {
        val regex = Regex("(?:to|for)\\s+(\\w+)")
        return regex.find(input)?.groupValues?.get(1) ?: ""
    }

    private fun extractMessage(input: String): String {
        val patterns = listOf(
            Regex("(?:saying|message|text|that|tell them)\\s+[\"']?(.+)[\"']?$"),
            Regex("(?:reply|respond)\\s+(?:to \\w+ )?(?:with|saying)?\\s+[\"']?(.+)[\"']?$")
        )
        for (p in patterns) {
            p.find(input)?.groupValues?.get(1)?.let { return it.trim() }
        }
        return input
    }

    fun getPackageName(appAlias: String): String {
        return appAliases[appAlias.lowercase()] ?: appAlias
    }

    fun getLaunchIntent(packageNameOrAction: String): Intent? {
        return when {
            packageNameOrAction.startsWith("android.") ||
                    packageNameOrAction.startsWith("com.android.") -> {
                try {
                    Intent(packageNameOrAction).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                } catch (e: Exception) {
                    context.packageManager.getLaunchIntentForPackage(packageNameOrAction)
                }
            }
            else -> context.packageManager.getLaunchIntentForPackage(packageNameOrAction)
        }
    }
}
