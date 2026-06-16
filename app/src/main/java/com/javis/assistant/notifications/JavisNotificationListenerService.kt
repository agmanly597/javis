package com.javis.assistant.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.javis.assistant.domain.model.NotificationItem
import com.javis.assistant.domain.repository.NotificationRepository
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ignoredPackages = setOf(
        "android",
        "com.android.systemui",
        "com.javis.assistant",
        "com.javis.assistant.debug"
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

        val notification = NotificationItem(
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        serviceScope.launch {
            notificationRepository.insertNotification(notification)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notifications are kept in our local DB for history
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.split(".").last().replaceFirstChar { it.uppercase() }
        }
    }
}
