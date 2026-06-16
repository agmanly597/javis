package com.javis.assistant.whatsapp

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that captures incoming WhatsApp notification data and provides
 * the ability to fire a reply via notification RemoteInput — no root needed.
 */
@Singleton
class WhatsAppReplyManager @Inject constructor() {

    data class WhatsAppMessage(
        val sender: String,
        val message: String,
        val timestamp: Long,
        val replyPendingIntent: PendingIntent?,
        val remoteInputKey: String?,
        val remoteInputs: Array<RemoteInput>?   // android.app.RemoteInput
    )

    private val _messages = mutableListOf<WhatsAppMessage>()

    @Synchronized
    fun onWhatsAppNotification(msg: WhatsAppMessage) {
        _messages.removeAll { it.sender.equals(msg.sender, ignoreCase = true) }
        _messages.add(0, msg)
        while (_messages.size > 50) _messages.removeAt(_messages.lastIndex)
    }

    @Synchronized
    fun getRecentMessages(count: Int = 15): List<WhatsAppMessage> = _messages.take(count)

    @Synchronized
    fun getMessagesFrom(sender: String): List<WhatsAppMessage> =
        _messages.filter { it.sender.contains(sender, ignoreCase = true) }

    /**
     * Fire a reply to [sender] using the stored notification action's PendingIntent.
     * Uses android.app.RemoteInput (framework class, always available).
     */
    @Synchronized
    fun sendReply(context: Context, sender: String, replyText: String): Boolean {
        val msg = _messages.find { it.sender.contains(sender, ignoreCase = true) } ?: return false
        val pendingIntent = msg.replyPendingIntent ?: return false
        val key = msg.remoteInputKey ?: return false

        return try {
            val replyIntent = Intent()
            val bundle = Bundle().apply { putCharSequence(key, replyText) }
            val inputs = msg.remoteInputs?.takeIf { it.isNotEmpty() }
                ?: arrayOf(RemoteInput.Builder(key).build())
            RemoteInput.addResultsToIntent(inputs, replyIntent, bundle)
            pendingIntent.send(context, 0, replyIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun buildSpokenSummary(messages: List<WhatsAppMessage>): String {
        if (messages.isEmpty()) return "No recent WhatsApp messages, sir."
        val grouped = messages.groupBy { it.sender }
        val parts = grouped.entries.take(5).map { (sender, msgs) ->
            val latest = msgs.first()
            if (msgs.size == 1) "$sender says: ${latest.message}"
            else "$sender has ${msgs.size} messages. Latest: ${latest.message}"
        }
        val intro = if (parts.size == 1) "You have one WhatsApp message. "
                    else "You have messages from ${parts.size} contacts. "
        return intro + parts.joinToString(". ")
    }

    fun hasMessages() = _messages.isNotEmpty()

    @Synchronized
    fun clear() = _messages.clear()
}
