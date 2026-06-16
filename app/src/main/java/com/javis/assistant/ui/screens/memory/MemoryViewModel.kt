package com.javis.assistant.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javis.assistant.domain.model.UserMemory
import com.javis.assistant.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    val memories = memoryRepository.getAllMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMemory(memory: UserMemory) = viewModelScope.launch {
        memoryRepository.deleteMemory(memory.id)
    }

    fun clearAll() = viewModelScope.launch {
        memoryRepository.clearAll()
    }
}
