package com.javis.assistant.notifications

import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.javis.assistant.domain.model.NotificationItem
import com.javis.assistant.domain.repository.NotificationRepository
import com.javis.assistant.whatsapp.WhatsAppReplyManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class JavisNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var whatsAppReplyManager: WhatsAppReplyManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ignoredPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.server.telecom",
        "com.javis.assistant",
        "com.javis.assistant.debug"
    )

    private val whatsAppPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",        // WhatsApp Business
        "com.gbwhatsapp",           // GB WhatsApp
        "com.whatsapp.plus"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName in ignoredPackages) return
        if (!sbn.isClearable) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank()) return

        val appName = getAppName(sbn.packageName)

        // Store in local DB
        val item = NotificationItem(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )
        serviceScope.launch { notificationRepository.insertNotification(item) }

        // Special handling for WhatsApp — capture reply action
        if (sbn.packageName in whatsAppPackages) {
            captureWhatsAppReplyAction(sbn, title, text)
        }
    }

    private fun captureWhatsAppReplyAction(sbn: StatusBarNotification, sender: String, message: String) {
        val actions = sbn.notification?.actions ?: return

        // Find the Reply action
        val replyAction = actions.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase() ?: ""
            label.contains("reply") || label.contains("respond") || label.contains("repl")
        } ?: actions.firstOrNull() // fallback to first action if no obvious reply action

        if (replyAction != null) {
            // Extract RemoteInput for typing the reply
            val remoteInputs = RemoteInput.getInputsFromIntent(replyAction.actionIntent?.intent ?: return)
                ?: replyAction.remoteInputs?.map {
                    RemoteInput.Builder(it.resultKey)
                        .setLabel(it.label)
                        .build()
                }?.toTypedArray()

            val key = remoteInputs?.firstOrNull()?.resultKey
                ?: replyAction.remoteInputs?.firstOrNull()?.resultKey

            val msg = WhatsAppReplyManager.WhatsAppMessage(
                sender = sender,
                message = message,
                timestamp = sbn.postTime,
                replyPendingIntent = replyAction.actionIntent,
                remoteInputKey = key ?: "text_reply",
                remoteInputs = remoteInputs
            )
            whatsAppReplyManager.onWhatsAppNotification(msg)
        } else {
            // Still store the message even if we can't reply
            val msg = WhatsAppReplyManager.WhatsAppMessage(
                sender = sender,
                message = message,
                timestamp = sbn.postTime,
                replyPendingIntent = null,
                remoteInputKey = null,
                remoteInputs = null
            )
            whatsAppReplyManager.onWhatsAppNotification(msg)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Keep in local DB for history — intentional
    }

    private fun getAppName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.split(".").last().replaceFirstChar { it.uppercase() }
        }
    }
}
