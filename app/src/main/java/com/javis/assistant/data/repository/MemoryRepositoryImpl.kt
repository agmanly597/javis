package com.javis.assistant.data.repository

import com.javis.assistant.data.local.dao.MemoryDao
import com.javis.assistant.data.local.entities.UserMemoryEntity
import com.javis.assistant.domain.model.MemoryCategory
import com.javis.assistant.domain.model.UserMemory
import com.javis.assistant.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MemoryRepositoryImpl @Inject constructor(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override fun getAllMemories(): Flow<List<UserMemory>> =
        memoryDao.getAllMemories().map { it.map { e -> e.toDomain() } }

    override fun getMemoriesByCategory(category: MemoryCategory): Flow<List<UserMemory>> =
        memoryDao.getMemoriesByCategory(category.name).map { it.map { e -> e.toDomain() } }

    override suspend fun upsertMemory(memory: UserMemory) {
        memoryDao.upsertMemory(memory.toEntity())
    }

    override suspend fun deleteMemory(id: String) {
        memoryDao.deleteMemory(id)
    }

    override suspend fun clearAll() {
        memoryDao.clearAll()
    }

    override suspend fun searchMemories(query: String): List<UserMemory> =
        memoryDao.searchMemories(query).map { it.toDomain() }

    private fun UserMemoryEntity.toDomain() = UserMemory(
        id = id, key = key, value = value,
        category = MemoryCategory.valueOf(category), timestamp = timestamp
    )

    private fun UserMemory.toEntity() = UserMemoryEntity(
        id = id, key = key, value = value,
        category = category.name, timestamp = timestamp
    )
}
