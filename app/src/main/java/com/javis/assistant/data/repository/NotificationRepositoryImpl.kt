package com.javis.assistant.data.repository

import com.javis.assistant.data.local.dao.NotificationDao
import com.javis.assistant.data.local.entities.NotificationEntity
import com.javis.assistant.domain.model.NotificationItem
import com.javis.assistant.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao
) : NotificationRepository {

    override fun getNotifications(): Flow<List<NotificationItem>> =
        notificationDao.getNotifications().map { it.map { e -> e.toDomain() } }

    override suspend fun insertNotification(notification: NotificationItem) {
        notificationDao.insertNotification(notification.toEntity())
    }

    override suspend fun markAsRead(id: String) {
        notificationDao.markAsRead(id)
    }

    override suspend fun clearAll() {
        notificationDao.clearAll()
    }

    private fun NotificationEntity.toDomain() = NotificationItem(
        id = id, packageName = packageName, appName = appName,
        title = title, text = text, timestamp = timestamp, isRead = isRead
    )

    private fun NotificationItem.toEntity() = NotificationEntity(
        id = id, packageName = packageName, appName = appName,
        title = title, text = text, timestamp = timestamp, isRead = isRead
    )
}
