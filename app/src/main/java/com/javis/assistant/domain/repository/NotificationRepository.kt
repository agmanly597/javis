package com.javis.assistant.domain.repository

import com.javis.assistant.domain.model.NotificationItem
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(): Flow<List<NotificationItem>>
    suspend fun insertNotification(notification: NotificationItem)
    suspend fun markAsRead(id: String)
    suspend fun clearAll()
}
