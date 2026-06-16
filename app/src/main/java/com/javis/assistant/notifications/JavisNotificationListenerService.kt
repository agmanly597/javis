package com.javis.assistant.notifications

import android.app.RemoteInput
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
        "com.whatsapp.w4b",
        "com.gbwhatsapp",
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

        serviceScope.launch {
            notificationRepository.insertNotification(
                NotificationItem(
                    packageName = sbn.packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    timestamp = sbn.postTime
                )
            )
        }

        if (sbn.packageName in whatsAppPackages) {
            captureWhatsAppReplyAction(sbn, sender = title, message = text)
        }
    }

    private fun captureWhatsAppReplyAction(
        sbn: StatusBarNotification,
        sender: String,
        message: String
    ) {
        val actions = sbn.notification?.actions ?: run {
            // Still store message even without reply capability
            storeWhatsAppMessage(sender, message, sbn.postTime, null, null, null)
            return
        }

        // Find the Reply action by checking action labels
        val replyAction = actions.firstOrNull { action ->
            val label = action.title?.toString()?.lowercase() ?: ""
            label.contains("reply") || label.contains("respond")
        } ?: actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true }

        if (replyAction != null) {
            // remoteInputs are android.app.RemoteInput[] — directly from framework
            val inputs: Array<RemoteInput>? = replyAction.remoteInputs
            val key = inputs?.firstOrNull()?.resultKey ?: "text_reply"

            storeWhatsAppMessage(
                sender = sender,
                message = message,
                timestamp = sbn.postTime,
                replyPendingIntent = replyAction.actionIntent,
                remoteInputKey = key,
                remoteInputs = inputs
            )
        } else {
            storeWhatsAppMessage(sender, message, sbn.postTime, null, null, null)
        }
    }

    private fun storeWhatsAppMessage(
        sender: String,
        message: String,
        timestamp: Long,
        replyPendingIntent: android.app.PendingIntent?,
        remoteInputKey: String?,
        remoteInputs: Array<RemoteInput>?
    ) {
        whatsAppReplyManager.onWhatsAppNotification(
            WhatsAppReplyManager.WhatsAppMessage(
                sender = sender,
                message = message,
                timestamp = timestamp,
                replyPendingIntent = replyPendingIntent,
                remoteInputKey = remoteInputKey,
                remoteInputs = remoteInputs
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Keep in local DB for history
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
