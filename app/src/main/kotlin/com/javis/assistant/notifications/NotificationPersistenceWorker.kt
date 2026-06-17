package com.javis.assistant.notifications

import com.javis.assistant.data.db.NotificationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens to the NotificationListenerService's shared flow and persists
 * incoming notifications to Room.
 *
 * Intentionally has NO @Inject constructor so the Hilt module can control
 * exactly when it is instantiated (eager singleton at app start via
 * JavisApplication injection).
 */
class NotificationPersistenceWorker(
    private val notificationDao: NotificationDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            JavisNotificationListenerService.newNotification.collect { item ->
                notificationDao.insert(item)
                notificationDao.deleteOlderThan(
                    System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                )
            }
        }
    }
}
