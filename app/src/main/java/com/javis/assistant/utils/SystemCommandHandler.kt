package com.javis.assistant.utils

import android.app.AlarmManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles all system-level JAVIS commands:
 * calls, WhatsApp chat, alarms, timers, SMS, web search, app-specific search.
 */
@Singleton
class SystemCommandHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    data class CommandResult(
        val success: Boolean,
        val message: String,
        val requiresFollowUp: Boolean = false
    )

    data class ResolvedContact(val name: String, val phone: String)

    // ─── Contact Lookup ────────────────────────────────────────────────────────

    fun findContacts(query: String): List<ResolvedContact> {
        val results = mutableListOf<ResolvedContact>()
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val phone = c.getString(1) ?: continue
                if (phone.isNotBlank()) results.add(ResolvedContact(name, normalizePhone(phone)))
            }
        }
        return results.distinctBy { it.name.lowercase() }
    }

    // ─── Call ─────────────────────────────────────────────────────────────────

    fun callContact(name: String): CommandResult {
        val contacts = findContacts(name)
        return when {
            contacts.isEmpty() -> CommandResult(false, "Couldn't find $name in your contacts.")
            contacts.size == 1 -> {
                dialNumber(contacts[0].phone)
                CommandResult(true, "Calling ${contacts[0].name}.")
            }
            else -> {
                // Multiple matches — return disambiguation message
                val names = contacts.take(4).joinToString(", ") { it.name }
                CommandResult(false, "I found a few: $names. Which one?", requiresFollowUp = true)
            }
        }
    }

    fun dialNumber(phone: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${normalizePhone(phone)}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ─── WhatsApp ─────────────────────────────────────────────────────────────

    /**
     * Opens WhatsApp directly to the contact's chat (with optional pre-filled message).
     * Looks up phone number from contacts.
     */
    fun openWhatsAppChat(contactName: String, message: String = ""): CommandResult {
        val contacts = findContacts(contactName)

        if (contacts.isEmpty()) {
            // Try opening WhatsApp directly — the user may type the chat name manually
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: return CommandResult(false, "WhatsApp doesn't appear to be installed.")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return CommandResult(false, "Couldn't find $contactName in your contacts. I've opened WhatsApp — search for them manually.")
        }

        val contact = contacts.first()
        val phone = contact.phone.replace(Regex("[^+0-9]"), "")

        return try {
            val uri = if (message.isNotBlank()) {
                Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://wa.me/$phone")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                CommandResult(true, if (message.isNotBlank()) "Opened ${contact.name}'s chat with your message ready to send." else "Opened ${contact.name} on WhatsApp.")
            } else {
                // Fallback to generic link without package constraint
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
                CommandResult(true, "Opened ${contact.name}'s chat.")
            }
        } catch (e: Exception) {
            CommandResult(false, "Couldn't open WhatsApp. Make sure it's installed.")
        }
    }

    // ─── SMS ──────────────────────────────────────────────────────────────────

    fun sendSms(contactName: String, message: String): CommandResult {
        val contacts = findContacts(contactName)
        if (contacts.isEmpty()) return CommandResult(false, "Couldn't find $contactName in contacts.")
        val phone = contacts.first().phone
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
            intent.putExtra("sms_body", message)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandResult(true, "Opened SMS to ${contacts.first().name}.")
        } catch (e: Exception) {
            CommandResult(false, "Couldn't open SMS app.")
        }
    }

    // ─── Alarms & Timers ──────────────────────────────────────────────────────

    fun setAlarm(hour: Int, minute: Int, label: String = "JAVIS Alarm"): CommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val amPm = if (hour < 12) "AM" else "PM"
            val h = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
            val m = minute.toString().padStart(2, '0')
            CommandResult(true, "Alarm set for $h:$m $amPm.")
        } catch (e: Exception) {
            CommandResult(false, "Couldn't set the alarm.")
        }
    }

    fun setTimer(minutes: Int, label: String = ""): CommandResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult(true, "Timer set for $minutes ${if (minutes == 1) "minute" else "minutes"}.")
        } catch (e: Exception) {
            CommandResult(false, "Couldn't set the timer.")
        }
    }

    // ─── App Search ───────────────────────────────────────────────────────────

    fun searchInApp(packageName: String, appLabel: String, query: String): CommandResult {
        return try {
            val launched = when (packageName) {
                "com.google.android.youtube" -> searchYouTube(query)
                "com.zhiliaoapp.musically" -> searchTikTok(query)
                "com.google.android.apps.maps" -> searchMaps(query)
                "com.spotify.music" -> searchSpotify(query)
                "com.android.chrome" -> searchChrome(query)
                else -> searchGeneric(packageName, query)
            }
            if (launched) CommandResult(true, "Searching for \"$query\" on $appLabel.")
            else CommandResult(false, "Couldn't search in $appLabel.")
        } catch (e: Exception) {
            CommandResult(false, "Couldn't search in $appLabel.")
        }
    }

    private fun searchYouTube(query: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent); true
            } else {
                launchWithUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            }
        } catch (e: Exception) {
            launchWithUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        }
    }

    private fun searchTikTok(query: String): Boolean {
        return launchWithUrl("https://www.tiktok.com/search?q=${Uri.encode(query)}")
    }

    private fun searchMaps(query: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent); true
        } catch (e: Exception) { false }
    }

    private fun searchSpotify(query: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent); true
        } catch (e: Exception) { false }
    }

    private fun searchChrome(query: String): Boolean {
        return launchWithUrl("https://www.google.com/search?q=${Uri.encode(query)}")
    }

    private fun searchGeneric(packageName: String, query: String): Boolean {
        return launchWithUrl("https://www.google.com/search?q=${Uri.encode(query)}")
    }

    // ─── Web Search ───────────────────────────────────────────────────────────

    fun webSearch(query: String): CommandResult {
        return if (launchWithUrl("https://www.google.com/search?q=${Uri.encode(query)}")) {
            CommandResult(true, "Searching for \"$query\".")
        } else {
            CommandResult(false, "Couldn't open the browser.")
        }
    }

    // ─── Parse JSON action from AI response ──────────────────────────────────

    fun parseAndExecute(json: String): CommandResult {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            when (obj.get("action")?.asString) {
                "CALL_CONTACT" -> callContact(obj.get("name")?.asString ?: "")
                "CALL_NUMBER" -> {
                    val num = obj.get("number")?.asString ?: ""
                    dialNumber(num)
                    CommandResult(true, "Dialling $num.")
                }
                "WHATSAPP_CHAT" -> openWhatsAppChat(
                    obj.get("name")?.asString ?: "",
                    obj.get("message")?.asString ?: ""
                )
                "SET_ALARM" -> setAlarm(
                    obj.get("hour")?.asInt ?: 7,
                    obj.get("minute")?.asInt ?: 0,
                    obj.get("label")?.asString ?: "JAVIS Alarm"
                )
                "SET_TIMER" -> setTimer(
                    obj.get("minutes")?.asInt ?: 1,
                    obj.get("label")?.asString ?: ""
                )
                "WEB_SEARCH" -> webSearch(obj.get("query")?.asString ?: "")
                "SEND_SMS" -> sendSms(
                    obj.get("name")?.asString ?: "",
                    obj.get("message")?.asString ?: ""
                )
                "SEARCH_IN_APP" -> searchInApp(
                    obj.get("package")?.asString ?: "",
                    obj.get("label")?.asString ?: "App",
                    obj.get("query")?.asString ?: ""
                )
                else -> CommandResult(false, "Unknown action.")
            }
        } catch (e: Exception) {
            CommandResult(false, "Couldn't execute that action.")
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun launchWithUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent); true
        } catch (e: Exception) { false }
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.replace(Regex("[\\s\\-()]"), "")
        return digits
    }
}
