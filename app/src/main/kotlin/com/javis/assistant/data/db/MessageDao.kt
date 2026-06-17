package com.javis.assistant.data.db

import androidx.room.*
import com.javis.assistant.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 50): List<Message>

    @Query("SELECT DISTINCT sessionId FROM messages ORDER BY timestamp DESC")
    suspend fun getAllSessionIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Delete
    suspend fun delete(message: Message)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
