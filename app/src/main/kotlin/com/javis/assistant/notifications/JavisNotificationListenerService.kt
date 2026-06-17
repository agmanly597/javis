package com.javis.assistant.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.javis.assistant.data.model.NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class JavisNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private var instance: JavisNotificationListenerService? = null

        fun getInstance() = instance

        private val _newNotification = MutableSharedFlow<NotificationItem>(extraBufferCapacity = 20)
        val newNotification: SharedFlow<NotificationItem> = _newNotification

        fun isEnabled() = instance != null

        // Packages to ignore (system noise)
        private val IGNORE_PACKAGES = setOf(
            "android", "com.android.systemui", "com.android.settings",
            "com.google.android.gms", "com.android.vending"
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (IGNORE_PACKAGES.contains(pkg)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: return
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) { pkg }

        val item = NotificationItem(
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            key = sbn.key
        )

        scope.launch {
            _newNotification.emit(item)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Can observe removals if needed
    }

    fun getFilteredNotifications(): List<NotificationItem> {
        return try {
            activeNotifications?.mapNotNull { sbn ->
                val pkg = sbn.packageName ?: return@mapNotNull null
                if (IGNORE_PACKAGES.contains(pkg)) return@mapNotNull null
                val extras = sbn.getNotification()?.extras ?: return@mapNotNull null
                val title = extras.getString("android.title") ?: return@mapNotNull null
                val text = extras.getCharSequence("android.text")?.toString() ?: ""
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } catch (e: Exception) { pkg }
                NotificationItem(
                    packageName = pkg,
                    appName = appName,
                    title = title,
                    text = text,
                    key = sbn.key
                )
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun dismissNotification(key: String) {
        try { cancelNotification(key) } catch (e: Exception) {}
    }
}
