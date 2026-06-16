package com.javis.assistant.whatsapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsAppReplyManager @Inject constructor() {

    data class WhatsAppMessage(
        val sender: String,
        val message: String,
        val timestamp: Long,
        val replyPendingIntent: PendingIntent?,
        val remoteInputKey: String?,
        val remoteInputs: Array<RemoteInput>?
    )

    private val _messages = mutableListOf<WhatsAppMessage>()

    /** Called by the notification listener whenever a WhatsApp notification arrives */
    @Synchronized
    fun onWhatsAppNotification(msg: WhatsAppMessage) {
        // Keep only the latest message per sender
        _messages.removeAll { it.sender.equals(msg.sender, ignoreCase = true) }
        _messages.add(0, msg)
        // Cap list to 50 entries
        while (_messages.size > 50) _messages.removeAt(_messages.lastIndex)
    }

    /** Returns recent messages, newest first */
    @Synchronized
    fun getRecentMessages(count: Int = 15): List<WhatsAppMessage> = _messages.take(count)

    /** Returns messages from a specific sender */
    @Synchronized
    fun getMessagesFrom(sender: String): List<WhatsAppMessage> =
        _messages.filter { it.sender.contains(sender, ignoreCase = true) }

    /** Sends a reply to the most recent message from `sender` via notification RemoteInput */
    @Synchronized
    fun sendReply(context: Context, sender: String, replyText: String): Boolean {
        val msg = _messages.find { it.sender.contains(sender, ignoreCase = true) } ?: return false
        val pendingIntent = msg.replyPendingIntent ?: return false
        val key = msg.remoteInputKey ?: return false

        return try {
            val replyIntent = Intent()
            val bundle = Bundle().apply { putCharSequence(key, replyText) }
            val inputs = msg.remoteInputs ?: arrayOf(RemoteInput.Builder(key).build())
            RemoteInput.addResultsToIntent(inputs, replyIntent, bundle)
            pendingIntent.send(context, 0, replyIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Format messages as a spoken summary */
    fun buildSpokenSummary(messages: List<WhatsAppMessage>): String {
        if (messages.isEmpty()) return "No recent WhatsApp messages, sir."
        val grouped = messages.groupBy { it.sender }
        val parts = grouped.entries.take(5).map { (sender, msgs) ->
            val latest = msgs.first()
            if (msgs.size == 1) {
                "$sender says: ${latest.message}"
            } else {
                "$sender has ${msgs.size} messages. Latest: ${latest.message}"
            }
        }
        val intro = when (parts.size) {
            1 -> "You have one WhatsApp message. "
            else -> "You have messages from ${parts.size} contacts. "
        }
        return intro + parts.joinToString(". ")
    }

    /** Check if there are any unread messages */
    fun hasMessages() = _messages.isNotEmpty()

    /** Clear all stored messages */
    @Synchronized
    fun clear() = _messages.clear()
}
