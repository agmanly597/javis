package com.javis.assistant.data.db

import androidx.room.*
import com.javis.assistant.data.model.Memory
import com.javis.assistant.data.model.MemoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY useCount DESC, lastUsed DESC")
    fun getAllMemories(): Flow<List<Memory>>

    @Query("SELECT * FROM memories WHERE type = :type")
    suspend fun getByType(type: MemoryType): List<Memory>

    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): Memory?

    @Query("SELECT value FROM memories WHERE `key` = 'user_name' LIMIT 1")
    suspend fun getUserName(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory): Long

    @Query("UPDATE memories SET useCount = useCount + 1, lastUsed = :time WHERE id = :id")
    suspend fun incrementUse(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE memories SET value = :value WHERE `key` = :key")
    suspend fun updateValue(key: String, value: String)

    @Delete
    suspend fun delete(memory: Memory)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}
