package com.javis.assistant.domain.repository

import com.javis.assistant.domain.model.MemoryCategory
import com.javis.assistant.domain.model.UserMemory
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun getAllMemories(): Flow<List<UserMemory>>
    fun getMemoriesByCategory(category: MemoryCategory): Flow<List<UserMemory>>
    suspend fun upsertMemory(memory: UserMemory)
    suspend fun deleteMemory(id: String)
    suspend fun clearAll()
    suspend fun searchMemories(query: String): List<UserMemory>
}
