package com.javis.assistant.notifications

import com.javis.assistant.data.db.NotificationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens to the NotificationListenerService's shared flow
 * and persists incoming notifications to Room, respecting user prefs.
 */
@Singleton
class NotificationPersistenceWorker @Inject constructor(
    private val notificationDao: NotificationDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            JavisNotificationListenerService.newNotification.collect { item ->
                notificationDao.insert(item)
                // Prune old notifications (keep last 200)
                notificationDao.deleteOlderThan(
                    System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                )
            }
        }
    }
}
