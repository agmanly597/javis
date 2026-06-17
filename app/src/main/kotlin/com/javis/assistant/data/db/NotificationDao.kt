package com.javis.assistant.data.db

import androidx.room.*
import com.javis.assistant.data.model.NotificationItem
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT 100")
    fun getAll(): Flow<List<NotificationItem>>

    @Query("SELECT * FROM notifications WHERE packageName = :pkg ORDER BY timestamp DESC LIMIT 20")
    suspend fun getByApp(pkg: String): List<NotificationItem>

    @Query("SELECT * FROM notifications WHERE isRead = 0 ORDER BY timestamp DESC")
    suspend fun getUnread(): List<NotificationItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NotificationItem)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}
