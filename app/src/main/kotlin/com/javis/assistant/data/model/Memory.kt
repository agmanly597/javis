package com.javis.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryType {
    USER_NAME, PREFERENCE, FAVORITE_APP, COMMAND, HABIT, FACT, ROUTINE
}

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: MemoryType,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val useCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
